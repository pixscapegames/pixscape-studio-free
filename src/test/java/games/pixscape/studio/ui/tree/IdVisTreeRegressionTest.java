package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IdVisTreeRegressionTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void sharedRowKeepsSceneIconsRenameIndexesAndMultiSelection() {
        IdVisTree tree = new IdVisTree();
        BaseDrawable icon = new BaseDrawable();
        EntityNode first = new EntityNode("First", icon, 1, true);
        EntityNode second = new EntityNode("Second", null, 2, true);
        EntityNode body = new EntityNode(
                "Body", null, 1, true, EntityNode.NodeKind.BODY);
        EntityNode joint = new EntityNode(
                "Joint", null, 3, true, EntityNode.NodeKind.JOINT);
        EntityNode map = new EntityNode(
                "Map", null, 4, true, EntityNode.NodeKind.TILED_MAP);
        EntityNode spatial = new EntityNode(
                "Spatial", null, 4, true, EntityNode.NodeKind.SPATIAL_BLOCKS);
        first.add(body);
        second.add(joint);
        second.add(map);
        map.add(spatial);
        tree.add(first);
        tree.add(second);
        tree.registerNode(first, 1);
        tree.registerNode(second, 2);
        tree.registerNode(body, 1);
        tree.registerJointNode(joint, 3);
        tree.registerNode(map, 4);
        tree.registerMapNode(map, 4);
        tree.registerNode(spatial, 4);

        first.setLabelName("Renamed");
        assertEquals("Renamed", first.getLabel().getText().toString());
        assertSame(icon, first.getIcon());
        assertSame(first, tree.findNode(1));
        assertSame(body, tree.findBodyNode(1));
        assertSame(joint, tree.findJointNode(3));
        assertSame(map, tree.findMapNode(4));
        assertSame(spatial, tree.findSpatialBlocksNode(4));

        tree.selectIds(new IntArray(new int[]{1, 2}), true);
        assertEquals(2, tree.getSelection().size());
        assertTrue(tree.getSelection().contains(first));
        assertTrue(tree.getSelection().contains(second));
    }
}
