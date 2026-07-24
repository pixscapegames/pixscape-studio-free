package games.pixscape.studio.persistence;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsCompiledFixtureCachePublisher;
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
        shape.physicsShapeId = 1;
        shape.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
        shape.radius = 2f;
        sources.add(shape);

        PhysicsCompiledFixturesComponent cache =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        CompiledFixtureData fixture = new CompiledFixtureData();
        fixture.physicsShapeId = 1;
        fixture.partIndex = 0;
        fixture.shapeType = CompiledFixtureData.SHAPE_CIRCLE;
        fixture.radius = 2f;
        Array<CompiledFixtureData> candidate =
                new Array<>(true, 1, CompiledFixtureData.class);
        candidate.add(fixture);
        new PhysicsCompiledFixtureCachePublisher().publish(
                cache, new PhysicsBodyCompiler().prepare(candidate));
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

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
