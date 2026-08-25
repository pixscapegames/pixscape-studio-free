package games.pixscape.studio.ui.tree;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PrefabInstanceTreeModelTest {
    @BeforeClass
    public static void loadVisUi() { VisUiTestBootstrap.loadSkin(); }

    @AfterClass
    public static void unloadVisUi() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void virtualNodeCopiesMembersAndUsesASeparateInstanceIndex() {
        IdVisTree tree = new IdVisTree();
        EntityNode entity = new EntityNode("Wall", null, 7, true);
        EntityNode prefab = EntityNode.prefabInstance(
                "Castle", VisUI.getSkin().getDrawable("cube"),
                31, new IntArray(new int[]{7}), true);
        prefab.add(entity);
        tree.add(prefab);
        tree.registerNode(entity, 7);
        tree.registerPrefabInstanceNode(prefab);

        assertTrue(prefab.isPrefabInstanceNode());
        assertEquals("Castle", prefab.getPrefabId());
        assertEquals(-1, prefab.getEntityId());
        assertSame(VisUI.getSkin().getDrawable("cube"), prefab.getIcon());
        assertSame(entity, tree.findNode(7));
        assertSame(prefab, tree.findPrefabInstanceNode(31));
        IntArray copy = prefab.getPrefabMemberIds();
        copy.clear();
        assertEquals(1, prefab.getPrefabMemberIds().size);

        tree.clearNodes();
        assertNull(tree.findPrefabInstanceNode(31));
    }

    @Test
    public void sharedLogicalOrderGroupsValidMetadataAndDegradesCorruptionToStandalone() {
        World world = new World();
        try {
            int valid = member(world, 1, "Castle", 0, 12);
            int crossA = member(world, 2, "Tower", 0, 5);
            int crossB = member(world, 2, "Tower", 1, 6);
            int conflictA = member(world, 3, "A", 0, 8);
            int conflictB = member(world, 3, "B", 0, 9);
            int blank = member(world, 4, " ", 0, 11);
            int invalidId = member(world, -1, "Ignored", 0, 20);
            LayerLogicalOrderService.LayerOrder order =
                    new LayerLogicalOrderService(world).derive(0);
            long prefabCount = order.items().stream()
                    .filter(LayerLogicalOrderService.LogicalItem::isPrefab).count();
            assertEquals(1, prefabCount);
            assertTrue(order.items().stream().anyMatch(item ->
                    item.isPrefab() && item.prefabInstanceId() == 1
                            && item.members().size == 1));
            assertEquals(6, order.items().size());
        } finally {
            world.dispose();
        }
    }

    @Test
    public void originalInterleavingCaseFlattensPrefabAsOneLogicalBlock() {
        World world = new World();
        try {
            int a = member(world, 1, "Castle", 0, 14);
            int b = member(world, 1, "Castle", 0, 13);
            int c = member(world, 1, "Castle", 0, 10);
            int d = member(world, 1, "Castle", 0, 9);
            int standalone = standalone(world, 0, 12);

            LayerLogicalOrderService.LayerOrder order =
                    new LayerLogicalOrderService(world).derive(0);
            assertEquals(new IntArray(new int[]{a, b, c, d, standalone}),
                    order.flattenedTopToBottom());
            assertEquals(new IntArray(new int[]{standalone, a, b, c, d}),
                    order.movePrefab(1, 1));
            assertEquals(new IntArray(new int[]{standalone, a, b, c, d}),
                    order.moveEntity(standalone, -1));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void prefabMembersUseDescendingZRatherThanCaptureOrder() {
        World world = new World();
        try {
            int z139 = member(world, 41, "Observed", 0, 139);
            int z141 = member(world, 41, "Observed", 0, 141);
            int z142 = member(world, 41, "Observed", 0, 142);
            int z140 = member(world, 41, "Observed", 0, 140);

            LayerLogicalOrderService.LogicalItem prefab =
                    new LayerLogicalOrderService(world).derive(0).items().get(0);

            assertTrue(prefab.isPrefab());
            assertEquals(new IntArray(new int[]{z142, z141, z140, z139}),
                    prefab.members());
        } finally {
            world.dispose();
        }
    }

    private static int member(
            World world, int instanceId, String prefabId, int layer, int z) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layer;
        index.zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "Entity " + entity;
        PrefabInstanceComponent prefab =
                world.getMapper(PrefabInstanceComponent.class).create(entity);
        prefab.instanceId = instanceId;
        prefab.prefabId = prefabId;
        return entity;
    }

    private static int standalone(World world, int layer, int z) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layer;
        index.zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "Entity " + entity;
        return entity;
    }
}
