package games.pixscape.studio.system;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TiledStandalonePreviewContractTest {

    @Test
    public void tiledGhostPreviewRejectsNonTileAssetMetadataBeforeStandaloneLoad() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/TiledGhostPreviewSystem.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("if (meta.type != AssetType.TILE)"));
        assertTrue(source.indexOf("if (meta.type != AssetType.TILE)")
                < source.indexOf("StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath)"));
    }

    @Test
    public void tiledFallbackRejectsNonTileAssetMetadataBeforeStandaloneLoad() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/TiledFallbackSystem.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("if (meta.type != AssetType.TILE)"));
        assertTrue(source.indexOf("if (meta.type != AssetType.TILE)")
                < source.indexOf("StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath)"));
    }

    @Test
    public void tiledFallbackDoesNotPatchPackedAtlasTiles() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/TiledFallbackSystem.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("AtlasRuntimeService.CachedRegion cachedRegion"));
        assertTrue(source.contains("if (cachedRegion != null)"));
        assertTrue(source.indexOf("if (cachedRegion != null)")
                < source.indexOf("AssetMeta meta = resolveAssetMeta(visualAssetId);"));
        assertTrue(source.contains("RuntimeTilesetProfile profile = resolveTilesetProfile(visualAssetId);"));
        assertTrue(source.contains("reportMissingProfileOnce(visualAssetId, logicalAssetId, tiled.atlasTag);"));
    }
}
