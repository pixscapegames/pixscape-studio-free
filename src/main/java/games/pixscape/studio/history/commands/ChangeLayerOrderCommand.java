package games.pixscape.studio.history.commands;

import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;

public final class ChangeLayerOrderCommand implements Command, HistoryManager.SupportsNoop {

    private final LayerService layerService;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final int beforeIndex;
    private final int afterIndex;

    public ChangeLayerOrderCommand(LayerService layerService, int layerEntityId, int targetIndex) {
        this.layerService = layerService;
        this.historyIds = layerService.historyIds();
        this.layerHistoryId = historyIds.ensureForEntity(layerEntityId);

        int currentIndex = layerService.indexOfLayerEntity(layerEntityId);
        this.beforeIndex = currentIndex;
        this.afterIndex = clampTargetIndex(targetIndex, layerService.count());
    }

    @Override
    public String label() {
        return "Reorder Layer";
    }

    @Override
    public boolean isNoop() {
        return beforeIndex < 0 || beforeIndex == afterIndex;
    }

    @Override
    public void redo() {
        moveTo(afterIndex);
    }

    @Override
    public void undo() {
        moveTo(beforeIndex);
    }

    private void moveTo(int targetIndex) {
        int layerEntityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (layerEntityId == -1) {
            return;
        }

        int currentIndex = layerService.indexOfLayerEntity(layerEntityId);
        if (currentIndex < 0) {
            return;
        }

        layerService.moveLayer(currentIndex, targetIndex);
    }

    private static int clampTargetIndex(int targetIndex, int layerCount) {
        if (layerCount <= 0) {
            return -1;
        }
        return Math.max(0, Math.min(targetIndex, layerCount - 1));
    }
}
