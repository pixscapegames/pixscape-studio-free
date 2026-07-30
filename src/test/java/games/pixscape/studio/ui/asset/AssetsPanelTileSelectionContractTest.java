package games.pixscape.studio.ui.asset;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class AssetsPanelTileSelectionContractTest {

    @Test
    public void assetSelectionOnlyUpdatesTiledPaintForTileAssets() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/asset/AssetsPanel.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("if (node.root != AssetNode.Root.TILES || node.kind != AssetNode.Kind.IMAGE)"));
        assertTrue(source.contains("tiledPaintService.setActiveTileAssetId(asset.id());"));
    }
}
