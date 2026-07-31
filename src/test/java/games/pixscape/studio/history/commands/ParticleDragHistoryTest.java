package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParticleDragHistoryTest {

    @Test
    public void moveUndoRedoChangesParticlePositionOnly() {
        World world = new World(new WorldConfiguration());
        int entity = world.create();
        world.getMapper(ParticleEmitterComponent.class).create(entity);
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = 1f;
        transform.y = 2f;
        transform.originX = 3f;
        transform.originY = 4f;
        transform.rotationRad = 0.75f;
        transform.scaleX = 2f;
        transform.scaleY = 3f;

        HistoryIdRegistry ids = new HistoryIdRegistry();
        long historyId = ids.ensureForEntity(entity);
        GizmoTransformCommand.Snapshot before = GizmoTransformCommand.Snapshot.of(transform);
        transform.x = 11f;
        transform.y = 22f;
        GizmoTransformCommand.Snapshot after = GizmoTransformCommand.Snapshot.of(transform);
        GizmoTransformCommand command = new GizmoTransformCommand(world, ids, TransformOp.MOVE);
        command.addEntry(historyId, before, after);

        command.undo();
        assertTransform(transform, 1f, 2f);
        command.redo();
        assertTransform(transform, 11f, 22f);
    }

    private static void assertTransform(TransformComponent transform, float x, float y) {
        assertEquals(x, transform.x, 0f);
        assertEquals(y, transform.y, 0f);
        assertEquals(3f, transform.originX, 0f);
        assertEquals(4f, transform.originY, 0f);
        assertEquals(0.75f, transform.rotationRad, 0f);
        assertEquals(2f, transform.scaleX, 0f);
        assertEquals(3f, transform.scaleY, 0f);
    }
}
