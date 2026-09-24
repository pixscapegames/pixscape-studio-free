package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import games.pixscape.studio.ui.main.DockingTestFixture;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DockManagerLifecycleTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void repeatedHideAndRedockKeepsPersistentRightSplitOwnershipAndLayout() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.create()) {
            for (int cycle = 0; cycle < 10; cycle++) {
                f.manager.hide(f.properties);
                assertPersistentRightSplit(f);
                f.manager.dockToDefault(f.properties);
                f.manager.hide(f.layers);
                assertPersistentRightSplit(f);
                f.manager.dockToDefault(f.layers);
                f.resize(1200f + cycle * 23f, 800f + cycle * 17f);

                assertPersistentRightSplit(f);
                assertSame(f.left, f.items.getParent());
                assertSame(f.rightTop, f.properties.getParent());
                assertSame(f.rightBottom, f.layers.getParent());
                assertSame(f.bottom, f.assets.getParent());
                assertTrue(f.editorHost.getWidth() > 0f && f.editorHost.getHeight() > 0f);
                assertTrue(f.menu.getWidth() > 0f && f.menu.getHeight() > 0f);
            }
        }
    }

    private static void assertPersistentRightSplit(DockingTestFixture f) {
        assertSame(f.rightSplit, f.rightTop.getParent());
        assertSame(f.rightSplit, f.rightBottom.getParent());
        assertEquals(2, f.rightSplit.getChildren().size);
        assertSame(f.rightTop, f.rightSplit.getChildren().get(0));
        assertSame(f.rightBottom, f.rightSplit.getChildren().get(1));
        assertEquals(Touchable.childrenOnly, f.rightSplit.getTouchable());
    }
}
