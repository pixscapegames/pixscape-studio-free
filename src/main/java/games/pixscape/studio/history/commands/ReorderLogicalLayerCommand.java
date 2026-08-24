package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Applies one complete logical layer order as compact sequential z values. */
public final class ReorderLogicalLayerCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private record Entry(long historyId, int before, int after) { }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final int layerIndex;
    private final LayerLogicalOrderService logicalOrderService;
    private final Supplier<IntArray> additionalEntityIds;
    private final IntArray requestedOrder;
    private final DirtyTrackerSystem dirtyTracker;
    private final List<Entry> entries = new ArrayList<>();
    private boolean prepared;
    private boolean valid = true;

    public ReorderLogicalLayerCommand(
            World world,
            HistoryIdRegistry historyIds,
            int layerIndex,
            IntArray topToBottomEntityIds) {
        this(world, historyIds, layerIndex, null, null, topToBottomEntityIds);
        prepareIfNeeded();
    }

    /** Deferred form used after prefab creation inside the same composite history operation. */
    public static ReorderLogicalLayerCommand normalizeAfterCreation(
            World world,
            HistoryIdRegistry historyIds,
            int layerIndex,
            LayerLogicalOrderService logicalOrderService,
            Supplier<IntArray> additionalEntityIds) {
        return new ReorderLogicalLayerCommand(
                world, historyIds, layerIndex, logicalOrderService,
                additionalEntityIds, null);
    }

    private ReorderLogicalLayerCommand(
            World world,
            HistoryIdRegistry historyIds,
            int layerIndex,
            LayerLogicalOrderService logicalOrderService,
            Supplier<IntArray> additionalEntityIds,
            IntArray requestedOrder) {
        this.world = world;
        this.historyIds = historyIds;
        this.layerIndex = layerIndex;
        this.logicalOrderService = logicalOrderService;
        this.additionalEntityIds = additionalEntityIds;
        this.requestedOrder = requestedOrder != null ? new IntArray(requestedOrder) : null;
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }

    @Override
    public String label() {
        return "Reorder Layer Z";
    }

    @Override
    public boolean isNoop() {
        prepareIfNeeded();
        return !valid || entries.isEmpty();
    }

    @Override
    public void redo() {
        if (redoOutcome() == CommandOutcome.REJECTED) {
            throw new IllegalStateException("Layer logical z reorder was rejected.");
        }
    }

    @Override
    public void undo() {
        if (undoOutcome() == CommandOutcome.REJECTED) {
            throw new IllegalStateException("Layer logical z undo was rejected.");
        }
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        prepareIfNeeded();
        if (!valid) return CommandOutcome.REJECTED;
        if (entries.isEmpty()) return CommandOutcome.NO_CHANGE;
        return apply(false);
    }

    @Override
    public CommandOutcome undoOutcome() {
        if (!prepared || !valid) return CommandOutcome.REJECTED;
        if (entries.isEmpty()) return CommandOutcome.NO_CHANGE;
        return apply(true);
    }

    private void prepareIfNeeded() {
        if (prepared) return;
        prepared = true;
        if (isLayerLocked()) {
            valid = false;
            return;
        }
        IntArray order = requestedOrder != null
                ? requestedOrder
                : logicalOrderService.derive(
                        layerIndex, resolveAdditionalEntityIds()).flattenedTopToBottom();
        if (order == null || order.size == 0 || order.size - 1 > SortKey64.MAX_Z) {
            valid = false;
            return;
        }

        IntArray current = new LayerLogicalOrderService(world)
                .derive(layerIndex, resolveAdditionalEntityIds()).flattenedTopToBottom();
        if (current.size != order.size) {
            valid = false;
            return;
        }
        IntSet expected = new IntSet(current.size);
        for (int i = 0; i < current.size; i++) expected.add(current.get(i));

        ComponentMapper<EntityIndexComponent> indexes =
                world.getMapper(EntityIndexComponent.class);
        IntSet requested = new IntSet(order.size);
        for (int i = 0; i < order.size; i++) {
            int entityId = order.get(i);
            if (!requested.add(entityId)
                    || !expected.contains(entityId)
                    || !world.getEntityManager().isActive(entityId)) {
                valid = false;
                entries.clear();
                return;
            }
            EntityIndexComponent index = indexes.getSafe(entityId, null);
            if (index == null || index.layerIndex != layerIndex) {
                valid = false;
                entries.clear();
                return;
            }
            int after = order.size - 1 - i;
            if (index.zIndex != after) {
                entries.add(new Entry(
                        historyIds.ensureForEntity(entityId), index.zIndex, after));
            }
        }
    }

    private IntArray resolveAdditionalEntityIds() {
        if (additionalEntityIds == null) return null;
        IntArray resolved = additionalEntityIds.get();
        return resolved != null ? new IntArray(resolved) : null;
    }

    private CommandOutcome apply(boolean before) {
        if (isLayerLocked()) return CommandOutcome.REJECTED;
        ComponentMapper<EntityIndexComponent> indexes =
                world.getMapper(EntityIndexComponent.class);
        IntArray entities = new IntArray(false, entries.size());
        for (Entry entry : entries) {
            int entityId = historyIds.entityOfHistoryId(entry.historyId);
            EntityIndexComponent index = entityId >= 0
                    && world.getEntityManager().isActive(entityId)
                    ? indexes.getSafe(entityId, null) : null;
            if (index == null || index.layerIndex != layerIndex) {
                return CommandOutcome.REJECTED;
            }
            entities.add(entityId);
        }
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            int entityId = entities.get(i);
            EntityIndexComponent index = indexes.get(entityId);
            int value = before ? entry.before : entry.after;
            if (index.zIndex != value) {
                index.zIndex = value;
                if (dirtyTracker != null) dirtyTracker.order(entityId);
                changed = true;
            }
        }
        if (!changed) return CommandOutcome.NO_CHANGE;
        EventFlow.i().publish(new EventFlow.EntityZOrderChanged(
                layerIndex, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private boolean isLayerLocked() {
        IntBag layers = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class, LayerMetaComponent.class)).getEntities();
        ComponentMapper<LayerComponent> layerComponents = world.getMapper(LayerComponent.class);
        ComponentMapper<LayerMetaComponent> layerMeta = world.getMapper(LayerMetaComponent.class);
        for (int i = 0; i < layers.size(); i++) {
            int entityId = layers.get(i);
            if (layerComponents.get(entityId).layerIndex == layerIndex) {
                return layerMeta.get(entityId).locked;
            }
        }
        return true;
    }
}
