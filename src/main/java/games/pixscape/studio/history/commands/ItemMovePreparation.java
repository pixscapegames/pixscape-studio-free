package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;

import java.util.*;

/** Cold, detached preparation for Items moves. No identity allocation, commands, or ECS publication.
 * All IDs exposed here are stable IDs within the owning Scene document. Body/Joint information
 * rows are not sources: the UI supplies the owning item only for actual item rows.
 */
public final class ItemMovePreparation {
    /**
     * Absolute/relative tolerance for the authored-TRS conversion round trip, not a bound on
     * Runtime matrix positions or displayed pixels. Runtime multiplies each ancestor's frame;
     * recomposing accumulated angles uses different quantized trigonometric samples.
     * Small positional drift is accepted, including on descendants of a moved group, rather
     * than decomposing matrices or compensating descendant transforms during drag preparation.
     * Its world-space magnitude depends on offsets/scales/depth; screen-space drift also depends
     * on zoom. Undo/redo restores the captured authored poses exactly, without recomputation.
     */
    public static final float WORLD_POSE_EPSILON = 0.0001f;

    /** insertionIndex counts siblings AFTER removing the normalized sources, frontmost first.
     * parentStableId == 0 denotes the layer root. Before/after a group uses that group's parent.
     */
    public record Destination(OpenEditorDocument document, int layerStableId, int parentStableId, int insertionIndex) {}
    public record Result(Prepared move, String refusal) {
        public boolean accepted() { return move != null; }
    }
    public record Pose(float x, float y, float originX, float originY, float rotation, float scaleX, float scaleY) {
        static Pose read(TransformComponent t) {
            if (t == null) throw new IllegalArgumentException("Item requires a transform.");
            Pose value = new Pose(t.x, t.y, t.originX, t.originY, t.rotationRad, t.scaleX, t.scaleY);
            for (float f : value.values()) if (!Float.isFinite(f)) throw new IllegalArgumentException("Item transform must be finite.");
            return value;
        }
        TransformComponent component() {
            TransformComponent t = new TransformComponent();
            t.x = x; t.y = y; t.originX = originX; t.originY = originY;
            t.rotationRad = rotation; t.scaleX = scaleX; t.scaleY = scaleY; t.refreshCaches();
            return t;
        }
        private float[] values() { return new float[]{x, y, originX, originY, rotation, scaleX, scaleY}; }
        boolean closeTo(Pose other) {
            float[] a = values(), b = other.values();
            for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > WORLD_POSE_EPSILON * Math.max(1, Math.max(Math.abs(a[i]), Math.abs(b[i])))) return false;
            return true;
        }
    }
    /** storedLayer retains historical member indices; effective ownership is given by the group. */
    public record State(int stableId, int parentStableId, int storedLayer, int z, Pose pose) {}
    public record Change(State before, State after, int beforeEffectiveLayerStableId, int afterEffectiveLayerStableId) {}
    /** A parent group has layerStableId == 0; a layer-root group has parentStableId == 0. */
    public record Group(int layerStableId, int parentStableId) {}
    public record Order(Group group, List<Integer> before, List<Integer> after) {
        public Order { before = List.copyOf(before); after = List.copyOf(after); }
    }
    private record Layer(int stableId, int index, boolean locked, boolean spatial) {}

    public static final class Prepared {
        final SceneEditorDocument owner;
        final World world;
        final long generation;
        final List<Integer> requested;
        final Destination destination;
        final List<Integer> sources;
        final List<Change> changes;
        final List<Order> orders;
        final List<State> guards;
        final Set<Integer> subtreeIds;
        final List<Layer> layers;

        private Prepared(SceneEditorDocument owner, List<Integer> requested, Destination destination,
                         List<Integer> sources, List<Change> changes, List<Order> orders,
                         List<State> guards, Set<Integer> subtreeIds, List<Layer> layers) {
            this.owner = owner; this.world = owner.context().world(); this.generation = owner.context().generation();
            this.requested = List.copyOf(requested); this.destination = destination;
            this.sources = List.copyOf(sources); this.changes = List.copyOf(changes);
            this.orders = List.copyOf(orders); this.guards = List.copyOf(guards); this.layers = List.copyOf(layers);
            this.subtreeIds = Set.copyOf(subtreeIds);
        }
        public List<Integer> sources() { return sources; }
        public List<Change> changes() { return changes; }
        public List<Order> orders() { return orders; }
        public Destination destination() { return destination; }
        public boolean isNoop() { return changes.isEmpty(); }
        public MoveItemsCommand command() { return new MoveItemsCommand(this); }
        List<Integer> affectedLayerIndices() { return layers.stream().map(Layer::index).toList(); }
        /** Drop boundary: recompute and compare the detached result; never silently retarget. */
        public boolean isCurrent() {
            if (!sameDocument()) return false;
            Result current = prepare(owner, requested, destination);
            if (!current.accepted()) return false;
            Prepared p = current.move;
            return sources.equals(p.sources) && changes.equals(p.changes) && orders.equals(p.orders)
                    && guards.equals(p.guards) && layers.equals(p.layers);
        }
        boolean sameDocument() {
            return !owner.isClosed() && owner.context().isInitialized()
                    && owner.context().world() == world && owner.context().generation() == generation;
        }
        /** All validation precedes the command's writes, including undo/redo on restored ECS IDs. */
        boolean validate(boolean undo) {
            if (!sameDocument()) return false;
            try {
                Current current = new Current(owner);
                Set<Integer> currentSubtrees = new HashSet<>();
                for (int source : sources) current.collectSubtree(source, currentSubtrees);
                if (!subtreeIds.equals(currentSubtrees)) return false;
                Map<Integer, State> expected = new HashMap<>();
                Map<Integer, Change> byId = new HashMap<>();
                for (Change change : changes) byId.put(change.before.stableId, change);
                for (State state : guards) expected.put(state.stableId, state);
                if (undo) for (Change change : changes) expected.put(change.after.stableId, change.after);
                for (State state : expected.values()) if (!state.equals(current.states.get(state.stableId))) return false;
                for (Layer layer : layers) if (!layer.equals(current.layers.get(layer.stableId)) || layer.locked) return false;
                // Z/parent values are checked above. Restored ECS IDs may change the legacy
                // tie-break without changing authored state, so history checks membership here.
                for (Order order : orders) if (!new HashSet<>(current.order(order.group))
                        .equals(new HashSet<>(undo ? order.after : order.before))) return false;
                for (int source : sources) {
                    Change change = byId.get(source);
                    State target = change == null ? current.states.get(source) : undo ? change.before : change.after;
                    int layer = current.layerOf(source);
                    // During undo the old parent/layer supplies the destination, not the historical member index.
                    if (target.parentStableId != 0) layer = current.layerOf(target.parentStableId);
                    else layer = current.layerByIndex(target.storedLayer).stableId;
                    current.validateSource(source, target.parentStableId, layer);
                }
                return true;
            } catch (IllegalArgumentException | IllegalStateException ex) { return false; }
        }
    }

    public static Result prepare(OpenEditorDocument document, List<Integer> sourceStableIds, Destination destination) {
        try {
            if (!(document instanceof SceneEditorDocument owner) || owner.isClosed() || !owner.context().isInitialized())
                throw new IllegalArgumentException("Items moves require an open Scene document; HUD and Game Object assets are excluded.");
            if (destination == null || destination.document != document)
                throw new IllegalArgumentException("Source and destination must belong to the same Scene document.");
            if (sourceStableIds == null || sourceStableIds.isEmpty()) throw new IllegalArgumentException("Select at least one item.");
            Current current = new Current(owner);
            Layer targetLayer = current.requireLayer(destination.layerStableId);
            int parent = destination.parentStableId;
            if (parent != 0 && current.layerOf(parent) != targetLayer.stableId)
                throw new IllegalArgumentException("The destination parent belongs to another layer.");
            Set<Integer> selected = new HashSet<>(sourceStableIds);
            for (int id : selected) if (!current.states.containsKey(id)) throw new IllegalArgumentException("A source is missing or is not an item.");
            List<Integer> sources = new ArrayList<>();
            // Preorder gives deterministic reading order across layers/parents and removes selected descendants.
            Set<Integer> covered = new HashSet<>();
            for (int id : current.readingOrder) {
                State state = current.states.get(id);
                if (covered.contains(state.parentStableId)) covered.add(id);
                else if (selected.contains(id)) { sources.add(id); covered.add(id); }
            }
            if (sources.isEmpty()) throw new IllegalArgumentException("No movable source items.");
            Group targetGroup = new Group(parent == 0 ? targetLayer.stableId : 0, parent);
            LinkedHashMap<Group, List<Integer>> before = new LinkedHashMap<>();
            for (int id : sources) {
                current.validateSource(id, parent, targetLayer.stableId);
                Group group = current.groupOf(id); before.putIfAbsent(group, current.order(group));
            }
            before.putIfAbsent(targetGroup, current.order(targetGroup));
            Map<Group, List<Integer>> after = new LinkedHashMap<>();
            Set<Integer> moving = new HashSet<>(sources);
            before.forEach((group, list) -> after.put(group, new ArrayList<>(list.stream().filter(id -> !moving.contains(id)).toList())));
            List<Integer> target = after.get(targetGroup);
            if (destination.insertionIndex < 0 || destination.insertionIndex > target.size())
                throw new IllegalArgumentException("Insertion must be between remaining siblings, after removing sources.");
            target.addAll(destination.insertionIndex, sources);
            List<Order> orders = new ArrayList<>();
            Map<Integer, State> updated = new HashMap<>();
            for (int id : sources) {
                State old = current.states.get(id);
                Pose pose = old.pose;
                if (old.parentStableId != parent) {
                    TransformComponent original = GameObjectHierarchyCommandSupport.worldTransform(current.world, current.ids, current.entity(id));
                    boolean root = current.world.getMapper(GameObjectComponent.class).has(current.entity(id));
                    TransformComponent parentWorld = parent == 0 ? null : GameObjectHierarchyCommandSupport.worldTransform(current.world, current.ids, current.entity(parent));
                    TransformComponent local = parent == 0 ? original : GameObjectTransformMath.worldToLocal(parentWorld, original, root, new TransformComponent());
                    TransformComponent restored = parent == 0 ? local : GameObjectTransformMath.localToWorld(parentWorld, local, root, new TransformComponent());
                    if (!Pose.read(original).closeTo(Pose.read(restored))) throw new IllegalArgumentException("Parent conversion exceeds world-pose tolerance.");
                    pose = Pose.read(local);
                }
                updated.put(id, new State(id, parent, parent == 0 ? targetLayer.index : old.storedLayer, old.z, pose));
            }
            for (var entry : before.entrySet()) {
                Group group = entry.getKey(); List<Integer> next = after.get(group);
                orders.add(new Order(group, entry.getValue(), next));
                // Identical order is a true no-op, even for sparse or tied Z values.
                if (entry.getValue().equals(next)) continue;
                if (next.size() - 1 > SortKey64.MAX_Z) throw new IllegalArgumentException("Too many siblings for Runtime Z range.");
                for (int i = 0; i < next.size(); i++) {
                    int id = next.get(i); State state = updated.getOrDefault(id, current.states.get(id));
                    updated.put(id, new State(id, state.parentStableId, state.storedLayer, next.size() - 1 - i, state.pose));
                }
            }
            List<Change> changes = new ArrayList<>();
            Set<Integer> guarded = new HashSet<>(covered);
            before.values().forEach(guarded::addAll);
            for (int id : sources) current.guardAncestors(id, guarded);
            if (parent != 0) current.guardAncestors(parent, guarded);
            for (int id : current.readingOrder) {
                State old = current.states.get(id), next = updated.get(id);
                if (next != null && !old.equals(next)) changes.add(new Change(old, next,
                        current.layerOf(id), moving.contains(id) ? targetLayer.stableId : current.layerOf(id)));
            }
            List<State> guards = guarded.stream().sorted().map(current.states::get).toList();
            Set<Integer> layerIds = new TreeSet<>(); layerIds.add(targetLayer.stableId);
            for (int id : sources) layerIds.add(current.layerOf(id));
            return new Result(new Prepared(owner, sourceStableIds, destination, sources, changes, orders,
                    guards, covered, layerIds.stream().map(current.layers::get).toList()), null);
        } catch (IllegalArgumentException | IllegalStateException ex) { return new Result(null, ex.getMessage()); }
    }

    /** One preparation-local projection, discarded after preparation/validation; never a scene cache. */
    private static final class Current {
        final World world;
        final IdentityRegistry ids;
        final Map<Integer, State> states = new HashMap<>();
        final Map<Integer, Layer> layers = new HashMap<>();
        final Map<Integer, Layer> layersByIndex = new HashMap<>();
        final Map<Group, List<Integer>> groups = new HashMap<>();
        final Map<Integer, Integer> effectiveLayers = new HashMap<>();
        final List<Integer> readingOrder = new ArrayList<>();
        final IntMap<IntArray> children = new IntMap<>();

        Current(SceneEditorDocument document) {
            world = document.context().world(); ids = document.context().identityRegistry();
            IntArray entities = new IntArray(); ids.collectIndexedEntityIds(entities);
            for (int i = 0; i < entities.size; i++) {
                int entity = entities.get(i), stable = ids.getStableId(entity);
                LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(entity, null);
                if (layer != null) {
                    LayerMetaComponent meta = world.getMapper(LayerMetaComponent.class).getSafe(entity, null);
                    Layer value = new Layer(stable, layer.layerIndex, meta != null && meta.locked, layer.spatialEnabled);
                    layers.put(stable, value); layersByIndex.put(layer.layerIndex, value); continue;
                }
                EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).getSafe(entity, null);
                if (index == null || world.getMapper(PhysicsJointComponent.class).has(entity)) continue;
                var member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
                states.put(stable, new State(stable, member == null ? 0 : member.parentStableId,
                        index.layerIndex, index.zIndex, Pose.read(world.getMapper(TransformComponent.class).getSafe(entity, null))));
            }
            for (State state : states.values()) {
                if (state.parentStableId != 0 && (!states.containsKey(state.parentStableId)
                        || !world.getMapper(GameObjectComponent.class).has(entity(state.parentStableId))))
                    throw new IllegalArgumentException("Item hierarchy has an invalid parent.");
                groups.computeIfAbsent(groupOf(state.stableId), key -> new ArrayList<>()).add(state.stableId);
                if (state.parentStableId != 0) {
                    IntArray siblings = children.get(state.parentStableId);
                    if (siblings == null) { siblings = new IntArray(); children.put(state.parentStableId, siblings); }
                    siblings.add(entity(state.stableId));
                }
            }
            groups.forEach((group, list) -> list.sort((a, b) -> group.parentStableId == 0
                    ? LayerLogicalOrderService.compareTopToBottom(entity(a), states.get(a).z, entity(b), states.get(b).z)
                    : LayerLogicalOrderService.compareTopToBottom(a, states.get(a).z, b, states.get(b).z)));
            List<Layer> orderedLayers = layers.values().stream().sorted(Comparator.comparingInt(Layer::index).reversed()).toList();
            for (Layer layer : orderedLayers) for (int root : order(new Group(layer.stableId, 0))) visit(root, layer.stableId, 0);
            if (readingOrder.size() != states.size()) throw new IllegalArgumentException("Item hierarchy is cyclic or unreachable.");
        }
        void visit(int id, int layer, int depth) {
            if (depth >= 1024 || effectiveLayers.putIfAbsent(id, layer) != null) throw new IllegalArgumentException("Invalid hierarchy depth or cycle.");
            readingOrder.add(id);
            for (int child : order(new Group(0, id))) visit(child, layer, depth + 1);
        }
        int entity(int stable) { int e = ids.findByStableId(stable); if (e < 0) throw new IllegalArgumentException("Item no longer exists."); return e; }
        Layer requireLayer(int stable) {
            Layer layer = layers.get(stable);
            if (layer == null || layer.locked) throw new IllegalArgumentException("Source or destination layer is missing or locked.");
            return layer;
        }
        Layer layerByIndex(int index) {
            Layer layer = layersByIndex.get(index);
            if (layer == null) throw new IllegalArgumentException("Item layer no longer exists.");
            return layer;
        }
        int layerOf(int id) {
            Integer layer = effectiveLayers.get(id);
            if (layer == null) throw new IllegalArgumentException("Destination must be an item in this document.");
            return layer;
        }
        Group groupOf(int id) {
            State state = states.get(id);
            return new Group(state.parentStableId == 0 ? layerByIndex(state.storedLayer).stableId : 0, state.parentStableId);
        }
        List<Integer> order(Group group) { return groups.getOrDefault(group, List.of()); }
        void collectSubtree(int id, Set<Integer> result) {
            if (!states.containsKey(id)) throw new IllegalArgumentException("Source no longer exists.");
            result.add(id);
            for (int child : order(new Group(0, id))) collectSubtree(child, result);
        }
        void guardAncestors(int id, Set<Integer> result) {
            while (id != 0) { result.add(id); id = states.get(id).parentStableId; }
        }
        void validateSource(int source, int parent, int layer) {
            requireLayer(layerOf(source)); Layer target = requireLayer(layer);
            int entity = entity(source);
            if (parent != 0) GameObjectHierarchyCommandSupport.requireReparentCandidate(world, ids, entity, entity(parent), children);
            else if (world.getMapper(GameObjectComponent.class).has(entity)) GameObjectHierarchyCommandSupport.validateCurrentSubtree(world, ids, entity, children);
            validateSpatialSubtree(source, target.spatial);
        }
        void validateSpatialSubtree(int id, boolean spatial) {
            int entity = entity(id);
            if (!spatial) {
                if (world.getMapper(SpatialHeightComponent.class).has(entity)) throw new IllegalArgumentException("Spatial actors require a Spatial-enabled destination layer; data will not be removed.");
                var shapes = world.getMapper(PhysicsShapesComponent.class).getSafe(entity, null);
                if (shapes != null) for (var shape : shapes.shapes) if (shape.spatialFootprint)
                    throw new IllegalArgumentException("Spatial footprints require a Spatial-enabled destination layer.");
            }
            for (int child : order(new Group(0, id))) validateSpatialSubtree(child, spatial);
        }
    }
}
