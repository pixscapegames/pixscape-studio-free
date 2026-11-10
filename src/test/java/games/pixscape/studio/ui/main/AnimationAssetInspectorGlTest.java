package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.ui.asset.*;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.property.entityproperties.AnimationClipsDialog;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.SimpleSelectBox;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Exercises actual browser, editor, catalog reload and canvas Inspector routing in an isolated project. */
public class AnimationAssetInspectorGlTest {
    @Test public void animationInspectionWorkflow() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String previousHome = System.getProperty("user.home");
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        FileHandle directory = new FileHandle(Files.createTempDirectory("pixscape-animation-inspector").toFile());
        System.setProperty("user.home", directory.path());
        ProjectConfig config = new ProjectConfig();
        config.projectDirectoryPath = directory.path();
        config.projectFileName = "test.pixscape";
        ProjectConfig.setInstance(config);
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Animation inspector regression");
        gl.setInitialVisible(false);
        gl.setWindowedMode(1200, 800);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        try (var globals = new StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter();
                        studio.create();
                        var database = new games.pixscape.studio.asset.AssetMetaDatabase();
                        AnimationAssetMeta hero = animation(database, directory, "Hero");
                        AnimationAssetMeta other = animation(database, directory, "Other");
                        AnimationAssetMeta layerAsset = animation(database, directory, "LayerReturn");
                        AnimationAssetMeta used = animation(database, directory, "Used");
                        database.save(directory.child("assets.json"));
                        var databaseField = SceneService.class.getDeclaredField("assetMetaDatabase");
                        databaseField.setAccessible(true);
                        databaseField.set(studio.getSceneService(), database);
                        config.createSceneMeta("Scene");
                        var context = studio.getCanvas().createSceneContext(
                                config.canonicalSceneTagCurrent(), config.getCurrentSceneMeta());
                        context.layerService().addLayerTop("Layer");
                        context.world().process();
                        SceneService.saveScene(context.world(), directory.child("scenes")
                                .child(config.getCurrentSceneMeta().getFile()), false);
                        studio.getCanvas().releaseSceneContext(context);
                        context.dispose();
                        var load = SceneService.class.getDeclaredMethod("loadScene",
                                ProjectConfig.class, String.class, FileHandle.class);
                        load.setAccessible(true);
                        load.invoke(studio.getSceneService(), config, "Scene", directory);
                        context = studio.getSceneEditorContext();
                        var world = context.world();
                        var selection = context.selectionService();
                        int layer = context.layerService().getFirstLayerEntity();
                        selection.setActivelayerId(layer);
                        int entity = world.create();
                        int newerEntity = world.create();
                        world.getMapper(AssetRefComponent.class).create(entity).assetId = used.id();
                        AnimationComponent instance = world.getMapper(AnimationComponent.class).create(entity);
                        instance.animationAssetIds.add(used.id());
                        instance.currentClip = "idle";
                        instance.fps = 12;
                        world.process();
                        PropertiesPanel panel = (PropertiesPanel) field(studio, "propertiesPanel");
                        selection.selectOnly(entity);
                        flush(panel);
                        assertEquals(entity, field(panel, "boundEntity"));

                        AssetsPanel assets = studio.getAssetsPanel();
                        FolderTreeView tree = (FolderTreeView) field(assets, "treeView");
                        openAnimations(tree);
                        AssetsThumbsView thumbs = (AssetsThumbsView) field(assets, "thumbsView");
                        Actor heroTile = tile(thumbs, hero.id());
                        click(heroTile, 1);
                        assertNotNull(panel.inspectedAnimationProperties());
                        String before = studio.getSceneService().getAssetMetaDatabase().toSnapshotJson();
                        SimpleSelectBox<String> clipBox = panel.inspectedAnimationProperties().findActor("animationPreviewClip");
                        clipBox.setSelected("walk");
                        assertEquals(before, studio.getSceneService().getAssetMetaDatabase().toSnapshotJson());
                        assertEquals("idle", instance.currentClip);
                        assertEquals(12, instance.fps, 0);
                        assertEquals(entity, selection.getSelectionSnapshot().first());
                        Actor firstPreview = panel.inspectedAnimationProperties().findActor("animationAssetPreview");
                        firstPreview.act(.1f);
                        assertEquals(2, field(firstPreview, "currentFrame"));
                        studio.resize(1200, 800);
                        studio.getUiStage().act(.2f);
                        studio.getUiStage().draw();
                        studio.getUiStage().act(.2f);
                        studio.getUiStage().draw();
                        Pixmap capture = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, 1200, 800);
                        PixmapIO.writePNG(new FileHandle("build/reports/animation-inspector.png"), capture, -1, true);
                        capture.dispose();
                        VisScrollPane inspectorScroll = (VisScrollPane) panel.inspectedAnimationProperties().getParent().getParent();
                        inspectorScroll.setScrollPercentY(1);
                        inspectorScroll.updateVisualScroll();
                        studio.getUiStage().draw();
                        capture = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, 1200, 800);
                        PixmapIO.writePNG(new FileHandle("build/reports/animation-inspector-clips.png"), capture, -1, true);
                        capture.dispose();

                        click(heroTile, 2);
                        AnimationClipsDialog dialog = dialog(studio);
                        assertNotNull("Double-click opens the existing editor", dialog);
                        ((SimpleFloatField) field(dialog, "fpsField")).setText("24");
                        result(dialog, false);
                        flush(panel);
                        assertEquals(before, studio.getSceneService().getAssetMetaDatabase().toSnapshotJson());
                        assertNotNull(panel.inspectedAnimationProperties());

                        panel.inspectedAnimationProperties().findActor("configureAnimationClips").fire(new ChangeListener.ChangeEvent());
                        dialog = dialog(studio);
                        ((SimpleFloatField) field(dialog, "fpsField")).setText("24");
                        result(dialog, true);
                        flush(panel);
                        assertNotNull("Catalog refresh must preserve asset inspection", panel.inspectedAnimationProperties());
                        assertEquals(24, ((AnimationAssetMeta) studio.getSceneService().getAssetMetaDatabase().findById(hero.id())).fps, 0);
                        assertEquals(24f, (Float) field(panel.inspectedAnimationProperties().findActor("animationAssetPreview"), "fps"), 0);
                        assertEquals(entity, selection.getSelectionSnapshot().first());

                        // Deliver a real drag gesture through Stage, including capture and touch focus.
                        studio.getUiStage().act(0);
                        studio.getUiStage().draw();
                        heroTile = tile(thumbs, hero.id());
                        Vector2 point = studio.getUiStage().stageToScreenCoordinates(
                                heroTile.localToStageCoordinates(new Vector2(10, 10)));
                        var stage = studio.getUiStage();
                        stage.touchDown(Math.round(point.x), Math.round(point.y), 0, 0);
                        stage.touchDragged(Math.round(point.x) + 70, Math.round(point.y), 0);
                        assertTrue("Animation drag gesture produced a payload", DragContext.get().active());
                        assertEquals(hero.id(), DragContext.get().peek().assetId);
                        stage.touchUp(Math.round(point.x) + 70, Math.round(point.y), 0, 0);
                        assertTrue(DragContext.get().releasePending());
                        var payload = DragContext.get().consumeIfReleasedInside(true);
                        assertEquals("anim-sheet", payload.type);
                        if (payload.ghostPixmap != null) payload.ghostPixmap.dispose();
                        assertFalse(DragContext.get().active());

                        var delete = AssetsThumbsView.class.getDeclaredMethod("showDeleteAssetDialog", Array.class);
                        delete.setAccessible(true);
                        delete.invoke(thumbs, Array.with((AssetNode) heroTile.getUserObject()));
                        Actor confirmation = studio.getUiStage().getActors().peek();
                        var cancelDelete = confirmation.getClass().getDeclaredMethod("result", Object.class);
                        cancelDelete.setAccessible(true);
                        cancelDelete.invoke(confirmation, false);
                        confirmation.remove();
                        flush(panel);
                        assertEquals(Integer.toString(hero.id()), label(panel, "animationAssetId"));

                        // A denied deletion and deletion of another asset leave inspection intact.
                        click(tile(thumbs, used.id()), 1);
                        assertThrows(IllegalStateException.class,
                                () -> studio.getSceneService().deleteProjectAssets(Array.with(used.id())));
                        flush(panel);
                        assertEquals(Integer.toString(used.id()), label(panel, "animationAssetId"));
                        click(tile(thumbs, hero.id()), 1);
                        studio.getSceneService().deleteProjectAssets(Array.with(other.id()));
                        flush(panel);
                        assertEquals(Integer.toString(hero.id()), label(panel, "animationAssetId"));
                        assertNotNull(panel.inspectedAnimationProperties().findActor("animationAssetPreview"));

                        // The live selection can change after inspection starts, before queued UI refresh.
                        Actor deletedPreview = panel.inspectedAnimationProperties().findActor("animationAssetPreview");
                        selection.selectOnly(newerEntity);
                        studio.getSceneService().deleteProjectAssets(Array.with(hero.id()));
                        panel.updateIfDirty();
                        assertNull(panel.inspectedAnimationProperties());
                        assertEquals(newerEntity, field(panel, "boundEntity"));
                        assertEquals(newerEntity, selection.getSelectionSnapshot().first());
                        assertEquals(false, field(deletedPreview, "playing"));
                        flush(panel);

                        selection.clearSelection();
                        selection.setActivelayerId(layer);
                        flush(panel);
                        openAnimations(tree);
                        click(tile(thumbs, layerAsset.id()), 1);
                        studio.getSceneService().deleteProjectAssets(Array.with(layerAsset.id()));
                        flush(panel);
                        assertNull(panel.inspectedAnimationProperties());
                        assertEquals(layer, field(panel, "boundLayer"));
                        assertTrue(selection.getSelectionSnapshot().isEmpty());
                        assertEquals(layer, selection.getActivelayerId());

                        // Normal canvas selection wins over asset inspection, then an asset click wins again.
                        openAnimations(tree);
                        click(tile(thumbs, used.id()), 1);
                        selection.selectOnly(entity);
                        flush(panel);
                        assertNull(panel.inspectedAnimationProperties());
                        assertEquals(entity, field(panel, "boundEntity"));
                        openAnimations(tree);
                        click(tile(thumbs, used.id()), 1);
                        assertNotNull(panel.inspectedAnimationProperties());
                    } catch (Throwable ex) { failure[0] = ex; }
                    finally { Gdx.app.exit(); }
                }
                @Override public void dispose() { if (studio != null) studio.dispose(); }
            }, gl);
        } finally {
            System.setProperty("user.home", previousHome);
            ProjectConfig.setInstance(previousConfig);
            directory.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Animation inspector workflow failed", failure[0]);
    }

    private static AnimationAssetMeta animation(AssetMetaDatabase db, FileHandle directory, String name) {
        String source = StudioFs.DIR_ORIG_ANIMATIONS + "/" + name;
        var meta = (AnimationAssetMeta) db.registerIfAbsent(AssetType.ANIMATION,
                "animations/" + name, source, AssetMeta.AssetScope.USER);
        meta.frameCount = 4;
        meta.fps = 12;
        meta.currentClip = "idle";
        meta.clips.put("idle", new AnimationClipMeta(0, 0));
        meta.clips.put("walk", new AnimationClipMeta(1, 3));
        directory.child(source).mkdirs();
        for (int i = 0; i < 4; i++) {
            Pixmap image = new Pixmap(24, 32, Pixmap.Format.RGBA8888);
            image.setColor(new Color(.2f, .3f + i * .15f, .8f, 1));
            image.fill();
            PixmapIO.writePNG(directory.child(source).child("frame" + i + ".png"), image);
            image.dispose();
        }
        return meta;
    }

    private static void openAnimations(FolderTreeView tree) throws Exception {
        tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.ANIMATIONS, "", "Animations", null));
        ((Actor) field(tree, "tree")).fire(new ChangeListener.ChangeEvent());
    }

    private static Actor tile(AssetsThumbsView thumbs, int id) throws Exception {
        for (Object item : (Array<?>) field(thumbs, "tileWidgets")) {
            Actor actor = (Actor) item;
            if (actor.getUserObject() instanceof AssetNode node && node.assetId == id) return actor;
        }
        AssetNode folder = (AssetNode) field(thumbs, "currentFolder");
        throw new AssertionError("Missing thumbnail " + id + " in " + folder.root + ":" + folder.path
                + " (assets: " + field(thumbs, "currentAssets") + ")");
    }

    private static void click(Actor actor, int taps) {
        for (var listener : actor.getListeners()) {
            if (listener instanceof ClickListener click) {
                click.setTapCount(taps);
                click.clicked(new InputEvent(), 0, 0);
                return;
            }
        }
        throw new AssertionError("Missing click listener");
    }

    private static AnimationClipsDialog dialog(StudioApplicationAdapter studio) {
        for (Actor actor : studio.getUiStage().getActors()) {
            if (actor instanceof AnimationClipsDialog dialog) return dialog;
        }
        return null;
    }

    private static void result(AnimationClipsDialog dialog, boolean ok) throws Exception {
        var method = AnimationClipsDialog.class.getDeclaredMethod("result", Object.class);
        method.setAccessible(true);
        method.invoke(dialog, ok);
        dialog.remove();
    }

    private static String label(PropertiesPanel panel, String name) {
        return ((VisLabel) panel.inspectedAnimationProperties().findActor(name)).getText().toString();
    }

    private static void flush(PropertiesPanel panel) {
        EventFlow.i().flush();
        panel.updateIfDirty();
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
