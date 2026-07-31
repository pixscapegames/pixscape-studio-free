package games.pixscape.studio.ui.asset;

import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class FolderTreeBuilderTest {

    @Test
    public void animationDirectoryUsesLogicalDisplayNameButRemainsNavigationNode() {
        AssetMetaDatabase assetSnapshot = new AssetMetaDatabase();
        assetSnapshot.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero__a304",
                AssetMeta.AssetScope.USER
        );

        AssetNode folder = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.ANIMATIONS,
                "hero__a304",
                "hero__a304",
                assetSnapshot
        );

        assertEquals("hero", folder.name);
        assertEquals(AssetNode.Kind.FOLDER, folder.kind);
        assertEquals(-1, folder.assetId);
        assertNull(folder.assetInfo);
    }

    @Test
    public void unmatchedGroupingDirectoryKeepsFilesystemName() {
        AssetNode folder = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.ANIMATIONS,
                "characters",
                "characters",
                new AssetMetaDatabase()
        );

        assertEquals("characters", folder.name);
        assertEquals(-1, folder.assetId);
        assertNull(folder.assetInfo);
    }

    @Test
    public void callerSuppliedSnapshotIsReusedAcrossRecursiveFolderLevels() {
        AssetMetaDatabase assetSnapshot = new AssetMetaDatabase();
        assetSnapshot.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero__a304",
                AssetMeta.AssetScope.USER
        );
        assetSnapshot.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/enemies/slime",
                "orig/animations/enemies/slime__a305",
                AssetMeta.AssetScope.USER
        );

        AssetNode rootLevel = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.ANIMATIONS,
                "hero__a304",
                "hero__a304",
                assetSnapshot
        );
        AssetNode nested = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.ANIMATIONS,
                "enemies/slime__a305",
                "slime__a305",
                assetSnapshot
        );

        assertEquals("hero", rootLevel.name);
        assertEquals("slime", nested.name);
    }

    @Test
    public void viewLoadsOneSnapshotAndRecursiveBuilderNeverLoadsMetadata() throws Exception {
        String viewSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/asset/FolderTreeView.java"),
                StandardCharsets.UTF_8
        );
        String builderSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/asset/FolderTreeBuilder.java"),
                StandardCharsets.UTF_8
        );

        assertEquals(1, occurrenceCount(viewSource, "AssetMetaDatabase.load("));
        assertFalse(builderSource.contains("AssetMetaDatabase.load("));
    }

    private static int occurrenceCount(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
