package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImageButton;
import com.kotcrab.vis.ui.widget.VisImageButton.VisImageButtonStyle;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import games.pixscape.studio.ui.modal.StudioModalChrome;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.widget.ScrollableCodeEditor;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ShaderManagerDialogSkinTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        Assert.assertSame(skin.getFont("default-font"), titleStyle.font);
        Assert.assertEquals(skin.getColor("black"), titleStyle.fontColor);
        Assert.assertEquals(Align.center, dialog.getTitleLabel().getLabelAlign());
        Assert.assertNull(dialog.getTitleTable().getBackground());

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
    public void drawsModalTitleBarsAcrossTheWindowWidthAfterResize() {
        ShaderManagerDialog window = new ShaderManagerDialog(null);
        window.validate();
        RecordingDrawable drawable = new RecordingDrawable();
        Skin skin = VisUI.getSkin();
        skin.add("modal-titlebar-light", drawable, com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        Batch batch = batchProxy();

        StudioModalChrome.drawTitleBarBackground(window, batch, 1f, 12f, 24f);
        Assert.assertEquals(12f, drawable.x, 0.01f);
        Assert.assertEquals(window.getWidth(), drawable.width, 0.01f);
        Assert.assertEquals(window.getPadTop(), drawable.height, 0.01f);

        window.setWidth(1080f);
        window.validate();
        StudioModalChrome.drawTitleBarBackground(window, batch, 1f, 0f, 0f);
        Assert.assertEquals(1080f, drawable.width, 0.01f);

        StudioDialog dialog = new StudioDialog("Confirmation");
        dialog.setSize(420f, 240f);
        dialog.validate();
        StudioModalChrome.drawTitleBarBackground(dialog, batch, 1f, 0f, 0f);
        Assert.assertEquals(420f, drawable.width, 0.01f);
    }

    @Test
    public void staticOkDialogsReceiveSharedModalChrome() {
        Batch batch = batchProxy();
        Stage stage = new Stage(new ScreenViewport(), batch);

        VisDialog dialog = Dialogs.showOKDialog(stage, "Confirmation", "Done");

        Assert.assertTrue(dialog.isModal());
        Assert.assertSame(VisUI.getSkin().get("modal-title", LabelStyle.class), dialog.getTitleLabel().getStyle());
        Assert.assertNull(dialog.getTitleTable().getBackground());
        stage.dispose();
    }

    @Test
    public void keepsFormControlsFixedWidthWhenDialogWidens() throws ReflectiveOperationException {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        VisSelectBox<?> typeBox = field(dialog, "typeBox", VisSelectBox.class);
        VisSelectBox<?> shaderBox = field(dialog, "shaderBox", VisSelectBox.class);

        dialog.validate();
        Assert.assertEquals(120f, typeBox.getWidth(), 0.01f);
        Assert.assertEquals(typeBox.getWidth(), shaderBox.getWidth(), 0.01f);

        dialog.setWidth(1100f);
        dialog.validate();
        Assert.assertEquals(120f, typeBox.getWidth(), 0.01f);
        Assert.assertEquals(120f, shaderBox.getWidth(), 0.01f);
        Assert.assertEquals(1, countLabels(dialog, "Shader type:"));
        Assert.assertEquals(1, countLabels(dialog, "Shader name:"));
        Assert.assertEquals(0, countLabels(dialog, "Project shader:"));
        Assert.assertEquals(0, countLabels(dialog, "Name:"));
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

    @Test
    public void usesGlobalScrollAndFourIndependentlyScrollableCodeEditors()
            throws ReflectiveOperationException {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        VisScrollPane mainScrollPane = field(dialog, "mainScrollPane", VisScrollPane.class);
        VisTextButton testButton = field(dialog, "testButton", VisTextButton.class);
        VisTextButton saveButton = field(dialog, "saveButton", VisTextButton.class);

        Assert.assertEquals(3, countActors(dialog, VisScrollPane.class));
        Assert.assertFalse(testButton.isDescendantOf(mainScrollPane));
        Assert.assertFalse(saveButton.isDescendantOf(mainScrollPane));
        dialog.validate();
        mainScrollPane.validate();
        Assert.assertTrue(mainScrollPane.isScrollingDisabledX());
        Assert.assertFalse(mainScrollPane.isScrollingDisabledY());
        Assert.assertFalse(mainScrollPane.isForceScrollX());
        Assert.assertFalse(mainScrollPane.isForceScrollY());
        Assert.assertFalse(mainScrollPane.getFadeScrollBars());
        Assert.assertFalse(mainScrollPane.isScrollY());

        dialog.setHeight(500f);
        dialog.validate();
        mainScrollPane.validate();
        Assert.assertTrue(mainScrollPane.isScrollY());
        assertCodeEditors(field(dialog, "vertEditors", ObjectMap.class));
        assertCodeEditors(field(dialog, "fragEditors", ObjectMap.class));
        Assert.assertEquals(1, countLabels(dialog,
                "Material shaders can use #include \"pixscape_common.glsl\"."));
    }

    @Test
    public void createsMaterialAndFxAssetsAndSelectsTheRequestedShader() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        File projectDir = temporaryFolder.newFolder("shader-project");
        ProjectConfig config = new ProjectConfig();
        config.projectFileName = "shader-project";
        config.projectDirectoryPath = projectDir.getAbsolutePath();
        ProjectConfig.setInstance(config);

        try {
            ShaderManagerDialog dialog = new ShaderManagerDialog(null);
            VisSelectBox<?> typeBox = field(dialog, "typeBox", VisSelectBox.class);
            Object material = typeBox.getItems().get(0);
            Object fx = typeBox.getItems().get(1);
            Method create = privateMethod("createNewShaderAsset", String.class, material.getClass());
            Method select = privateMethod("selectShader", material.getClass(), String.class);

            create.invoke(dialog, "soft_light", material);
            assertShaderAsset(projectDir, "material", "soft_light", "material", "pixscapeApplyMaterial");

            create.invoke(dialog, "screen_pulse", fx);
            assertShaderAsset(projectDir, "fx", "screen_pulse", "fx", "u_intensity");

            try {
                create.invoke(dialog, "screen_pulse", fx);
                Assert.fail("A duplicate shader must be rejected.");
            } catch (InvocationTargetException expected) {
                Assert.assertTrue(expected.getCause() instanceof IllegalStateException);
            }

            File rollbackDir = new File(projectDir, "orig/shaders/custom/fx/rollback_me");
            Assert.assertTrue(new File(rollbackDir, "desktop-gl30.vert").mkdirs());
            Method write = privateMethod(
                    "writeNewShaderAsset",
                    com.badlogic.gdx.files.FileHandle.class,
                    String.class,
                    material.getClass()
            );
            try {
                write.invoke(dialog, new com.badlogic.gdx.files.FileHandle(rollbackDir), "rollback_me", fx);
                Assert.fail("A failed asset write must throw.");
            } catch (InvocationTargetException expected) {
                Assert.assertTrue(expected.getCause() instanceof RuntimeException);
            }
            Assert.assertFalse(rollbackDir.exists());

            select.invoke(dialog, fx, "screen_pulse");
            Assert.assertSame(fx, typeBox.getSelected());
            Assert.assertEquals("screen_pulse", field(dialog, "shaderBox", VisSelectBox.class).getSelected());
            ObjectMap<?, ?> vertEditors = field(dialog, "vertEditors", ObjectMap.class);
            for (Object value : vertEditors.values()) {
                Assert.assertTrue(((ScrollableCodeEditor) value).getText().contains("u_projTrans"));
            }
        } finally {
            ProjectConfig.setInstance(previous);
        }
    }

    @Test
    public void emptyProjectSelectionClearsEditorsAndDisablesDependentActions() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig empty = new ProjectConfig();
        ProjectConfig.setInstance(empty);
        try {
            ShaderManagerDialog dialog = new ShaderManagerDialog(null);
            Assert.assertFalse(field(dialog, "newButton", VisTextButton.class).isDisabled());
            Assert.assertTrue(field(dialog, "saveButton", VisTextButton.class).isDisabled());
            Assert.assertTrue(field(dialog, "testButton", VisTextButton.class).isDisabled());
            Assert.assertTrue(field(dialog, "duplicateButton", VisTextButton.class).isDisabled());
            Assert.assertTrue(field(dialog, "renameButton", VisTextButton.class).isDisabled());
            Assert.assertTrue(field(dialog, "deleteButton", VisTextButton.class).isDisabled());
            Assert.assertEquals("", invokeString(dialog, "sanitizeName", " _ / \\ "));
            Assert.assertEquals("my_shader", invokeString(dialog, "sanitizeName", " My / Shader "));
        } finally {
            ProjectConfig.setInstance(previous);
        }
    }

    private static void assertShaderAsset(
            File projectDir, String category, String name, String jsonKind, String sourceMarker) {
        File shaderDir = new File(projectDir, "orig/shaders/custom/" + category + "/" + name);
        Assert.assertTrue(new File(shaderDir, "desktop-gl30.vert").isFile());
        Assert.assertTrue(new File(shaderDir, "desktop-gl30.frag").isFile());
        Assert.assertTrue(new File(shaderDir, "es3-webgl2.vert").isFile());
        Assert.assertTrue(new File(shaderDir, "es3-webgl2.frag").isFile());
        Assert.assertTrue(new File(shaderDir, "includes").isDirectory());
        String json = new com.badlogic.gdx.files.FileHandle(new File(shaderDir, "shader.json")).readString("UTF-8");
        Assert.assertTrue(json.contains("\"name\": \"" + name + "\""));
        Assert.assertTrue(json.contains("\"kind\": \"" + jsonKind + "\""));
        String fragment = new com.badlogic.gdx.files.FileHandle(new File(shaderDir, "desktop-gl30.frag"))
                .readString("UTF-8");
        Assert.assertTrue(fragment.contains(sourceMarker));
    }

    private static Method privateMethod(String name, Class<?>... types) throws NoSuchMethodException {
        Method method = ShaderManagerDialog.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static String invokeString(Object owner, String methodName, String value) throws Exception {
        Method method = privateMethod(methodName, String.class);
        return (String) method.invoke(owner, value);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static void assertCodeEditors(ObjectMap<?, ?> editors) {
        for (Object value : editors.values()) {
            ScrollableCodeEditor editor = (ScrollableCodeEditor) value;
            VisScrollPane pane = editor.getScrollPane();
            VisTextArea area = editor.getTextArea();
            Assert.assertSame(pane, area.getParent());
            Assert.assertFalse(pane.isScrollingDisabledX());
            Assert.assertFalse(pane.isScrollingDisabledY());
            Assert.assertFalse(pane.isForceScrollX());
            Assert.assertFalse(pane.isForceScrollY());
            Assert.assertFalse(pane.getFadeScrollBars());
        }
    }

    private static Batch batchProxy() {
        return (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static final class RecordingDrawable extends BaseDrawable {
        float x;
        float width;
        float height;

        @Override
        public void draw(Batch batch, float x, float y, float width, float height) {
            this.x = x;
            this.width = width;
            this.height = height;
        }
    }

    private static int countActors(Actor actor, Class<?> type) {
        int count = type.isInstance(actor) ? 1 : 0;
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                count += countActors(child, type);
            }
        }
        return count;
    }

    private static int countLabels(Actor actor, String text) {
        int count = actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label
                && text.contentEquals(((com.badlogic.gdx.scenes.scene2d.ui.Label) actor).getText()) ? 1 : 0;
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                count += countLabels(child, text);
            }
        }
        return count;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Long.TYPE) return 0L;
        return null;
    }
}
