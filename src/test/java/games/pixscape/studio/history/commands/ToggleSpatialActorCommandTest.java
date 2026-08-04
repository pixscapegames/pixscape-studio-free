package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Test;

public class ToggleSpatialActorCommandTest {
    @Test
    public void enableCreatesOneDedicatedCircleAndUndoRedoPreservesItsIdentity() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);

        ToggleSpatialActorCommand command = new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, footprint(0.5f, 0.25f, -0.25f));
        harness.history.execute(command);

        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).get(entityId);
        PhysicsShapeData created = marked(harness.world, entityId);
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC, body.type);
        Assert.assertEquals(0f, body.gravityScale, 0f);
        Assert.assertTrue(body.fixedRotation);
        Assert.assertNotNull(created);
        Assert.assertEquals(0.5f, created.geometry.radius, 0f);
        Assert.assertTrue(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));
        int shapeId = created.physicsShapeId;
        int highWater = harness.meta.nextPhysicsShapeId;

        harness.history.undo();
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));

        harness.history.redo();
        Assert.assertEquals(shapeId, marked(harness.world, entityId).physicsShapeId);
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);
    }

    @Test
    public void disableRemovesOnlyMarkedCircleAndUndoRestoresExactState() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsService.initDefaultBody(body);
        body.type = PhysicsBodyComponent.KINEMATIC;
        body.fixedRotation = false;
        body.bullet = true;
        body.allowSleep = false;
        body.awake = false;
        body.gravityScale = 2f;
        body.linearDamping = 0.75f;
        body.angularDamping = 1.5f;

        PhysicsShapesComponent shapes = harness.world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData ordinary = footprint(0.25f, 0f, 0f);
        ordinary.physicsShapeId = harness.physics.allocateNewPhysicsShapeId();
        ordinary.spatialFootprint = false;
        PhysicsShapeData spatial = footprint(0.5f, 0.1f, -0.2f);
        spatial.physicsShapeId = harness.physics.allocateNewPhysicsShapeId();
        shapes.shapes.add(ordinary);
        shapes.shapes.add(spatial);
        PhysicsService.publishPreparedCandidate(shapes,
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId),
                PhysicsService.prepareBodyCandidate(shapes.shapes));
        SpatialHeightComponent height = harness.world.getMapper(SpatialHeightComponent.class).create(entityId);
        height.altitude = 3f;
        height.height = 4f;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                false, false, null));

        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(ordinary.physicsShapeId, shapes.shapes.first().physicsShapeId);
        Assert.assertEquals(PhysicsBodyComponent.KINEMATIC, body.type);
        Assert.assertFalse(body.fixedRotation);
        Assert.assertTrue(body.bullet);
        Assert.assertFalse(body.allowSleep);
        Assert.assertFalse(body.awake);
        Assert.assertEquals(2f, body.gravityScale, 0f);
        Assert.assertEquals(0.75f, body.linearDamping, 0f);
        Assert.assertEquals(1.5f, body.angularDamping, 0f);
        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));

        harness.history.undo();
        Assert.assertEquals(2, shapes.shapes.size);
        Assert.assertEquals(spatial.physicsShapeId, marked(harness.world, entityId).physicsShapeId);
        height = harness.world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(3f, height.altitude, 0f);
        Assert.assertEquals(4f, height.height, 0f);
    }

    @Test
    public void disableSpatialHeightOnlyStateDoesNotCreatePhysicsBody() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        harness.world.getMapper(SpatialHeightComponent.class).create(entityId);

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                false, false, null));

        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
    }

    @Test
    public void disableMarkedFootprintWithoutBodyRemovesItWithoutCreatingBody() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        PhysicsShapesComponent shapes = harness.world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData spatial = footprint(0.5f, 0f, 0f);
        spatial.physicsShapeId = harness.physics.allocateNewPhysicsShapeId();
        shapes.shapes.add(spatial);
        harness.world.getMapper(SpatialHeightComponent.class).create(entityId);

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                false, false, null));

        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class).has(entityId));
    }

    @Test
    public void duplicateMarkedFixtureCreatesUnmarkedCopy() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        harness.world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsShapesComponent shapes = harness.world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData spatial = footprint(0.5f, 0f, 0f);
        spatial.physicsShapeId = harness.physics.allocateNewPhysicsShapeId();
        shapes.shapes.add(spatial);
        PhysicsService.publishPreparedCandidate(shapes,
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId),
                PhysicsService.prepareBodyCandidate(shapes.shapes));

        DuplicateFixtureCommand duplicate = new DuplicateFixtureCommand(
                harness.world, harness.historyIds, new PhysicsSelectionService(), harness.physics, entityId,
                spatial.physicsShapeId);
        Assert.assertFalse(duplicate.isNoop());
        duplicate.redo();
        Assert.assertEquals(2, shapes.shapes.size);
        Assert.assertTrue(shapes.shapes.first().spatialFootprint);
        Assert.assertFalse(shapes.shapes.get(1).spatialFootprint);
    }

    private static PhysicsShapeData footprint(float radius, float x, float y) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = radius;
        shape.geometry.offsetX = x;
        shape.geometry.offsetY = y;
        shape.spatialFootprint = true;
        return shape;
    }

    private static PhysicsShapeData marked(World world, int entityId) {
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(entityId);
        for (int i = 0; i < shapes.shapes.size; i++) {
            if (shapes.shapes.get(i).spatialFootprint) return shapes.shapes.get(i);
        }
        return null;
    }

    private static final class Harness {
        final World world = new World(new WorldConfiguration());
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, meta);
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();
        final HistoryManager history = new HistoryManager(16);
    }
}
