package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AssetsFolderScannerMetadataTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ProjectConfig previousConfig;

    @BeforeClass
    public static void initializeGdxFiles() {
        if (Gdx.app == null) {
            new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }
    }

    @After
    public void restoreProjectConfig() {
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void scannerPopulatesMetadataIdentityWithoutChangingOperationalPaths() throws IOException {
        File projectDir = temporaryFolder.newFolder("asset-node-metadata");
        createFile(projectDir, "orig/images/tux__a1.png");
        Files.createDirectories(projectDir.toPath().resolve("orig/animations/hero__a2"));
        createFile(projectDir, "orig/effects/fire.p");
        createFile(projectDir, "orig/tiles/terrain/grass__a4.png");

        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta image = db.registerIfAbsent(
                AssetType.IMAGE, "images/tux", "orig/images/tux__a1.png", AssetMeta.AssetScope.USER);
        AssetMeta animation = db.registerIfAbsent(
                AssetType.ANIMATION, "animations/hero", "orig/animations/hero__a2", AssetMeta.AssetScope.USER);
        AssetMeta particle = db.registerIfAbsent(
                AssetType.PARTICLE, "effects/fire", "orig/effects/fire.p", AssetMeta.AssetScope.USER);
        AssetMeta tile = db.registerIfAbsent(
                AssetType.TILE, "tiles/terrain/grass", "orig/tiles/terrain/grass__a4.png", AssetMeta.AssetScope.USER);
        db.save(new FileHandle(new File(projectDir, StudioFs.FILE_ASSETS_JSON)));

        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.projectFileName = "asset-node-metadata";
        config.projectDirectoryPath = projectDir.getAbsolutePath();
        ProjectConfig.setInstance(config);

        assertScannedNode(AssetNode.Root.IMAGES, "", image, "tux__a1.png");
        assertScannedNode(AssetNode.Root.ANIMATIONS, "", animation, "hero__a2");
        assertScannedNode(AssetNode.Root.PARTICLES, "", particle, "fire.p");
        assertScannedNode(AssetNode.Root.TILES, "terrain", tile, "terrain/grass__a4.png");
    }

    private static void assertScannedNode(AssetNode.Root root,
                                          String folderPath,
                                          AssetMeta meta,
                                          String expectedOperationalPath) {
        Array<AssetNode> nodes = AssetsFolderScanner.scan(new AssetNode(
                AssetNode.Kind.FOLDER,
                root,
                folderPath,
                root.name(),
                null
        ));

        AssetNode node = nodes.size > 0 ? nodes.first() : null;
        assertNotNull(node);
        assertEquals(meta.id(), node.assetId);
        assertEquals(meta.logicalPath().substring(meta.logicalPath().lastIndexOf('/') + 1), node.name);
        assertEquals(meta.sourceRelPath(), node.assetInfo.sourcePath());
        assertEquals(expectedOperationalPath, node.path);
    }

    private static void createFile(File projectDir, String relativePath) throws IOException {
        File file = new File(projectDir, relativePath.replace('/', File.separatorChar));
        Files.createDirectories(file.toPath().getParent());
        Files.createFile(file.toPath());
    }
}
