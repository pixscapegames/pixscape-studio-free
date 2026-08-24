package games.pixscape.studio.service.zorder;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.commands.ReorderLogicalLayerCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LayerLogicalOrderJointExclusionTest {
    @Test
    public void jointIsNotALogicalItemOrCompactSlot() {
        World world = worldWithLayer();
        try {
            int spriteA = visual(world, 10, -1);
            int joint = joint(world, 999, -1, spriteA, spriteA);
            int spriteB = visual(world, 4, -1);
            world.process();

            LayerLogicalOrderService order = new LayerLogicalOrderService(world);
            assertEquals(new IntArray(new int[]{spriteA, spriteB}),
                    order.derive(0).flattenedTopToBottom());
            assertNull(order.derive(0).moveEntity(joint, 1));

            ReorderLogicalLayerCommand command = new ReorderLogicalLayerCommand(
                    world,
                    new HistoryIdRegistry(),
                    0,
                    order.derive(0).flattenedTopToBottom());
            command.redo();

            assertEquals(1, world.getMapper(EntityIndexComponent.class).get(spriteA).zIndex);
            assertEquals(0, world.getMapper(EntityIndexComponent.class).get(spriteB).zIndex);
            assertEquals(999, world.getMapper(EntityIndexComponent.class).get(joint).zIndex);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void jointZ999DoesNotAffectPrefabEffectiveZOrInternalMembers() {
        World world = worldWithLayer();
        try {
            int prefabA = visual(world, 5, 71);
            int prefabB = visual(world, 4, 71);
            int joint = joint(world, 999, 71, prefabA, prefabB);
            int standalone = visual(world, 6, -1);
            world.process();

            LayerLogicalOrderService.LayerOrder order =
                    new LayerLogicalOrderService(world).derive(0);
            assertEquals(2, order.items().size());
            assertFalse(order.items().get(0).isPrefab());
            assertEquals(standalone, order.items().get(0).entityId());
            assertTrue(order.items().get(1).isPrefab());
            assertEquals(new IntArray(new int[]{prefabA, prefabB}),
                    order.items().get(1).members());
            assertFalse(order.items().get(1).members().contains(joint));
        } finally {
            world.dispose();
        }
    }

    private static World worldWithLayer() {
        World world = new World(new WorldConfiguration()
                .setSystem(new DirtyTrackerSystem(32)));
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
        LayerMetaComponent meta = world.getMapper(LayerMetaComponent.class).create(layer);
        meta.name = "Layer";
        meta.locked = false;
        return world;
    }

    private static int visual(World world, int z, int prefabInstanceId) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = 0;
        index.zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "E" + entity;
        if (prefabInstanceId > 0) {
            PrefabInstanceComponent prefab =
                    world.getMapper(PrefabInstanceComponent.class).create(entity);
            prefab.instanceId = prefabInstanceId;
            prefab.prefabId = "Prefab";
        }
        return entity;
    }

    private static int joint(
            World world, int z, int prefabInstanceId, int bodyA, int bodyB) {
        int entity = visual(world, z, prefabInstanceId);
        PhysicsJointComponent joint =
                world.getMapper(PhysicsJointComponent.class).create(entity);
        joint.type = PhysicsJointComponent.TYPE_REVOLUTE;
        joint.aEid = bodyA;
        joint.bEid = bodyB;
        return entity;
    }
}
