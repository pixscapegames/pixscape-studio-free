package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TogglePhysicsBodyCommandTest {
    private ProjectConfig config;

    @Before
    public void activateSceneAllocator() {
        config = new ProjectConfig();
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
        source.directGeometry = new PhysicsDirectGeometryData();
        source.physicsShapeId = 41;
        source.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
        source.directGeometry.radius = 2f;
        shapes.add(source);

        TogglePhysicsBodyCommand command = new TogglePhysicsBodyCommand(
                world, new HistoryIdRegistry(), physicsService(world), entityId, false,
                PhysicsBodyComponent.DYNAMIC, false);
        command.redo();

        Assert.assertFalse(body.enabled);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(41, shapes.shapes.first().physicsShapeId);

        command.undo();
        Assert.assertTrue(body.enabled);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(2f, shapes.shapes.first().directGeometry.radius, 0f);
    }

    @Test
    public void creatingBodyAllocatesShapeIdentityOnlyOnFirstExecution() {
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        TogglePhysicsBodyCommand command = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                physicsService(world),
                entityId,
                true,
                PhysicsBodyComponent.DYNAMIC,
                true);

        int nextBefore = config.getCurrentSceneMeta().nextPhysicsShapeId;
        command.redo();
        int allocatedId = world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.first().physicsShapeId;
        int nextAfterFirstExecution = config.getCurrentSceneMeta().nextPhysicsShapeId;

        command.undo();
        command.redo();
        Assert.assertEquals(
                allocatedId,
                world.getMapper(PhysicsShapesComponent.class)
                        .get(entityId).shapes.first().physicsShapeId);
        Assert.assertEquals(
                nextAfterFirstExecution,
                config.getCurrentSceneMeta().nextPhysicsShapeId);

        command.undo();
        command.redo();
        Assert.assertEquals(
                allocatedId,
                world.getMapper(PhysicsShapesComponent.class)
                        .get(entityId).shapes.first().physicsShapeId);
        Assert.assertEquals(nextBefore + 1, nextAfterFirstExecution);
        Assert.assertEquals(
                nextAfterFirstExecution,
                config.getCurrentSceneMeta().nextPhysicsShapeId);
    }

    private games.pixscape.runtime.service.PhysicsService physicsService(World world) {
        return new games.pixscape.runtime.service.PhysicsService(
                world, null, config.getCurrentSceneMeta());
    }
}
