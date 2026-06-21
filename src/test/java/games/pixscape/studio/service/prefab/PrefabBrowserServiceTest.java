package games.pixscape.studio.service.prefab;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.backends.headless.mock.input.MockInput;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PrefabBrowserServiceTest {

    @BeforeClass
    public static void bootGdx() {
        if (Gdx.app == null) {
            HeadlessApplicationConfiguration cfg = new HeadlessApplicationConfiguration();
            new HeadlessApplication(new com.badlogic.gdx.ApplicationAdapter() {}, cfg);
            Gdx.graphics = new MockGraphics();
            Gdx.input = new MockInput();
        }
    }

    @Test
    public void scanFindsPrefabsAndPreviewPath() {
        ProjectConfig cfg = testCfg("prefab-scan");
        FileHandle dir = StudioFs.requirePrefabsDir(cfg);
        dir.child("car.pixprefab").writeString("{}", false);

        PrefabBrowserService service = new PrefabBrowserService();
        Array<PrefabAssetItem> items = service.scan(cfg);

        Assert.assertEquals(1, items.size);
        Assert.assertEquals("car", items.first().name());
        Assert.assertTrue(items.first().previewFile().path().endsWith("car.preview.png"));
    }

    @Test
    public void deleteRemovesPrefabAndPreview() {
        ProjectConfig cfg = testCfg("prefab-delete");
        FileHandle prefab = StudioFs.requirePrefabFile(cfg, "truck");
        FileHandle preview = StudioFs.requirePrefabPreviewFile(cfg, "truck");
        prefab.writeString("{}", false);
        preview.writeString("x", false);

        PrefabBrowserService service = new PrefabBrowserService();
        service.deletePrefab(new PrefabAssetItem("truck", prefab, preview));

        Assert.assertFalse(prefab.exists());
        Assert.assertFalse(preview.exists());
    }

    @Test
    public void placeholderPreviewIsWritten() {
        ProjectConfig cfg = testCfg("prefab-preview");
        FileHandle preview = StudioFs.requirePrefabPreviewFile(cfg, "plane");

        PrefabPreviewWriter.writePlaceholder(preview);

        Assert.assertTrue(preview.exists());
        Assert.assertTrue(preview.length() > 0);
    }

    private static ProjectConfig testCfg(String name) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectFileName = "test-" + name;
        return cfg;
    }
}
