package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;

import java.util.Arrays;

/** Normalizes supported V1 clipboard roots without mutating the Scene. */
public final class ClipboardSelectionNormalizer {
    private final World world;
    private final ComponentMapper<GameObjectComponent> roots;
    private final ComponentMapper<GameObjectMemberComponent> members;
    private final ComponentMapper<PixscapeIdentityComponent> identities;

    public ClipboardSelectionNormalizer(World world) {
        this.world = world;
        roots = world.getMapper(GameObjectComponent.class);
        members = world.getMapper(GameObjectMemberComponent.class);
        identities = world.getMapper(PixscapeIdentityComponent.class);
    }

    public IntArray normalize(IntArray selection) {
        IntSet selected = new IntSet();
        if (selection != null) for (int i = 0; i < selection.size; i++) selected.add(selection.get(i));
        IntIntMap entityByStableId = entityByStableId();
        IntArray candidates = new IntArray();
        for (IntSet.IntSetIterator it = selected.iterator(); it.hasNext;) candidates.add(it.next());
        sortByStableIdentity(candidates);
        IntArray result = new IntArray(false, candidates.size);
        for (int i = 0; i < candidates.size; i++) {
            int entityId = candidates.get(i);
            if (!world.getEntityManager().isActive(entityId)) throw new IllegalArgumentException("Clipboard selection contains an inactive entity.");
            if (members.has(entityId) && !roots.has(entityId)) {
                if (hasSelectedAncestor(entityId, selected, entityByStableId)) continue;
                throw new IllegalArgumentException("Copying a Game Object member alone is not supported in V1.");
            }
            if (roots.has(entityId) && hasSelectedAncestor(entityId, selected, entityByStableId)) continue;
            result.add(entityId);
        }
        return result;
    }

    private boolean hasSelectedAncestor(int entityId, IntSet selected, IntIntMap entityByStableId) {
        int current = entityId;
        for (int depth = 0; depth < 1024; depth++) {
            GameObjectMemberComponent member = members.getSafe(current, null);
            if (member == null) return false;
            int parent = entityByStableId.get(member.parentStableId, -1);
            if (parent < 0) throw new IllegalArgumentException("Game Object parent is missing.");
            if (selected.contains(parent)) return true;
            current = parent;
        }
        throw new IllegalArgumentException("Game Object hierarchy depth limit exceeded.");
    }

    private IntIntMap entityByStableId() {
        IntIntMap entityByStableId = new IntIntMap();
        IntBag bag = world.getAspectSubscriptionManager().get(
                Aspect.all(PixscapeIdentityComponent.class)).getEntities();
        for (int i = 0; i < bag.size(); i++) {
            PixscapeIdentityComponent identity = identities.get(bag.get(i));
            if (identity != null && identity.stableId > 0) {
                entityByStableId.put(identity.stableId, bag.get(i));
            }
        }
        return entityByStableId;
    }

    private void sortByStableIdentity(IntArray candidates) {
        long[] order = new long[candidates.size];
        for (int i = 0; i < candidates.size; i++) {
            int entityId = candidates.get(i);
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            int stableId = identity != null && identity.stableId > 0
                    ? identity.stableId : Integer.MAX_VALUE;
            order[i] = ((long) stableId << 32) | (entityId & 0xffffffffL);
        }
        Arrays.sort(order);
        for (int i = 0; i < candidates.size; i++) candidates.set(i, (int) order[i]);
    }
}
