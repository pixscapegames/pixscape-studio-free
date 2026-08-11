package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.HistoryIdRegistry;

/**
 * History command for deleting/restoring a physics joint.
 */
public final class DeleteJointCommand implements Command {

    private final DeleteEntitiesCommand delegate;

    public DeleteJointCommand(
            World world,
            HistoryIdRegistry historyIds,
            int jointEntityId) {
        if (world == null
                || historyIds == null
                || jointEntityId < 0
                || !world.getEntityManager().isActive(jointEntityId)
                || !world.getMapper(PhysicsJointComponent.class).has(jointEntityId)) {
            throw new IllegalArgumentException(
                    "Invalid joint entity for deletion: " + jointEntityId);
        }

        IntArray jointEntityIds = new IntArray(1);
        jointEntityIds.add(jointEntityId);
        delegate = new DeleteEntitiesCommand(
                world, historyIds, jointEntityIds);
    }

    @Override
    public String label() {
        return "Delete Joint";
    }

    @Override
    public void redo() {
        delegate.redo();
    }

    @Override
    public void undo() {
        delegate.undo();
    }
}
