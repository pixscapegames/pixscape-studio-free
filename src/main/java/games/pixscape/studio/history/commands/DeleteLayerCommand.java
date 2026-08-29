package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

public final class DeleteLayerCommand implements Command {

    private final LayerService layerService;
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final IntConsumer onLayerSelected;

    private LayerService.LayerSnapshot snapshot;

    public DeleteLayerCommand(LayerService layerService, int layerEntityId, IntConsumer onLayerSelected) {
        this.layerService = layerService;
        this.world = layerService.getWorld();
        this.onLayerSelected = onLayerSelected;
        this.historyIds = layerService.historyIds();
        this.layerHistoryId = historyIds.ensureForEntity(layerEntityId);
    }

    @Override
    public String label() {
        return "Delete Layer";
    }

    @Override
    public void redo() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
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
    public void undo() {
        if (snapshot == null) {
            return;
        }

        int entityId = layerService.insertLayerSnapshot(snapshot.index(), snapshot);
        snapshot = null;

        if (onLayerSelected != null) {
            onLayerSelected.accept(entityId);
        }
    }

    private void unbindSnapshotHistoryIds(LayerService.LayerSnapshot snap) {
        if (snap == null) return;
        historyIds.unbindHistoryId(snap.layerHistoryId());
        for (LayerService.TiledMapSnapshot map : snap.tiledMaps()) {
            historyIds.unbindHistoryId(map.historyId());
        }
        for (LayerService.DrawableSnapshot drawable : snap.drawables()) {
            historyIds.unbindHistoryId(drawable.historyId);
        }
    }
}
