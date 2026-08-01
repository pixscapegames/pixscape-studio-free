package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import games.pixscape.studio.ui.modal.StudioModalChrome;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

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
    public void usesSharedModalChromeIdempotently() {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        Skin skin = VisUI.getSkin();
        LabelStyle titleStyle = skin.get("modal-title", LabelStyle.class);

        Assert.assertSame(titleStyle, dialog.getTitleLabel().getStyle());
        Assert.assertSame(skin.getFont("regular-font"), titleStyle.font);
        Assert.assertEquals(skin.getColor("black"), titleStyle.fontColor);
        Assert.assertEquals(Align.center, dialog.getTitleLabel().getLabelAlign());
        Assert.assertSame(skin.getDrawable("modal-titlebar-light"), dialog.getTitleTable().getBackground());

        StudioModalChrome.apply(dialog);
        VisImageButtonStyle closeStyle = skin.get("modal-close", VisImageButtonStyle.class);
        int matchingCloseButtons = 0;
        for (Actor child : dialog.getTitleTable().getChildren()) {
            if (child instanceof VisImageButton
                    && ((VisImageButton) child).getStyle() == closeStyle) {
                matchingCloseButtons++;
            }
        }
        Assert.assertEquals(1, matchingCloseButtons);
    }

    @Test
    public void staticOkDialogsReceiveSharedModalChrome() {
        Batch batch = (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        Stage stage = new Stage(new ScreenViewport(), batch);

        VisDialog dialog = Dialogs.showOKDialog(stage, "Confirmation", "Done");

        Assert.assertTrue(dialog.isModal());
        Assert.assertSame(VisUI.getSkin().get("modal-title", LabelStyle.class), dialog.getTitleLabel().getStyle());
        Assert.assertSame(VisUI.getSkin().getDrawable("modal-titlebar-light"),
                dialog.getTitleTable().getBackground());
        stage.dispose();
    }

    @Test
    public void keepsFormControlsFixedWidthWhenDialogWidens() throws ReflectiveOperationException {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        VisSelectBox<?> typeBox = field(dialog, "typeBox", VisSelectBox.class);
        VisSelectBox<?> shaderBox = field(dialog, "shaderBox", VisSelectBox.class);
        VisTextField nameField = field(dialog, "nameField", VisTextField.class);

        dialog.validate();
        Assert.assertEquals(420f, typeBox.getWidth(), 0.01f);
        Assert.assertEquals(typeBox.getWidth(), shaderBox.getWidth(), 0.01f);
        Assert.assertEquals(typeBox.getWidth(), nameField.getWidth(), 0.01f);

        dialog.setWidth(1100f);
        dialog.validate();
        Assert.assertEquals(420f, typeBox.getWidth(), 0.01f);
        Assert.assertEquals(420f, shaderBox.getWidth(), 0.01f);
        Assert.assertEquals(420f, nameField.getWidth(), 0.01f);
    }

    @Test
    public void framesOnlyTabContentAndUsesDedicatedActiveTabStyle() throws ReflectiveOperationException {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        TabbedPane tabs = field(dialog, "targetTabs", TabbedPane.class);
        Table targetArea = (Table) tabs.getTabsPane().getParent();
        Actor contentFrame = null;
        for (Actor child : targetArea.getChildren()) {
            if (child instanceof Table
                    && ((Table) child).getBackground() == VisUI.getSkin().getDrawable("tabbed-pane-frame")) {
                contentFrame = child;
            }
        }

        Assert.assertNotNull(contentFrame);
        Assert.assertNotSame(tabs.getTabsPane(), contentFrame);
        TabbedPaneStyle tabStyle = VisUI.getSkin().get("shader-tabs", TabbedPaneStyle.class);
        Assert.assertSame(VisUI.getSkin().getDrawable("window-border-bg"), tabStyle.buttonStyle.checked);
        Assert.assertNotSame(tabStyle.buttonStyle.checked, tabStyle.buttonStyle.up);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Long.TYPE) return 0L;
        return null;
    }
}
