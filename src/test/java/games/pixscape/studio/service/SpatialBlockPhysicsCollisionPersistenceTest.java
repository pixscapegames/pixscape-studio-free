package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SetSpatialBlockPhysicsCollisionCommand;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class SpatialBlockPhysicsCollisionPersistenceTest {
    private ProjectConfig previousConfig;
    private ProjectConfig config;
    private SceneMeta meta;

    @Before
    public void configureScene() {
        previousConfig = ProjectConfig.getInstance();
        config = new ProjectConfig();
        config.createSceneMeta("CollisionRoundtrip");
        meta = config.getCurrentSceneMeta();
        meta.pixelsPerMeter = 32f;
        meta.physicsEnabled = true;
        meta.tileWidth = 16f;
        meta.tileHeight = 8f;
        meta.chunkSize = 2;
        meta.tiledProjection = TiledProjection.ISO;
        meta.nextEntityStableId = 2;
        ProjectConfig.setInstance(config);
    }

    @After
    public void restoreConfig() {
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void commandRelationSurvivesStudioSaveAndActivationRebuild() {
        World source = serializationWorld();
        createTiledHost(source);
        int layer = source.create();
        source.getMapper(EntityIndexComponent.class).create(layer).layerIndex = 0;
        source.getMapper(PixscapeIdentityComponent.class)
                .create(layer).stableId = 1;
        TiledLayerComponent tiled =
                source.getMapper(TiledLayerComponent.class).create(layer);
        tiled.projection = TiledProjection.ORTHO;
        tiled.tileWidth = 32;
        tiled.tileHeight = 16;
        tiled.mapWidthCells = 4;
        tiled.mapHeightCells = 4;
        tiled.chunkSize = 8;
        tiled.data = new TiledMapLayerData(
                4, 4, 32, 16, 8, TiledProjection.ORTHO);
        tiled.data.setTile(1, 1, 101);
        tiled.tileXs.add(1);
        tiled.tileYs.add(1);
        tiled.tileAssetIds.add(101);
        tiled.tileTransformFlags.add(TileTransformFlags.NONE);
        SpatialBlocksComponent blocks =
                source.getMapper(SpatialBlocksComponent.class).create(layer);
        blocks.nextSpatialBlockId = 2;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.structureId = 1;
        block.x = 1f;
        block.y = 1f;
        block.width = 1f;
        block.depth = 1f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 1, 101);
        blocks.blocks.add(block);
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(layer);
        PhysicsService physics = new PhysicsService(source, null, meta);
        history.execute(new SetSpatialBlockPhysicsCollisionCommand(
                source,
                history.historyIds(),
                new SpatialBlockSelectionService(),
                physics,
                layer,
                1,
                true));
        TransformComponent createdTransform = source.getMapper(
                TransformComponent.class).getSafe(layer, null);
        Assert.assertNotNull(createdTransform);
        assertIdentityTransform(createdTransform);
        int physicsShapeId = source.getMapper(PhysicsShapesComponent.class)
                .get(layer).shapes.first().physicsShapeId;
        source.process();
        FileHandle sceneFile = tempSceneFile();
        SceneService.saveScene(source, sceneFile, false);

        World loaded = serializationWorld();
        try {
            SceneLoader.loadScene(loaded, sceneFile, false, meta);
            loaded.process();
            ResolvedSceneActivationPipeline.resolveTiledLayersForActivation(
                    loaded, null, null, "Test", "CollisionRoundtrip");
            ResolvedSceneActivationPipeline.validateAndCompileSpatialBlocksForActivation(
                    loaded, "Test", "CollisionRoundtrip");
            PhysicsService.rebuildPreparedBodyCaches(loaded, meta.pixelsPerMeter);

            IntBag owners = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(SpatialBlocksComponent.class))
                    .getEntities();
            Assert.assertEquals(1, owners.size());
            int restoredLayer = owners.get(0);
            SpatialBlockData restoredBlock = loaded.getMapper(
                    SpatialBlocksComponent.class).get(restoredLayer).blocks.first();
            PhysicsShapeData restoredShape = loaded.getMapper(
                    PhysicsShapesComponent.class).get(restoredLayer).shapes.first();
            PhysicsCompiledFixturesComponent compiled = loaded.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(restoredLayer);
            TiledLayerComponent restoredTiled = loaded.getMapper(
                    TiledLayerComponent.class).get(restoredLayer);
            TransformComponent restoredTransform = loaded.getMapper(
                    TransformComponent.class).getSafe(restoredLayer, null);
            Assert.assertEquals(1, restoredBlock.id);
            Assert.assertNotNull(restoredTransform);
            assertIdentityTransform(restoredTransform);
            Assert.assertEquals(physicsShapeId, restoredShape.physicsShapeId);
            Assert.assertEquals(restoredBlock.id, restoredShape.spatialBlockId);
            Assert.assertNull(restoredShape.geometry);
            Assert.assertEquals(PhysicsBodyComponent.STATIC, loaded.getMapper(
                    PhysicsBodyComponent.class).get(restoredLayer).type);
            Assert.assertEquals(TiledProjection.ORTHO, restoredTiled.projection);
            Assert.assertEquals(32, restoredTiled.tileWidth);
            Assert.assertEquals(16, restoredTiled.tileHeight);
            Assert.assertEquals(8, restoredTiled.chunkSize);
            Assert.assertEquals(TiledProjection.ORTHO, restoredTiled.data.projection);
            Assert.assertEquals(32, restoredTiled.data.tileWidth);
            Assert.assertEquals(16, restoredTiled.data.tileHeight);
            Assert.assertEquals(8, restoredTiled.data.chunkSize);
            Assert.assertTrue(compiled.valid);
            Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                    compiled.fixtures.first().shapeType);
        } finally {
            loaded.dispose();
            source.dispose();
        }
    }

    @Test
    public void sensorSpatialFootprintSurvivesStudioSaveAndLoad() {
        meta.nextPhysicsShapeId = 2;
        World source = serializationWorld();
        int entity = source.create();
        source.getMapper(PixscapeIdentityComponent.class)
                .create(entity).stableId = 1;
        source.getMapper(TransformComponent.class).create(entity);
        PhysicsBodyComponent body = source.getMapper(
                PhysicsBodyComponent.class).create(entity);
        PhysicsService.initDefaultBody(body);
        PhysicsShapesComponent shapes = source.getMapper(
                PhysicsShapesComponent.class).create(entity);
        PhysicsShapeData footprint = new PhysicsShapeData();
        footprint.physicsShapeId = 1;
        footprint.geometry = new PhysicsGeometryData();
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 0.5f;
        footprint.spatialFootprint = true;
        footprint.sensor = true;
        shapes.shapes.add(footprint);
        source.process();
        FileHandle sceneFile = tempSceneFile("sensor-spatial-footprint-roundtrip.json");
        SceneService.saveScene(source, sceneFile, false);

        World loaded = serializationWorld();
        try {
            SceneLoader.loadScene(loaded, sceneFile, false, meta);
            loaded.process();

            IntBag owners = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsShapesComponent.class))
                    .getEntities();
            Assert.assertEquals(1, owners.size());
            PhysicsShapeData restored = loaded.getMapper(PhysicsShapesComponent.class)
                    .get(owners.get(0)).shapes.first();
            Assert.assertTrue(restored.spatialFootprint);
            Assert.assertTrue(restored.sensor);
        } finally {
            loaded.dispose();
            source.dispose();
        }
    }

    @Test
    public void activationCreatesIdentityTransformForAuthoredTiledPhysics() {
        World world = serializationWorld();
        createTiledHost(world);
        int layer = world.create();
        world.getMapper(EntityIndexComponent.class).create(layer).layerIndex = 0;
        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.projection = TiledProjection.ORTHO;
        tiled.tileWidth = 32;
        tiled.tileHeight = 16;
        tiled.mapWidthCells = 4;
        tiled.mapHeightCells = 4;
        tiled.chunkSize = 8;
        tiled.tileXs.add(1);
        tiled.tileYs.add(1);
        tiled.tileAssetIds.add(101);
        tiled.tileTransformFlags.add(TileTransformFlags.NONE);
        SpatialBlocksComponent blocks =
                world.getMapper(SpatialBlocksComponent.class).create(layer);
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.structureId = 1;
        block.x = 1f;
        block.y = 1f;
        block.width = 1f;
        block.depth = 1f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 1, 101);
        blocks.blocks.add(block);
        PhysicsBodyComponent body =
                world.getMapper(PhysicsBodyComponent.class).create(layer);
        PhysicsService.initDefaultBody(body);
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(layer);
        PhysicsShapeData linked = new PhysicsShapeData();
        linked.physicsShapeId = 1;
        linked.spatialBlockId = 1;
        shapes.shapes.add(linked);
        world.process();
        Assert.assertFalse(world.getMapper(TransformComponent.class).has(layer));

        try {
            ResolvedSceneActivationPipeline.resolveTiledLayersForActivation(
                    world, null, null, "Test", "MissingTransform");
            PhysicsService.rebuildPreparedBodyCaches(world, meta.pixelsPerMeter);

            TransformComponent transform = world.getMapper(
                    TransformComponent.class).getSafe(layer, null);
            Assert.assertNotNull(transform);
            assertIdentityTransform(transform);
            Assert.assertEquals(PhysicsBodyComponent.STATIC, body.type);
            Assert.assertTrue(world.getMapper(PhysicsCompiledFixturesComponent.class)
                    .get(layer).valid);
        } finally {
            world.dispose();
        }
    }

    private static World serializationWorld() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }

    private static void createTiledHost(World world) {
        int host = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(host);
        layer.type = LayerComponent.TYPE_CLASSIC;
        layer.layerIndex = 0;
    }

    private static void assertIdentityTransform(TransformComponent transform) {
        Assert.assertEquals(0f, transform.x, 0f);
        Assert.assertEquals(0f, transform.y, 0f);
        Assert.assertEquals(0f, transform.rotationRad, 0f);
        Assert.assertEquals(1f, transform.scaleX, 0f);
        Assert.assertEquals(1f, transform.scaleY, 0f);
    }

    private static FileHandle tempSceneFile() {
        return tempSceneFile("spatial-block-collision-roundtrip.json");
    }

    private static FileHandle tempSceneFile(String fileName) {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "pixscape-studio-tests");
        Assert.assertTrue(dir.exists() || dir.mkdirs());
        File file = new File(dir, fileName);
        if (file.exists()) Assert.assertTrue(file.delete());
        return new FileHandle(file);
    }
}
