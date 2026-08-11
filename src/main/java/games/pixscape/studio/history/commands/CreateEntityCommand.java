package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.Initializer;

import java.util.function.Consumer;

/**
 * History command for creating an entity.
 * <p>
 * - The Initializer is configured BEFORE execution (atlasTag, regionPath, etc.).
 * - redo() creates the entity, binds the historyId, calls init().
 * - undo() captures current state through syncFrom(e), then deletes the entity.
 * (which lets future redo() calls recreate the entity in its last known state).
 */
public final class CreateEntityCommand implements Command {
    private final World world;
    private final Initializer initializer;
    private final Consumer<Integer> onCreated;
    private final HistoryIdRegistry historyIds;

    private long historyId = -1L;
    private int lastEntityId = -1;
    private int createdEntityId = -1;

    public CreateEntityCommand(World world,
                               HistoryIdRegistry historyIds,
                               Initializer initializer,
                               Consumer<Integer> onCreated) {
        this.world = world;
        this.historyIds = historyIds;
        this.initializer = initializer;
        this.onCreated = onCreated;
    }

    @Override
    public String label() {
        return "Create " + initializer.label();
    }

    @Override
    public void redo() {
        if (historyId > 0L) {
            int currentEntityId = historyIds.entityOfHistoryId(historyId);
            if (currentEntityId >= 0
                    && world.getEntityManager().isActive(currentEntityId)) {
                throw new IllegalStateException(
                        "Cannot redo CreateEntityCommand for historyId " + historyId
                                + ": current incarnation entity " + currentEntityId
                                + " is still active."
                );
            }
        }

        lastEntityId = world.create();
        createdEntityId = lastEntityId;
        try {
            if (historyId <= 0L) {
                historyId = historyIds.ensureForEntity(lastEntityId);
            } else {
                historyIds.bind(lastEntityId, historyId);
            }

            // Build the entity (Transform/Dimensions/TR/Meta/Visibility...) through the initializer.
            initializer.init(lastEntityId);

            // Notify the UI (selection, focus, etc.)
            if (onCreated != null) {
                onCreated.accept(lastEntityId);
            }
        } catch (RuntimeException | Error failure) {
            IdentityRegistry.unindexEntityImmediately(world, lastEntityId);
            if (world.getEntityManager().isActive(lastEntityId)) {
                world.delete(lastEntityId);
            }
            historyIds.unbindEntity(lastEntityId);
            throw failure;
        }
    }

    @Override
    public void undo() {
        int entityId = historyId > 0L
                ? historyIds.entityOfHistoryId(historyId)
                : -1;

        if (entityId < 0
                && lastEntityId >= 0
                && world.getEntityManager().isActive(lastEntityId)) {
            entityId = lastEntityId;
        }

        if (entityId >= 0 && world.getEntityManager().isActive(entityId)) {
            // Capture CURRENT state before deletion (modified name, rotation, etc.)
            initializer.syncFrom(entityId);

            IdentityRegistry.unindexEntityImmediately(world, entityId);
            world.delete(entityId);
            historyIds.unbindEntity(entityId);

            lastEntityId = entityId;
        }
    }

    public int getCreatedEntityId() {
        return createdEntityId;
    }

}
