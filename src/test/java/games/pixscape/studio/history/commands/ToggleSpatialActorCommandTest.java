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
        Assert.assertFalse(created.sensor);
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
    public void technicalLightActivationAndDisableLeaveNoOrphanAndUndoRestoresIt() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        PhysicsShapeData source = new PhysicsShapeData();
        source.geometry = new PhysicsGeometryData();
        source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        source.geometry.radius = 0.05f;
        source.spatialFootprint = true;
        source.technicalSpatialLight = true;
        source.sensor = true;
        source.maskBits = 0;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true));
        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).get(entityId);
        Assert.assertTrue(body.technicalSpatialLight);
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC, body.type);
        Assert.assertEquals(0f, body.gravityScale, 0f);
        Assert.assertTrue(marked(harness.world, entityId).technicalSpatialLight);
        int shapeId = marked(harness.world, entityId).physicsShapeId;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                false, false, null, true));
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));

        harness.history.undo();
        Assert.assertEquals(shapeId, marked(harness.world, entityId).physicsShapeId);
        Assert.assertTrue(harness.world.getMapper(PhysicsBodyComponent.class)
                .get(entityId).technicalSpatialLight);
        harness.history.redo();
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true));
        Assert.assertEquals(1, harness.world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.size);
        Assert.assertTrue(harness.world.getMapper(PhysicsBodyComponent.class)
                .get(entityId).technicalSpatialLight);
    }

    @Test
    public void technicalLightAltitudeEditAndActivationUndoRedoKeepFixedHeight() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        PhysicsShapeData source = footprint(0.05f, 0f, 0f);
        source.technicalSpatialLight = true;
        source.sensor = true;
        source.maskBits = 0;
        source.groupIndex = 0;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true));
        SpatialHeightComponent spatial = harness.world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(0f, spatial.altitude, 0f);
        Assert.assertEquals(ToggleSpatialActorCommand.TECHNICAL_LIGHT_HEIGHT_PX, spatial.height, 0f);

        EditSpatialHeightCommand.Snapshot before = EditSpatialHeightCommand.Snapshot.capture(spatial);
        harness.history.execute(new EditSpatialHeightCommand(
                harness.world, harness.historyIds, entityId, before, before.withAltitude(18.5f)));
        Assert.assertEquals(18.5f, spatial.altitude, 0f);
        Assert.assertEquals(ToggleSpatialActorCommand.TECHNICAL_LIGHT_HEIGHT_PX, spatial.height, 0f);

        harness.history.undo();
        Assert.assertEquals(0f, spatial.altitude, 0f);
        harness.history.undo();
        Assert.assertFalse(harness.world.getMapper(SpatialHeightComponent.class).has(entityId));
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        harness.history.redo();
        spatial = harness.world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(ToggleSpatialActorCommand.TECHNICAL_LIGHT_HEIGHT_PX, spatial.height, 0f);
        harness.history.redo();
        Assert.assertEquals(18.5f, spatial.altitude, 0f);
        Assert.assertEquals(ToggleSpatialActorCommand.TECHNICAL_LIGHT_HEIGHT_PX, spatial.height, 0f);
    }

    @Test
    public void activatingTechnicalLightPreservesAuthoredAltitudeAndUndoRestoresOldHeight() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        SpatialHeightComponent spatial = harness.world.getMapper(SpatialHeightComponent.class).create(entityId);
        spatial.altitude = 25f;
        spatial.height = 9f;
        PhysicsShapeData source = footprint(0.05f, 0f, 0f);
        source.technicalSpatialLight = true;
        source.sensor = true;
        source.maskBits = 0;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true));
        Assert.assertEquals(25f, spatial.altitude, 0f);
        Assert.assertEquals(ToggleSpatialActorCommand.TECHNICAL_LIGHT_HEIGHT_PX, spatial.height, 0f);
        harness.history.undo();
        Assert.assertEquals(25f, spatial.altitude, 0f);
        Assert.assertEquals(9f, spatial.height, 0f);
    }

    @Test
    public void technicalLightPreservesPreexistingCompatibleBody() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.historyIds.ensureForEntity(entityId);
        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsService.initDefaultBody(body);
        body.type = PhysicsBodyComponent.DYNAMIC;
        body.gravityScale = 0f;
        body.fixedRotation = false;
        PhysicsShapeData source = new PhysicsShapeData();
        source.geometry = new PhysicsGeometryData();
        source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        source.geometry.radius = 0.05f;
        source.spatialFootprint = true;
        source.technicalSpatialLight = true;
        source.sensor = true;
        source.maskBits = 0;

        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true));
        Assert.assertFalse(body.technicalSpatialLight);
        Assert.assertFalse(body.fixedRotation);
        harness.history.execute(new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                false, false, null, true));
        Assert.assertTrue(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(body.technicalSpatialLight);
    }

    @Test
    public void incompatiblePreexistingLightBodyIsLeftUntouched() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsService.initDefaultBody(body);
        body.gravityScale = 2f;
        PhysicsShapeData source = new PhysicsShapeData();
        source.geometry = new PhysicsGeometryData();
        source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        source.geometry.radius = 0.05f;
        source.spatialFootprint = true;
        source.technicalSpatialLight = true;
        source.sensor = true;
        source.maskBits = 0;

        ToggleSpatialActorCommand command = new ToggleSpatialActorCommand(
                harness.world, harness.historyIds, harness.physics, entityId,
                true, true, source, true);
        harness.history.execute(command);

        Assert.assertTrue(command.isNoop());
        Assert.assertEquals(2f, body.gravityScale, 0f);
        Assert.assertFalse(body.technicalSpatialLight);
        Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class).has(entityId));
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
