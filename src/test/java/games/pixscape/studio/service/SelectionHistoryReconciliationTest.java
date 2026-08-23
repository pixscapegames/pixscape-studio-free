package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.commands.GizmoTransformCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.history.initializer.Initializer;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionHistoryReconciliationTest {

    @Test
    public void gizmoUndoRedoKeepsSelectedSprite() {
        assertGizmoUndoRedoKeepsSelection(EntityKind.SPRITE);
    }

    @Test
    public void gizmoUndoRedoKeepsSelectedTiledRectangle() {
        assertGizmoUndoRedoKeepsSelection(EntityKind.TILED_RECTANGLE);
    }

    @Test
    public void structuralUndoRemovesSelectionForDeletedEntityWithoutReselectingOnRedo() {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(8);
        CreateEntityCommand create = new CreateEntityCommand(
                world,
                history.historyIds(),
                new Initializer() {
                    @Override
                    public void syncFrom(int sourceEid) {
                    }

                    @Override
                    public void init(int targetEid) {
                        world.getMapper(TransformComponent.class).create(targetEid);
                    }

                    @Override
                    public String label() {
                        return "Test Entity";
                    }
                },
                null);

        history.execute(create);
        int entity = create.getCreatedEntityId();
        world.process();
        selection.selectOnly(entity);

        history.undo();
        world.process();
        selection.reconcileActiveSelection();

        assertFalse(world.getEntityManager().isActive(entity));
        assertTrue(selection.getSelectionSet().isEmpty());
        assertEquals(-1, selection.getFirstSelectedEntityId());

        history.redo();
        world.process();
        selection.reconcileActiveSelection();
        assertTrue(selection.getSelectionSet().isEmpty());
        assertEquals(-1, selection.getFirstSelectedEntityId());
        world.dispose();
    }

    private static void assertGizmoUndoRedoKeepsSelection(EntityKind kind) {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(8);
        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = 10f;
        transform.y = 20f;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        world.getMapper(EntityMetaComponent.class).create(entity).kind = kind;
        world.process();
        selection.selectOnly(entity);

        long historyId = history.historyIds().ensureForEntity(entity);
        GizmoTransformCommand command = new GizmoTransformCommand(world, history.historyIds(), TransformOp.MOVE);
        command.addEntry(historyId, GizmoTransformCommand.Snapshot.of(transform),
                new GizmoTransformCommand.Snapshot(30f, 40f, 0f, 1f, 1f, 0f, 0f));
        history.execute(command);

        history.undo();
        selection.reconcileActiveSelection();
        assertTrue(world.getEntityManager().isActive(entity));
        assertTrue(selection.getSelectionSet().contains(entity));
        assertEquals(entity, selection.getFirstSelectedEntityId());
        assertEquals(10f, transform.x, 0f);
        assertEquals(20f, transform.y, 0f);

        history.redo();
        selection.reconcileActiveSelection();
        assertTrue(selection.getSelectionSet().contains(entity));
        assertEquals(entity, selection.getFirstSelectedEntityId());
        assertEquals(30f, transform.x, 0f);
        assertEquals(40f, transform.y, 0f);
        world.dispose();
    }
}
