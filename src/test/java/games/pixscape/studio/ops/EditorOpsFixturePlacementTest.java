package games.pixscape.studio.ops;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddFixtureCommand;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Test;

public class EditorOpsFixturePlacementTest {
    @Test
    public void boxAndCircleUseBodyLocalMetersAndKeepPlacementThroughUndoRedo() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        config.getCurrentSceneMeta().pixelsPerMeter = 32f;

        World world = new World(new WorldConfiguration());
        try {
            int bodyEid = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(bodyEid);
            transform.x = 100f;
            transform.y = -40f;
            transform.rotationRad = MathUtils.PI / 2f;
            transform.refreshCaches();
            world.getMapper(PhysicsBodyComponent.class).create(bodyEid);
            world.getMapper(PhysicsShapesComponent.class).create(bodyEid);

            HistoryIdRegistry ids = new HistoryIdRegistry();
            ids.ensureForEntity(bodyEid);
            HistoryManager history = new HistoryManager(16);
            PhysicsSelectionService selection = new PhysicsSelectionService();
            PhysicsService physics = new PhysicsService(world, null, config.getCurrentSceneMeta());

            config.createSceneMeta("Other");
            config.getCurrentSceneMeta().pixelsPerMeter = 64f;
            PhysicsShapeData otherSceneShape = PhysicsService.createDefaultShape(1);
            Assert.assertTrue(EditorOpsImpl.placeFixtureAtWorld(
                    world, bodyEid, 100f, -8f, otherSceneShape, new Vector2()));
            Assert.assertEquals(0.5f, otherSceneShape.geometry.offsetX, 0.0001f);
            config.setCurrentSceneByName("Main");

            for (int type : new int[] {PhysicsGeometryData.SHAPE_BOX, PhysicsGeometryData.SHAPE_CIRCLE}) {
                PhysicsShapeData shape = PhysicsService.createDefaultShape(1);
                shape.geometry.shapeType = type;
                Assert.assertTrue(EditorOpsImpl.placeFixtureAtWorld(
                        world, bodyEid, 100f, -8f, shape, new Vector2()));
                Assert.assertEquals(1f, shape.geometry.offsetX, 0.0001f);
                Assert.assertEquals(0f, shape.geometry.offsetY, 0.0001f);

                AddFixtureCommand command = new AddFixtureCommand(
                        world, ids, selection, physics, bodyEid, shape, -1);
                history.execute(command);
                int shapeId = command.getCreatedFixtureId();
                assertPlaced(world, bodyEid, shapeId, type);
                history.undo();
                Assert.assertEquals(-1, findShapeIndex(world, bodyEid, shapeId));
                history.redo();
                assertPlaced(world, bodyEid, shapeId, type);
            }
            selection.dispose();
        } finally {
            world.dispose();
        }
    }

    private static void assertPlaced(World world, int bodyEid, int shapeId, int type) {
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(bodyEid);
        int index = findShapeIndex(world, bodyEid, shapeId);
        Assert.assertTrue(index >= 0);
        PhysicsShapeData shape = shapes.shapes.get(index);
        Assert.assertEquals(type, shape.geometry.shapeType);
        Assert.assertEquals(1f, shape.geometry.offsetX, 0.0001f);
        Assert.assertEquals(0f, shape.geometry.offsetY, 0.0001f);
    }

    private static int findShapeIndex(World world, int bodyEid, int shapeId) {
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(bodyEid);
        for (int i = 0; i < shapes.shapes.size; i++) {
            if (shapes.shapes.get(i).physicsShapeId == shapeId) return i;
        }
        return -1;
    }
}
