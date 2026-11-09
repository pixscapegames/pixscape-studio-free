package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SceneService;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TiledMapConfigurationPersistenceTest {
    @After
    public void tearDown() {
        ProjectConfig.setInstance(null);
        EventFlow.i().discardPending();
    }

    @Test
    public void mapsOwnIndependentConfigurationAndBothConfigsRoundTrip() {
        World world = worldWithSerialization();
        World loaded = null;
        try {
            ProjectConfig cfg = new ProjectConfig();
            cfg.createSceneMeta("Main");
            ProjectConfig.setInstance(cfg);
            SceneMeta sceneMeta = cfg.getCurrentSceneMeta();
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, sceneMeta);
            LayerService layers = new LayerService(
                    world, null, new HistoryIdRegistry(), identities);
            int layerEntity = layers.getLayerEntity(layers.addLayerTop("Layer"));
            AtomicInteger selected = new AtomicInteger(-1);

            new AddTiledMapCommand(layers, layerEntity, 12, 9,
                    TiledProjection.ISO, 64, 32, 8, selected::set).redo();
            int mapA = selected.get();

            new AddTiledMapCommand(layers, layerEntity, 7, 5,
                    TiledProjection.ORTHO, 32, 32, 16, selected::set).redo();
            int mapB = selected.get();
            world.process();

            var identityA = world.getMapper(PixscapeIdentityComponent.class).get(mapA);
            var identityB = world.getMapper(PixscapeIdentityComponent.class).get(mapB);
            int stableIdA = identityA.stableId;
            int stableIdB = identityB.stableId;
            int nextStableId = sceneMeta.nextEntityStableId;
            HistoryManager history = new HistoryManager(16);
            rename(world, history, mapA, "MonSuperNom");
            rename(world, history, mapB, "");
            history.undo();
            history.redo();
            assertEquals(stableIdA, identityA.stableId);
            assertEquals(stableIdB, identityB.stableId);
            assertEquals(nextStableId, sceneMeta.nextEntityStableId);

            assertMap(world.getMapper(TiledLayerComponent.class).get(mapA),
                    TiledProjection.ISO, 64, 32, 8);
            assertMap(world.getMapper(TiledLayerComponent.class).get(mapB),
                    TiledProjection.ORTHO, 32, 32, 16);

            FileHandle file = tempSceneFile();
            SceneService.saveScene(world, file, false);
            loaded = worldWithSerialization();
            SceneLoader.loadScene(loaded, file, false, sceneMeta);
            loaded.process();

            IntBag maps = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(TiledLayerComponent.class)).getEntities();
            assertEquals(2, maps.size());
            int isoCount = 0;
            int orthoCount = 0;
            for (int i = 0; i < maps.size(); i++) {
                TiledLayerComponent map = loaded.getMapper(TiledLayerComponent.class)
                        .get(maps.get(i));
                if (map.projection == TiledProjection.ISO && map.tileWidth == 64) {
                    assertMap(map, TiledProjection.ISO, 64, 32, 8);
                    var identity = loaded.getMapper(PixscapeIdentityComponent.class).get(maps.get(i));
                    assertEquals("MonSuperNom", identity.name);
                    assertEquals(stableIdA, identity.stableId);
                    isoCount++;
                } else {
                    assertMap(map, TiledProjection.ORTHO, 32, 32, 16);
                    var identity = loaded.getMapper(PixscapeIdentityComponent.class).get(maps.get(i));
                    assertEquals("", identity.name);
                    assertEquals(stableIdB, identity.stableId);
                    orthoCount++;
                }
            }
            assertEquals(1, isoCount);
            assertEquals(1, orthoCount);
        } finally {
            if (loaded != null) loaded.dispose();
            world.dispose();
        }
    }

    @Test
    public void renamingAndSavingDoNotRepairMissingStableIdAndLoaderStillRejectsIt() {
        World world = worldWithSerialization();
        World loaded = worldWithSerialization();
        try {
            ProjectConfig cfg = new ProjectConfig();
            cfg.createSceneMeta("Main");
            ProjectConfig.setInstance(cfg);
            SceneMeta sceneMeta = cfg.getCurrentSceneMeta();
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, sceneMeta);
            LayerService layers = new LayerService(world, null, new HistoryIdRegistry(), identities);
            int layer = layers.getLayerEntity(layers.addLayerTop("Layer"));
            AtomicInteger selected = new AtomicInteger(-1);
            new AddTiledMapCommand(layers, layer, 12, 9,
                    TiledProjection.ORTHO, 32, 32, 8, selected::set).redo();
            world.process();
            var identity = world.getMapper(PixscapeIdentityComponent.class).get(selected.get());
            identity.stableId = -1;
            int nextStableId = sceneMeta.nextEntityStableId;
            rename(world, new HistoryManager(16), selected.get(), "Still no ID");
            FileHandle file = tempSceneFile();
            SceneService.saveScene(world, file, false);
            assertEquals(-1, identity.stableId);
            assertEquals("Still no ID", identity.name);
            assertEquals(nextStableId, sceneMeta.nextEntityStableId);
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> SceneLoader.loadScene(loaded, file, false, sceneMeta));
            assertTrue(error.getMessage(), error.getMessage().contains("ID must be positive"));
            assertEquals(nextStableId, sceneMeta.nextEntityStableId);
        } finally {
            loaded.dispose();
            world.dispose();
        }
    }

    private static void rename(World world, HistoryManager history, int entityId, String name) {
        var identity = world.getMapper(PixscapeIdentityComponent.class).get(entityId);
        history.execute(new ChangeEntityNameCommand(world, history.historyIds(),
                history.historyIds().ensureForEntity(entityId), identity.name, name, 0));
    }

    private static void assertMap(TiledLayerComponent map, TiledProjection projection,
                                  int tileWidth, int tileHeight, int chunkSize) {
        assertEquals(projection, map.projection);
        assertEquals(tileWidth, map.tileWidth);
        assertEquals(tileHeight, map.tileHeight);
        assertEquals(chunkSize, map.chunkSize);
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private static FileHandle tempSceneFile() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "pixscape-studio-tests");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create test directory: " + dir);
        }
        return new FileHandle(new File(dir, "tiled-map-configuration-roundtrip.json"));
    }
}
