package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

public final class DeleteLayerCommand implements Command {

    private final LayerService layerService;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final IntConsumer onLayerSelected;

    private final LayerService.LayerSnapshot snapshot;

    public DeleteLayerCommand(LayerService layerService, int layerEntityId, IntConsumer onLayerSelected) {
        this(layerService, layerEntityId, onLayerSelected, null);
    }

    public DeleteLayerCommand(LayerService layerService, int layerEntityId,
                              IntConsumer onLayerSelected, IntConsumer onRestoredEntity) {
        this.layerService = layerService;
        this.onLayerSelected = onLayerSelected;
        this.historyIds = layerService.historyIds();
        this.snapshot = layerService.snapshotLayer(layerService.indexOfLayerEntity(layerEntityId), onRestoredEntity);
        this.layerHistoryId = snapshot.layerHistoryId();
    }

    @Override
    public String label() {
        return "Delete Layer";
    }

    @Override
    public void redo() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (entityId == -1) throw new IllegalStateException("Layer deletion target no longer exists.");

        int index = layerService.indexOfLayerEntity(entityId);
        if (index == -1) throw new IllegalStateException("Layer deletion target is not a scene layer.");

        layerService.removeLayerSnapshot(snapshot);

        if (onLayerSelected != null) {
            int fallbackIndex = Math.min(index, layerService.count() - 1);
            int fallbackEntity = fallbackIndex >= 0 ? layerService.getLayerEntity(fallbackIndex) : -1;
            onLayerSelected.accept(fallbackEntity);
        }
    }

    @Override
    public void undo() {
        int entityId = layerService.insertLayerSnapshot(snapshot.index(), snapshot);

        if (onLayerSelected != null) {
            onLayerSelected.accept(entityId);
        }
    }

}
