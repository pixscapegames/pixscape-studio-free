package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialBlockCommand;

/** One detached interactive wall edit; authoritative ECS data is never used as preview storage. */
public final class SpatialWallEditSession {
    private static final int CLAMP_ITERATIONS = 16;

    private boolean active;
    private int layerEntityId = SpatialBlockSelectionService.NO_MAP;
    private int wallId = SpatialBlockSelectionService.NO_BLOCK;
    private SpatialBlocksComponent committed;
    private TiledMapLayerData map;
    private SpatialBlockData before;
    private SpatialBlockData candidate;
    private SpatialStructureTopology.Plan candidateTopology;
    private SpatialWallAttachments attachments;
    private boolean candidateValid;
    private String rejectionReason;
    private final boolean[] handleEnabled =
            new boolean[SpatialBlockInteractiveEditSupport.ResizeHandle.values().length];

    public boolean begin(int layerEntityId,
                         int wallId,
                         SpatialBlocksComponent source,
                         TiledMapLayerData map) {
        cancel();
        SpatialBlockData selected = find(source, wallId);
        if (layerEntityId < 0 || selected == null || map == null) return false;
        this.layerEntityId = layerEntityId;
        this.wallId = wallId;
        this.committed = detached(source);
        this.map = map;
        this.before = selected.copy();
        this.candidate = before.copy();
        this.attachments = SpatialWallAttachments.derive(this.committed, find(this.committed, wallId));
        Evaluation initial = evaluate(candidate, false);
        if (!initial.valid) {
            cancel();
            return false;
        }
        publish(initial, null);
        active = true;
        computeHandleAvailability();
        return true;
    }

    public boolean updateMove(float dx, float dy) {
        if (!active) return false;
        SpatialBlockData requested = before.copy();
        if (attachments.isAttached()) {
            if (!attachments.canSlide()) return reject("The established junctions have no common slide range.");
            float delta = attachments.slideAxis() == SpatialWallAttachments.Axis.HORIZONTAL ? dx : dy;
            delta = attachments.clampSlideDelta(delta);
            requested.x = before.x + (attachments.slideAxis() == SpatialWallAttachments.Axis.HORIZONTAL ? delta : 0f);
            requested.y = before.y + (attachments.slideAxis() == SpatialWallAttachments.Axis.VERTICAL ? delta : 0f);
        } else if (!SpatialBlockInteractiveEditSupport.move(requested, before, dx, dy)) {
            return publish(evaluate(before, false), null);
        }
        if (sameFootprint(requested, before)) return publish(evaluate(before, false), null);
        return publish(evaluate(requested, true), "Wall move is not a valid Spatial V3 edit.");
    }

