package games.pixscape.studio.service.zorder;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives the authoritative Studio logical z order for one layer without mutating the world. */
public final class LayerLogicalOrderService {
    private final World world;
    private final ComponentMapper<EntityIndexComponent> indexes;
    private final ComponentMapper<PrefabInstanceComponent> prefabs;

    public LayerLogicalOrderService(World world) {
        this.world = world;
        this.indexes = world.getMapper(EntityIndexComponent.class);
        this.prefabs = world.getMapper(PrefabInstanceComponent.class);
    }

    public LayerOrder derive(int layerIndex) {
        return derive(layerIndex, null);
    }

    /**
     * Derives an order while including entities created after an already-materialized Artemis
     * subscription was last synchronized. The additional IDs are used only for this read.
     */
    public LayerOrder derive(int layerIndex, IntArray additionalEntityIds) {
        IntBag bag = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class)
                        .exclude(LayerComponent.class)).getEntities();
        int[] data = bag.getData();
        IntArray candidates = new IntArray(false,
                bag.size() + (additionalEntityIds != null ? additionalEntityIds.size : 0));
        IntSet included = new IntSet(candidates.items.length);
        for (int i = 0; i < bag.size(); i++) {
            int entityId = data[i];
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
                        && indexes.has(entityId)
                        && identities.has(entityId)
                        && !layers.has(entityId)
                        && included.add(entityId)) {
                    candidates.add(entityId);
                }
            }
        }

        Map<Integer, GroupCandidate> groups =
                collectGroups(candidates.items, candidates.size);
        List<LogicalItem> items = new ArrayList<>();
        Set<Integer> addedGroups = new HashSet<>();

        for (int i = 0; i < candidates.size; i++) {
            int entityId = candidates.get(i);
            EntityIndexComponent index = indexes.get(entityId);
            if (index.layerIndex != layerIndex) continue;
            PrefabInstanceComponent prefab = prefabs.getSafe(entityId, null);
            GroupCandidate group = prefab != null && prefab.instanceId > 0
                    ? groups.get(prefab.instanceId) : null;
            if (group == null || !group.valid) {
                items.add(LogicalItem.standalone(entityId, index.zIndex));
            } else if (addedGroups.add(group.instanceId)) {
                sortMembers(group.members);
                items.add(LogicalItem.prefab(
                        group.instanceId, group.prefabId, group.maxZ, group.members));
            }
        }
        items.sort(LayerLogicalOrderService::compareItems);
        return new LayerOrder(layerIndex, items);
    }

    private Map<Integer, GroupCandidate> collectGroups(int[] data, int count) {
        Map<Integer, GroupCandidate> groups = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int entityId = data[i];
            PrefabInstanceComponent prefab = prefabs.getSafe(entityId, null);
            if (prefab == null || prefab.instanceId <= 0) continue;
            GroupCandidate group = groups.computeIfAbsent(
                    prefab.instanceId, GroupCandidate::new);
            group.add(entityId, indexes.getSafe(entityId, null), prefab.prefabId);
        }
        return groups;
    }

    private int compareEntities(int first, int second) {
        int firstZ = indexes.get(first).zIndex;
        int secondZ = indexes.get(second).zIndex;
        if (firstZ != secondZ) return Integer.compare(secondZ, firstZ);
        return Integer.compare(first, second);
    }

    private void sortMembers(IntArray members) {
        for (int i = 1; i < members.size; i++) {
            int value = members.get(i);
            int j = i - 1;
            while (j >= 0 && compareEntities(value, members.get(j)) < 0) {
                members.set(j + 1, members.get(j));
                j--;
            }
            members.set(j + 1, value);
        }
    }

    private static int compareItems(LogicalItem first, LogicalItem second) {
        if (first.effectiveZ != second.effectiveZ) {
            return Integer.compare(second.effectiveZ, first.effectiveZ);
        }
        if (first.isPrefab() && second.isPrefab()) {
            return Integer.compare(first.prefabInstanceId, second.prefabInstanceId);
        }
        if (!first.isPrefab() && !second.isPrefab()) {
            return Integer.compare(first.entityId, second.entityId);
        }
        return first.isPrefab() ? -1 : 1;
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

        public IntArray movePrefab(int prefabInstanceId, int direction) {
            if (prefabInstanceId <= 0) return null;
            int itemIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                LogicalItem item = items.get(i);
                if (item.prefabInstanceId == prefabInstanceId) {
                    itemIndex = i;
                    break;
                }
            }
            return moveTopLevel(itemIndex, direction);
        }

        public IntArray moveEntity(int entityId, int direction) {
            if (direction != -1 && direction != 1) return null;
            for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
                LogicalItem item = items.get(itemIndex);
                int memberIndex = item.indexOf(entityId);
                if (memberIndex < 0) continue;
                if (!item.isPrefab()) return moveTopLevel(itemIndex, direction);
                int siblingIndex = memberIndex + direction;
                if (siblingIndex < 0 || siblingIndex >= item.members.size) return null;
                List<LogicalItem> moved = copyItems();
                IntArray members = moved.get(itemIndex).members;
                int sibling = members.get(siblingIndex);
                members.set(siblingIndex, entityId);
                members.set(memberIndex, sibling);
                return flatten(moved);
            }
            return null;
        }

        private IntArray moveTopLevel(int itemIndex, int direction) {
            if (direction != -1 && direction != 1) return null;
            int siblingIndex = itemIndex + direction;
            if (itemIndex < 0 || siblingIndex < 0 || siblingIndex >= items.size()) return null;
            List<LogicalItem> moved = copyItems();
            LogicalItem sibling = moved.get(siblingIndex);
            moved.set(siblingIndex, moved.get(itemIndex));
            moved.set(itemIndex, sibling);
            return flatten(moved);
        }

        private List<LogicalItem> copyItems() {
            List<LogicalItem> copy = new ArrayList<>(items.size());
            for (LogicalItem item : items) copy.add(item.copy());
            return copy;
        }

        private static IntArray flatten(List<LogicalItem> orderedItems) {
            IntArray flattened = new IntArray();
            for (LogicalItem item : orderedItems) flattened.addAll(item.members);
            return flattened;
        }
    }

    public static final class LogicalItem {
        private final int entityId;
        private final int prefabInstanceId;
        private final String prefabId;
        private final int effectiveZ;
        private final IntArray members;

        private LogicalItem(int entityId, int prefabInstanceId, String prefabId,
                            int effectiveZ, IntArray members) {
            this.entityId = entityId;
            this.prefabInstanceId = prefabInstanceId;
            this.prefabId = prefabId;
            this.effectiveZ = effectiveZ;
            this.members = new IntArray(members);
        }

        static LogicalItem standalone(int entityId, int zIndex) {
            return new LogicalItem(entityId, -1, null, zIndex, new IntArray(new int[]{entityId}));
        }

        static LogicalItem prefab(int instanceId, String prefabId, int maxZ, IntArray members) {
            return new LogicalItem(-1, instanceId, prefabId, maxZ, members);
        }

        public boolean isPrefab() { return prefabInstanceId > 0; }
        public int entityId() { return entityId; }
        public int prefabInstanceId() { return prefabInstanceId; }
        public String prefabId() { return prefabId; }
        public IntArray members() { return new IntArray(members); }

        private int indexOf(int entityId) { return members.indexOf(entityId); }
        private LogicalItem copy() {
            return new LogicalItem(entityId, prefabInstanceId, prefabId, effectiveZ, members);
        }
    }

    private static final class GroupCandidate {
        final int instanceId;
        final IntArray members = new IntArray();
        String prefabId;
        int layerIndex = -1;
        int maxZ = Integer.MIN_VALUE;
        boolean valid = true;

        GroupCandidate(int instanceId) { this.instanceId = instanceId; }

        void add(int entityId, EntityIndexComponent index, String candidatePrefabId) {
            members.add(entityId);
            if (index == null || candidatePrefabId == null || candidatePrefabId.isBlank()) {
                valid = false;
                return;
            }
            if (layerIndex < 0) layerIndex = index.layerIndex;
            if (layerIndex != index.layerIndex) valid = false;
            if (prefabId == null) prefabId = candidatePrefabId;
            if (!prefabId.equals(candidatePrefabId)) valid = false;
            maxZ = Math.max(maxZ, index.zIndex);
        }
    }
}
