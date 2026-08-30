package games.pixscape.studio.ui.asset;

import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class AssetsPanelSelectionStateTest {
    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void productionSelectionAccessorReturnsSelectedImageAndAnimation() {
        AssetsThumbsView view = new AssetsThumbsView(null);
        AssetNode image = new AssetNode(
                AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES,
                "hero.png", "Hero", null);
        AssetNode animation = new AssetNode(
                AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS,
                "walk", "Walk", null);

        assertNull(AssetsPanel.selectedAssetNode(view));
        view.setSelectedNode(image);
        assertSame(image, AssetsPanel.selectedAssetNode(view));
        view.setSelectedNode(animation);
        assertSame(animation, AssetsPanel.selectedAssetNode(view));
        view.clearSelection();
        assertNull(AssetsPanel.selectedAssetNode(view));
    }
}
