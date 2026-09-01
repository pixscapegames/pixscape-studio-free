package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.GameObjectRootInitializer;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;

import java.util.ArrayList;
import java.util.List;

/** One history operation that wraps existing top-level scene items in a real Game Object root. */
public final class ConvertSelectionToGameObjectCommand implements Command {
    private record IndexSnapshot(long historyId, int layer, int z) { }

    private static final class MemberSnapshot {
        private final long historyId;
        private final int stableId;
        private final TransformComponent beforeWorld;
        private final TransformComponent afterLocal;
        private final int layer;
        private final int globalZ;
        private final int localZ;

        private MemberSnapshot(long historyId, int stableId, TransformComponent beforeWorld,
                               TransformComponent afterLocal, int layer, int globalZ, int localZ) {
            this.historyId = historyId;
            this.stableId = stableId;
            this.beforeWorld = beforeWorld;
            this.afterLocal = afterLocal;
            this.layer = layer;
            this.globalZ = globalZ;
            this.localZ = localZ;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identities;
    private final SelectionService selection;
    private final int layerIndex;
    private final String sourceAssetId;
    private final GameObjectRootInitializer rootInitializer;
    private final CreateEntityCommand createRoot;
    private final IntArray originalSelection;
    private final List<MemberSnapshot> members = new ArrayList<>();
    private final List<IndexSnapshot> originalOrder = new ArrayList<>();
    private final IntArray desiredTopToBottom = new IntArray();
    private final int sourceTag = EventFlow.tag(this);
    private long rootHistoryId = -1L;

    public ConvertSelectionToGameObjectCommand(
            World world, HistoryIdRegistry historyIds, IdentityRegistry identities,
            SelectionService selection, IntArray selectedTopToBottom,
            LayerLogicalOrderService.LayerOrder order, float rootX, float rootY,
            float rootOriginX, float rootOriginY, String sourceAssetId) {
        if (world == null || historyIds == null || identities == null || selection == null) {
            throw new IllegalArgumentException("World, history, identity and selection services are required.");
        }
        if (selectedTopToBottom == null || selectedTopToBottom.size == 0 || order == null) {
            throw new IllegalArgumentException("A non-empty contiguous logical selection is required.");
        }
        for (int i = 0; i < selectedTopToBottom.size; i++) {
            int entityId = selectedTopToBottom.get(i);
            if (world.getMapper(PhysicsBodyComponent.class).has(entityId)
                    || world.getMapper(PhysicsShapesComponent.class).has(entityId)) {
                throw new IllegalArgumentException(
                        "Physics conversion to Game Object assets remains unavailable until P3.");
            }
        }
        this.world = world;
        this.historyIds = historyIds;
        this.identities = identities;
        this.selection = selection;
        this.layerIndex = order.layerIndex();
        this.sourceAssetId = sourceAssetId;
        this.originalSelection = orderedSelection(selection);
        rootInitializer = new GameObjectRootInitializer(world)
                .configure(rootX, rootY, rootOriginX, rootOriginY, layerIndex);
        rootInitializer.setSourceAssetId(sourceAssetId);
        createRoot = new CreateEntityCommand(world, historyIds, rootInitializer, null);

        int first = indexOf(order, selectedTopToBottom.first());
        if (first < 0) throw new IllegalArgumentException("Selection is not in its Layer logical order.");
        for (int i = 0; i < order.items().size(); i++) {
            LayerLogicalOrderService.LogicalItem item = order.items().get(i);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(item.entityId());
            originalOrder.add(new IndexSnapshot(historyIds.ensureForEntity(item.entityId()),
                    index.layerIndex, index.zIndex));
            if (i == first) desiredTopToBottom.add(-1); // root placeholder
            if (!contains(selectedTopToBottom, item.entityId())) desiredTopToBottom.add(item.entityId());
        }
        TransformComponent rootWorld = new TransformComponent();
        rootWorld.x = rootX; rootWorld.y = rootY;
        rootWorld.originX = rootOriginX; rootWorld.originY = rootOriginY;
        rootWorld.scaleX = 1f; rootWorld.scaleY = 1f; rootWorld.rotationRad = 0f;
        rootWorld.refreshCaches();
        ComponentMapper<TransformComponent> transforms = world.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> identityMapper = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<GameObjectComponent> gameObjects = world.getMapper(GameObjectComponent.class);
        for (int i = 0; i < selectedTopToBottom.size; i++) {
            int entityId = selectedTopToBottom.get(i);
            PixscapeIdentityComponent identity = identityMapper.get(entityId);
            EntityIndexComponent index = indexes.get(entityId);
            TransformComponent before = copy(transforms.get(entityId));
            TransformComponent local = GameObjectTransformMath.worldToLocal(
                    rootWorld, before, gameObjects.has(entityId), new TransformComponent());
            members.add(new MemberSnapshot(historyIds.ensureForEntity(entityId), identity.stableId,
                    before, local, index.layerIndex, index.zIndex,
                    selectedTopToBottom.size - 1 - i));
        }
    }

    @Override public String label() { return "Convert Selection to Game Object"; }

    @Override
    public void redo() {
        if (rootHistoryId < 0L) {
            rootInitializer.setIdentityStableId(identities.allocateStableId());
        }
        createRoot.redo();
        int root = createRoot.getCreatedEntityId();
        rootHistoryId = historyIds.historyIdOfEntity(root);
        try {
            for (MemberSnapshot snapshot : members) {
                int entityId = resolve(snapshot.historyId);
                if (world.getMapper(GameObjectMemberComponent.class).has(entityId)) {
                    throw new IllegalStateException("Selected item is no longer top-level.");
                }
                apply(world.getMapper(TransformComponent.class).get(entityId), snapshot.afterLocal);
                EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entityId);
                index.layerIndex = layerIndex;
                index.zIndex = snapshot.localZ;
                world.getMapper(GameObjectMemberComponent.class).create(entityId)
                        .parentStableId = rootInitializer.stableId();
                dirty(entityId);
            }
            applyConvertedOrder(root);
            selection.selectOnly(root);
        } catch (RuntimeException | Error failure) {
            rollbackRedo(root);
            throw failure;
        }
    }

