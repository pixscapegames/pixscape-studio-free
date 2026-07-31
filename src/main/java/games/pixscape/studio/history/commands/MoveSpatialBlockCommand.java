package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

public final class MoveSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final EditSpatialBlockCommand delegate;

    public MoveSpatialBlockCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   SpatialBlockSelectionService selection,
                                   int layerEntityId,
                                   int blockId,
                                   float beforeX,
                                   float beforeY,
                                   float afterX,
                                   float afterY) {
        SpatialBlocksComponent component = world != null ? SpatialBlockCommandSupport.get(world, layerEntityId) : null;
        SpatialBlockData current = SpatialBlockCommandSupport.find(component, blockId);
        SpatialBlockData before = current != null ? current.copy() : null;
        SpatialBlockData after = current != null ? current.copy() : null;
        if (before != null) {
            before.x = beforeX;
            before.y = beforeY;
        }
        if (after != null) {
            after.x = afterX;
            after.y = afterY;
        }
        this.delegate = new EditSpatialBlockCommand(
                world,
                historyIds,
                selection,
                layerEntityId,
                blockId,
                before,
                after
        );
    }

    @Override
    public String label() {
        return "Move Spatial Block";
    }

    @Override
    public boolean isNoop() {
        return delegate.isNoop();
    }

    @Override
    public void redo() {
        delegate.redo();
    }

    @Override
    public void undo() {
        delegate.undo();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return delegate.executeOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return delegate.undoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return delegate.redoOutcome();
    }
}
