package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.service.asset.StudioAnimationAssets;
import games.pixscape.studio.system.StudioParticleFallbackSystem;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.asset.AssetsThumbsView;
import games.pixscape.studio.ui.asset.FolderTreeView;
import games.pixscape.studio.ui.property.PropertiesPanel;
import org.junit.Test;

import java.nio.file.Files;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Real desktop UI and project lifecycle, isolated from the user's projects and settings.
 */
public class RecentProjectLifecycleGlTest {
    @Test
    public void startupAndAssetDeselectionNeedNoProjectOrCatalog() throws Exception {
        withStudio((studio, root) -> {
            assertEmpty(studio);
            assertNull(field(studio.getSceneService(), "assetMetaDatabase"));
            // Keep the real Assets -> Inspector listener installed by create().
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            inspector(studio).inspectAsset(null);
            assertEmpty(studio);
            assertNull("Deselection must not initialize a catalog",
                    field(studio.getSceneService(), "assetMetaDatabase"));
        });
    }

    @Test
    public void closingLastDocumentPreservesProjectAndTileUndoHistory() throws Exception {
        withStudio((studio, root) -> {
            FileHandle first = project(studio, root.child("first"), "First");
            openProject(studio.getSceneService(), first);
            ProjectConfig cfg = ProjectConfig.getInstance();
            AssetMetaDatabase db = studio.getSceneService().getAssetMetaDatabase();
            var tile = (games.pixscape.studio.asset.TileAssetMeta) db.findByLogicalPath("tiles/set/0");
            PropertiesPanel inspector = inspector(studio);
            AssetNode node = new AssetNode(AssetNode.Kind.REGION, AssetNode.Root.TILES, "set/0", "Tile", null);
            node.assetId = tile.id();
            inspector.inspectAsset(node);
            var properties = inspector.inspectedTileProperties();
            var apply = properties.getClass().getDeclaredMethod("apply", int.class,
                    games.pixscape.runtime.property.PropertySet.class);
            apply.setAccessible(true);
            apply.invoke(properties, tile.id(), new games.pixscape.runtime.property.PropertySet().putInt("cost", 4));
            assertTrue(properties.canUndo());
            var closed = inspectedContext(studio);

            studio.getEditorDocumentManager().closeNow(studio.getEditorDocumentManager().activeDocument().key());
            EventFlow.i().flush();
            inspector.markDirty();
            inspector.updateIfDirty();

            assertNull(studio.getEditorDocumentManager().activeDocument());
            assertSame(cfg, ProjectConfig.getInstance());
            assertSame(db, studio.getSceneService().getAssetMetaDatabase());
            assertTrue(((Group) field(inspector, "contentHolder")).getChildren().isEmpty());
            assertContextReleased(inspector, closed);
            inspector.inspectAsset(node);
            inspector.inspectAsset(null);
            assertTrue("Leaving the view must preserve undo", properties.canUndo());
            inspector.inspectAsset(node);
            assertTrue(properties.undo());
            assertFalse(tile.properties.contains("cost"));
            assertTrue(properties.redo());
            assertEquals(4, tile.properties.getInt("cost", 0));
            studio.getSceneService().unloadProjectToEmptyEditor();
            assertNull(field(properties, "database"));
            assertFalse(((games.pixscape.studio.history.HistoryManager) field(properties, "history")).canUndo());
        });
    }

    @Test
    public void gameObjectsCrossUnloadAndOnlyShowTheNextProject() throws Exception {
        withStudio((studio, root) -> {
            FileHandle first = project(studio, root.child("first"), "First");
            FileHandle next = project(studio, root.child("next"), "Next");
            openProject(studio.getSceneService(), first);
            selectGameObjects(studio);
            assertGameObjects(studio, "First");
            PropertiesPanel inspector = inspector(studio);
            Actor previousProperties = (Actor) field(inspector, "sceneProperties");
            var previous = inspectedContext(studio);
            studio.getSceneService().unloadProjectToEmptyEditor();
            assertEmpty(studio);
            assertNull(previousProperties.getParent());
            assertContextReleased(inspector, previous);
            openProject(studio.getSceneService(), next);
            selectGameObjects(studio);
            assertGameObjects(studio, "Next");
            assertLiveInspectorContexts(inspector);
        });
    }

