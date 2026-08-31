package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Guards map-node reorder after the tree switches selection to its tiled editing target. */
public class ReorderLogicalLayerCommandTiledMapTest {
    @Test
    public void movesTiledMapAsATopLevelPeerAndRestoresItOnUndo() {
        World world = new World(new WorldConfiguration());
        try {
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 2;
            world.getMapper(LayerMetaComponent.class).create(layer).locked = false;

            int sprite = orderedEntity(world, 11, 2, 1);
            int map = orderedEntity(world, 12, 2, 0);
            world.getMapper(TiledLayerComponent.class).create(map);
            world.process();

            LayerLogicalOrderService.LayerOrder order = new LayerLogicalOrderService(world).derive(2);
            assertEquals(new IntArray(new int[]{sprite, map}), order.flattenedTopToBottom());

            ReorderLogicalLayerCommand command = new ReorderLogicalLayerCommand(
                    world, new HistoryManager(8).historyIds(), 2, order.moveEntity(map, -1));
            command.redo();
            assertEquals(1, world.getMapper(EntityIndexComponent.class).get(map).zIndex);
            assertEquals(0, world.getMapper(EntityIndexComponent.class).get(sprite).zIndex);

            command.undo();
            assertEquals(0, world.getMapper(EntityIndexComponent.class).get(map).zIndex);
            assertEquals(1, world.getMapper(EntityIndexComponent.class).get(sprite).zIndex);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void mapsMoveAcrossGameObjectRootsAndSwapWithOtherMapsAsAtomicPeers() {
        World world = new World(new WorldConfiguration());
        try {
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 4;
            world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
            int firstMap = map(world, 21, 4, 3);
            int gameObject = orderedEntity(world, 22, 4, 2);
            world.getMapper(GameObjectComponent.class).create(gameObject);
            int secondMap = map(world, 23, 4, 1);
            int sprite = orderedEntity(world, 24, 4, 0);
            HistoryManager history = new HistoryManager(8);
            world.process();

            LayerLogicalOrderService orderService = new LayerLogicalOrderService(world);
            ReorderLogicalLayerCommand moveAboveRoot = new ReorderLogicalLayerCommand(
                    world, history.historyIds(), 4,
                    orderService.derive(4).moveEntity(secondMap, -1));
            moveAboveRoot.redo();
            assertEquals(new IntArray(new int[]{firstMap, secondMap, gameObject, sprite}),
                    orderService.derive(4).flattenedTopToBottom());
            moveAboveRoot.undo();
            assertEquals(new IntArray(new int[]{firstMap, gameObject, secondMap, sprite}),
                    orderService.derive(4).flattenedTopToBottom());
            moveAboveRoot.redo();

            ReorderLogicalLayerCommand swapMaps = new ReorderLogicalLayerCommand(
                    world, history.historyIds(), 4,
                    orderService.derive(4).moveEntity(firstMap, 1));
            swapMaps.redo();
            assertEquals(new IntArray(new int[]{secondMap, firstMap, gameObject, sprite}),
                    orderService.derive(4).flattenedTopToBottom());
        } finally {
            world.dispose();
        }
    }

    private static int orderedEntity(World world, int stableId, int layerIndex, int zIndex) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = zIndex;
        return entity;
    }

    private static int map(World world, int stableId, int layerIndex, int zIndex) {
        int entity = orderedEntity(world, stableId, layerIndex, zIndex);
        world.getMapper(TiledLayerComponent.class).create(entity);
        return entity;
    }
}
