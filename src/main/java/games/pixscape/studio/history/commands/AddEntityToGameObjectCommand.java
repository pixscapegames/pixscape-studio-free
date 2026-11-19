package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;

/**
 * Atomically attaches one existing entity while preserving its world appearance and identity.
 */
public final class AddEntityToGameObjectCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identities;
    private final long childHistoryId;
    private final long parentHistoryId;
    private final int parentStableId;
    private final boolean promoteParent;
    private final TransformComponent beforeParentLocal;
    private final TransformComponent afterParentLocal;
    private final TransformComponent beforeWorld;
    private final TransformComponent afterLocal;
    private final int beforeLayer;
    private final int beforeZ;
    private final int afterLocalZ;
    private final int sourceTag = EventFlow.tag(this);
    private final SpatialRepresentationInitialization representations;

    public AddEntityToGameObjectCommand(
            World world, HistoryIdRegistry historyIds, IdentityRegistry identities,
            int childEntityId, int parentEntityId, int localZ) {
        if (world == null || historyIds == null || identities == null) {
            throw new IllegalArgumentException("World and identity registries are required.");
        }
        GameObjectHierarchyCommandSupport.requireAttachCandidate(
                world, identities, childEntityId, parentEntityId);
        if (localZ < SortKey64.MIN_Z || localZ > SortKey64.MAX_Z) {
            throw new IllegalArgumentException("Game Object local z is outside Runtime range.");
        }
        this.world = world;
        this.historyIds = historyIds;
        this.identities = identities;
        promoteParent = !world.getMapper(GameObjectComponent.class).has(parentEntityId);
        beforeParentLocal = GameObjectHierarchyCommandSupport.copy(world.getMapper(TransformComponent.class).get(parentEntityId));
        afterParentLocal = promoteParent
                ? GameObjectHierarchyCommandSupport.promotedParentLocal(world, identities, parentEntityId)
                : beforeParentLocal;
        childHistoryId = historyIds.ensureForEntity(childEntityId);
        parentHistoryId = historyIds.ensureForEntity(parentEntityId);
        parentStableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(parentEntityId).stableId;
        beforeWorld = GameObjectHierarchyCommandSupport.worldTransform(
                world, identities, childEntityId);
        TransformComponent parentWorld = GameObjectHierarchyCommandSupport.worldTransform(
                world, identities, parentEntityId);
        afterLocal = GameObjectTransformMath.worldToLocal(
                parentWorld, beforeWorld,
                world.getMapper(GameObjectComponent.class).has(childEntityId),
                new TransformComponent());
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(childEntityId);
        beforeLayer = index.layerIndex;
        beforeZ = index.zIndex;
        afterLocalZ = localZ;
        representations = new SpatialRepresentationInitialization(world, historyIds);
        if (SpatialRepresentationInitialization.hasFootprintOwner(world, parentEntityId)) {
            representations.prepareSubtree(childEntityId);
        }
    }

    @Override
    public String label() {
        return "Add Entity to Game Object";
    }

    @Override
    public void redo() {
        int child = resolve(childHistoryId, "child");
        int parent = resolve(parentHistoryId, "parent");
        GameObjectHierarchyCommandSupport.requireAttachCandidate(world, identities, child, parent);
        if (world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId != parentStableId) {
            throw new IllegalStateException("Game Object parent stable identity changed.");
        }
        representations.apply(false);
        if (promoteParent) {
            world.getMapper(GameObjectComponent.class).create(parent);
            GameObjectHierarchyCommandSupport.apply(world.getMapper(TransformComponent.class).get(parent), afterParentLocal);
            dirty(parent, beforeParentLocal, afterParentLocal);
        }
        GameObjectHierarchyMutation.applyPreparedLocalPose(world, child, parentStableId, afterLocal);
        world.getMapper(EntityIndexComponent.class).get(child).zIndex = afterLocalZ;
        dirty(child, beforeWorld, afterLocal);
    }

    @Override
    public void undo() {
        int child = resolve(childHistoryId, "child");
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(child, null);
        if (member == null || member.parentStableId != parentStableId) {
            throw new IllegalStateException("Game Object membership changed before attach undo.");
        }
        representations.apply(true);
        GameObjectHierarchyMutation.applyPreparedLocalPose(world, child, 0, beforeWorld);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(child);
        index.layerIndex = beforeLayer;
        index.zIndex = beforeZ;
        if (promoteParent) {
            int parent = resolve(parentHistoryId, "parent");
            world.getMapper(GameObjectComponent.class).remove(parent);
            GameObjectHierarchyCommandSupport.apply(world.getMapper(TransformComponent.class).get(parent), beforeParentLocal);
            dirty(parent, beforeParentLocal, afterParentLocal);
        }
        dirty(child, beforeWorld, afterLocal);
    }

    private int resolve(long historyId, String role) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        GameObjectHierarchyCommandSupport.requireActive(world, entityId, role);
        return entityId;
    }

    private void dirty(int entityId, TransformComponent before, TransformComponent after) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
            dirty.layer(entityId);
            dirty.order(entityId);
        }
        GameObjectHierarchyCommandSupport.notifyTransformChanged(entityId, before, after, sourceTag);
        EventFlow.i().publish(new EventFlow.EntityZOrderChanged(
                CurrentGameObjectRelations.layerIndex(world, entityId), sourceTag));
    }
}
