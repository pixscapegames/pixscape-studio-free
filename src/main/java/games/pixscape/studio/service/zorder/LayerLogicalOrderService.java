package games.pixscape.studio.service.zorder;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;

import java.util.ArrayList;
import java.util.List;

/** Derives top-level Studio z order for one layer without mutating the world. */
public final class LayerLogicalOrderService {
    private final World world;
    private final ComponentMapper<EntityIndexComponent> indexes;
    private final ComponentMapper<PhysicsJointComponent> joints;
    private final ComponentMapper<GameObjectMemberComponent> members;

    public LayerLogicalOrderService(World world) {
        this.world = world;
        indexes = world.getMapper(EntityIndexComponent.class);
        joints = world.getMapper(PhysicsJointComponent.class);
        members = world.getMapper(GameObjectMemberComponent.class);
    }

    public LayerOrder derive(int layerIndex) { return derive(layerIndex, null); }

    public LayerOrder derive(int layerIndex, IntArray additionalEntityIds) {
        IntBag bag = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class)
                        .exclude(LayerComponent.class, PhysicsJointComponent.class,
                                GameObjectMemberComponent.class)).getEntities();
        IntArray candidates = new IntArray(false,
                bag.size() + (additionalEntityIds != null ? additionalEntityIds.size : 0));
        IntSet included = new IntSet(Math.max(1, candidates.items.length));
        for (int i = 0; i < bag.size(); i++) {
            int entityId = bag.get(i);
            if (world.getEntityManager().isActive(entityId) && included.add(entityId)) {
                candidates.add(entityId);
            }
        }
        if (additionalEntityIds != null) {
            ComponentMapper<PixscapeIdentityComponent> identities =
                    world.getMapper(PixscapeIdentityComponent.class);
            ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
            for (int i = 0; i < additionalEntityIds.size; i++) {
                int entityId = additionalEntityIds.get(i);
                if (world.getEntityManager().isActive(entityId)
                        && indexes.has(entityId) && identities.has(entityId)
                        && !layers.has(entityId) && !joints.has(entityId)
                        && !members.has(entityId) && included.add(entityId)) {
                    candidates.add(entityId);
                }
            }
        }

        List<LogicalItem> items = new ArrayList<>();
        for (int i = 0; i < candidates.size; i++) {
            int entityId = candidates.get(i);
            EntityIndexComponent index = indexes.get(entityId);
            if (index.layerIndex == layerIndex) items.add(new LogicalItem(entityId, index.zIndex));
        }
        items.sort((first, second) -> first.effectiveZ != second.effectiveZ
                ? Integer.compare(second.effectiveZ, first.effectiveZ)
                : Integer.compare(second.entityId, first.entityId));
        return new LayerOrder(layerIndex, items);
    }

    public static final class LayerOrder {
        private final int layerIndex;
        private final List<LogicalItem> items;

        private LayerOrder(int layerIndex, List<LogicalItem> items) {
            this.layerIndex = layerIndex;
            this.items = items;
        }

        public int layerIndex() { return layerIndex; }
        public List<LogicalItem> items() { return List.copyOf(items); }
        public IntArray flattenedTopToBottom() { return flatten(items); }

        public IntArray moveEntity(int entityId, int direction) {
            if (direction != -1 && direction != 1) return null;
            int itemIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).entityId == entityId) { itemIndex = i; break; }
            }
            int siblingIndex = itemIndex + direction;
            if (itemIndex < 0 || siblingIndex < 0 || siblingIndex >= items.size()) return null;
            List<LogicalItem> moved = new ArrayList<>(items);
            LogicalItem sibling = moved.get(siblingIndex);
            moved.set(siblingIndex, moved.get(itemIndex));
            moved.set(itemIndex, sibling);
            return flatten(moved);
        }

        private static IntArray flatten(List<LogicalItem> ordered) {
            IntArray result = new IntArray(ordered.size());
            for (LogicalItem item : ordered) result.add(item.entityId);
            return result;
        }
    }

    public static final class LogicalItem {
        private final int entityId;
        private final int effectiveZ;
        private LogicalItem(int entityId, int effectiveZ) {
            this.entityId = entityId; this.effectiveZ = effectiveZ;
        }
        public int entityId() { return entityId; }
        public IntArray members() { return new IntArray(new int[]{entityId}); }
    }
}
