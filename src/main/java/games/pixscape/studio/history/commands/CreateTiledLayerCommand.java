package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.LayerInitializer;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

public final class CreateTiledLayerCommand implements Command {

    private final LayerService layerService;
    private final LayerInitializer initializer;
    private final HistoryIdRegistry historyIds;
    private final IntConsumer onLayerSelected;

    private final int insertionIndex;

    private long historyId = -1L;
    private LayerService.LayerSnapshot snapshot;

    public CreateTiledLayerCommand(LayerService layerService,
                                   String name,
                                   int mapWidth,
                                   int mapHeight,
                                   IntConsumer onLayerSelected) {

        this.layerService = layerService;
        this.onLayerSelected = onLayerSelected;
        this.historyIds = layerService.historyIds();

        this.insertionIndex = layerService.count();

        String effectiveName =
                (name != null && !name.isBlank())
                        ? name
                        : "New Tiled Layer";

        this.initializer = new LayerInitializer(
                layerService.getWorld(),
                layerService.getTiledAllocatorService()
        ).configureNewTiledLayer(
                effectiveName,
                insertionIndex,
                mapWidth,
                mapHeight
        );
    }

    @Override
    public String label() {
        return "Create Tiled Layer";
    }

    @Override
    public void redo() {

        int layerEntityId;

        if (snapshot != null) {
            layerEntityId = layerService.insertLayerSnapshot(
                    snapshot.index(),
                    snapshot
            );
            historyId = snapshot.layerHistoryId();
            snapshot = null;

        } else {
            layerEntityId = layerService.insertLayerAt(
                    insertionIndex,
                    initializer
            );

            if (historyId <= 0L) {
                historyId = historyIds.ensureForEntity(layerEntityId);
            } else {
                historyIds.bind(layerEntityId, historyId);
            }
        }

        if (onLayerSelected != null) {
            onLayerSelected.accept(layerEntityId);
        }
    }

    @Override
    public void undo() {

        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId == -1) return;

        int index = layerService.indexOfLayerEntity(entityId);
        if (index == -1) return;

        snapshot = layerService.snapshotLayer(index);

        layerService.removeLayerCascade(index);

        unbindSnapshotHistoryIds(snapshot);

        if (onLayerSelected != null) {
            int fallbackIndex =
                    Math.min(index, layerService.count() - 1);

            int fallbackEntity =
                    fallbackIndex >= 0
                            ? layerService.getLayerEntity(fallbackIndex)
                            : -1;

            onLayerSelected.accept(fallbackEntity);
        }
    }

    private void unbindSnapshotHistoryIds(LayerService.LayerSnapshot snap) {
        if (snap == null) return;

        historyIds.unbindHistoryId(snap.layerHistoryId());
        if (snap.tiledMapHistoryId() > 0L) {
            historyIds.unbindHistoryId(snap.tiledMapHistoryId());
        }

        for (LayerService.DrawableSnapshot drawable : snap.drawables()) {
            historyIds.unbindHistoryId(drawable.historyId);
        }
    }
}
