package games.pixscape.studio.service.zorder;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives top-level Studio z order for one layer without mutating the world.
 */
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

    public LayerOrder derive(int layerIndex) {
        return derive(layerIndex, null);
    }

    public LayerOrder derive(int layerIndex, IntArray additionalEntityIds) {
        // Cold Studio query: current identity/component state, never published membership.
        var registry = games.pixscape.runtime.service.IdentityRegistry.boundTo(world);
        if (registry == null) throw new IllegalStateException("Logical order requires scene identities.");
        IntArray candidates = new IntArray();
        registry.collectIndexedEntityIds(candidates);
        // Asset instantiation can supply freshly materialized entities inside a composite command.
        if (additionalEntityIds != null) candidates.addAll(additionalEntityIds);
        IntSet included = new IntSet();
        var identities = world.getMapper(PixscapeIdentityComponent.class);
        var layers = world.getMapper(LayerComponent.class);
        List<LogicalItem> items = new ArrayList<>();
        for (int i = 0; i < candidates.size; i++) {
            int entityId = candidates.get(i);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)
                    || !included.add(entityId) || !indexes.has(entityId) || !identities.has(entityId)
                    || layers.has(entityId) || joints.has(entityId) || members.has(entityId)) continue;
            EntityIndexComponent index = indexes.get(entityId);
            if (index.layerIndex == layerIndex) items.add(new LogicalItem(entityId, index.zIndex));
        }
        items.sort((first, second) -> compareTopToBottom(first.entityId, first.effectiveZ,
                second.entityId, second.effectiveZ));
        return new LayerOrder(layerIndex, items);
    }

    public static int compareTopToBottom(int firstEntity, int firstZ, int secondEntity, int secondZ) {
        return firstZ != secondZ ? Integer.compare(secondZ, firstZ) : Integer.compare(secondEntity, firstEntity);
    }

    public static final class LayerOrder {
        private final int layerIndex;
        private final List<LogicalItem> items;

        private LayerOrder(int layerIndex, List<LogicalItem> items) {
            this.layerIndex = layerIndex;
            this.items = items;
        }

        public int layerIndex() {
            return layerIndex;
        }

        public List<LogicalItem> items() {
            return List.copyOf(items);
        }

        public IntArray flattenedTopToBottom() {
            return flatten(items);
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
            this.entityId = entityId;
            this.effectiveZ = effectiveZ;
        }

        public int entityId() {
            return entityId;
        }

        public IntArray members() {
            return new IntArray(new int[]{entityId});
        }
    }
}
