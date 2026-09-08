package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;

/** Atomically detaches one member into top-level world space without recreating it. */
public final class RemoveEntityFromGameObjectCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identities;
    private final long childHistoryId;
    private final int parentStableId;
    private final TransformComponent beforeLocal;
    private final TransformComponent afterWorld;
    private final int beforeLayer;
    private final int beforeLocalZ;
    private final int afterLayer;
    private final int afterZ;
    private final int sourceTag = EventFlow.tag(this);

    public RemoveEntityFromGameObjectCommand(
            World world, HistoryIdRegistry historyIds, IdentityRegistry identities,
            int childEntityId) {
        this(world, historyIds, identities, childEntityId, null);
    }

    public RemoveEntityFromGameObjectCommand(
            World world, HistoryIdRegistry historyIds, IdentityRegistry identities,
            int childEntityId, Integer detachedZ) {
        if (world == null || historyIds == null || identities == null) {
            throw new IllegalArgumentException("World and identity registries are required.");
        }
        GameObjectHierarchyCommandSupport.requireActive(world, childEntityId, "child");
        GameObjectHierarchyCommandSupport.requireCore(world, childEntityId, "child");
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(childEntityId, null);
        if (member == null) throw new IllegalArgumentException("Entity is not a Game Object member.");
        GameObjectHierarchyCommandSupport.requireSupportedMember(world, childEntityId);
        this.world = world;
        this.historyIds = historyIds;
        this.identities = identities;
        childHistoryId = historyIds.ensureForEntity(childEntityId);
        parentStableId = member.parentStableId;
        TransformComponent authored = world.getMapper(TransformComponent.class).get(childEntityId);
        beforeLocal = GameObjectHierarchyCommandSupport.copy(authored);
        afterWorld = GameObjectHierarchyCommandSupport.worldTransform(
                world, identities, childEntityId);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(childEntityId);
        beforeLayer = index.layerIndex;
        beforeLocalZ = index.zIndex;
        int root = GameObjectHierarchyCommandSupport.topLevelRoot(
                world, identities, childEntityId);
        EntityIndexComponent rootIndex = world.getMapper(EntityIndexComponent.class).get(root);
        afterLayer = rootIndex.layerIndex;
        int requestedZ = detachedZ != null ? detachedZ
                : rootIndex.zIndex < SortKey64.MAX_Z ? rootIndex.zIndex + 1 : rootIndex.zIndex;
        if (requestedZ < SortKey64.MIN_Z || requestedZ > SortKey64.MAX_Z) {
            throw new IllegalArgumentException("Detached global z is outside Runtime range.");
        }
        afterZ = requestedZ;
    }

    @Override public String label() { return "Remove Entity from Game Object"; }

    @Override
    public void redo() {
        int child = resolve();
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(child, null);
        if (member == null || member.parentStableId != parentStableId) {
            throw new IllegalStateException("Game Object membership changed before detach redo.");
        }
        world.getMapper(GameObjectMemberComponent.class).remove(child);
        GameObjectHierarchyCommandSupport.apply(
                world.getMapper(TransformComponent.class).get(child), afterWorld);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(child);
        index.layerIndex = afterLayer;
        index.zIndex = afterZ;
        dirty(child);
    }

    @Override
    public void undo() {
        int child = resolve();
        if (world.getMapper(GameObjectMemberComponent.class).has(child)) {
            throw new IllegalStateException("Entity was reparented before detach undo.");
        }
        int parent = identities.findByStableId(parentStableId);
        GameObjectHierarchyCommandSupport.requireAttachCandidate(world, identities, child, parent);
        GameObjectHierarchyCommandSupport.apply(
                world.getMapper(TransformComponent.class).get(child), beforeLocal);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(child);
        index.layerIndex = beforeLayer;
        index.zIndex = beforeLocalZ;
        world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = parentStableId;
        dirty(child);
    }

    private int resolve() {
        int child = historyIds.entityOfHistoryId(childHistoryId);
        GameObjectHierarchyCommandSupport.requireActive(world, child, "child");
        return child;
    }

    private void dirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
            dirty.layer(entityId);
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.EntityZOrderChanged(
                world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex, sourceTag));
    }
}
