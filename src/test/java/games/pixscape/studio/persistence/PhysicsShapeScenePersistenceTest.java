package games.pixscape.studio.persistence;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class PhysicsShapeScenePersistenceTest {
    @Test
    public void scenePersistsSourcesButNotCompiledCacheAndRestoresLiveCacheAfterSave() {
        World world = world();
        int entityId = world.create();
        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.physicsShapeId = 1;
        shape.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
        shape.directGeometry.radius = 2f;
        sources.add(shape);

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
                System.getProperty("java.io.tmpdir"), "pixscape-v2-source-scene.json"));
        SceneService.saveScene(world, file, false);

        String json = file.readString("UTF-8");
        Assert.assertTrue(json.contains("PhysicsShapesComponent"));
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
        meta.nextPhysicsShapeId = 2;
        SceneLoader.loadScene(loaded, file, false, meta);
        int loadedEntity = loaded.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsShapesComponent.class))
                .getEntities().get(0);
        Assert.assertEquals(1,
                loaded.getMapper(PhysicsShapesComponent.class)
                        .get(loadedEntity).shapes.first().physicsShapeId);
        Assert.assertFalse(
                loaded.getMapper(PhysicsCompiledFixturesComponent.class).has(loadedEntity));
        Assert.assertFalse(
                loaded.getMapper(SpatialPhysicsFootprintComponent.class).has(loadedEntity));
    }

    @Test
    public void sceneWithoutDirectGeometryIsRejectedAsCleanBreak() {
        World world = world();
        int entityId = world.create();
        PhysicsShapesComponent sources =
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.physicsShapeId = 13;
        sources.add(shape);
        world.process();

        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-missing-direct-geometry.json"));
        SceneService.saveScene(world, file, false);
        String json = file.readString("UTF-8");
        file.writeString(
                json.replaceFirst(",?\"directGeometry\":\\{[^}]*\\}", ""),
                false,
                "UTF-8");

        try {
            SceneLoader.loadScene(world(), file, false, new SceneMetaRuntime());
            Assert.fail("Missing directGeometry must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains(file.path()));
            Assert.assertTrue(expected.getMessage().contains("entityId"));
            Assert.assertTrue(expected.getMessage().contains("physicsShapeId 13"));
            Assert.assertTrue(expected.getMessage().contains("directGeometry is missing"));
            Assert.assertTrue(expected.getMessage().contains("clean break Physics Model"));
        }
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
