package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.widget.VisTree;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class FolderTreeBuilderTest {

    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void logicalRootCanRemainVisibleBeforeItsImportDirectoryExists() throws Exception {
        VisTree tree = new VisTree();
        FileHandle missing = new FileHandle(Files.createTempDirectory("empty-font-root")
                .resolve("orig/fonts").toFile());

        VisTree.Node root = FolderTreeBuilder.buildFoldersIncludingEmptyRoot(
                tree, missing, "Fonts", AssetNode.Root.FONTS, new AssetMetaDatabase());

        assertNotNull(root);
        assertEquals(1, tree.getRootNodes().size);
        AssetNode data = (AssetNode) root.getActor().getUserObject();
        assertEquals("Fonts", data.name);
        assertEquals(AssetNode.Root.FONTS, data.root);
        assertEquals(0, root.getChildren().size);
        assertFalse(missing.exists());
    }

    @Test public void skinRootRemainsVisibleBeforeItsImportDirectoryExists() throws Exception {
        VisTree tree = new VisTree();
        FileHandle missing = new FileHandle(Files.createTempDirectory("empty-skin-root")
                .resolve("orig/skins").toFile());

        VisTree.Node root = FolderTreeBuilder.buildFoldersIncludingEmptyRoot(
                tree, missing, "Skins", AssetNode.Root.SKINS, new AssetMetaDatabase());

        assertNotNull(root);
        AssetNode data = (AssetNode) root.getActor().getUserObject();
        assertEquals("Skins", data.name);
        assertEquals(AssetNode.Root.SKINS, data.root);
        assertEquals(0, root.getChildren().size);
        assertFalse(missing.exists());
    }

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

    @Test public void fontStorageDirectoryUsesDescriptorNameWithoutExtension() {
        AssetMetaDatabase assets = new AssetMetaDatabase();
        assets.registerIfAbsent(AssetType.FONT, "fonts/font-export",
                "orig/fonts/1700/font-export.fnt", AssetMeta.AssetScope.USER);

        AssetNode folder = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.FONTS, "1700", "1700", assets);

        assertEquals("font-export", folder.name);
        assertEquals("1700", folder.path);
        assertEquals(AssetNode.Kind.FOLDER, folder.kind);
    }

    @Test public void skinStorageDirectoryUsesDescriptorNameWithoutExtension() {
        AssetMetaDatabase assets = new AssetMetaDatabase();
        assets.registerIfAbsent(AssetType.SKIN, "skins/game",
                "orig/skins/1800/game.json", AssetMeta.AssetScope.USER);

        AssetNode folder = FolderTreeBuilder.createNavigationFolderNode(
                AssetNode.Root.SKINS, "1800", "1800", assets);

        assertEquals("game", folder.name);
        assertEquals("1800", folder.path);
        assertEquals(AssetNode.Kind.FOLDER, folder.kind);
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
