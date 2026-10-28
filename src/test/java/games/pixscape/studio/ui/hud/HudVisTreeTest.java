package games.pixscape.studio.ui.hud;

import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HudVisTreeTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void clearNodesClearsTreeAndStringIdIndex() {
        HudVisTree tree = new HudVisTree();
        HudTreeNode old = new HudTreeNode("same-id", HudNodeKind.GROUP);
        tree.registerNode(old);
        tree.add(old);
        assertSame(old, tree.findNode("same-id"));

        tree.clearNodes();
        assertNull(tree.findNode("same-id"));
        assertNull(old.getTree());

        HudTreeNode replacement = new HudTreeNode("same-id", HudNodeKind.STACK);
        tree.registerNode(replacement);
        tree.add(replacement);
        assertSame(replacement, tree.findNode("same-id"));
    }
}