    @Test
    public void noDiscardsChangesAndReleasesProjectInspection() throws Exception {
        withStudio((studio, root) -> {
            FileHandle first = project(studio, root.child("first"), "First");
            FileHandle next = project(studio, root.child("next"), "Next");
            var service = studio.getSceneService();
            openProject(service, first);
            selectGameObjects(studio);
            PropertiesPanel inspector = inspector(studio);
            AssetNode animation = animation();
            inspector.inspectAsset(animation);
            var properties = inspector.inspectedAnimationProperties();
            assertNotNull(properties);
            Actor preview = properties.findActor("animationAssetPreview");
            Object oldTexture = ((Array<?>) field(properties, "frames")).first();
            var oldContext = inspectedContext(studio);
            assertParticleRoots(studio, first.parent());
            var before = snapshot(first.parent());
            ProjectConfig.getInstance().projectTitle = "Unsaved title";
            service.markCurrentSceneSaveRequired();

            openRecentAndAnswerNo(studio, next);

            assertEquals("No must leave every previous project file unchanged", before, snapshot(first.parent()));
            assertContextReleased(inspector, oldContext);
            assertParticleRoots(studio, next.parent());
            assertNull(inspector.inspectedAnimationProperties());
            assertNull(field(properties, "animation"));
            assertNull(field(properties, "database"));
            assertTrue(((Array<?>) field(properties, "frames")).isEmpty());
            assertEquals(false, field(preview, "playing"));
            assertEquals("Next", ProjectConfig.getInstance().projectTitle);
            // Mode notifications may select another category, but it must be a node of the rebuilt tree.
            assertSame(((FolderTreeView) field(studio.getAssetsPanel(), "treeView")).getSelectedFolder(),
                    field(field(studio.getAssetsPanel(), "thumbsView"), "currentFolder"));
            selectGameObjects(studio);
            assertGameObjects(studio, "Next");
            inspector.inspectAsset(animation);
            assertNotNull(inspector.inspectedAnimationProperties());
            assertNotSame("Project-relative preview cache must be rebuilt", oldTexture,
                    ((Array<?>) field(properties, "frames")).first());
            assertLiveInspectorContexts(inspector);

            assertUnloadAfterAllDocumentsClosed(studio, animation);
            openProject(service, next);
            inspector.inspectAsset(animation);
            assertNotNull("Asset inspection must work after reopening", inspector.inspectedAnimationProperties());
        });
    }

    @Test
    public void failedOpenShowsTheLoadErrorAndAllowsRetry() throws Exception {
        withStudio((studio, root) -> {
            FileHandle first = project(studio, root.child("first"), "First");
            FileHandle broken = project(studio, root.child("broken"), "Broken");
            FileHandle assets = broken.parent().child(StudioFs.FILE_ASSETS_JSON);
            AssetMetaDatabase invalid = AssetMetaDatabase.load(assets);
            ((AnimationAssetMeta) invalid.findById(1)).fps = 0;
            invalid.save(assets);
            var service = studio.getSceneService();
            openProject(service, first);
            selectGameObjects(studio);
            inspector(studio).inspectAsset(animation());
            var oldContext = inspectedContext(studio);
            service.markCurrentSceneSaveRequired();
            openRecentAndAnswerNo(studio, broken);
            Window error = dialog(studio, "Project not loaded");
            assertNotNull("The load failure must remain visible", error);
            assertEmpty(studio);
            assertContextReleased(inspector(studio), oldContext);
            error.remove();
            var result = service.tryOpenProject(broken, "test");
            assertTrue(result.isPresent());
            assertTrue(result.get().message().contains("Cannot load current scene"));
            assertNotNull(result.get().cause().getCause());
            assertEquals("fps must be finite and > 0", result.get().cause().getCause().getMessage());
            assertEmpty(studio);
            openProject(service, first);
            selectGameObjects(studio);
            assertGameObjects(studio, "First");
            inspector(studio).inspectAsset(animation());
            assertNotNull(inspector(studio).inspectedAnimationProperties());
            assertLiveInspectorContexts(inspector(studio));
        });
    }

    @FunctionalInterface
    private interface Scenario {
        void run(StudioApplicationAdapter studio, FileHandle root) throws Exception;
    }

