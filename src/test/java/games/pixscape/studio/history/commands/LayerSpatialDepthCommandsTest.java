package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialCompiledLayerCache;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialProjectedFaceCache;
import games.pixscape.runtime.spatial.SpatialTileOrderCache;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.spatial.SpatialStructureGeometryCache;
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
    public void editTiledLayerSpatialDefaults_advancesAuthoredRevisionOncePerHistoryTransition() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createTiledLayer(world, 0);
        history.historyIds().ensureForEntity(layerId);

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        tiled.spatialEnabled = true;
        tiled.defaultTileAltitude = 1f;
        tiled.defaultTileHeight = 8f;
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData inherited = spatialWall(1, 1, 1f);
        SpatialBlockData explicit = spatialWall(2, 2, 42f);
        blocks.blocks.add(inherited);
        blocks.blocks.add(explicit);

        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(tiled);
        EditTiledLayerSpatialDefaultsCommand command = new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                layerId,
                before,
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(3f, 12f)
        );

        history.execute(command);
        Assert.assertEquals(1, blocks.revision);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertEquals(3f, inherited.altitude, 0f);
        Assert.assertEquals(42f, explicit.altitude, 0f);

        history.undo();
        Assert.assertEquals(2, blocks.revision);
        Assert.assertEquals(1f, inherited.altitude, 0f);
        Assert.assertEquals(42f, explicit.altitude, 0f);

        history.redo();
        Assert.assertEquals(3, blocks.revision);
        Assert.assertEquals(3f, inherited.altitude, 0f);

        history.undo();
        history.redo();
        Assert.assertEquals(5, blocks.revision);
    }

    @Test
    public void editTiledLayerSpatialDefaults_rebuildsCompiledProjectedOrderAndOverlayCaches() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createTiledLayer(world, 0);
        history.historyIds().ensureForEntity(layerId);

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        tiled.data = spatialMap();
        tiled.spatialEnabled = true;
        tiled.defaultTileAltitude = 1f;
        tiled.defaultTileHeight = 8f;
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        blocks.blocks.add(spatialWall(1, 1, 1f));

        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        SpatialStructureGeometryCache overlay = new SpatialStructureGeometryCache();
        Assert.assertTrue(compiled.ensure(blocks));
        Assert.assertTrue(projected.ensure(compiled, tiled.data));
        Assert.assertTrue(order.ensure(layerId, tiled.data, blocks, compiled));
        Assert.assertTrue(overlay.synchronize(layerId, blocks, tiled.data).published());
        float originalIntercept = projected.intercept[0];

        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(tiled);
        history.execute(new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                layerId,
                before,
                before.withDefaultAltitude(5f)
        ));

        assertCachesRebuilt(layerId, tiled.data, blocks, compiled, projected, order, overlay, 5f);
        Assert.assertNotEquals(originalIntercept, projected.intercept[0], 0f);

        history.undo();
        assertCachesRebuilt(layerId, tiled.data, blocks, compiled, projected, order, overlay, 1f);
        Assert.assertEquals(originalIntercept, projected.intercept[0], 0f);

        history.redo();
        assertCachesRebuilt(layerId, tiled.data, blocks, compiled, projected, order, overlay, 5f);
    }

    @Test
    public void editTiledLayerSpatialDefaults_failedAndNoopApplicationsDoNotAdvanceRevision() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = createLayer(world, 0, LayerComponent.TYPE_TILED);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                new EditTiledLayerSpatialDefaultsCommand.Snapshot(1f, 8f);

        EditTiledLayerSpatialDefaultsCommand failed = new EditTiledLayerSpatialDefaultsCommand(
                world, history.historyIds(), layerId, before, before.withDefaultAltitude(3f));
        failed.redo();
        Assert.assertEquals(0, blocks.revision);

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.defaultTileAltitude = 1f;
        tiled.defaultTileHeight = 8f;
        EditTiledLayerSpatialDefaultsCommand noop = new EditTiledLayerSpatialDefaultsCommand(
                world, history.historyIds(), layerId, before, before);
        Assert.assertTrue(noop.isNoop());
        noop.redo();
        Assert.assertEquals(0, blocks.revision);
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

        games.pixscape.studio.service.SceneSaveTestSupport.save(
                world, sceneFile, new SceneMetaRuntime());

        World loaded = serializableWorld();
        SceneLoader.loadScene(loaded, sceneFile, false, new games.pixscape.runtime.loading.SceneMetaRuntime());

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

        games.pixscape.studio.service.SceneSaveTestSupport.save(
                world, sceneFile, new SceneMetaRuntime());

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

    private static SpatialBlockData spatialWall(int id, int structureId, float altitude) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = structureId == 1 ? 2f : 5f;
        wall.y = 2f;
        wall.width = 2f;
        wall.depth = 1f;
        wall.altitude = altitude;
        wall.height = 6f;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef((int) wall.x, (int) wall.y, 1);
        wall.addLinkedTileRef((int) wall.x + 1, (int) wall.y, 1);
        return wall;
    }

    private static TiledMapLayerData spatialMap() {
        TiledMapLayerData map = new TiledMapLayerData(
                8, 8, 64, 32, 4, SceneMetaRuntime.TiledProjection.ISO);
        for (int gy = 0; gy < 8; gy++) {
            for (int gx = 0; gx < 8; gx++) map.setTile(gx, gy, 1);
        }
        int nextRef = 0;
        for (int cy = 0; cy < 2; cy++) {
            for (int cx = 0; cx < 2; cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                chunk.renderRefStartIndex = nextRef;
                chunk.renderRefCount = chunk.cellCount();
                nextRef += chunk.cellCount();
            }
        }
        return map;
    }

    private static void assertCachesRebuilt(int layerId,
                                            TiledMapLayerData map,
                                            SpatialBlocksComponent blocks,
                                            SpatialCompiledLayerCache compiled,
                                            SpatialProjectedFaceCache projected,
                                            SpatialTileOrderCache order,
                                            SpatialStructureGeometryCache overlay,
                                            float expectedAltitude) {
        Assert.assertTrue(compiled.ensure(blocks));
        Assert.assertTrue(projected.ensure(compiled, map));
        Assert.assertTrue(order.ensure(layerId, map, blocks, compiled));
        Assert.assertTrue(overlay.synchronize(layerId, blocks, map).published());
        Assert.assertEquals(expectedAltitude, compiled.structure(0).altitude(), 0f);
        Assert.assertEquals(expectedAltitude, projected.faceAltitude[0], 0f);
        Assert.assertEquals(expectedAltitude, overlay.structure(0).altitude(), 0f);
    }

    private static World serializableWorld() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
