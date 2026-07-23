package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TogglePhysicsBodyCommandTest {
    @Before
    public void activateSceneAllocator() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
    }

    @Test
    public void disablingAndUndoPreserveAuthoredSources() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        PhysicsBodyComponent body =
                world.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.enabled = true;
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 41;
        source.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
        source.radius = 2f;
        shapes.add(source);

        TogglePhysicsBodyCommand command = new TogglePhysicsBodyCommand(
                world, new HistoryIdRegistry(), entityId, false,
                PhysicsBodyComponent.DYNAMIC, false);
        command.redo();

        Assert.assertFalse(body.enabled);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(41, shapes.shapes.first().physicsShapeId);

        command.undo();
        Assert.assertTrue(body.enabled);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(2f, shapes.shapes.first().radius, 0f);
    }
}
