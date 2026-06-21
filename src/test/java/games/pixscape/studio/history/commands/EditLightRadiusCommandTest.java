package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditLightRadiusCommandTest {

    @Test
    public void pointLightOverlayCommandUndoRedoRadius() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        PointLightComponent light = world.getMapper(PointLightComponent.class).create(entityId);
        light.radius = 10f;

        EditLightRadiusCommand command = new EditLightRadiusCommand(world, history.historyIds(), entityId, 10f, 24f);
        history.execute(command);
        Assert.assertEquals(24f, light.radius, 0.0001f);

        history.undo();
        Assert.assertEquals(10f, light.radius, 0.0001f);

        history.redo();
        Assert.assertEquals(24f, light.radius, 0.0001f);
    }

    @Test
    public void coneLightOverlayCommandUndoRedoRadiusAndRotation() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = 0.25f;
        ConeLightComponent light = world.getMapper(ConeLightComponent.class).create(entityId);
        light.radius = 12f;

        EditLightRadiusCommand command = new EditLightRadiusCommand(
                world,
                history.historyIds(),
                entityId,
                12f,
                32f,
                0.25f,
                1.75f
        );
        history.execute(command);
        Assert.assertEquals(32f, light.radius, 0.0001f);
        Assert.assertEquals(1.75f, transform.rotationRad, 0.0001f);

        history.undo();
        Assert.assertEquals(12f, light.radius, 0.0001f);
        Assert.assertEquals(0.25f, transform.rotationRad, 0.0001f);

        history.redo();
        Assert.assertEquals(32f, light.radius, 0.0001f);
        Assert.assertEquals(1.75f, transform.rotationRad, 0.0001f);
    }

    @Test
    public void radiusClampsToMinimum() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        PointLightComponent light = world.getMapper(PointLightComponent.class).create(entityId);
        light.radius = 5f;

        EditLightRadiusCommand command = new EditLightRadiusCommand(world, history.historyIds(), entityId, 5f, -2f);
        history.execute(command);
        Assert.assertEquals(EditLightRadiusCommand.MIN_RADIUS, light.radius, 0.0001f);

        history.undo();
        Assert.assertEquals(5f, light.radius, 0.0001f);
    }

    @Test
    public void noopWhenRadiusAndRotationUnchanged() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(entityId);

        world.getMapper(TransformComponent.class).create(entityId).rotationRad = 0.5f;
        world.getMapper(ConeLightComponent.class).create(entityId).radius = 15f;

        EditLightRadiusCommand command = new EditLightRadiusCommand(
                world,
                history.historyIds(),
                entityId,
                15f,
                15f,
                0.5f,
                0.5f
        );
        Assert.assertTrue(command.isNoop());
    }
}
