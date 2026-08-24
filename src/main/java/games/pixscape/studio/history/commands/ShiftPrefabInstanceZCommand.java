package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;

import java.util.ArrayList;
import java.util.List;

/** Atomically shifts every current member of one Studio prefab instance by a common z delta. */
public final class ShiftPrefabInstanceZCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private record Entry(long historyId, int before, int after) {
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final LayerService layerService;
    private final int prefabInstanceId;
    private final int expectedLayerIndex;
    private final List<Entry> entries = new ArrayList<>();
    private final DirtyTrackerSystem dirtyTracker;
    private final CommandOutcome initialOutcome;

    public ShiftPrefabInstanceZCommand(
            World world,
            HistoryIdRegistry historyIds,
            LayerService layerService,
            int prefabInstanceId,
            IntArray memberEntityIds,
            long delta) {
        this.world = world;
        this.historyIds = historyIds;
        this.layerService = layerService;
        this.prefabInstanceId = prefabInstanceId;
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);

        Prepared prepared = prepare(memberEntityIds, delta);
        this.expectedLayerIndex = prepared.layerIndex;
        this.initialOutcome = prepared.outcome;
        this.entries.addAll(prepared.entries);
    }

    @Override
    public String label() {
        return "Shift Prefab Instance Z";
    }

    @Override
    public boolean isNoop() {
        return initialOutcome != CommandOutcome.APPLIED;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(false);
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(true);
    }

    private CommandOutcome apply(boolean toBefore) {
        if (initialOutcome != CommandOutcome.APPLIED || isLayerLocked(expectedLayerIndex)) {
            return CommandOutcome.REJECTED;
        }

        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PrefabInstanceComponent> mPrefab =
                world.getMapper(PrefabInstanceComponent.class);
        IntArray resolved = new IntArray(false, entries.size());
        IntSet resolvedSet = new IntSet(entries.size());

        for (Entry entry : entries) {
            int entityId = historyIds.entityOfHistoryId(entry.historyId);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
                return CommandOutcome.REJECTED;
            }
            EntityIndexComponent index = mIndex.getSafe(entityId, null);
            PrefabInstanceComponent prefab = mPrefab.getSafe(entityId, null);
            int expectedCurrent = toBefore ? entry.after : entry.before;
            if (index == null
                    || index.layerIndex != expectedLayerIndex
                    || index.zIndex != expectedCurrent
                    || prefab == null
                    || prefab.instanceId != prefabInstanceId) {
                return CommandOutcome.REJECTED;
            }
            resolved.add(entityId);
            if (!resolvedSet.add(entityId)) return CommandOutcome.REJECTED;
        }

        if (!isCompleteCurrentInstance(resolvedSet, expectedLayerIndex)) {
            return CommandOutcome.REJECTED;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int entityId = resolved.get(i);
            EntityIndexComponent index = mIndex.get(entityId);
            index.zIndex = toBefore ? entry.before : entry.after;
            if (dirtyTracker != null) dirtyTracker.order(entityId);
        }
        return CommandOutcome.APPLIED;
    }

    private Prepared prepare(IntArray memberEntityIds, long delta) {
        if (prefabInstanceId <= 0
                || memberEntityIds == null
                || memberEntityIds.size == 0
                || delta == 0L) {
            return Prepared.rejected();
        }

        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PrefabInstanceComponent> mPrefab =
                world.getMapper(PrefabInstanceComponent.class);
        List<Entry> prepared = new ArrayList<>(memberEntityIds.size);
        IntSet requestedMembers = new IntSet(memberEntityIds.size);
        int layerIndex = -1;

        for (int i = 0; i < memberEntityIds.size; i++) {
            int entityId = memberEntityIds.get(i);
            if (entityId < 0
                    || !requestedMembers.add(entityId)
                    || !world.getEntityManager().isActive(entityId)) {
                return Prepared.rejected();
            }
            EntityIndexComponent index = mIndex.getSafe(entityId, null);
            PrefabInstanceComponent prefab = mPrefab.getSafe(entityId, null);
            if (index == null || prefab == null || prefab.instanceId != prefabInstanceId) {
                return Prepared.rejected();
            }
            if (layerIndex < 0) layerIndex = index.layerIndex;
            if (index.layerIndex != layerIndex) return Prepared.rejected();

            long minimumDelta = (long) SortKey64.MIN_Z - index.zIndex;
            long maximumDelta = (long) SortKey64.MAX_Z - index.zIndex;
            if (delta < minimumDelta || delta > maximumDelta) {
                return Prepared.rejected();
            }
            long candidate = (long) index.zIndex + delta;
            prepared.add(new Entry(
                    historyIds.ensureForEntity(entityId), index.zIndex, (int) candidate));
        }

        if (!isCompleteCurrentInstance(requestedMembers, layerIndex)
                || isLayerLocked(layerIndex)) {
            return Prepared.rejected();
        }
        return new Prepared(CommandOutcome.APPLIED, layerIndex, prepared);
    }

    private boolean isCompleteCurrentInstance(IntSet expectedMembers, int layerIndex) {
        EntitySubscription subscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(PrefabInstanceComponent.class));
        IntBag entities = subscription.getEntities();
        int[] data = entities.getData();
        ComponentMapper<PrefabInstanceComponent> mPrefab =
                world.getMapper(PrefabInstanceComponent.class);
        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        int matching = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entityId = data[i];
            PrefabInstanceComponent prefab = mPrefab.get(entityId);
            if (prefab.instanceId != prefabInstanceId) continue;
            EntityIndexComponent index = mIndex.getSafe(entityId, null);
            if (index == null
                    || index.layerIndex != layerIndex
                    || !expectedMembers.contains(entityId)) {
                return false;
            }
            matching++;
        }
        return matching == expectedMembers.size;
    }

    private boolean isLayerLocked(int layerIndex) {
        if (layerService == null || layerIndex < 0) return true;
        LayerMetaComponent meta = layerService.meta(layerIndex);
        return meta == null || meta.locked;
    }

    private record Prepared(CommandOutcome outcome, int layerIndex, List<Entry> entries) {
        static Prepared rejected() {
            return new Prepared(CommandOutcome.REJECTED, -1, List.of());
        }
    }
}
