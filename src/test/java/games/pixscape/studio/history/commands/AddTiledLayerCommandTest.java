package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledMapHierarchy;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.tiled.TiledObjectAuthoringService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AddTiledLayerCommandTest {
    private World world;
    private IdentityRegistry identities;
    private HistoryManager history;
    private LayerService layers;
    private int map;
    private final AtomicInteger target = new AtomicInteger(-1);

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        var config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        identities = new IdentityRegistry();
        identities.bind(world, config.getCurrentSceneMeta());
        history = new HistoryManager(16);
        layers = new LayerService(world, null, history.historyIds(), identities);
        world.getMapper(LayerComponent.class).create(world.create()).layerIndex = 0;
        map = TiledMapHierarchy.createMap(world, identities, "Map", 0, 0,
                TiledProjection.ISO, 48, 24, 17, 19);
        world.getMapper(TransformComponent.class).get(map).x = 120f;
        world.process();
    }

    @After
    public void tearDown() {
        world.dispose();
        ProjectConfig.setInstance(null);
    }

    @Test
    public void appendsEmptyLayersAfterMixedSiblingsWithoutCreatingAnotherMap() {
        int objects = new TiledObjectAuthoringService(world, history).createObjectLayer(map);
        world.getMapper(EntityIndexComponent.class).get(objects).zIndex = 4;
        world.process();
        history.execute(new AddTiledLayerCommand(layers, map, target::set));
        int first = target.get();
        history.execute(new AddTiledLayerCommand(layers, map, target::set));
        int second = target.get();
        world.process();
        assertEquals(1, world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().size());
        assertEquals(5, world.getMapper(EntityIndexComponent.class).get(first).zIndex);
        assertEquals(6, world.getMapper(EntityIndexComponent.class).get(second).zIndex);
        assertLayer(first);
        assertLayer(second);
        assertEquals(120f, world.getMapper(TransformComponent.class).get(map).x, 0f);
    }

    @Test
    public void undoRedoPreservesIdentityParentAndEditingTargetWithoutDeletingSiblings() {
        history.execute(new AddTiledLayerCommand(layers, map, target::set));
        int sibling = target.get();
        history.execute(new AddTiledLayerCommand(layers, map, target::set));
        int stable = world.getMapper(PixscapeIdentityComponent.class).get(target.get()).stableId;
        long historyId = history.historyIds().historyIdOfEntity(target.get());
        int nextStable = ProjectConfig.getInstance().getCurrentSceneMeta().nextEntityStableId;
        for (int cycle = 0; cycle < 3; cycle++) {
            history.undo();
            assertEquals(-1, target.get());
            assertEquals(-1, identities.findByStableId(stable));
            history.redo();
            assertEquals(stable, world.getMapper(PixscapeIdentityComponent.class).get(target.get()).stableId);
            assertEquals(historyId, history.historyIds().historyIdOfEntity(target.get()));
            assertLayer(target.get());
            assertTrue(world.getEntityManager().isActive(map));
            assertTrue(world.getEntityManager().isActive(sibling));
            assertEquals(nextStable, ProjectConfig.getInstance().getCurrentSceneMeta().nextEntityStableId);
        }
        world.process();
        assertEquals(2, world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities().size());
    }

    @Test
    public void rejectsNonMapAndInvalidGridBeforeIdentityOrHistoryMutation() {
        int nextStable = ProjectConfig.getInstance().getCurrentSceneMeta().nextEntityStableId;
        assertThrows(IllegalArgumentException.class, () -> new AddTiledLayerCommand(layers, -1, target::set));
        world.getMapper(TiledMapComponent.class).get(map).tileWidth = 0;
        assertThrows(IllegalArgumentException.class,
                () -> history.execute(new AddTiledLayerCommand(layers, map, target::set)));
        assertEquals(0, history.getCursor());
        assertEquals(-1, target.get());
        assertEquals(nextStable, ProjectConfig.getInstance().getCurrentSceneMeta().nextEntityStableId);
        world.process();
        assertEquals(0, world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities().size());
    }

    private void assertLayer(int entity) {
        var tiles = world.getMapper(TiledLayerComponent.class).get(entity);
        assertEquals(TiledProjection.ISO, tiles.projection);
        assertEquals(48, tiles.tileWidth);
        assertEquals(24, tiles.tileHeight);
        assertEquals(17, tiles.mapWidthCells);
        assertEquals(19, tiles.mapHeightCells);
        assertEquals("scene1", tiles.atlasTag);
        assertNotNull(tiles.data);
        assertEquals(0, tiles.tileAssetIds.size);
        assertFalse(tiles.spatialEnabled);
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entity));
        assertEquals(TiledMapLayerComponent.Kind.TILE, world.getMapper(TiledMapLayerComponent.class).get(entity).kind);
        assertEquals(world.getMapper(PixscapeIdentityComponent.class).get(map).stableId,
                world.getMapper(GameObjectMemberComponent.class).get(entity).parentStableId);
        assertEquals(0f, world.getMapper(TransformComponent.class).get(entity).x, 0f);
    }
}
