package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsSpatialFootprintProjector;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditLightRadiusCommandTest {
    @Test
    public void changingSpatialPointLightHaloLeavesCenteredTechnicalFootprintFixed() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int lightId = world.create();
        history.historyIds().ensureForEntity(lightId);
        PointLightComponent light = world.getMapper(PointLightComponent.class).create(lightId);
        light.radius = 20f;
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(lightId);
        dimensions.width = dimensions.height = 40f;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(lightId);
        transform.x = 80f;
        transform.y = 60f;
        transform.originX = transform.originY = 20f;
        PhysicsShapeData footprint = new PhysicsShapeData();
        footprint.physicsShapeId = 1;
        footprint.geometry = new PhysicsGeometryData();
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 0.05f;
        footprint.geometry.offsetX = footprint.geometry.offsetY = 0f;
        footprint.spatialFootprint = true;
        footprint.technicalSpatialLight = true;
        footprint.sensor = true;
        footprint.maskBits = 0;
        footprint.groupIndex = 0;
        world.getMapper(PhysicsShapesComponent.class).create(lightId).shapes.add(footprint);
        FixtureCommandSupport.prepareAndPublish(world, lightId,
                FixtureCommandSupport.copyFixtures(world, lightId));
        int generation = world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(lightId).generation;

        history.execute(new EditLightRadiusCommand(world, history.historyIds(),
                lightId, 20f, 80f));

        Assert.assertEquals(80f, light.radius, 0f);
        Assert.assertEquals(160f, dimensions.width, 0f);
        Assert.assertEquals(80f, transform.x, 0f);
        Assert.assertEquals(60f, transform.y, 0f);
        PhysicsShapeData fixed = world.getMapper(PhysicsShapesComponent.class).get(lightId).shapes.first();
        Assert.assertEquals(0.05f, fixed.geometry.radius, 0f);
        Assert.assertEquals(0f, fixed.geometry.offsetX, 0f);
        Assert.assertEquals(0f, fixed.geometry.offsetY, 0f);
        Assert.assertTrue(fixed.sensor);
        Assert.assertEquals(0, fixed.maskBits);
        Assert.assertEquals(0, fixed.groupIndex);
        PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(lightId);
        Assert.assertEquals(generation, compiled.generation);
        PhysicsSpatialFootprintProjector projector = new PhysicsSpatialFootprintProjector();
        SpatialPhysicsFootprintComponent projected = new SpatialPhysicsFootprintComponent();
        projector.publish(projected, projector.prepare(compiled.fixtures, generation, 100f));
        Assert.assertEquals(5f, projected.radiusPx, 0f);
        Assert.assertEquals(0f, projected.localOffsetXPx, 0f);
        Assert.assertEquals(0f, projected.localOffsetYPx, 0f);

        history.undo();
        Assert.assertEquals(0.05f, world.getMapper(PhysicsShapesComponent.class)
                .get(lightId).shapes.first().geometry.radius, 0f);
        Assert.assertEquals(80f, transform.x, 0f);
        Assert.assertEquals(60f, transform.y, 0f);
        history.redo();
        Assert.assertEquals(0.05f, world.getMapper(PhysicsShapesComponent.class)
                .get(lightId).shapes.first().geometry.radius, 0f);
        Assert.assertEquals(generation, world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(lightId).generation);
    }

    @Test
    public void coneRotationWithoutRadiusChangeDoesNotRecompileFootprint() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);
        ConeLightComponent cone = world.getMapper(ConeLightComponent.class).create(entityId);
        cone.radius = 25f;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.x = 30f;
        transform.y = 40f;
        PhysicsShapeData footprint = new PhysicsShapeData();
        footprint.physicsShapeId = 2;
        footprint.geometry = new PhysicsGeometryData();
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 0.05f;
        footprint.spatialFootprint = true;
        footprint.technicalSpatialLight = true;
        footprint.sensor = true;
        footprint.maskBits = 0;
        world.getMapper(PhysicsShapesComponent.class).create(entityId).shapes.add(footprint);
        FixtureCommandSupport.prepareAndPublish(world, entityId,
                FixtureCommandSupport.copyFixtures(world, entityId));
        int generation = world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(entityId).generation;

        history.execute(new EditLightRadiusCommand(world, history.historyIds(),
                entityId, 25f, 50f, 0f, 0f));
        Assert.assertEquals(0.05f, world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.first().geometry.radius, 0f);
        Assert.assertEquals(generation, world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(entityId).generation);
        history.execute(new EditLightRadiusCommand(world, history.historyIds(),
                entityId, 50f, 50f, 0f, 1f));
        Assert.assertEquals(generation, world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(entityId).generation);
        Assert.assertEquals(30f, transform.x, 0f);
        Assert.assertEquals(40f, transform.y, 0f);
        Assert.assertEquals(1f, transform.rotationRad, 0f);
    }

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
