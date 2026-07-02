package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class LayerSpatialDepthCommandsTest {
    @Test
    public void toggleLayerSpatialDepth_updatesLayerAndTiledRuntimeState() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createTiledLayer(world, 0);
        history.historyIds().ensureForEntity(layerId);

        history.execute(new ToggleLayerSpatialDepthCommand(
                world,
                history.historyIds(),
                layerId,
                true,
                0f,
                32f
        ));

        LayerComponent layer = world.getMapper(LayerComponent.class).get(layerId);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        Assert.assertTrue(layer.spatialEnabled);
        Assert.assertTrue(tiled.spatialEnabled);
        Assert.assertTrue(tiled.data.spatialEnabled);
        Assert.assertEquals(32f, tiled.defaultTileHeight, 0.0001f);
        Assert.assertEquals(32f, tiled.data.defaultTileHeight, 0.0001f);

        history.undo();
        Assert.assertFalse(layer.spatialEnabled);
        Assert.assertFalse(tiled.spatialEnabled);
        Assert.assertFalse(tiled.data.spatialEnabled);
        Assert.assertEquals(0f, tiled.defaultTileHeight, 0.0001f);

        history.redo();
        Assert.assertTrue(layer.spatialEnabled);
        Assert.assertTrue(tiled.spatialEnabled);
        Assert.assertEquals(32f, tiled.defaultTileHeight, 0.0001f);
    }

    @Test
    public void editTiledLayerSpatialDefaults_updatesValuesWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createTiledLayer(world, 0);
        history.historyIds().ensureForEntity(layerId);

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        tiled.spatialEnabled = true;
        tiled.defaultTileAltitude = 1f;
        tiled.defaultTileHeight = 8f;

        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(tiled);
        EditTiledLayerSpatialDefaultsCommand.Snapshot after =
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(3f, 12f);

        history.execute(new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                layerId,
                before,
                after
        ));

        Assert.assertEquals(3f, tiled.defaultTileAltitude, 0.0001f);
        Assert.assertEquals(12f, tiled.defaultTileHeight, 0.0001f);
        Assert.assertEquals(3f, tiled.data.defaultTileAltitude, 0.0001f);
        Assert.assertEquals(12f, tiled.data.defaultTileHeight, 0.0001f);

        history.undo();
        Assert.assertEquals(1f, tiled.defaultTileAltitude, 0.0001f);
        Assert.assertEquals(8f, tiled.defaultTileHeight, 0.0001f);

        history.redo();
        Assert.assertEquals(3f, tiled.defaultTileAltitude, 0.0001f);
        Assert.assertEquals(12f, tiled.defaultTileHeight, 0.0001f);
    }

    @Test
    public void editTiledLayerSpatialDefaults_resyncsBlocksUsingPreviousDefaultAltitude() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createTiledLayer(world, 0);
        history.historyIds().ensureForEntity(layerId);

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        tiled.spatialEnabled = true;
        tiled.defaultTileAltitude = 0f;
        tiled.defaultTileHeight = 8f;

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData inherited = new SpatialBlockData();
        inherited.id = 1;
        inherited.altitude = 0f;
        blocks.blocks.add(inherited);
        SpatialBlockData explicit = new SpatialBlockData();
        explicit.id = 2;
        explicit.altitude = 42f;
        blocks.blocks.add(explicit);

        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(tiled);
        EditTiledLayerSpatialDefaultsCommand.Snapshot after =
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(155f, 8f);

        history.execute(new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                layerId,
                before,
                after
        ));

        Assert.assertEquals(155f, inherited.altitude, 0.0001f);
        Assert.assertEquals(42f, explicit.altitude, 0.0001f);

        history.undo();
        Assert.assertEquals(0f, inherited.altitude, 0.0001f);
        Assert.assertEquals(42f, explicit.altitude, 0.0001f);
    }

    @Test
    public void disablingLayerSpatialDepth_removesEntitySpatialDataAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createLayer(world, 2, LayerComponent.TYPE_CLASSIC);
        world.getMapper(LayerComponent.class).get(layerId).spatialEnabled = true;
        int actorId = createActor(world, 2, 4f, 10f);
        history.historyIds().ensureForEntity(layerId);
        history.historyIds().ensureForEntity(actorId);

        history.execute(new ToggleLayerSpatialDepthCommand(
                world,
                history.historyIds(),
                layerId,
                false,
                0f,
                0f
        ));

        Assert.assertFalse(world.getMapper(LayerComponent.class).get(layerId).spatialEnabled);
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(actorId));

        history.undo();
        SpatialHeightComponent restored = world.getMapper(SpatialHeightComponent.class).get(actorId);
        Assert.assertTrue(world.getMapper(LayerComponent.class).get(layerId).spatialEnabled);
        Assert.assertEquals(4f, restored.altitude, 0.0001f);
        Assert.assertEquals(10f, restored.height, 0.0001f);
    }

    @Test
    public void tiledLayerSpatialState_persistsThroughSceneSaveReload() throws Exception {
        World world = serializableWorld();
        int layerId = createTiledLayer(world, 0);
        LayerComponent layer = world.getMapper(LayerComponent.class).get(layerId);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        layer.spatialEnabled = true;
        tiled.spatialEnabled = true;
        tiled.defaultTileAltitude = 4f;
        tiled.defaultTileHeight = 20f;
        world.process();

        Path scenePath = Files.createTempFile("layer-spatial-depth", ".json");
        FileHandle sceneFile = new FileHandle(scenePath.toFile());

        SceneService.saveScene(world, sceneFile, false);

        World loaded = serializableWorld();
        SceneLoader.loadScene(loaded, sceneFile, false);

        IntBag entities = loaded.getAspectSubscriptionManager()
                .get(Aspect.all(LayerComponent.class, TiledLayerComponent.class))
                .getEntities();

        Assert.assertEquals(1, entities.size());
        int loadedLayerId = entities.get(0);
        LayerComponent loadedLayer = loaded.getMapper(LayerComponent.class).get(loadedLayerId);
        TiledLayerComponent loadedTiled = loaded.getMapper(TiledLayerComponent.class).get(loadedLayerId);

        Assert.assertTrue(loadedLayer.spatialEnabled);
        Assert.assertTrue(loadedTiled.spatialEnabled);
        Assert.assertEquals(4f, loadedTiled.defaultTileAltitude, 0.0001f);
        Assert.assertEquals(20f, loadedTiled.defaultTileHeight, 0.0001f);
    }

    @Test
    public void saveScene_omitsRuntimeOnlyTiledAndVisibilityState() throws Exception {
        World world = serializableWorld();
        int layerId = createTiledLayer(world, 0);

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(layerId);
        visibility.visible = true;
        visibility.culledByFrustum = false;
        visibility.inView = true;
        world.process();

        Path scenePath = Files.createTempFile("layer-runtime-state", ".json");
        FileHandle sceneFile = new FileHandle(scenePath.toFile());

        SceneService.saveScene(world, sceneFile, false);

        String saved = Files.readString(scenePath);
        Assert.assertFalse(saved.contains("\"culledByFrustum\""));
        Assert.assertFalse(saved.contains("\"inView\""));

        Assert.assertFalse(visibility.culledByFrustum);
        Assert.assertTrue(visibility.inView);
    }

    private static int createTiledLayer(World world, int layerIndex) {
        int layerId = createLayer(world, layerIndex, LayerComponent.TYPE_TILED);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(4, 4, 16, 32, 4);
        return layerId;
    }

    private static int createLayer(World world, int layerIndex, int type) {
        int layerId = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerId);
        layer.layerIndex = layerIndex;
        layer.type = type;
        layer.spatialEnabled = false;
        return layerId;
    }

    private static int createActor(World world, int layerIndex, float altitude, float height) {
        int actorId = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(actorId);
        index.layerIndex = layerIndex;
        index.zIndex = 0;
        SpatialHeightComponent spatial = world.getMapper(SpatialHeightComponent.class).create(actorId);
        spatial.altitude = altitude;
        spatial.height = height;
        return actorId;
    }

    private static World serializableWorld() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
