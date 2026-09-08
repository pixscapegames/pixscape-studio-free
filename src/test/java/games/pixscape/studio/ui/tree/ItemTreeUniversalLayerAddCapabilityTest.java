package games.pixscape.studio.ui.tree;

import games.pixscape.studio.ui.asset.AssetNode;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemTreeUniversalLayerAddCapabilityTest {

    @Test
    public void spriteAndAnimationActionsRecognizeOrdinarySelectedAssets() {
        AssetNode image = node(AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES);
        AssetNode animation = node(AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS);

        assertTrue(ItemTreePanel.isSpriteAsset(image));
        assertFalse(ItemTreePanel.isAnimationAsset(image));
        assertTrue(ItemTreePanel.isAnimationAsset(animation));
        assertFalse(ItemTreePanel.isSpriteAsset(animation));
    }

    @Test
    public void tiledAssetsCannotMasqueradeAsOrdinaryAddActions() {
        AssetNode tile = node(AssetNode.Kind.IMAGE, AssetNode.Root.TILES);
        AssetNode tiledAnimation = node(AssetNode.Kind.TILED_ANIMATION, AssetNode.Root.TILES);

        assertFalse(ItemTreePanel.isSpriteAsset(tile));
        assertFalse(ItemTreePanel.isAnimationAsset(tiledAnimation));
        assertFalse(ItemTreePanel.isSpriteAsset(null));
        assertFalse(ItemTreePanel.isAnimationAsset(null));
    }

    private static AssetNode node(AssetNode.Kind kind, AssetNode.Root root) {
        return new AssetNode(kind, root, "asset", "Asset", null);
    }
}
