package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class EditQuadDeformCommandTest {
    private World world;
    private HistoryManager history;
    private int entityId;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        history = new HistoryManager(16);
        entityId = world.create();
    }

    @After
    public void tearDown() {
        world.dispose();
    }

    @Test
    public void undoFirstDeformationRemovesComponentAndRedoRestoresAllValues() {
        EditQuadDeformCommand.Snapshot after = snapshot(
                true, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);

        history.execute(new EditQuadDeformCommand(
                world,
                history.historyIds(),
                entityId,
                EditQuadDeformCommand.Snapshot.absent(),
                after));
        assertQuad(after, world.getMapper(QuadDeformComponent.class).get(entityId));

        history.undo();
        assertFalse(world.getMapper(QuadDeformComponent.class).has(entityId));

        history.redo();
        assertQuad(after, world.getMapper(QuadDeformComponent.class).get(entityId));
    }

    @Test
    public void existingComponentUndoRedoRestoresCompleteSnapshots() {
        QuadDeformComponent component = world.getMapper(QuadDeformComponent.class).create(entityId);
        EditQuadDeformCommand.Snapshot before = snapshot(
                true, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        before.applyTo(component);
        EditQuadDeformCommand.Snapshot after = snapshot(
                true, -1f, -2f, -3f, -4f, -5f, -6f, -7f, -8f);

        history.execute(new EditQuadDeformCommand(
                world, history.historyIds(), entityId, before, after));
        history.undo();
        assertQuad(before, component);
        history.redo();
        assertQuad(after, component);
    }

    @Test
    public void oneCommittedDragOccupiesExactlyOneHistoryPosition() {
        EditQuadDeformCommand.Snapshot after = snapshot(
                true, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

        history.execute(new EditQuadDeformCommand(
                world,
                history.historyIds(),
                entityId,
                EditQuadDeformCommand.Snapshot.absent(),
                after));

        assertEquals(1, history.getCursor());
        assertTrue(history.canUndo());
        history.undo();
        assertFalse(history.canUndo());
    }

    @Test
    public void noOpSnapshotDoesNotNeedAHistoryEntry() {
        EditQuadDeformCommand command = new EditQuadDeformCommand(
                world,
                history.historyIds(),
                entityId,
                EditQuadDeformCommand.Snapshot.absent(),
                EditQuadDeformCommand.Snapshot.absent());

        assertTrue(command.isNoop());
        history.execute(command);
        assertFalse(history.canUndo());
    }

    private static EditQuadDeformCommand.Snapshot snapshot(
            boolean present,
            float blX, float blY,
            float brX, float brY,
            float trX, float trY,
            float tlX, float tlY) {
        return new EditQuadDeformCommand.Snapshot(
                present, blX, blY, brX, brY, trX, trY, tlX, tlY);
    }

    private static void assertQuad(EditQuadDeformCommand.Snapshot expected,
                                   QuadDeformComponent actual) {
        assertNotNull(actual);
        assertEquals(expected.blX, actual.blX, 0f);
        assertEquals(expected.blY, actual.blY, 0f);
        assertEquals(expected.brX, actual.brX, 0f);
        assertEquals(expected.brY, actual.brY, 0f);
        assertEquals(expected.trX, actual.trX, 0f);
        assertEquals(expected.trY, actual.trY, 0f);
        assertEquals(expected.tlX, actual.tlX, 0f);
        assertEquals(expected.tlY, actual.tlY, 0f);
    }
}
