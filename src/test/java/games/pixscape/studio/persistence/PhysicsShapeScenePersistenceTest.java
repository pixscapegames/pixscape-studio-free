package games.pixscape.studio.persistence;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class PhysicsShapeScenePersistenceTest {
    @Test
    public void sceneRoundTripRetainsTechnicalLightOwnershipAndFilter() {
        World source = world();
        int entityId = source.create();
        PhysicsBodyComponent body = source.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.type = PhysicsBodyComponent.DYNAMIC;
        body.gravityScale = 0f;
        body.technicalSpatialLight = true;
        SpatialHeightComponent spatial = source.getMapper(SpatialHeightComponent.class).create(entityId);
        spatial.altitude = 18.5f;
        spatial.height = 1f;
        PhysicsShapesComponent shapes = source.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData circle = new PhysicsShapeData();
        circle.physicsShapeId = 1;
        circle.geometry = new PhysicsGeometryData();
        circle.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        circle.geometry.radius = 0.05f;
        circle.spatialFootprint = true;
        circle.technicalSpatialLight = true;
        circle.sensor = true;
        circle.maskBits = 0;
        circle.groupIndex = 0;
        shapes.shapes.add(circle);
        source.process();
        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-spatial-light-roundtrip.json"));
        SceneService.saveScene(source, file, false);

        World loaded = world();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.physicsEnabled = true;
        meta.nextPhysicsShapeId = 2;
        SceneLoader.loadScene(loaded, file, false, meta);
        int loadedEntity = loaded.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsShapesComponent.class))
                .getEntities().get(0);
        Assert.assertTrue(loaded.getMapper(PhysicsBodyComponent.class)
                .get(loadedEntity).technicalSpatialLight);
        SpatialHeightComponent restoredSpatial = loaded.getMapper(SpatialHeightComponent.class)
                .get(loadedEntity);
        Assert.assertEquals(18.5f, restoredSpatial.altitude, 0f);
        Assert.assertEquals(1f, restoredSpatial.height, 0f);
        PhysicsShapeData restored = loaded.getMapper(PhysicsShapesComponent.class)
                .get(loadedEntity).shapes.first();
        Assert.assertTrue(restored.technicalSpatialLight);
        Assert.assertTrue(restored.sensor);
        Assert.assertEquals(0, restored.maskBits);
        Assert.assertEquals(0, restored.groupIndex);
        Assert.assertEquals(0.05f, restored.geometry.radius, 0f);
    }

    @Test
    public void scenePersistsSourcesButNotCompiledCacheAndRestoresLiveCacheAfterSave() {
        World world = world();
        int entityId = world.create();
        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.physicsShapeId = 1;
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = 2f;
        sources.shapes.add(shape);

        PhysicsCompiledFixturesComponent cache =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        PhysicsService.publishPreparedCandidate(
                sources, cache, PhysicsService.prepareBodyCandidate(sources.shapes));
        int generation = cache.generation;
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).create(entityId);
        footprint.valid = true;
        footprint.radiusPx = 2f;
        footprint.physicsGeneration = generation;
        world.process();

        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-physics-source-scene.json"));
        SceneService.saveScene(world, file, false);

        String json = file.readString("UTF-8");
        Assert.assertTrue(json.contains("PhysicsShapesComponent"));
        Assert.assertTrue(json.contains("\"physicsShapeId\":1"));
        Assert.assertTrue(json.contains("\"geometry\":"));
        Assert.assertFalse(json.contains("PhysicsCompiledFixturesComponent"));
        Assert.assertFalse(json.contains("SpatialPhysicsFootprintComponent"));
        PhysicsCompiledFixturesComponent restoredCache =
                world.getMapper(PhysicsCompiledFixturesComponent.class).get(entityId);
        Assert.assertTrue(restoredCache.valid);
        Assert.assertEquals(generation, restoredCache.generation);
        Assert.assertEquals(1, restoredCache.fixtures.size);
        SpatialPhysicsFootprintComponent restoredFootprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);
        Assert.assertTrue(restoredFootprint.valid);
        Assert.assertEquals(2f, restoredFootprint.radiusPx, 0f);
        Assert.assertEquals(generation, restoredFootprint.physicsGeneration);

        World loaded = world();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.physicsEnabled = true;
        meta.nextPhysicsShapeId = 2;
        SceneLoader.loadScene(loaded, file, false, meta);
        int loadedEntity = loaded.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsShapesComponent.class))
                .getEntities().get(0);
        Assert.assertEquals(1,
                loaded.getMapper(PhysicsShapesComponent.class)
                        .get(loadedEntity).shapes.first().physicsShapeId);
        Assert.assertEquals(0,
                loaded.getMapper(PhysicsShapesComponent.class)
                        .get(loadedEntity).shapes.first().spatialBlockId);
        Assert.assertFalse(
                loaded.getMapper(PhysicsCompiledFixturesComponent.class).has(loadedEntity));
        Assert.assertFalse(
                loaded.getMapper(SpatialPhysicsFootprintComponent.class).has(loadedEntity));
    }

    @Test
    public void sceneWithoutManualGeometryIsRejectedAsInvalid() {
        World world = world();
        int entityId = world.create();
        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.physicsShapeId = 13;
        sources.shapes.add(shape);
        world.process();

        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-missing-manual-geometry.json"));
        SceneService.saveScene(world, file, false);
        String json = file.readString("UTF-8");
        file.writeString(
                json.replaceFirst(",?\"geometry\":\\{[^}]*\\}", ""),
                false,
                "UTF-8");

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.physicsEnabled = true;
        meta.nextPhysicsShapeId = 14;

        try {
            SceneLoader.loadScene(world(), file, false, meta);
            Assert.fail("Missing geometry must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains(file.path()));
            Assert.assertTrue(expected.getMessage().contains("ownerEntityId"));
            Assert.assertTrue(expected.getMessage().contains("geometry"));
        }
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
