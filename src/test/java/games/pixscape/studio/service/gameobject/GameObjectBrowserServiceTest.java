package games.pixscape.studio.service.gameobject;

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

public class GameObjectBrowserServiceTest {

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
    public void scanFindsGameObjectsAndPreviewPath() {
        ProjectConfig cfg = testCfg("gameObject-scan");
        FileHandle dir = StudioFs.requireGameObjectsDir(cfg);
        dir.child("car.gameobject").writeString("{}", false);
        dir.child("legacy.pixprefab").writeString("{}", false);

        GameObjectBrowserService service = new GameObjectBrowserService();
        Array<GameObjectAssetItem> items = service.scan(cfg);

        Assert.assertEquals(1, items.size);
        Assert.assertEquals("car", items.first().name());
        Assert.assertEquals("gameobjects/car.gameobject", items.first().logicalAssetId());
        Assert.assertTrue(items.first().previewFile().path().endsWith("car.preview.png"));
    }

    @Test
    public void deleteRemovesGameObjectAndPreview() {
        ProjectConfig cfg = testCfg("gameObject-delete");
        FileHandle gameObject = StudioFs.requireGameObjectFile(cfg, "truck");
        FileHandle preview = StudioFs.requireGameObjectPreviewFile(cfg, "truck");
        gameObject.writeString("{}", false);
        preview.writeString("x", false);

        GameObjectBrowserService service = new GameObjectBrowserService();
        service.deleteGameObject(new GameObjectAssetItem("truck", gameObject, preview));

        Assert.assertFalse(gameObject.exists());
        Assert.assertFalse(preview.exists());
    }

    @Test
    public void placeholderPreviewIsWritten() {
        ProjectConfig cfg = testCfg("gameObject-preview");
        FileHandle preview = StudioFs.requireGameObjectPreviewFile(cfg, "plane");

        GameObjectPreviewWriter.writePlaceholder(preview);

        Assert.assertTrue(preview.exists());
        Assert.assertTrue(preview.length() > 0);
    }

    private static ProjectConfig testCfg(String name) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectFileName = "test-" + name;
        return cfg;
    }
}
