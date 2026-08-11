package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import org.junit.Test;

import static org.junit.Assert.*;

public class LayerServiceLightParallaxTest {

    @Test
    public void parallax_returnsParallaxComponentForLightLayer() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new SceneMetaRuntime());
        LayerService service = new LayerService(world, null, new HistoryIdRegistry(), identities);

        int layerEntity = world.create();

        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        layer.type = LayerComponent.TYPE_LIGHT;

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

    private static IdentityRegistry identities(World world, SceneMetaRuntime meta) {
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        return identities;
    }
}
