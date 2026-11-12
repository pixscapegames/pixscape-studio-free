package games.pixscape.studio.history.commands;

import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

/**
 * Deletes and restores one Tiled Map without cascading to its owning layer.
 */
public final class DeleteTiledMapCommand implements Command {
    private final HistoryIdRegistry historyIds;
    private final DeleteGameObjectHierarchyCommand deletion;
    private final long historyId;
    private final IntConsumer onTargetChanged;

    public DeleteTiledMapCommand(LayerService layerService, int mapEntityId,
                                 IntConsumer onTargetChanged) {
        this.historyIds = layerService.historyIds();
        this.historyId = historyIds.ensureForEntity(mapEntityId);
        IntArray targets = new IntArray(1);
        targets.add(mapEntityId);
        this.deletion = new DeleteGameObjectHierarchyCommand(layerService.getWorld(),
                historyIds, targets, null, layerService.getTiledAllocatorService());
        this.onTargetChanged = onTargetChanged;
    }

    @Override
    public String label() {
        return "Delete Tiled Map";
    }

    @Override
    public void redo() {
        int entityId = historyIds.entityOfHistoryId(historyId);
        deletion.redo();
        EventFlow.i().publish(new EventFlow.TiledMapContentChanged(entityId, EventFlow.tag(this)));
        if (onTargetChanged != null) onTargetChanged.accept(-1);
    }

    @Override
    public void undo() {
        deletion.undo();
        int entityId = historyIds.entityOfHistoryId(historyId);
        EventFlow.i().publish(new EventFlow.TiledMapContentChanged(entityId, EventFlow.tag(this)));
        if (onTargetChanged != null) onTargetChanged.accept(entityId);
    }
}
