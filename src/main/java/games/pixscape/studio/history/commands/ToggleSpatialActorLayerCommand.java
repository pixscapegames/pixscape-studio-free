package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;

/** Toggles participation of one ordinary layer in Spatial actor ordering. */
public final class ToggleSpatialActorLayerCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final LayerService layerService;
    private final long layerHistoryId;
    private final boolean before;
    private final boolean after;
    private final boolean noop;

    public ToggleSpatialActorLayerCommand(
            World world,
            HistoryIdRegistry historyIds,
            LayerService layerService,
            int layerEntityId,
            boolean enabled) {
        this.world = world;
        this.historyIds = historyIds;
        this.layerService = layerService;

        LayerComponent layer = validOrdinaryLayer(layerEntityId);
        this.layerHistoryId = layer != null
                ? historyIds.ensureForEntity(layerEntityId)
                : -1L;
        this.before = layer != null && layer.spatialEnabled;
        this.after = enabled;
        this.noop = layer == null || layerHistoryId <= 0L || before == after;
    }

    @Override
    public String label() {
        return after ? "Enable Layer Spatial" : "Disable Layer Spatial";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after);
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(boolean enabled) {
        if (noop) return CommandOutcome.NO_CHANGE;

        int layerEntityId = resolveLayerEntity();
        LayerComponent layer = validOrdinaryLayer(layerEntityId);
        if (layer == null || layer.spatialEnabled == enabled) {
            return CommandOutcome.NO_CHANGE;
        }
        if (enabled && layerService.hasOtherSpatialActorLayer(layerEntityId)) {
            return CommandOutcome.REJECTED;
        }

        layer.spatialEnabled = enabled;
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(layerEntityId);
            dirty.order(layerEntityId);
        }
        EventFlow.i().publish(
                new EventFlow.LayerSpatialDepthChanged(layerEntityId, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private LayerComponent validOrdinaryLayer(int entityId) {
        if (world == null || historyIds == null || layerService == null
                || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return null;
        }
        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(entityId, null);
        return layer;
    }

    private int resolveLayerEntity() {
        if (historyIds == null || layerHistoryId <= 0L) return -1;
        return historyIds.entityOfHistoryId(layerHistoryId);
    }
}