    public boolean updateResize(SpatialBlockInteractiveEditSupport.ResizeHandle handle, float gx, float gy) {
        if (!active || handle == null) return false;
        if (!isHandleEnabled(handle)) return reject("This resize handle is locked by an established junction.");

        SpatialBlockData requested = before.copy();
        if (!SpatialBlockInteractiveEditSupport.resize(requested, before, handle, gx, gy)) {
            return publish(evaluate(before, false), null);
        }
        Evaluation requestedEvaluation = evaluate(requested, false);
        if (requestedEvaluation.valid) return publish(requestedEvaluation, null);

        SpatialBlockData best = before.copy();
        Evaluation bestEvaluation = evaluate(best, false);
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < CLAMP_ITERATIONS; i++) {
            float amount = (low + high) * 0.5f;
            SpatialBlockData trial = interpolate(before, requested, amount);
            Evaluation trialEvaluation = evaluate(trial, false);
            if (trialEvaluation.valid) {
                low = amount;
                best = trial;
                bestEvaluation = trialEvaluation;
            } else {
                high = amount;
            }
        }
        return publish(bestEvaluation,
                sameFootprint(best, before) ? "No junction-preserving resize range is available."
                        : "Resize stopped at the junction-preserving boundary.");
    }

    public boolean updateHeight(float height) {
        if (!active || !SpatialWallGeometry.isFinite(height)) return false;
        SpatialBlockData requested = before.copy();
        requested.height = Math.max(0f, height);
        return publish(evaluate(requested, true), "Structure height edit is invalid.");
    }

    public boolean updateProperty(Float x, Float y, Float width, Float depth) {
        if (!active) return false;
        SpatialBlockData requested = before.copy();
        if (x != null) requested.x = x;
        if (y != null) requested.y = y;
        if (width != null) requested.width = width;
        if (depth != null) requested.depth = depth;
        if (changesLockedLongitudinalEnd(requested)) {
            return reject("The requested value moves a longitudinal end locked by a junction.");
        }
        if (attachments.isAttached() && (Float.compare(requested.x, before.x) != 0
                || Float.compare(requested.y, before.y) != 0)) {
            if (!attachments.canSlide()) return reject("The established junctions have no common slide range.");
            float dx = requested.x - before.x;
            float dy = requested.y - before.y;
            if (attachments.slideAxis() == SpatialWallAttachments.Axis.HORIZONTAL) {
                if (Float.compare(dy, 0f) != 0
                        || Float.compare(attachments.clampSlideDelta(dx), dx) != 0) {
                    return reject("Attached wall movement must remain inside its horizontal slide range.");
                }
            } else if (Float.compare(dx, 0f) != 0
                    || Float.compare(attachments.clampSlideDelta(dy), dy) != 0) {
                return reject("Attached wall movement must remain inside its vertical slide range.");
            }
        }
        return publish(evaluate(requested, true), "The requested footprint would break an established junction.");
    }

    public boolean commit(World world, HistoryManager history, SpatialBlockSelectionService selection) {
        if (!active || !candidateValid || world == null || history == null) {
            cancel();
            return false;
        }
        EditSpatialBlockCommand command = new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerEntityId, wallId, before, candidate);
        boolean changed = !command.isNoop();
        if (changed) history.execute(command);
        cancel();
        return changed;
    }

    public void cancel() {
        active = false;
        layerEntityId = SpatialBlockSelectionService.NO_MAP;
        wallId = SpatialBlockSelectionService.NO_BLOCK;
        committed = null;
        map = null;
        before = null;
        candidate = null;
        candidateTopology = null;
        attachments = null;
        candidateValid = false;
        rejectionReason = null;
        for (int i = 0; i < handleEnabled.length; i++) handleEnabled[i] = false;
    }

    public boolean isActive() { return active; }
    public int layerEntityId() { return layerEntityId; }
    public int wallId() { return wallId; }
    public SpatialBlockData before() { return before != null ? before.copy() : null; }
    public SpatialBlockData candidate() { return candidate; }
    public SpatialStructureTopology.Plan candidateTopology() { return candidateTopology; }
    public boolean isCandidateValid() { return candidateValid; }
    public String rejectionReason() { return rejectionReason; }
    public SpatialWallAttachments attachments() { return attachments; }
    public boolean canMove() { return active && (!attachments.isAttached() || attachments.canSlide()); }
    public boolean isSlidingAttachedWall() { return active && attachments.isAttached() && attachments.canSlide(); }

    public boolean isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        return active && handle != null && handleEnabled[handle.ordinal()];
    }

    private void computeHandleAvailability() {
        SpatialBlockInteractiveEditSupport.ResizeHandle[] handles =
                SpatialBlockInteractiveEditSupport.ResizeHandle.values();
        for (int i = 0; i < handles.length; i++) {
            SpatialBlockInteractiveEditSupport.ResizeHandle handle = handles[i];
            handleEnabled[i] = !movesLockedLongitudinalEnd(handle)
                    && (!attachments.isAttached() || hasNonZeroValidResize(handle));
        }
    }

    private boolean hasNonZeroValidResize(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        float step = Math.max(SpatialWallGeometry.GEOMETRY_EPSILON * 8f,
                Math.min(before.width, before.depth) * 0.02f);
        for (int attempt = 0; attempt < 4; attempt++) {
            float dx = (attempt & 1) == 0 ? step : -step;
            float dy = (attempt & 2) == 0 ? step : -step;
            float gx = handleMovesMinX(handle) ? before.x + dx
                    : handleMovesMaxX(handle) ? before.x + before.width + dx : before.x;
            float gy = handleMovesMinY(handle) ? before.y + dy
                    : handleMovesMaxY(handle) ? before.y + before.depth + dy : before.y;
            SpatialBlockData trial = before.copy();
            if (SpatialBlockInteractiveEditSupport.resize(trial, before, handle, gx, gy)
                    && evaluate(trial, false).valid) return true;
        }
        return false;
    }

    private Evaluation evaluate(SpatialBlockData requested, boolean compile) {
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.edit(committed, wallId, requested, map);
        if (!plan.valid) return Evaluation.failure(plan.error);
        SpatialBlocksComponent planned = new SpatialBlocksComponent();
        planned.blocks = copy(plan.walls);
        SpatialBlockData normalized = find(planned, wallId);
        if (normalized == null || !attachments.preservesAll(normalized, planned)) {
            return Evaluation.failure("The edit would remove an established wall junction.");
        }
        if (compile) {
            SpatialStructureCompilation.Result compilation = SpatialStructureCompilation.tryCompile(plan.walls, map);
            if (!compilation.success()) return Evaluation.failure(compilation.diagnostic());
        }
        return Evaluation.success(plan, normalized.copy());
    }

    private boolean publish(Evaluation evaluation, String fallbackReason) {
        if (evaluation == null || !evaluation.valid) {
            return reject(evaluation != null && evaluation.reason != null ? evaluation.reason : fallbackReason);
        }
        candidate = evaluation.wall;
        candidateTopology = evaluation.plan;
        candidateValid = true;
        rejectionReason = null;
        return true;
    }

    private boolean reject(String reason) {
        candidateValid = false;
        rejectionReason = reason != null ? reason : "Invalid Spatial V3 wall edit.";
        return false;
    }

    private boolean movesLockedLongitudinalEnd(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        if (attachments.isHorizontal()) {
            return attachments.minLongitudinalLocked() && handleMovesMinX(handle)
                    || attachments.maxLongitudinalLocked() && handleMovesMaxX(handle);
        }
        return attachments.minLongitudinalLocked() && handleMovesMinY(handle)
                || attachments.maxLongitudinalLocked() && handleMovesMaxY(handle);
    }

    private boolean changesLockedLongitudinalEnd(SpatialBlockData requested) {
        if (attachments.isHorizontal()) {
            return attachments.minLongitudinalLocked() && Float.compare(requested.x, before.x) != 0
                    || attachments.maxLongitudinalLocked()
                    && Float.compare(requested.x + requested.width, before.x + before.width) != 0;
        }
        return attachments.minLongitudinalLocked() && Float.compare(requested.y, before.y) != 0
                || attachments.maxLongitudinalLocked()
                && Float.compare(requested.y + requested.depth, before.y + before.depth) != 0;
    }

    private static boolean handleMovesMinX(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        return handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MIN_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MAX_Y;
    }

    private static boolean handleMovesMaxX(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        return handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MIN_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MAX_Y;
    }

    private static boolean handleMovesMinY(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        return handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MIN_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MIN_Y;
    }

    private static boolean handleMovesMaxY(SpatialBlockInteractiveEditSupport.ResizeHandle handle) {
        return handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MAX_Y
                || handle == SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MAX_Y;
    }

    private static SpatialBlockData interpolate(SpatialBlockData from, SpatialBlockData to, float amount) {
        SpatialBlockData result = from.copy();
        result.x = lerp(from.x, to.x, amount);
        result.y = lerp(from.y, to.y, amount);
        result.width = lerp(from.width, to.width, amount);
        result.depth = lerp(from.depth, to.depth, amount);
        return result;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static boolean sameFootprint(SpatialBlockData a, SpatialBlockData b) {
        return Float.compare(a.x, b.x) == 0 && Float.compare(a.y, b.y) == 0
                && Float.compare(a.width, b.width) == 0 && Float.compare(a.depth, b.depth) == 0;
    }

    private static SpatialBlocksComponent detached(SpatialBlocksComponent source) {
        SpatialBlocksComponent result = new SpatialBlocksComponent();
        result.blocks = SpatialStructureTopology.copyWalls(source);
        result.nextSpatialBlockId = source != null ? source.nextSpatialBlockId : 1;
        result.revision = source != null ? source.revision : 0;
        return result;
    }

    private static Array<SpatialBlockData> copy(Array<SpatialBlockData> source) {
        Array<SpatialBlockData> result = new Array<>(SpatialBlockData[]::new);
        for (int i = 0; i < source.size; i++) result.add(source.get(i).copy());
        return result;
    }

    private static SpatialBlockData find(SpatialBlocksComponent component, int id) {
        if (component == null || component.blocks == null) return null;
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall != null && wall.id == id) return wall;
        }
        return null;
    }

    private static final class Evaluation {
        final boolean valid;
        final String reason;
        final SpatialStructureTopology.Plan plan;
        final SpatialBlockData wall;

        private Evaluation(boolean valid, String reason, SpatialStructureTopology.Plan plan, SpatialBlockData wall) {
            this.valid = valid;
            this.reason = reason;
            this.plan = plan;
            this.wall = wall;
        }

        static Evaluation success(SpatialStructureTopology.Plan plan, SpatialBlockData wall) {
            return new Evaluation(true, null, plan, wall);
        }

        static Evaluation failure(String reason) {
            return new Evaluation(false, reason, null, null);
        }
    }
}
