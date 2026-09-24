package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HudScreenAssetAuthoringServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final HudScreenAssetAuthoringService service = new HudScreenAssetAuthoringService();

    @Test
    public void createsLoadableScreenAndRootDocument() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "inventory");

        Assert.assertEquals("hud/inventory", id);
        HudScreenAsset asset = new HudScreenAssetLoader().load(root, id);
        Assert.assertEquals(1, asset.schemaVersion);
        Assert.assertEquals("hud/inventory.json", asset.documentId);
        Assert.assertTrue(root.child("hud/inventory.hudscreen").exists());
        Assert.assertTrue(root.child(asset.documentId).exists());
    }

    @Test
    public void rejectsDuplicateAndInvalidInputWithoutReplacingAsset() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        service.create(root, "pause");
        String original = root.child("hud/pause.hudscreen").readString("UTF-8");

        IllegalArgumentException duplicate = Assert.assertThrows(IllegalArgumentException.class,
                () -> service.create(root, "pause"));
        Assert.assertTrue(duplicate.getMessage().contains("already exists"));
        Assert.assertEquals(original, root.child("hud/pause.hudscreen").readString("UTF-8"));

        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.create(root, ""));
    }

    @Test
    public void appearsInBrowserAndReopensWithItsRootDocument() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        String id = service.create(root, "character");

        var visible = HudScreenAssetBrowser.scan(root);
        Assert.assertEquals(1, visible.size);
        Assert.assertEquals(id, visible.first().path);

        var loaded = new HudDocumentPersistenceService().load(root, "character");
        Assert.assertEquals("hud/character.json", loaded.asset().documentId);
        Assert.assertNotNull(loaded.document());
        Assert.assertEquals("root", loaded.document().root.id);
    }
}
