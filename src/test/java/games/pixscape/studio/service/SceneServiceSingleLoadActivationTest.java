package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.property.TiledMapProperties;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SceneServiceSingleLoadActivationTest {

    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void activation_loadsOnceReconstructsEveryTiledLayerBeforeSpatialValidationAndSelection()
            throws Exception {
        Fixture fixture = fixture("single-load", "Main");
        writeScene(fixture.sceneFile("Main"), new int[]{448, 88}, new int[]{101, 201});
        World active = serializationWorld();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger renderRebuilds = new AtomicInteger();

        activate(active, fixture, "Main", loads, renderRebuilds);

        assertEquals(1, loads.get());
        assertEquals(1, renderRebuilds.get());
        int[] tiledLayers = tiledLayerIds(active);
        assertEquals(2, tiledLayers.length);
        int[] occupied = new int[tiledLayers.length];
        for (int i = 0; i < tiledLayers.length; i++) {
            TiledLayerComponent tiled = active.getMapper(TiledLayerComponent.class).get(tiledLayers[i]);
            assertNotNull(tiled.data);
            occupied[i] = occupiedCount(tiled.data);
        }
        Arrays.sort(occupied);
        assertEquals(88, occupied[0]);
        assertEquals(448, occupied[1]);

        fixture.cfg.setCurrentSceneByName("Main");
        ProjectConfig.setInstance(fixture.cfg);
        new TiledMapProperties(active, () -> { }).setLayerEntityId(tiledLayers[0]);
        active.dispose();
    }

    @Test
    public void twoValidSceneSwitches_preserveTiledContentsAcrossAtoBtoA() throws Exception {
        Fixture fixture = fixture("scene-switch", "A", "B");
        writeScene(fixture.sceneFile("A"), new int[]{37, 19}, new int[]{301, 302});
        writeScene(fixture.sceneFile("B"), new int[]{61, 23}, new int[]{401, 402});
        World active = serializationWorld();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger renderRebuilds = new AtomicInteger();

        activate(active, fixture, "A", loads, renderRebuilds);
        assertOccupiedCounts(active, 19, 37);

        clear(active);
        activate(active, fixture, "B", loads, renderRebuilds);
        assertOccupiedCounts(active, 23, 61);

        clear(active);
        activate(active, fixture, "A", loads, renderRebuilds);
        assertOccupiedCounts(active, 19, 37);
        assertEquals(3, loads.get());
        assertEquals(3, renderRebuilds.get());
        active.dispose();
    }

    @Test
    public void invalidSpatialActivation_propagatesBeforeHistoryAndRenderRebuild() throws Exception {
        Fixture fixture = fixture("invalid-spatial", "Main");
        writeScene(fixture.sceneFile("Main"), new int[]{7, 5}, new int[]{501, 502});
        String json = fixture.sceneFile("Main").readString("UTF-8")
                .replace("\"structureId\":1", "\"structureId\":0");
        fixture.sceneFile("Main").writeString(json, false, "UTF-8");
        World active = serializationWorld();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger renderRebuilds = new AtomicInteger();

        org.junit.Assert.assertThrows(
                SceneService.SpatialSceneActivationException.class,
                () -> activate(active, fixture, "Main", loads, renderRebuilds)
        );

        assertEquals(1, loads.get());
        assertEquals(0, renderRebuilds.get());
        active.dispose();
    }

    @Test
    public void spatialToNonSpatialToSpatial_doesNotRetainStaleSpatialState() throws Exception {
        Fixture fixture = fixture("spatial-non-spatial", "A", "B");
        writeScene(fixture.sceneFile("A"), new int[]{29, 11}, new int[]{601, 602});
        writeNonSpatialScene(fixture.sceneFile("B"), new int[]{17, 3}, new int[]{701, 702});
        World active = serializationWorld();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger renderRebuilds = new AtomicInteger();

        activate(active, fixture, "A", loads, renderRebuilds);
        assertEquals(2, spatialLayerIds(active).length);
        clear(active);

        activate(active, fixture, "B", loads, renderRebuilds);
        assertEquals(0, spatialLayerIds(active).length);
        assertOccupiedCounts(active, 3, 17);
        clear(active);

        activate(active, fixture, "A", loads, renderRebuilds);
        assertEquals(2, spatialLayerIds(active).length);
        assertOccupiedCounts(active, 11, 29);
        assertEquals(3, loads.get());
        assertEquals(3, renderRebuilds.get());
        active.dispose();
    }

    private static void activate(World world,
                                 Fixture fixture,
                                 String sceneName,
                                 AtomicInteger loads,
                                 AtomicInteger renderRebuilds) {
        HistoryManager history = new HistoryManager(16);
        history.historyIds().ensureForEntity(999);
        ResolvedSceneActivationPipeline pipeline = new ResolvedSceneActivationPipeline(
                world,
                null,
                null,
                history,
                (config, canonicalTag, projectDir) -> {
                    int[] layers = tiledLayerIds(world);
                    assertEquals(2, layers.length);
                    for (int layer : layers) {
                        assertNotNull(world.getMapper(TiledLayerComponent.class).get(layer).data);
                        assertNotNull(world.getMapper(LayerMetaComponent.class).get(layer));
                        org.junit.Assert.assertTrue(history.historyIds().historyIdOfEntity(layer) > 0L);
                    }
                    org.junit.Assert.assertEquals(-1L, history.historyIds().historyIdOfEntity(999));
                    org.junit.Assert.assertNull(
                            ResolvedSceneActivationPipeline.firstInvalidSpatialBlock(world));
                    renderRebuilds.incrementAndGet();
                },
                (target, file, editMode) -> {
                    loads.incrementAndGet();
                    SceneLoader.loadScene(target, file, editMode);
                }
        );
        pipeline.activate(new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                fixture.cfg,
                fixture.cfg.getSceneMeta(sceneName),
                fixture.sceneFile(sceneName),
                fixture.projectDir,
                fixture.cfg.projectTitle,
                sceneName,
                fixture.cfg.canonicalSceneTag(sceneName)
        ));
    }

    private static void writeScene(FileHandle sceneFile, int[] counts, int[] assetIds) {
        World authored = serializationWorld();
        for (int i = 0; i < counts.length; i++) {
            int layerEntity = authored.create();
            LayerComponent layer = authored.getMapper(LayerComponent.class).create(layerEntity);
            layer.spatialEnabled = true;
            authored.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Layer " + i;
            TiledLayerComponent tiled = authored.getMapper(TiledLayerComponent.class).create(layerEntity);
            tiled.mapWidthCells = 32;
            tiled.mapHeightCells = 32;
            tiled.spatialEnabled = true;
            for (int cell = 0; cell < counts[i]; cell++) {
                tiled.tileXs.add(cell % tiled.mapWidthCells);
                tiled.tileYs.add(cell / tiled.mapWidthCells);
                tiled.tileAssetIds.add(assetIds[i]);
                tiled.tileTransformFlags.add((byte) 0);
            }

            SpatialBlockData wall = new SpatialBlockData();
            wall.id = 100 + i;
            wall.structureId = 1;
            wall.x = 0f;
            wall.y = 0f;
            wall.width = 1f;
            wall.depth = 1f;
            wall.height = 8f;
            wall.actorOccluder = true;
            wall.beginAuthoredLinkedTileRefs();
            wall.addLinkedTileRef(0, 0, assetIds[i]);
            authored.getMapper(SpatialBlocksComponent.class).create(layerEntity).blocks.add(wall);
        }
        authored.process();
        SceneService.saveScene(authored, sceneFile, false);
        authored.dispose();
    }

    private static void writeNonSpatialScene(FileHandle sceneFile, int[] counts, int[] assetIds) {
        World authored = serializationWorld();
        for (int i = 0; i < counts.length; i++) {
            int layerEntity = authored.create();
            authored.getMapper(LayerComponent.class).create(layerEntity).spatialEnabled = false;
            authored.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Layer " + i;
            TiledLayerComponent tiled = authored.getMapper(TiledLayerComponent.class).create(layerEntity);
            tiled.mapWidthCells = 32;
            tiled.mapHeightCells = 32;
            for (int cell = 0; cell < counts[i]; cell++) {
                tiled.tileXs.add(cell % tiled.mapWidthCells);
                tiled.tileYs.add(cell / tiled.mapWidthCells);
                tiled.tileAssetIds.add(assetIds[i]);
                tiled.tileTransformFlags.add((byte) 0);
            }
        }
        authored.process();
        SceneService.saveScene(authored, sceneFile, false);
        authored.dispose();
    }

    private static void assertOccupiedCounts(World world, int first, int second) {
        int[] ids = tiledLayerIds(world);
        assertEquals(2, ids.length);
        int[] counts = new int[]{
                occupiedCount(world.getMapper(TiledLayerComponent.class).get(ids[0]).data),
                occupiedCount(world.getMapper(TiledLayerComponent.class).get(ids[1]).data)
        };
        Arrays.sort(counts);
        assertEquals(first, counts[0]);
        assertEquals(second, counts[1]);
    }

    private static int occupiedCount(TiledMapLayerData data) {
        assertNotNull(data);
        int count = 0;
        for (com.badlogic.gdx.utils.IntMap.Values<TileChunk> chunks = data.getChunks(); chunks.hasNext(); ) {
            TileChunk chunk = chunks.next();
            for (int assetId : chunk.assetIds) {
                if (assetId != 0) count++;
            }
        }
        return count;
    }

    private static int[] tiledLayerIds(World world) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class)).getEntities();
        return Arrays.copyOf(bag.getData(), bag.size());
    }

    private static int[] spatialLayerIds(World world) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class)).getEntities();
        return Arrays.copyOf(bag.getData(), bag.size());
    }

    private static void clear(World world) {
        IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        int[] ids = Arrays.copyOf(entities.getData(), entities.size());
        for (int id : ids) world.delete(id);
        world.process();
    }

    private static Fixture fixture(String directoryName, String... sceneNames) throws Exception {
        Path path = Files.createTempDirectory("scene-service-" + directoryName);
        FileHandle projectDir = new FileHandle(path.toFile());
        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Single-load activation test";
        cfg.projectFileName = "single-load-activation-test";
        cfg.exportRootPathDir = path.resolve("export").toString();
        for (String sceneName : sceneNames) {
            cfg.createSceneMeta(sceneName);
            SceneMeta meta = cfg.getSceneMeta(sceneName);
            meta.tiledEnabled = true;
            meta.tileWidth = 16;
            meta.tileHeight = 16;
            meta.chunkSize = 8;
            meta.tiledProjection = SceneMetaRuntime.TiledProjection.ORTHO;
        }
        return new Fixture(cfg, projectDir);
    }

    private static World serializationWorld() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private record Fixture(ProjectConfig cfg, FileHandle projectDir) {
        FileHandle sceneFile(String sceneName) {
            return projectDir.child(StudioFs.DIR_SCENES).child(cfg.getSceneMeta(sceneName).getFile());
        }
    }
}
