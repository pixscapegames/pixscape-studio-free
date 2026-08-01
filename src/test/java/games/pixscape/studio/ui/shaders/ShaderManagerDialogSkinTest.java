package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
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
import games.pixscape.studio.ui.modal.StudioModalWindow;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.widget.ScrollableCodeEditor;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.runtime.render.ShaderVariant;
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
        Assert.assertSame(skin.getDrawable("modal-close-icon"), closeStyle.imageUp);
        Assert.assertSame(closeStyle.imageUp, closeStyle.imageDown);
        Assert.assertSame(closeStyle.imageUp, closeStyle.imageOver);
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
    public void modalWindowsInstallTheBackgroundStripeWithoutCustomBackgroundDrawing() {
        Skin skin = VisUI.getSkin();
        StudioModalWindow window = new StudioModalWindow("Shader Manager");
        StudioDialog dialog = new StudioDialog("Confirmation");

        Assert.assertEquals("FullWidthTitleBackground", window.getStyle().background.getClass().getSimpleName());
        Assert.assertEquals("FullWidthTitleBackground", dialog.getStyle().background.getClass().getSimpleName());
        Assert.assertSame(skin.get("modal-title", LabelStyle.class), window.getTitleLabel().getStyle());
        Assert.assertSame(skin.get("modal-title", LabelStyle.class), dialog.getTitleLabel().getStyle());
        Assert.assertSame(window.getTitleTable(), window.getTitleLabel().getParent());
        Assert.assertSame(dialog.getTitleTable(), dialog.getTitleLabel().getParent());
        assertNoDeclaredMethod(StudioModalWindow.class, "drawBackground");
        assertNoDeclaredMethod(StudioDialog.class, "drawBackground");
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
    public void shaderEditorsUseMatchingFlatHeadersWithoutIntermediateSpacing()
            throws ReflectiveOperationException {
        ShaderManagerDialog dialog = new ShaderManagerDialog(null);
        Actor vertexLabel = findLabel(dialog, "Vertex shader");
        Actor fragmentLabel = findLabel(dialog, "Fragment shader");
        Assert.assertNotNull(vertexLabel);
        Assert.assertNotNull(fragmentLabel);

        Table vertexHeader = (Table) vertexLabel.getParent();
        Table fragmentHeader = (Table) fragmentLabel.getParent();
        Assert.assertSame(VisUI.getSkin().getDrawable("shader-section-header"), vertexHeader.getBackground());
        Assert.assertSame(vertexHeader.getBackground(), fragmentHeader.getBackground());
        Assert.assertSame(((com.badlogic.gdx.scenes.scene2d.ui.Label) vertexLabel).getStyle(),
                ((com.badlogic.gdx.scenes.scene2d.ui.Label) fragmentLabel).getStyle());

        Table editorTable = (Table) vertexHeader.getParent();
        int vertexHeaderIndex = editorTable.getChildren().indexOf(vertexHeader, true);
        Actor vertexEditor = editorTable.getChildren().get(vertexHeaderIndex + 1);
        Assert.assertTrue(vertexEditor instanceof ScrollableCodeEditor);
        dialog.validate();
        editorTable.validate();
        Assert.assertEquals(vertexEditor.getY(), fragmentHeader.getY() + fragmentHeader.getHeight(), 0.01f);
        Assert.assertEquals(vertexHeader.getHeight(), fragmentHeader.getHeight(), 0.01f);
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

    @Test
    public void duplicatedAssetUsesUnsavedEditorSourcesAndPreservesIncludes() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        File projectDir = temporaryFolder.newFolder("duplicate-shader-project");
        ProjectConfig config = new ProjectConfig();
        config.projectFileName = "duplicate-shader-project";
        config.projectDirectoryPath = projectDir.getAbsolutePath();
        ProjectConfig.setInstance(config);

        try {
            ShaderManagerDialog dialog = new ShaderManagerDialog(null);
            VisSelectBox<?> typeBox = field(dialog, "typeBox", VisSelectBox.class);
            Object material = typeBox.getItems().first();
            Method create = privateMethod("createNewShaderAsset", String.class, material.getClass());
            Method select = privateMethod("selectShader", material.getClass(), String.class);
            create.invoke(dialog, "source_shader", material);
            select.invoke(dialog, material, "source_shader");

            FileHandle sourceDir = new FileHandle(
                    new File(projectDir, "orig/shaders/custom/material/source_shader"));
            sourceDir.child("desktop-gl30.vert").writeString("old disk vertex", false, "UTF-8");
            sourceDir.child("includes/common/nested.glsl").writeString("nested include", false, "UTF-8");
            sourceDir.child("includes/root.glsl").writeString("root include", false, "UTF-8");

            @SuppressWarnings("unchecked")
            ObjectMap<ShaderVariant, ScrollableCodeEditor> vertices =
                    (ObjectMap<ShaderVariant, ScrollableCodeEditor>) field(dialog, "vertEditors", ObjectMap.class);
            @SuppressWarnings("unchecked")
            ObjectMap<ShaderVariant, ScrollableCodeEditor> fragments =
                    (ObjectMap<ShaderVariant, ScrollableCodeEditor>) field(dialog, "fragEditors", ObjectMap.class);
            for (ShaderVariant variant : ShaderVariant.values()) {
                vertices.get(variant).setText("unsaved vertex " + variant);
                fragments.get(variant).setText("unsaved fragment " + variant);
            }

            Method snapshot = privateMethod("snapshotCurrentEditorSources");
            Object sources = snapshot.invoke(dialog);
            Method validateSources = privateMethod("validateShaderSources", sources.getClass());
            validateSources.invoke(dialog, sources);
            Method writeDuplicate = privateMethod(
                    "writeDuplicatedShaderAsset",
                    FileHandle.class,
                    FileHandle.class,
                    String.class,
                    material.getClass(),
                    sources.getClass()
            );

            FileHandle targetDir = new FileHandle(
                    new File(projectDir, "orig/shaders/custom/material/editor_copy"));
            writeDuplicate.invoke(dialog, sourceDir, targetDir, "editor_copy", material, sources);

            for (ShaderVariant variant : ShaderVariant.values()) {
                String prefix = invokeVariantPrefix(dialog, variant);
                Assert.assertEquals("unsaved vertex " + variant,
                        targetDir.child(prefix + ".vert").readString("UTF-8"));
                Assert.assertEquals("unsaved fragment " + variant,
                        targetDir.child(prefix + ".frag").readString("UTF-8"));
            }
            Assert.assertFalse(targetDir.child("source_shader").exists());
            Assert.assertFalse(targetDir.child("includes/includes").exists());
            Assert.assertEquals("nested include",
                    targetDir.child("includes/common/nested.glsl").readString("UTF-8"));
            Assert.assertEquals("root include", targetDir.child("includes/root.glsl").readString("UTF-8"));
            String json = targetDir.child("shader.json").readString("UTF-8");
            Assert.assertTrue(json.contains("\"name\": \"editor_copy\""));
            Assert.assertTrue(json.contains("\"kind\": \"material\""));

            FileHandle sourceWithoutIncludes = new FileHandle(new File(projectDir, "source-without-includes"));
            sourceWithoutIncludes.mkdirs();
            FileHandle emptyIncludesTarget = new FileHandle(
                    new File(projectDir, "orig/shaders/custom/material/no_includes_copy"));
            writeDuplicate.invoke(
                    dialog, sourceWithoutIncludes, emptyIncludesTarget, "no_includes_copy", material, sources);
            Assert.assertTrue(emptyIncludesTarget.child("includes").isDirectory());
            Assert.assertEquals(0, emptyIncludesTarget.child("includes").list().length);

            vertices.get(ShaderVariant.DESKTOP_GL30).setText("   ");
            Object invalidSources = snapshot.invoke(dialog);
            try {
                validateSources.invoke(dialog, invalidSources);
                Assert.fail("An empty editor source must be rejected.");
            } catch (InvocationTargetException expected) {
                Assert.assertTrue(expected.getCause().getMessage().contains("Missing vertex shader for DESKTOP_GL30"));
            }
            Assert.assertFalse(new File(projectDir, "orig/shaders/custom/material/rejected_copy").exists());

            FileHandle rollbackTarget = new FileHandle(
                    new File(projectDir, "orig/shaders/custom/material/rollback_copy"));
            rollbackTarget.child("desktop-gl30.vert").mkdirs();
            try {
                writeDuplicate.invoke(dialog, sourceDir, rollbackTarget, "rollback_copy", material, sources);
                Assert.fail("A failed duplicate write must throw.");
            } catch (InvocationTargetException expected) {
                Assert.assertTrue(expected.getCause() instanceof RuntimeException);
            }
            Assert.assertFalse(rollbackTarget.exists());

            Method validateDirectory = privateMethod("validateStructuredShaderDirectory", FileHandle.class);
            targetDir.child("desktop-gl30.vert").delete();
            assertDirectoryValidationFails(validateDirectory, dialog, targetDir, "desktop-gl30.vert");
            targetDir.child("desktop-gl30.vert").writeString("", false, "UTF-8");
            assertDirectoryValidationFails(validateDirectory, dialog, targetDir, "empty");
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

    private static String invokeVariantPrefix(ShaderManagerDialog dialog, ShaderVariant variant) throws Exception {
        Method method = privateMethod("variantFilePrefix", ShaderVariant.class);
        return (String) method.invoke(dialog, variant);
    }

    private static void assertDirectoryValidationFails(
            Method validation, ShaderManagerDialog dialog, FileHandle directory, String messageFragment) throws Exception {
        try {
            validation.invoke(dialog, directory);
            Assert.fail("Invalid shader directory must be rejected.");
        } catch (InvocationTargetException expected) {
            Assert.assertTrue(expected.getCause().getMessage().contains(messageFragment));
        }
    }

    private static void assertNoDeclaredMethod(Class<?> type, String methodName) {
        for (Method method : type.getDeclaredMethods()) {
            Assert.assertNotEquals(methodName, method.getName());
        }
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

    private static Actor findLabel(Actor actor, String text) {
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label
                && text.contentEquals(((com.badlogic.gdx.scenes.scene2d.ui.Label) actor).getText())) {
            return actor;
        }
        if (actor instanceof Group) {
            for (Actor child : ((Group) actor).getChildren()) {
                Actor match = findLabel(child, text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Long.TYPE) return 0L;
        return null;
    }
}
