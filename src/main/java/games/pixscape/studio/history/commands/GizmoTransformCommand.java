package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager.SupportsNoop;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * History command for a gizmo drag (MOVE / ROTATE / SCALE).
 * Identifies entities by historyId and applies before/after snapshots.
 */
public final class GizmoTransformCommand implements Command, SupportsNoop {

    private static final String DEBUG_HISTORY_PROP = "pixscape.debug.history";

    /**
     * Full Transform snapshot for an entity.
     */
    public static final class Snapshot {
        public float x, y;
        public float rotRad;
        public float sx, sy;
        public float ox, oy;

        public Snapshot() {
        }

        public Snapshot(float x, float y,
                        float rotRad,
                        float sx, float sy,
                        float ox, float oy) {
            this.x = x;
            this.y = y;
            this.rotRad = rotRad;
            this.sx = sx;
            this.sy = sy;
            this.ox = ox;
            this.oy = oy;
        }

        public static Snapshot of(TransformComponent t) {
            return new Snapshot(
                    t.x, t.y,
                    t.rotationRad,
                    t.scaleX, t.scaleY,
                    t.originX, t.originY
            );
        }

        public void applyTo(TransformComponent t) {
            t.x = x;
            t.y = y;
            t.rotationRad = rotRad;
            t.scaleX = sx;
            t.scaleY = sy;
            t.originX = ox;
            t.originY = oy;
        }
    }

    /**
     * 1 entry = 1 entity (historyId) + before / after snapshots.
     */
    private record Entry(long historyId, Snapshot before, Snapshot after) {
    }

    private final World world;
    private final TransformOp op;
    private final HistoryIdRegistry historyIds;
    private final List<Entry> entries = new ArrayList<>();
    private final DirtyTrackerSystem dirtyTracker;
    private final int sourceTag = EventFlow.tag(this);

    public GizmoTransformCommand(World world, HistoryIdRegistry historyIds, TransformOp op) {
        this.world = world;
        this.historyIds = historyIds;
        this.op = Objects.requireNonNull(op, "TransformOp cannot be null");
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
        debug("ctor op=" + op + " entryCount=" + entries.size());
    }

    @Override
    public String label() {
        switch (op) {
            case MOVE:
                return "Gizmo Move";
            case ROTATE:
                return "Gizmo Rotate";
            case SCALE:
                return "Gizmo Scale";
            default:
                return "Gizmo Transform";
        }
    }

    public TransformOp op() {
        return op;
    }

    /**
     * Adds an entry (historyId + before/after).
     * If before == after, do not add it (no-op for this entity).
     */
    public void addEntry(long historyId, Snapshot before, Snapshot after) {
        if (before == null || after == null) {
            debug("addEntry skipped null historyId=" + historyId);
            return;
        }

        boolean same = before.x == after.x
                && before.y == after.y
                && before.rotRad == after.rotRad
                && before.sx == after.sx
                && before.sy == after.sy
                && before.ox == after.ox
                && before.oy == after.oy;

        if (debugEnabled() && op == TransformOp.MOVE) {
            debug("addEntry MOVE historyId=" + historyId
                    + " same=" + same
                    + " before=(" + before.x + "," + before.y + ")"
                    + " after=(" + after.x + "," + after.y + ")");
        }

        if (same) return;

        entries.add(new Entry(historyId, before, after));
    }

    @Override
    public boolean isNoop() {
        boolean noop = entries.isEmpty();
        debug("isNoop=" + noop);
        return noop;
    }

    @Override
    public void undo() {
        apply(true);
    }

    @Override
    public void redo() {
        apply(false);
    }

    private void apply(boolean toBefore) {
        debug((toBefore ? "undo" : "redo") + " apply label=" + label());
        ComponentMapper<TransformComponent> mT = world.getMapper(TransformComponent.class);

        for (Entry e : entries) {
            int entityId = historyIds.entityOfHistoryId(e.historyId);
            if (entityId == -1 || !world.getEntityManager().isActive(entityId)) {
                continue; // entity deleted in the meantime, ignore
            }

            TransformComponent t = mT.get(entityId);
            if (t == null) continue;

            if (debugEnabled() && op == TransformOp.MOVE) {
                debug("MOVE before historyId=" + e.historyId + " x=" + t.x + " y=" + t.y);
            }
            (toBefore ? e.before : e.after).applyTo(t);
            if (debugEnabled() && op == TransformOp.MOVE) {
                debug("MOVE after historyId=" + e.historyId + " x=" + t.x + " y=" + t.y);
            }
            if (dirtyTracker != null) dirtyTracker.geometry(entityId, GeometryDirty.ALL);
            EventFlow.i().publish(new EventFlow.EntityChanged(entityId, op, sourceTag));
        }
    }

    private static boolean debugEnabled() {
        return Boolean.getBoolean(DEBUG_HISTORY_PROP);
    }

    private static void debug(String msg) {
        if (!debugEnabled()) return;
        System.out.println("[GizmoTransformCommand] " + msg);
    }

}
