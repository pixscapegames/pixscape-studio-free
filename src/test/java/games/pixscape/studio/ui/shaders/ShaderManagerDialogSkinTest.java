package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ShaderManagerDialogSkinTest {

    @BeforeClass
    public static void loadStudioSkin() {
        VisUiTestBootstrap.loadSkin();
        VisUI.dispose();
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
    }

    @AfterClass
    public static void unloadStudioSkin() {
        VisUI.dispose();
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void usesDedicatedTitleBarStyles() {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        Skin skin = VisUI.getSkin();
        LabelStyle titleStyle = skin.get("shader-dialog-title", LabelStyle.class);

        Assert.assertSame(titleStyle, dialog.getTitleLabel().getStyle());
        Assert.assertSame(skin.getFont("regular-font"), titleStyle.font);
        Assert.assertEquals(skin.getColor("black"), titleStyle.fontColor);
        Assert.assertEquals(Align.center, dialog.getTitleLabel().getLabelAlign());
        Assert.assertSame(skin.getDrawable("modal-titlebar-light"), dialog.getTitleTable().getBackground());

        VisImageButton closeButton = (VisImageButton) dialog.getTitleTable().getChildren().peek();
        Assert.assertSame(skin.get("shader-dialog-close", VisImageButtonStyle.class), closeButton.getStyle());
    }
}
