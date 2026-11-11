package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.service.IdentityRegistry;

/** One-shot authored relations for Studio command preparation, never for frame processing. */
public final class CurrentGameObjectRelations {
    private CurrentGameObjectRelations() {}

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
