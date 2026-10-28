package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HudScreenAssetBrowserTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void discoversOnlyHudScreenAssetsInStableOrder() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        root.child("hud/zeta.hudscreen").writeString("{}", false);
        root.child("hud/alpha.hudscreen").writeString("{}", false);
        root.child("hud/ignore.json").writeString("{}", false);
        var assets = HudScreenAssetBrowser.scan(root);
        Assert.assertEquals(2, assets.size);
        Assert.assertEquals("hud/alpha", assets.get(0).path);
        Assert.assertEquals("hud/zeta", assets.get(1).path);
    }
}
