package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.history.commands.CreateTiledLayerCommand;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

import static org.junit.Assert.*;

public class LayerServiceParallaxTest {

    @Test
    public void parallax_returnsParallaxComponentForClassicLayer() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new SceneMetaRuntime());
        LayerService service = new LayerService(world, null, new HistoryIdRegistry(), identities);

        int layerEntity = world.create();

        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        layer.type = LayerComponent.TYPE_CLASSIC;

        world.getMapper(LayerMetaComponent.class).create(layerEntity);

        LayerParallaxComponent parallax = world.getMapper(LayerParallaxComponent.class).create(layerEntity);
        parallax.factorX = 0.75f;
        parallax.factorY = 1.25f;

        world.process();          // important
        service.rebuildFromWorld();

        LayerParallaxComponent resolved = service.parallax(0);
        assertNotNull(resolved);
        assertEquals(0.75f, resolved.factorX, 0.0001f);
        assertEquals(1.25f, resolved.factorY, 0.0001f);
    }

    @Test
    public void manualLayerReceivesStableIdFromWorldRegistry() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        IdentityRegistry identities = identities(world, meta);
        LayerService service = new LayerService(world, null, new HistoryIdRegistry(), identities);

        int index = service.addLayerTop("Manual");
        int layer = service.getLayerEntity(index);

        assertEquals(1, world.getMapper(PixscapeIdentityComponent.class).get(layer).stableId);
        assertEquals(layer, identities.findByStableId(1));
        assertEquals(2, meta.nextEntityStableId);
    }

    @Test
    public void layerRedoRestoresStableIdWithoutAdvancingHighWater() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        IdentityRegistry identities = identities(world, meta);
        HistoryManager history = new HistoryManager(8);
        LayerService service = new LayerService(world, null, history.historyIds(), identities);
        CreateLayerCommand command = new CreateLayerCommand(service, 0, "History", null);

        history.execute(command);
        int stableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(service.getLayerEntity(0)).stableId;
        history.undo();
        history.redo();

        assertEquals(1, stableId);
        assertTrue(identities.findByStableId(stableId) >= 0);
        assertEquals(stableId, world.getMapper(PixscapeIdentityComponent.class)
                .get(service.getLayerEntity(0)).stableId);
        assertEquals(2, meta.nextEntityStableId);
    }

    @Test
    public void failedIdentityAllocationLeavesNoLayerOrHistoryBinding() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, null);
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        LayerService service = new LayerService(world, null, historyIds, identities);

        assertThrows(IllegalStateException.class, () -> service.addLayerTop("Rejected"));
        world.process();

        assertEquals(0, service.count());
        assertEquals(0, world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size());
        assertEquals(-1L, historyIds.historyIdOfEntity(0));
    }

    @Test
    public void tiledLayerCreatesDistinctMapAndRedoPreservesMapStateAndIdentity() {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        World world = new World(new WorldConfiguration());
        try {
            SceneMetaRuntime meta = config.getCurrentSceneMeta();
            IdentityRegistry identities = identities(world, meta);
            HistoryManager history = new HistoryManager(8);
            LayerService service = new LayerService(world, null, history.historyIds(), identities);
            CreateTiledLayerCommand command = new CreateTiledLayerCommand(
                    service, "Tiled", 8, 6, null);

            history.execute(command);
            world.process();
            int host = service.getLayerEntity(0);
            int map = service.requireTiledMapForHost(host);

            assertNotEquals(host, map);
            assertEquals(LayerComponent.TYPE_TILED,
                    world.getMapper(LayerComponent.class).get(host).type);
            assertTrue(world.getMapper(LayerMetaComponent.class).has(host));
            assertTrue(world.getMapper(VisibilityComponent.class).has(host));
            assertFalse(world.getMapper(TiledLayerComponent.class).has(host));
            assertFalse(world.getMapper(EntityIndexComponent.class).has(host));

            assertFalse(world.getMapper(LayerComponent.class).has(map));
            assertFalse(world.getMapper(LayerMetaComponent.class).has(map));
            assertTrue(world.getMapper(TiledLayerComponent.class).has(map));
            assertTrue(world.getMapper(TransformComponent.class).has(map));
            assertTrue(world.getMapper(VisibilityComponent.class).has(map));
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(map);
            assertEquals(0, index.layerIndex);
            assertEquals(0, index.zIndex);

            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(map);
            tiled.tileXs.add(2);
            tiled.tileYs.add(3);
            tiled.tileAssetIds.add(77);
            tiled.tileTransformFlags.add((byte) 0);
            tiled.data.setTile(2, 3, 77);
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(map);
            SpatialBlockData block = new SpatialBlockData();
            block.id = 9;
            blocks.blocks.add(block);
            int hostStableId = world.getMapper(PixscapeIdentityComponent.class).get(host).stableId;
            int mapStableId = world.getMapper(PixscapeIdentityComponent.class).get(map).stableId;
            long mapHistoryId = history.historyIds().historyIdOfEntity(map);

            history.undo();
            world.process();
            assertEquals(0, service.count());

            history.redo();
            world.process();
            int restoredHost = service.getLayerEntity(0);
            int restoredMap = service.requireTiledMapForHost(restoredHost);
            assertEquals(hostStableId,
                    world.getMapper(PixscapeIdentityComponent.class).get(restoredHost).stableId);
            assertEquals(mapStableId,
                    world.getMapper(PixscapeIdentityComponent.class).get(restoredMap).stableId);
            assertEquals(mapHistoryId, history.historyIds().historyIdOfEntity(restoredMap));
            TiledLayerComponent restored = world.getMapper(TiledLayerComponent.class).get(restoredMap);
            assertEquals(77, restored.data.getTile(2, 3));
            assertEquals(1, world.getMapper(SpatialBlocksComponent.class)
                    .get(restoredMap).blocks.size);
        } finally {
            world.dispose();
            ProjectConfig.setInstance(previous);
        }
    }

    private static IdentityRegistry identities(World world, SceneMetaRuntime meta) {
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        return identities;
    }
}
