package games.pixscape.studio.ui.asset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AssetsPanelTileSelectionContractTest {

    @Test
    public void assetSelectionUsesMetadataAssetIdOnlyForTileAssets() {
        AssetNode tile = new AssetNode(
                AssetNode.Kind.IMAGE, AssetNode.Root.TILES, "physical.png", "grass", null);
        tile.assetId = 42;
        AssetNode image = new AssetNode(
                AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES, "physical.png", "sprite", null);
        image.assetId = 43;
        AssetNode tiledAnimation = new AssetNode(
                AssetNode.Kind.TILED_ANIMATION, AssetNode.Root.TILES, "animation", "water", null);
        tiledAnimation.tileAnimationId = 99;

        assertEquals(42, AssetsPanel.tileAssetIdForSelection(tile));
        assertEquals(-1, AssetsPanel.tileAssetIdForSelection(image));
        assertEquals(-1, AssetsPanel.tileAssetIdForSelection(tiledAnimation));
    }
}