    private static void withStudio(Scenario scenario) throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String previousHome = System.getProperty("user.home");
        ProjectConfig previous = ProjectConfig.getInstance();
        FileHandle root = new FileHandle(Files.createTempDirectory("recent-project-lifecycle").toFile());
        System.setProperty("user.home", root.path());
        ProjectConfig.setInstance(new ProjectConfig());
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Recent project lifecycle regression");
        gl.setInitialVisible(false);
        gl.setWindowedMode(1000, 700);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        try (var globals = new StudioGlTestScope()) {
            // LWJGL frees its callback on exit but leaves the native pointer installed.
            org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;

                @Override
                public void create() {
                    try {
                        studio = new StudioApplicationAdapter() {
                            @Override
                            public void saveActiveDocumentWithProgress(Runnable ok, Consumer<Throwable> error) {
                                fail("No must never invoke saving");
                            }
                        };
                        studio.create();
                    } catch (Throwable ex) {
                        failure[0] = ex;
                        Gdx.app.exit();
                    }
                }

                @Override
                public void render() {
                    if (failure[0] != null) return;
                    try {
                        studio.render();
                        scenario.run(studio, root);
                        studio.render();
                    } catch (Throwable ex) {
                        failure[0] = ex;
                    } finally {
                        Gdx.app.exit();
                    }
                }

                @Override
                public void dispose() {
                    try {
                        if (studio != null) studio.dispose();
                    } catch (Throwable ex) {
                        if (failure[0] == null) failure[0] = ex;
                        else failure[0].addSuppressed(ex);
                    }
                }
            }, gl);
        } finally {
            org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
            System.setProperty("user.home", previousHome);
            ProjectConfig.setInstance(previous);
            root.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Recent project lifecycle failed", failure[0]);
    }

    private static PropertiesPanel inspector(StudioApplicationAdapter studio) throws Exception {
        return (PropertiesPanel) field(studio, "propertiesPanel");
    }

    private static AssetNode animation() {
        AssetNode node = new AssetNode(AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS, "Shared", "Animation", null);
        node.assetId = 1;
        return node;
    }

    private static java.util.Map<String, String> snapshot(FileHandle directory) throws Exception {
        var root = directory.file().toPath();
        var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(path).toString(),
                        java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    private record InspectedContext(games.pixscape.studio.scene.SceneEditorContext context,
                                    com.artemis.World world, games.pixscape.studio.service.SelectionService selection) {
    }

    private static InspectedContext inspectedContext(StudioApplicationAdapter studio) {
        var context = studio.getSceneEditorContext();
        return new InspectedContext(context, context.world(), context.selectionService());
    }

    private static void assertContextReleased(PropertiesPanel inspector, InspectedContext previous) throws Exception {
        assertTrue(previous.context().isDisposed());
        assertFalse(((java.util.Map<?, ?>) field(inspector, "editorsByContext")).containsKey(previous.context()));
        assertNotSame(previous.world(), field(inspector, "world"));
        assertNotSame(previous.selection(), field(inspector, "selectionService"));
        assertFalse(((java.util.Set<?>) field(inspector, "refreshBoundWorlds")).contains(previous.world()));
    }

    private static void assertLiveInspectorContexts(PropertiesPanel inspector) throws Exception {
        for (Object key : ((java.util.Map<?, ?>) field(inspector, "editorsByContext")).keySet()) {
            assertFalse(((games.pixscape.studio.scene.SceneEditorContext) key).isDisposed());
        }
    }

    private static FileHandle project(StudioApplicationAdapter studio, FileHandle dir, String name) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectDirectoryPath = dir.path();
        cfg.projectFileName = name;
        cfg.projectTitle = name;
        cfg.exportRootPathDir = dir.child("export").path();
        cfg.createSceneMeta("Scene");
        var context = studio.getCanvas().createSceneContext(cfg.canonicalSceneTagCurrent(), cfg.getCurrentSceneMeta());
        context.layerService().addLayerTop("Layer");
        context.world().process();
        SceneService.saveScene(context.world(), dir.child("scenes/scene1.json"), false);
        studio.getCanvas().releaseSceneContext(context);
        context.dispose();
        AssetMetaDatabase db = new AssetMetaDatabase();
        var animation = (AnimationAssetMeta) db.registerIfAbsent(AssetType.ANIMATION,
                "animations/Shared", "orig/animations/Shared", AssetMeta.AssetScope.USER);
        StudioAnimationAssets.initializeImportedFrames(animation, 1);
        Pixmap frame = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        frame.setColor(name.equals("First") ? 1 : 0, 0, 1, 1);
        frame.fill();
        PixmapIO.writePNG(dir.child("orig/animations/Shared/frame.png"), frame);
        frame.dispose();
        var tileset = db.registerIfAbsent(AssetType.TILESET, "tiles/set", null, AssetMeta.AssetScope.USER);
        var tile = (games.pixscape.studio.asset.TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE, "tiles/set/0", null, AssetMeta.AssetScope.USER);
        tile.tilesetId = tileset.id();
        tile.sheetIndex = 0;
        db.save(dir.child(StudioFs.FILE_ASSETS_JSON));
        dir.child(StudioFs.DIR_GAME_OBJECTS).child(name + ".gameobject").writeString("{}", false);
        FileHandle project = dir.child(name + ".json");
        ProjectConfig.ProjectIO.saveProject(cfg, project);
        return project;
    }

    private static void openProject(SceneService service, FileHandle file) {
        service.tryOpenProject(file, "test").ifPresent(failure -> {
            throw new AssertionError(failure.message(), failure.cause());
        });
    }

    private static void openRecentAndAnswerNo(StudioApplicationAdapter studio, FileHandle project) throws Exception {
        var open = TopMenuBar.class.getDeclaredMethod("openRecentProject", String.class);
        open.setAccessible(true);
        open.invoke(field(studio, "topMenuBar"), project.path());
        Dialog confirmation = (Dialog) dialog(studio, "Unsaved changes");
        assertNotNull(confirmation);
        for (Actor actor : confirmation.getButtonTable().getChildren()) {
            if (actor instanceof TextButton button && "No".contentEquals(button.getText())) {
                button.fire(new ChangeListener.ChangeEvent());
                confirmation.remove();
                EventFlow.i().flush();
                return;
            }
        }
        fail("No button not found");
    }

    private static Window dialog(StudioApplicationAdapter studio, String title) {
        for (Actor actor : studio.getUiStage().getActors()) {
            if (actor instanceof Window dialog && title.contentEquals(dialog.getTitleLabel().getText())) return dialog;
        }
        return null;
    }

    private static void selectGameObjects(StudioApplicationAdapter studio) throws Exception {
        ((FolderTreeView) field(studio.getAssetsPanel(), "treeView")).selectFolder(
                new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.GAME_OBJECTS, "", "Game Objects", null));
    }

    private static void assertGameObjects(StudioApplicationAdapter studio, String name) throws Exception {
        Array<?> assets = (Array<?>) field(field(studio.getAssetsPanel(), "thumbsView"), "currentAssets");
        AssetNode folder = (AssetNode) field(field(studio.getAssetsPanel(), "thumbsView"), "currentFolder");
        assertEquals("Selected folder: " + (folder != null ? folder.root + ":" + folder.path : "none"), 1, assets.size);
        assertEquals(name, ((AssetNode) assets.first()).name);
    }

    private static void assertEmpty(StudioApplicationAdapter studio) throws Exception {
        EventFlow.i().flush();
        assertNull(ProjectConfig.getInstance().projectFileName);
        assertNull(studio.getEditorDocumentManager().activeDocument());
        assertNull(studio.getAssetsPanel().getSelectedAssetNode());
        AssetsThumbsView thumbs = (AssetsThumbsView) field(studio.getAssetsPanel(), "thumbsView");
        assertNull(field(thumbs, "currentFolder"));
        assertTrue(((Array<?>) field(thumbs, "currentAssets")).isEmpty());
        PropertiesPanel inspector = (PropertiesPanel) field(studio, "propertiesPanel");
        // Exercise the same deferred refresh as a frame with no active document.
        inspector.markDirty();
        inspector.updateIfDirty();
        assertTrue(((Group) field(inspector, "contentHolder")).getChildren().isEmpty());
        assertNull(inspector.inspectedAnimationProperties());
        assertNull(inspector.inspectedTileProperties());
        assertLiveInspectorContexts(inspector);
    }

    private static void assertUnloadAfterAllDocumentsClosed(StudioApplicationAdapter studio, AssetNode animation)
            throws Exception {
        studio.getEditorDocumentManager().clear();
        assertNull(studio.getEditorDocumentManager().activeDocument());
        PropertiesPanel inspector = (PropertiesPanel) field(studio, "propertiesPanel");
        inspector.inspectAsset(animation);
        var properties = inspector.inspectedAnimationProperties();
        assertNotNull("Assets remain inspectable without a Scene tab", properties);
        Actor preview = properties.findActor("animationAssetPreview");
        assertNotNull(preview);
        assertNotNull(field(properties, "database"));
        assertNotNull(field(properties, "animation"));

        studio.getSceneService().unloadProjectToEmptyEditor();

        assertEmpty(studio);
        assertNull(field(properties, "database"));
        assertNull(field(properties, "animation"));
        assertTrue(((Array<?>) field(properties, "frames")).isEmpty());
        assertEquals(false, field(preview, "playing"));
    }

    private static void assertParticleRoots(StudioApplicationAdapter studio, FileHandle project) throws Exception {
        var world = studio.getSceneEditorContext().world();
        var runtime = world.getSystem(RenderParticleSyncSystem.class);
        var fallback = world.getSystem(StudioParticleFallbackSystem.class);
        assertEquals(project.child(StudioFs.DIR_ORIG_EFFECTS),
                field(field(runtime, "particleAvailability"), "effectsRoot"));
        assertEquals(project.child(StudioFs.DIR_ORIG_EFFECTS), field(fallback, "effectsRoot"));
        assertEquals(project.child(StudioFs.DIR_ORIG_IMAGES), field(fallback, "imagesRoot"));
    }

    private static Object field(Object object, String name) throws Exception {
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