    @Override
    public void undo() {
        int root = historyIds.entityOfHistoryId(rootHistoryId);
        for (int i = members.size() - 1; i >= 0; i--) {
            MemberSnapshot snapshot = members.get(i);
            int entityId = resolve(snapshot.historyId);
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(entityId, null);
            if (member == null || member.parentStableId != rootInitializer.stableId()) {
                throw new IllegalStateException("Game Object membership changed before conversion undo.");
            }
            world.getMapper(GameObjectMemberComponent.class).remove(entityId);
            apply(world.getMapper(TransformComponent.class).get(entityId), snapshot.beforeWorld);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entityId);
            index.layerIndex = snapshot.layer;
            index.zIndex = snapshot.globalZ;
            dirty(entityId);
        }
        if (root >= 0 && world.getEntityManager().isActive(root)) createRoot.undo();
        restoreOriginalOrder();
        selection.replaceSelection(originalSelection, SelectionService.SelectionSource.VIEWPORT);
    }

    private void applyConvertedOrder(int rootEntityId) {
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        int count = desiredTopToBottom.size;
        for (int i = 0; i < count; i++) {
            int entityId = desiredTopToBottom.get(i);
            if (entityId < 0) entityId = rootEntityId;
            EntityIndexComponent index = indexes.get(entityId);
            index.layerIndex = layerIndex;
            index.zIndex = count - 1 - i;
            dirty(entityId);
        }
        EventFlow.i().publish(new EventFlow.EntityZOrderChanged(layerIndex, sourceTag));
    }

    private void restoreOriginalOrder() {
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        for (IndexSnapshot snapshot : originalOrder) {
            int entityId = historyIds.entityOfHistoryId(snapshot.historyId);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) continue;
            EntityIndexComponent index = indexes.getSafe(entityId, null);
            if (index == null) continue;
            index.layerIndex = snapshot.layer;
            index.zIndex = snapshot.z;
            dirty(entityId);
        }
        EventFlow.i().publish(new EventFlow.EntityZOrderChanged(layerIndex, sourceTag));
    }

    private void rollbackRedo(int root) {
        for (int i = members.size() - 1; i >= 0; i--) {
            MemberSnapshot snapshot = members.get(i);
            int entityId = historyIds.entityOfHistoryId(snapshot.historyId);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) continue;
            world.getMapper(GameObjectMemberComponent.class).remove(entityId);
            apply(world.getMapper(TransformComponent.class).get(entityId), snapshot.beforeWorld);
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entityId);
            index.layerIndex = snapshot.layer; index.zIndex = snapshot.globalZ;
        }
        if (root >= 0 && world.getEntityManager().isActive(root)) createRoot.undo();
        restoreOriginalOrder();
        selection.replaceSelection(originalSelection, SelectionService.SelectionSource.VIEWPORT);
    }

    private int resolve(long historyId) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            throw new IllegalStateException("Selected entity is no longer active.");
        }
        return entityId;
    }

    private void dirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
            dirty.layer(entityId);
            dirty.order(entityId);
        }
    }

    private static int indexOf(LayerLogicalOrderService.LayerOrder order, int entityId) {
        for (int i = 0; i < order.items().size(); i++) {
            if (order.items().get(i).entityId() == entityId) return i;
        }
        return -1;
    }

    private static boolean contains(IntArray values, int value) {
        for (int i = 0; i < values.size; i++) if (values.get(i) == value) return true;
        return false;
    }

    private static IntArray orderedSelection(SelectionService selection) {
        IntArray snapshot = selection.getSelectionSnapshot();
        IntArray ordered = new IntArray(false, snapshot.size);
        int primary = selection.getFirstSelectedEntityId();
        if (primary >= 0) ordered.add(primary);
        for (int i = 0; i < snapshot.size; i++) {
            int entityId = snapshot.get(i);
            if (entityId != primary) ordered.add(entityId);
        }
        return ordered;
    }

    private static TransformComponent copy(TransformComponent source) {
        TransformComponent copy = new TransformComponent();
        apply(copy, source);
        return copy;
    }

    private static void apply(TransformComponent target, TransformComponent source) {
        target.x = source.x; target.y = source.y; target.rotationRad = source.rotationRad;
        target.scaleX = source.scaleX; target.scaleY = source.scaleY;
        target.originX = source.originX; target.originY = source.originY;
        target.refreshCaches();
    }
}
