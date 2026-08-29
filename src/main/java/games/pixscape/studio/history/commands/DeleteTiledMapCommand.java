package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

/** Deletes and restores one Tiled Map without cascading to its owning layer. */
public final class DeleteTiledMapCommand implements Command {
    private final LayerService layerService;
    private final HistoryIdRegistry historyIds;
    private final TiledMapInitializer snapshot;
    private final long historyId;
    private final IntConsumer onTargetChanged;

    public DeleteTiledMapCommand(LayerService layerService, int mapEntityId,
                                 IntConsumer onTargetChanged) {
        this.layerService = layerService;
        this.historyIds = layerService.historyIds();
        this.historyId = historyIds.ensureForEntity(mapEntityId);
        this.snapshot = new TiledMapInitializer(layerService.getWorld(),
                layerService.getTiledAllocatorService());
        this.snapshot.syncFrom(mapEntityId);
        this.onTargetChanged = onTargetChanged;
    }

    @Override public String label() { return "Delete Tiled Map"; }

    @Override
    public void redo() {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId >= 0) layerService.removeTiledMap(entityId);
        if (onTargetChanged != null) onTargetChanged.accept(-1);
    }

    @Override
    public void undo() {
        int entityId = layerService.insertTiledMap(snapshot, historyId);
        if (onTargetChanged != null) onTargetChanged.accept(entityId);
    }
}
