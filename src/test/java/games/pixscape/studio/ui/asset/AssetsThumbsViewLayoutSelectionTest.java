package games.pixscape.studio.ui.asset;

import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssetsThumbsViewLayoutSelectionTest {

    @Test
    public void shouldPreserveTilesetLayout_whenTilesetHasSourceImage() {
        TilesetAssetMeta meta = new TilesetAssetMeta(
                1,
                "tiles/terrain",
                "orig/tiles/terrain/terrain__a1.png",
                AssetMeta.AssetScope.USER
        );
        meta.columns = 12;

        assertTrue(AssetsThumbsView.shouldPreserveTilesetLayout(meta));
    }

    @Test
    public void shouldUseResponsiveLayout_whenTilesetWasImportedFromDirectory() {
        TilesetAssetMeta meta = new TilesetAssetMeta(
                1,
                "tiles/terrain",
                null,
                AssetMeta.AssetScope.USER
        );
        meta.columns = 12;

        assertFalse(AssetsThumbsView.shouldPreserveTilesetLayout(meta));
    }
}
