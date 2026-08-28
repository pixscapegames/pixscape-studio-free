package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.LayerInitializer;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

public final class CreateLayerCommand implements Command, OutcomeAwareCommand {

    private final LayerService layerService;
    private final World world;
    private final int insertionIndex;
    private final LayerInitializer initializer;
    private final HistoryIdRegistry historyIds;
    private final boolean spatialActorLayer;
    private long historyId = -1L;
    private final IntConsumer onLayerSelected;
    private boolean rejected;

    private LayerService.LayerSnapshot snapshot;

    public CreateLayerCommand(LayerService layerService,
                              int insertionIndex,
                              String name,
                              IntConsumer onLayerSelected) {
        this(layerService, insertionIndex, name, LayerComponent.TYPE_CLASSIC, onLayerSelected);
    }

    public CreateLayerCommand(LayerService layerService,
                              int insertionIndex,
                              String name,
                              int type,
                              IntConsumer onLayerSelected) {
        this(layerService, insertionIndex, name, type, false, onLayerSelected);
    }

    public CreateLayerCommand(LayerService layerService,
                              int insertionIndex,
                              String name,
                              int type,
                              boolean spatialActorLayer,
                              IntConsumer onLayerSelected) {
        this.layerService = layerService;
        this.world = layerService.getWorld();
        this.insertionIndex = insertionIndex;
        this.onLayerSelected = onLayerSelected;
        this.historyIds = layerService.historyIds();
        this.spatialActorLayer = spatialActorLayer && type == LayerComponent.TYPE_CLASSIC;
        String effectiveName = (name != null && !name.isBlank()) ? name : "New Layer";
        this.initializer = new LayerInitializer(world, layerService.getTiledAllocatorService())
                .configureNewLayer(effectiveName, insertionIndex, type, this.spatialActorLayer);
    }

    @Override
    public String label() {
        return "Create Layer";
    }

    @Override
    public void redo() {
        applyRedo();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return applyRedo();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return applyRedo();
    }

    private CommandOutcome applyRedo() {
        rejected = spatialActorLayer && layerService.hasSpatialActorLayer();
        if (rejected) {
            return CommandOutcome.REJECTED;
        }

        int layerEntityId;
        if (snapshot != null) {
            layerEntityId = layerService.insertLayerSnapshot(snapshot.index(), snapshot);
            historyId = snapshot.layerHistoryId();
            snapshot = null;
        } else {
            layerEntityId = layerService.insertLayerAt(insertionIndex, initializer);
            if (historyId <= 0L) {
                historyId = historyIds.ensureForEntity(layerEntityId);
            } else {
                historyIds.bind(layerEntityId, historyId);
            }
        }

        if (onLayerSelected != null) {
            onLayerSelected.accept(layerEntityId);
        }
        return CommandOutcome.APPLIED;
    }

    @Override
    public void undo() {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId == -1) {
            return;
        }

        int index = layerService.indexOfLayerEntity(entityId);
        if (index == -1) {
            return;
        }

        snapshot = layerService.snapshotLayer(index);
        layerService.removeLayerCascade(index);
        unbindSnapshotHistoryIds(snapshot);

        if (onLayerSelected != null) {
            int fallbackIndex = Math.min(index, layerService.count() - 1);
            int fallbackEntity = fallbackIndex >= 0 ? layerService.getLayerEntity(fallbackIndex) : -1;
            onLayerSelected.accept(fallbackEntity);
        }
    }

    @Override
    public CommandOutcome undoOutcome() {
        undo();
        return CommandOutcome.APPLIED;
    }

    public boolean wasRejected() {
        return rejected;
    }

    private void unbindSnapshotHistoryIds(LayerService.LayerSnapshot snap) {
        if (snap == null) return;
        historyIds.unbindHistoryId(snap.layerHistoryId());
        for (LayerService.DrawableSnapshot drawable : snap.drawables()) {
            historyIds.unbindHistoryId(drawable.historyId);
        }
    }
}
