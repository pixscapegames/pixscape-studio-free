package games.pixscape.studio.ui.tree;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void groupingAcceptsOneMemberAndRejectsCrossLayerNameAndBlankCorruption() {
        World world = new World();
        try {
            int valid = member(world, 1, "Castle", 0, 12);
            int crossA = member(world, 2, "Tower", 0, 5);
            int crossB = member(world, 2, "Tower", 1, 6);
            int conflictA = member(world, 3, "A", 0, 8);
            int conflictB = member(world, 3, "B", 0, 9);
            int blank = member(world, 4, " ", 0, 11);
            int invalidId = member(world, -1, "Ignored", 0, 20);
            int[] ids = {valid, crossA, crossB, conflictA, conflictB, blank, invalidId};

            Map<Integer, ItemTreePanel.PrefabGroupCandidate> groups =
                    ItemTreePanel.collectPrefabGroups(world, ids, ids.length);
            ItemTreePanel.PrefabGroupCandidate one = groups.get(1);
            assertTrue(one.valid);
            assertEquals(1, one.members.size);
            assertEquals(12, one.maxZ);
            assertFalse(groups.get(2).valid);
            assertFalse(groups.get(3).valid);
            assertFalse(groups.get(4).valid);
            assertNull(groups.get(-1));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void strictCrossingDeltaMovesAcrossStandaloneInBothDirections() {
        List<ItemTreePanel.TreeDisplayEntry> down = ordered(
                prefabEntry(1, 20), ItemTreePanel.TreeDisplayEntry.entity(90, 15));
        assertEquals(Long.valueOf(-6),
                ItemTreePanel.computePrefabTreeShiftDelta(down, 1, -1));
        List<ItemTreePanel.TreeDisplayEntry> afterDown = ordered(
                prefabEntry(1, 14), ItemTreePanel.TreeDisplayEntry.entity(90, 15));
        assertNull(afterDown.get(0).group);
        assertEquals(1, afterDown.get(1).group.instanceId);

        List<ItemTreePanel.TreeDisplayEntry> up = ordered(
                ItemTreePanel.TreeDisplayEntry.entity(91, 25), prefabEntry(1, 20));
        assertEquals(Long.valueOf(6),
                ItemTreePanel.computePrefabTreeShiftDelta(up, 1, 1));
        List<ItemTreePanel.TreeDisplayEntry> afterUp = ordered(
                ItemTreePanel.TreeDisplayEntry.entity(91, 25), prefabEntry(1, 26));
        assertEquals(1, afterUp.get(0).group.instanceId);
        assertNull(afterUp.get(1).group);
    }

    @Test
    public void prefabSiblingEqualityAndNoSiblingUseDeterministicStrictSemantics() {
        List<ItemTreePanel.TreeDisplayEntry> prefabs = ordered(
                prefabEntry(1, 30), prefabEntry(2, 20));
        assertEquals(Long.valueOf(-11),
                ItemTreePanel.computePrefabTreeShiftDelta(prefabs, 1, -1));
        assertEquals(Long.valueOf(11),
                ItemTreePanel.computePrefabTreeShiftDelta(prefabs, 2, 1));
        assertNull(ItemTreePanel.computePrefabTreeShiftDelta(prefabs, 1, 1));
        assertNull(ItemTreePanel.computePrefabTreeShiftDelta(prefabs, 2, -1));

        List<ItemTreePanel.TreeDisplayEntry> equal = ordered(
                prefabEntry(1, 20), ItemTreePanel.TreeDisplayEntry.entity(92, 20));
        assertEquals(Long.valueOf(-1),
                ItemTreePanel.computePrefabTreeShiftDelta(equal, 1, -1));
        List<ItemTreePanel.TreeDisplayEntry> equalPrefabs = ordered(
                prefabEntry(1, 20), prefabEntry(2, 20));
        assertEquals(Long.valueOf(1),
                ItemTreePanel.computePrefabTreeShiftDelta(equalPrefabs, 2, 1));
    }

    @Test
    public void siblingBoundaryCannotOverflowStrictTarget() {
        List<ItemTreePanel.TreeDisplayEntry> upBoundary = ordered(
                ItemTreePanel.TreeDisplayEntry.entity(93, SortKey64.MAX_Z),
                prefabEntry(1, SortKey64.MAX_Z - 10));
        assertNull(ItemTreePanel.computePrefabTreeShiftDelta(upBoundary, 1, 1));

        List<ItemTreePanel.TreeDisplayEntry> downBoundary = ordered(
                prefabEntry(1, SortKey64.MIN_Z + 10),
                ItemTreePanel.TreeDisplayEntry.entity(94, SortKey64.MIN_Z));
        assertNull(ItemTreePanel.computePrefabTreeShiftDelta(downBoundary, 1, -1));
    }

    private static List<ItemTreePanel.TreeDisplayEntry> ordered(
            ItemTreePanel.TreeDisplayEntry... entries) {
        List<ItemTreePanel.TreeDisplayEntry> ordered =
                new ArrayList<>(Arrays.asList(entries));
        ordered.sort(ItemTreePanel::compareDisplayEntries);
        return ordered;
    }

    private static ItemTreePanel.TreeDisplayEntry prefabEntry(int instanceId, int maxZ) {
        ItemTreePanel.PrefabGroupCandidate group =
                new ItemTreePanel.PrefabGroupCandidate(instanceId);
        group.prefabId = "Prefab " + instanceId;
        group.layerIndex = 0;
        group.maxZ = maxZ;
        group.members.add(instanceId);
        return ItemTreePanel.TreeDisplayEntry.group(group);
    }

    private static int member(
            World world, int instanceId, String prefabId, int layer, int z) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layer;
        index.zIndex = z;
        PrefabInstanceComponent prefab =
                world.getMapper(PrefabInstanceComponent.class).create(entity);
        prefab.instanceId = instanceId;
        prefab.prefabId = prefabId;
        return entity;
    }
}
