package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConvertSelectionToGameObjectCommandTest {
    @Test
    public void wrapsExistingEntitiesWithoutChangingTheirStableOrHistoryIdentities() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        HistoryManager history = new HistoryManager(8);
        try {
            int before = entity(world, 10, 3, 8, -5f, 0f);
            int first = entity(world, 11, 3, 7, 10f, 20f);
            int second = entity(world, 12, 3, 6, 30f, 40f);
            int after = entity(world, 13, 3, 5, 70f, 0f);
            world.process();
            identities.rebuild();

            SelectionService selection = new SelectionService(world, null);
            selection.replaceSelection(new IntArray(new int[]{first, second}),
                    SelectionService.SelectionSource.VIEWPORT);
            long firstHistory = history.historyIds().ensureForEntity(first);
            long secondHistory = history.historyIds().ensureForEntity(second);
            LayerLogicalOrderService.LayerOrder order = new LayerLogicalOrderService(world).derive(3);
            ConvertSelectionToGameObjectCommand command = new ConvertSelectionToGameObjectCommand(
                    world, history.historyIds(), identities, selection,
                    new IntArray(new int[]{first, second}), order,
                    10f, 20f, 10f, 10f, "gameobjects/group.gameobject");

            history.execute(command);
            int root = selection.getFirstSelectedEntityId();
            assertTrue(world.getMapper(GameObjectComponent.class).has(root));
            assertEquals("gameobjects/group.gameobject",
                    world.getMapper(GameObjectComponent.class).get(root).sourceAssetId);
            assertEquals(11, world.getMapper(PixscapeIdentityComponent.class).get(first).stableId);
            assertEquals(firstHistory, history.historyIds().historyIdOfEntity(first));
            assertEquals(secondHistory, history.historyIds().historyIdOfEntity(second));
            assertEquals(world.getMapper(PixscapeIdentityComponent.class).get(root).stableId,
                    world.getMapper(GameObjectMemberComponent.class).get(first).parentStableId);
            assertEquals(0f, world.getMapper(TransformComponent.class).get(first).x, 0.0001f);
            assertEquals(20f, world.getMapper(TransformComponent.class).get(second).x, 0.0001f);
            assertEquals(2, world.getMapper(EntityIndexComponent.class).get(before).zIndex);
            assertEquals(1, world.getMapper(EntityIndexComponent.class).get(root).zIndex);
            assertEquals(0, world.getMapper(EntityIndexComponent.class).get(after).zIndex);

            long rootHistory = history.historyIds().historyIdOfEntity(root);
            int rootStable = world.getMapper(PixscapeIdentityComponent.class).get(root).stableId;
            history.undo();
            world.process();
            assertFalse(world.getEntityManager().isActive(root));
            assertFalse(world.getMapper(GameObjectMemberComponent.class).has(first));
            assertEquals(10f, world.getMapper(TransformComponent.class).get(first).x, 0.0001f);
            assertEquals(7, world.getMapper(EntityIndexComponent.class).get(first).zIndex);
            assertEquals(2, selection.getSelectionSnapshot().size);
            assertTrue(selection.getSelectionSet().contains(first));
            assertTrue(selection.getSelectionSet().contains(second));
            assertEquals(first, selection.getFirstSelectedEntityId());

            history.redo();
            world.process();
            int redoneRoot = history.historyIds().entityOfHistoryId(rootHistory);
            assertEquals(rootStable, world.getMapper(PixscapeIdentityComponent.class)
                    .get(redoneRoot).stableId);
            assertEquals(new IntArray(new int[]{redoneRoot}), selection.getSelectionSnapshot());
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void convertsPhysicsWithoutRecreatingTheBodyOrChangingShapeIdentity() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        HistoryManager history = new HistoryManager(8);
        try {
            int body = entity(world, 10, 3, 0, 4f, 8f);
            world.getMapper(TransformComponent.class).get(body).scaleX = -1f;
            world.getMapper(TransformComponent.class).get(body).scaleY = 2f;
            world.getMapper(TransformComponent.class).get(body).refreshCaches();
            world.getMapper(PhysicsBodyComponent.class).create(body);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = 77;
            shape.geometry = new PhysicsGeometryData();
            world.getMapper(PhysicsShapesComponent.class).create(body).shapes.add(shape);
            world.process();
            identities.rebuild();

            SelectionService selection = new SelectionService(world, null);
            selection.selectOnly(body);
            LayerLogicalOrderService.LayerOrder order = new LayerLogicalOrderService(world).derive(3);

            ConvertSelectionToGameObjectCommand command = new ConvertSelectionToGameObjectCommand(
                    world, history.historyIds(), identities, selection,
                    new IntArray(new int[]{body}), order,
                    4f, 8f, 0f, 0f, "gameobjects/physics.gameobject");
            history.execute(command);
            int root = selection.getFirstSelectedEntityId();
            assertTrue(world.getMapper(GameObjectComponent.class).has(root));
            assertTrue(world.getMapper(PhysicsBodyComponent.class).has(body));
            assertEquals(77, world.getMapper(PhysicsShapesComponent.class)
                    .get(body).shapes.first().physicsShapeId);
            assertEquals(0f, world.getMapper(TransformComponent.class).get(body).x, 0.0001f);
            assertEquals(-1f, world.getMapper(TransformComponent.class).get(body).scaleX, 0f);
            assertEquals(2f, world.getMapper(TransformComponent.class).get(body).scaleY, 0f);
            history.undo();
            world.process();
            assertFalse(world.getMapper(GameObjectMemberComponent.class).has(body));
            assertEquals(4f, world.getMapper(TransformComponent.class).get(body).x, 0.0001f);
            assertEquals(-1f, world.getMapper(TransformComponent.class).get(body).scaleX, 0f);
            assertEquals(77, world.getMapper(PhysicsShapesComponent.class)
                    .get(body).shapes.first().physicsShapeId);
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    private static int entity(World world, int stableId, int layer, int z, float x, float y) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = layer;
        world.getMapper(EntityIndexComponent.class).get(entity).zIndex = z;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = x; transform.y = y; transform.scaleX = transform.scaleY = 1f;
        transform.refreshCaches();
        return entity;
    }
}
