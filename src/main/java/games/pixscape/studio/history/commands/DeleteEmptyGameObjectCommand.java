package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.GameObjectRootInitializer;

import java.util.function.IntConsumer;

/** Deletes one already-validated empty Game Object root with stable undo/redo identity. */
public final class DeleteEmptyGameObjectCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final GameObjectRootInitializer initializer;
    private final long historyId;
    private final IntConsumer onRestored;

    public DeleteEmptyGameObjectCommand(
            World world, HistoryIdRegistry historyIds, int entityId, IntConsumer onRestored) {
        this.world = world;
        this.historyIds = historyIds;
        this.onRestored = onRestored;
        initializer = new GameObjectRootInitializer(world);
        initializer.syncFrom(entityId);
        historyId = historyIds.ensureForEntity(entityId);
    }

    @Override public String label() { return "Delete Game Object"; }

    @Override
    public void redo() {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return;
        IdentityRegistry.unindexEntityImmediately(world, entityId);
        world.delete(entityId);
        historyIds.unbindHistoryId(historyId);
    }

    @Override
    public void undo() {
        int entityId = world.create();
        initializer.init(entityId);
        historyIds.bind(entityId, historyId);
        if (onRestored != null) onRestored.accept(entityId);
    }
}
