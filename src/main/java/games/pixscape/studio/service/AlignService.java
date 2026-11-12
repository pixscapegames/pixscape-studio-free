package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.helper.QuadGeometryHelper;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.EditTransformCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public final class AlignService {
    public enum Operation {
        LEFT("Align Left"), RIGHT("Align Right"), TOP("Align Top"), BOTTOM("Align Bottom"),
        CENTER_HORIZONTAL("Center Horizontally"), CENTER_VERTICAL("Center Vertically"),
        PACK_HORIZONTAL("Pack Horizontally"), PACK_VERTICAL("Pack Vertically");

        private final String label;

        Operation(String label) {
            this.label = label;
        }

        boolean horizontal() {
            return this == LEFT || this == RIGHT || this == CENTER_HORIZONTAL || this == PACK_HORIZONTAL;
        }

        boolean packing() {
            return this == PACK_HORIZONTAL || this == PACK_VERTICAL;
        }

        float anchor(Bounds b) {
            return switch (this) {
                case LEFT, PACK_HORIZONTAL -> b.left;
                case RIGHT -> b.right;
                case TOP -> b.top;
                case BOTTOM, PACK_VERTICAL -> b.bottom;
                case CENTER_HORIZONTAL -> (b.left + b.right) * 0.5f;
                case CENTER_VERTICAL -> (b.bottom + b.top) * 0.5f;
            };
        }
    }

    private final WorldCanvas canvas;
    private final World world;
    private final SelectionService selectionService;
    private final HistoryManager historyManager;
    private final ComponentMapper<TransformComponent> transforms;
    private final ComponentMapper<GameObjectComponent> roots;
    private final ComponentMapper<GameObjectMemberComponent> members;
    private final GameObjectHierarchySystem hierarchy;
    private final GameObjectCompositionSystem composition;

    public AlignService(WorldCanvas canvas) {
        this.canvas = canvas;
        world = canvas.getEcsWorld();
        selectionService = canvas.getSelectionService();
        historyManager = canvas.getHistoryManager();
        transforms = world.getMapper(TransformComponent.class);
        roots = world.getMapper(GameObjectComponent.class);
        members = world.getMapper(GameObjectMemberComponent.class);
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        composition = world.getSystem(GameObjectCompositionSystem.class);
    }

    // All eight existing operations require two independent, usable targets and a reference.
    // The toolbar and every public command use this same admission path.

    /**
     * Cold command-time query. Game Object content requires one O(scene identities) membership
     * snapshot; UI refresh must use the explicitly post-ECS reader below instead.
     */
    public boolean isPlacementAvailable() {
        return prepare(false) != null;
    }

    /**
     * UI-only boundary: call after hierarchy, geometry and composition have completed their ECS step.
     */
    public boolean isPlacementAvailableAfterEcsStep() {
        return prepare(true) != null;
    }

    public void alignLeft() {
        execute(Operation.LEFT);
    }

    public void alignRight() {
        execute(Operation.RIGHT);
    }

    public void alignTop() {
        execute(Operation.TOP);
    }

    public void alignBottom() {
        execute(Operation.BOTTOM);
    }

    public void centerHorizontal() {
        execute(Operation.CENTER_HORIZONTAL);
    }

    public void centerVertical() {
        execute(Operation.CENTER_VERTICAL);
    }

    public void packHorizontal() {
        execute(Operation.PACK_HORIZONTAL);
    }

    public void packVertical() {
        execute(Operation.PACK_VERTICAL);
    }

    public void execute(Operation operation) {
        Placement placement = prepare(false);
        if (placement == null || operation == null) return;
        ArrayList<Target> targets = placement.targets;
        if (operation.packing()) {
            targets.sort(Comparator.comparingDouble((Target t) -> operation.anchor(t.bounds))
                    .thenComparingInt(t -> t.entityId));
        }
        float cursor = operation.anchor(operation.packing() ? targets.getFirst().bounds : placement.reference);
        ArrayList<Command> commands = new ArrayList<>();
        for (Target target : targets) {
            float delta = cursor - operation.anchor(target.bounds);
            if (!Float.isFinite(delta)) return;
            if (!operation.packing() && target.entityId == placement.referenceEntityId) continue;
            if (Math.abs(delta) >= 0.0001f) {
                float dx = operation.horizontal() ? delta : 0f;
                float dy = operation.horizontal() ? 0f : delta;
                Affine2 inverse = target.inverseParent;
                TransformComponent transform = transforms.get(target.entityId);
                float x = transform.x + inverse.m00 * dx + inverse.m01 * dy;
                float y = transform.y + inverse.m10 * dx + inverse.m11 * dy;
                // Reject the entire candidate before publishing any movement.
                if (!Float.isFinite(x) || !Float.isFinite(y)) return;
                EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
                EditTransformCommand command = new EditTransformCommand(world, historyManager.historyIds(),
                        target.entityId, TransformOp.MOVE, before, before.withX(x).withY(y));
                if (!command.isNoop()) commands.add(command);
            }
            if (operation.packing()) {
                cursor += operation.horizontal() ? target.bounds.right - target.bounds.left
                        : target.bounds.top - target.bounds.bottom;
            }
        }
        if (!commands.isEmpty()) historyManager.execute(new CompositeCommand(operation.label, commands));
    }

    private Placement prepare(boolean afterEcsStep) {
        SceneEditorContext context = canvas.getAttachedSceneContext();
        if (context == null || context.world() != world
                || !canvas.getStudioEditingModeService().allowsWorldEditingActions()
                || !selectionService.isEntityEditingContext()
                || selectionService.isTiledMapEditingTargetActive()
                || context.spatialBlockSelectionService().isEditingActive()
                || context.physicsSelectionService().isPhysicsEditingActive()) return null;
        IntArray selection = selectionService.getSelectionSnapshot();
        if (selection.size < 2) return null;
        Geometry geometry = new Geometry(afterEcsStep);
        IntSet selected = new IntSet();
        for (int i = 0; i < selection.size; i++) selected.add(selection.get(i));
        try {
            Map<Integer, Integer> representatives = new HashMap<>();
            int referenceId = representative(selectionService.getValidFirstSelectedEntityId(), selected,
                    representatives, 0);
            ArrayList<Target> targets = new ArrayList<>();
            Bounds reference = null;
            for (int i = 0; i < selection.size; i++) {
                int entity = selection.get(i);
                if (representative(entity, selected, representatives, 0) != entity) continue;
                if (!roots.has(entity) && !isImageOrAnimation(entity)) return null;
                if (selectionService.isLayerLocked(entity)) return null;
                Bounds bounds = geometry.bounds(entity, 0);
                if (bounds == null || !bounds.valid()) return null;
                int parent = parent(entity);
                Affine2 inverseParent = new Affine2();
                if (parent >= 0) {
                    inverseParent.set(geometry.frame(parent, 0).matrix);
                    float determinant = inverseParent.det();
                    if (!Float.isFinite(determinant)
                            || Math.abs(determinant) <= GameObjectTransformMath.DECOMPOSITION_EPSILON) return null;
                    inverseParent.inv();
                }
                targets.add(new Target(entity, bounds, inverseParent));
                if (entity == referenceId) reference = bounds;
            }
            return targets.size() >= 2 && reference != null
                    ? new Placement(targets, referenceId, reference) : null;
        } catch (IllegalArgumentException invalidHierarchy) {
            return null;
        }
    }

    private int representative(int entity, IntSet selected, Map<Integer, Integer> resolved, int depth) {
        requireActive(entity, depth);
        Integer cached = resolved.get(entity);
        if (cached != null) return cached;
        int parent = parent(entity);
        int ancestor = parent < 0 ? -1 : representative(parent, selected, resolved, depth + 1);
        int result = ancestor >= 0 ? ancestor : selected.contains(entity) ? entity : -1;
        resolved.put(entity, result);
        return result;
    }

    private int parent(int entity) {
        GameObjectMemberComponent member = members.getSafe(entity, null);
        if (member == null) return -1;
        IdentityRegistry identities = IdentityRegistry.boundTo(world);
        int parent = identities != null ? identities.findByStableId(member.parentStableId) : -1;
        requireActive(parent, 0);
        if (!roots.has(parent)) throw new IllegalArgumentException("Hierarchy parent must be a Game Object.");
        return parent;
    }

    private void requireActive(int entity, int depth) {
        if (depth >= 1024 || entity < 0 || !world.getEntityManager().isActive(entity)) {
            throw new IllegalArgumentException("Placement requires an active, acyclic hierarchy.");
        }
    }

    private boolean isImageOrAnimation(int entity) {
        return !world.getMapper(ParticleEmitterComponent.class).has(entity)
                && !world.getMapper(TiledLayerComponent.class).has(entity)
                && (world.getMapper(TextureRegionComponent.class).has(entity)
                || world.getMapper(AnimationComponent.class).has(entity));
    }

    private TransformComponent requireTransform(int entity) {
        TransformComponent local = transforms.getSafe(entity, null);
        if (local == null || !finite(local)) throw new IllegalArgumentException("Invalid transform.");
        if (roots.has(entity) && !GameObjectTransformMath.isPositiveUniformParentScale(local)) {
            throw new IllegalArgumentException("Invalid Game Object scale.");
        }
        return local;
    }

    private DimensionsComponent dimensions(int entity) {
        DimensionsComponent d = world.getMapper(DimensionsComponent.class).getSafe(entity, null);
        if (d != null && (!Float.isFinite(d.width) || !Float.isFinite(d.height) || d.width <= 0 || d.height <= 0)) {
            throw new IllegalArgumentException("Invalid dimensions.");
        }
        return d;
    }

    private void requireParentRestrictions(int entity, int parent, boolean unitScaleAncestry) {
        if (parent < 0) return;
        if (selectionService.isLayerLocked(parent)) throw new IllegalArgumentException("Locked ancestor.");
        if (world.getMapper(PhysicsBodyComponent.class).has(entity) && !unitScaleAncestry) {
            throw new IllegalArgumentException("Physics requires unit-scale ancestors.");
        }
    }

    /**
     * One evaluation only: memoized authored frames, never a second persistent geometry cache.
     */
    private final class Geometry {
        private final Map<Integer, Frame> worldFrames = new HashMap<>();
        private final boolean afterEcsStep;
        private IntMap<IntArray> childrenByParent;
        private final OrientedBoundsComponent scratchBounds = new OrientedBoundsComponent();
        private final float[] corners = new float[8];

        Geometry(boolean afterEcsStep) {
            this.afterEcsStep = afterEcsStep;
        }

        /**
         * One O(scene identities) snapshot at a command boundary, never on the UI frame path.
         */
        private void collectCurrentChildren() {
            IdentityRegistry identities = IdentityRegistry.boundTo(world);
            childrenByParent = CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(world, identities);
            // Preserve placement's validation of every collected relation, even outside the selection.
            for (IntMap.Entry<IntArray> entry : childrenByParent.entries()) {
                int parent = identities.findByStableId(entry.key);
                requireActive(parent, 0);
                if (!roots.has(parent)) throw new IllegalArgumentException("Hierarchy parent must be a Game Object.");
            }
        }

        Frame frame(int entity, int depth) {
            requireActive(entity, depth);
            Frame cached = worldFrames.get(entity);
            if (cached != null) return cached;
            TransformComponent local = requireTransform(entity);
            int parent = parent(entity);
            TransformComponent resolved = new TransformComponent();
            EditTransformCommand.Snapshot.capture(local).apply(resolved);
            Affine2 matrix = GameObjectTransformMath.toMemberFrame(local, roots.has(entity), new Affine2());
            boolean unitScaleAncestry = !roots.has(entity) || GameObjectTransformMath.isUnitParentScale(local);
            if (parent >= 0) {
                Frame parentFrame = frame(parent, depth + 1);
                TransformComponent parentWorld = parentFrame.transform;
                requireParentRestrictions(entity, parent, parentFrame.unitScaleAncestry);
                // Compose the actual matrices as Runtime does. Rebuilding a matrix from the
                // summed angle would introduce lookup-table rounding drift in nested parents.
                unitScaleAncestry &= parentFrame.unitScaleAncestry;
                GameObjectTransformMath.multiply(parentFrame.matrix, matrix, matrix);
                resolved.rotationRad += parentWorld.rotationRad;
                resolved.scaleX *= parentWorld.scaleX;
                resolved.scaleY *= parentWorld.scaleY;
            }
            resolved.x = matrix.m02;
            resolved.y = matrix.m12;
            if (roots.has(entity)) {
                resolved.x += -local.originX + matrix.m00 * local.originX + matrix.m01 * local.originY;
                resolved.y += -local.originY + matrix.m10 * local.originX + matrix.m11 * local.originY;
            }
            VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).getSafe(entity, null);
            boolean visible = (visibility == null || visibility.visible)
                    && (parent < 0 || worldFrames.get(parent).visible);
            Frame result = new Frame(resolved, matrix, unitScaleAncestry, visible);
            worldFrames.put(entity, result);
            return result;
        }

        Bounds bounds(int entity, int depth) {
            TransformComponent t = frame(entity, depth).transform;
            if (afterEcsStep) return publishedBounds(entity, depth);
            if (roots.has(entity)) {
                // Commands use current authored membership, never the last published child index.
                if (hierarchy == null || composition == null) return null;
                if (childrenByParent == null) collectCurrentChildren();
                Bounds union = null;
                IntArray children = childrenByParent.get(IdentityRegistry.boundTo(world).getStableId(entity));
                if (children == null) return null;
                for (int i = 0; i < children.size; i++) {
                    int child = children.get(i);
                    if (!world.getEntityManager().isActive(child)) continue;
                    // Physics restrictions apply even to hidden/non-drawable bodies.
                    if (world.getMapper(PhysicsBodyComponent.class).has(child)) frame(child, depth + 1);
                    if (!roots.has(child) && !drawable(child, depth + 1)) continue;
                    Bounds childBounds = bounds(child, depth + 1);
                    if (childBounds == null && !roots.has(child)) {
                        throw new IllegalArgumentException("Drawable content has no bounds.");
                    }
                    if (childBounds != null) {
                        if (!childBounds.valid()) throw new IllegalArgumentException("Invalid content bounds.");
                        union = union == null ? childBounds : union.union(childBounds);
                    }
                }
                return frame(entity, depth).visible ? union : null;
            }
            DimensionsComponent d = dimensions(entity);
            if (d == null) return null;
            OrientedBoundsComponent b = scratchBounds;
            OrientedBoundsHelper.setAxesAndExtents(b, d.width, d.height, t.rotationRad, t.scaleX, t.scaleY);
            OrientedBoundsHelper.setCenter(b, d.width, d.height, t.originX, t.originY,
                    t.x, t.y, t.scaleX, t.scaleY);
            QuadGeometryHelper.toWorldCorners(b, t,
                    world.getMapper(QuadDeformComponent.class).getSafe(entity, null), corners);
            float left = Float.POSITIVE_INFINITY, bottom = Float.POSITIVE_INFINITY;
            float right = Float.NEGATIVE_INFINITY, top = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 8; i += 2) {
                left = Math.min(left, corners[i]);
                right = Math.max(right, corners[i]);
                bottom = Math.min(bottom, corners[i + 1]);
                top = Math.max(top, corners[i + 1]);
            }
            return new Bounds(left, right, bottom, top);
        }

        private Bounds publishedBounds(int entity, int depth) {
            if (!roots.has(entity)) {
                if (dimensions(entity) == null) return null;
                AABBComponent a = world.getMapper(AABBComponent.class).getSafe(entity, null);
                return a == null ? null : new Bounds(a.minX, a.maxX, a.minY, a.maxY);
            }
            if (hierarchy == null || composition == null) return null;
            var state = composition.state();
            if (entity >= state.boundsResolved.length) return null;
            int parent = parent(entity);
            validatePublishedContent(entity, depth, parent < 0 || frame(parent, depth + 1).unitScaleAncestry);
            return state.hierarchyVisible[entity] && state.boundsResolved[entity]
                    ? new Bounds(state.minX[entity], state.maxX[entity], state.minY[entity], state.maxY[entity]) : null;
        }

        /**
         * O(selected subtrees), without per-descendant allocations or geometry reconstruction.
         */
        private void validatePublishedContent(int entity, int depth, boolean parentUnitScale) {
            requireActive(entity, depth);
            TransformComponent local = requireTransform(entity);
            var topology = hierarchy.topology();
            requireParentRestrictions(entity, topology.parentEntityId[entity], parentUnitScale);
            if (roots.has(entity)) {
                boolean unit = parentUnitScale && GameObjectTransformMath.isUnitParentScale(local);
                for (int child = topology.firstChildEntityId[entity]; child >= 0;
                     child = topology.nextSiblingEntityId[child]) {
                    if (roots.has(child) || world.getMapper(PhysicsBodyComponent.class).has(child)
                            || composition.contributesDrawableBounds(child)) {
                        validatePublishedContent(child, depth + 1, unit);
                    }
                }
            } else if (composition.contributesDrawableBounds(entity)) {
                AABBComponent a = world.getMapper(AABBComponent.class).getSafe(entity, null);
                if (dimensions(entity) == null || a == null || !validBounds(a.minX, a.maxX, a.minY, a.maxY)) {
                    throw new IllegalArgumentException("Drawable content has no valid bounds.");
                }
            }
        }

        private boolean drawable(int entity, int depth) {
            // Borrow only resource readiness, independent of hierarchy/pose/visibility publication.
            // Geometry and inherited visibility come from this evaluation's authored snapshot.
            DynamicEntityRenderState render = canvas.getDynamicEntityState();
            int slot = render != null ? render.renderSlotForEntity(entity) : DynamicEntityRenderState.NO_SLOT;
            return slot != DynamicEntityRenderState.NO_SLOT && render.enabled[slot]
                    && world.getMapper(AABBComponent.class).has(entity) && frame(entity, depth).visible;
        }
    }

    private static boolean finite(TransformComponent t) {
        return Float.isFinite(t.x) && Float.isFinite(t.y) && Float.isFinite(t.originX)
                && Float.isFinite(t.originY) && Float.isFinite(t.rotationRad)
                && Float.isFinite(t.scaleX) && Float.isFinite(t.scaleY);
    }

    private record Frame(TransformComponent transform, Affine2 matrix, boolean unitScaleAncestry, boolean visible) {
    }

    private record Placement(ArrayList<Target> targets, int referenceEntityId, Bounds reference) {
    }

    private record Target(int entityId, Bounds bounds, Affine2 inverseParent) {
    }

    private record Bounds(float left, float right, float bottom, float top) {
        boolean valid() {
            return validBounds(left, right, bottom, top);
        }

        Bounds union(Bounds other) {
            return new Bounds(Math.min(left, other.left), Math.max(right, other.right),
                    Math.min(bottom, other.bottom), Math.max(top, other.top));
        }
    }

    private static boolean validBounds(float left, float right, float bottom, float top) {
        return Float.isFinite(left) && Float.isFinite(right) && Float.isFinite(bottom)
                && Float.isFinite(top) && right > left && top > bottom;
    }
}
