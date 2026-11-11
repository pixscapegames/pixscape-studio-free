package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.GameObjectHierarchySystem;

/** Studio hierarchy reads: current authored relations at action boundaries, published topology in frame readers. */
public final class CurrentGameObjectRelations {
    private CurrentGameObjectRelations() {}

    /** Current authored root, O(ancestry depth), no allocation or global scan.
     * Command/interaction boundary only; published frame readers use Runtime topology instead.
     * A broken relation never falls back to the child's historical layer index.
     */
    public static int rootEntityId(World world, IdentityRegistry identities, int entityId) {
        var members = world.getMapper(GameObjectMemberComponent.class);
        int current = entityId;
        for (int depth = 0; depth < 1024; depth++) {
            if (current < 0 || !world.getEntityManager().isActive(current))
                throw new IllegalArgumentException("Layer ownership requires an active hierarchy root.");
            GameObjectMemberComponent member = members.getSafe(current, null);
            if (member == null) return current;
            current = identities != null ? identities.findByStableId(member.parentStableId) : -1;
            if (current < 0 || !world.getMapper(GameObjectComponent.class).has(current))
                throw new IllegalArgumentException("Layer ownership requires a valid Game Object parent.");
        }
        throw new IllegalArgumentException("Layer ownership hierarchy is cyclic or exceeds the depth limit.");
    }

    /** O(1) ownership from published topology for frame readers; never refreshes ECS.
     * Missing/unpublished member relations return -1 instead of using historical child indices.
     */
    public static int publishedLayerIndex(World world, int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return -1;
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        var topology = hierarchy != null ? hierarchy.topology() : null;
        int root = entityId;
        if (topology != null && entityId < topology.getEntityCapacity() && topology.parented[entityId]) {
            root = topology.rootEntityId[entityId];
        } else if (world.getMapper(GameObjectMemberComponent.class).has(entityId)) {
            return -1;
        }
        if (root < 0 || !world.getEntityManager().isActive(root)) return -1;
        var index = world.getMapper(EntityIndexComponent.class).getSafe(root, null);
        return index != null ? index.layerIndex : -1;
    }

    /** Stored descendant indices remain serialization/history data, not ownership authority. */
    public static int layerIndex(World world, int entityId) {
        int root = rootEntityId(world, IdentityRegistry.boundTo(world), entityId);
        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(root, null);
        if (layer != null) return layer.layerIndex;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).getSafe(root, null);
        if (index == null) throw new IllegalArgumentException("Layer ownership root has no EntityIndex.");
        return index.layerIndex;
    }

    /**
     * Collects current members of active indexed entities, keyed by their authored parent stable ID.
     * Includes indexed creations before ECS publication and excludes Studio deletions unindexed
     * immediately. Raw ECS mutations outside that identity lifecycle are not implicitly covered.
     * Invalid parent IDs are retained for consumer validation; no selection or content filter applies.
     * Traverses the identity index once and allocates command-local arrays; do not retain the result
     * between preparations or validations. Values contain ECS entity IDs, not stable IDs.
     */
    public static IntMap<IntArray> collectCurrentChildrenByParentStableId(
            World world, IdentityRegistry identities) {
        if (world == null || identities == null) {
            throw new IllegalArgumentException("World and scene identities are required.");
        }
        IntMap<IntArray> childrenByParent = new IntMap<>();
        IntArray entities = new IntArray();
        identities.collectIndexedEntityIds(entities);
        var members = world.getMapper(GameObjectMemberComponent.class);
        for (int i = 0; i < entities.size; i++) {
            int entity = entities.get(i);
            GameObjectMemberComponent member = members.getSafe(entity, null);
            if (member == null) continue;
            IntArray children = childrenByParent.get(member.parentStableId);
            if (children == null) {
                children = new IntArray(false, 4);
                childrenByParent.put(member.parentStableId, children);
            }
            children.add(entity);
        }
        return childrenByParent;
    }
}
