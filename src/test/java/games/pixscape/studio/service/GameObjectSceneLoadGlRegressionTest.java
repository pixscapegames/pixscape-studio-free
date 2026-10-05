package games.pixscape.studio.service;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Runs unchanged against the original develop: failure must occur after real Scene loading. */
public class GameObjectSceneLoadGlRegressionTest {
    @Test public void realSceneLoadPreservesTheActiveAssetWorldAndUnsavedHistory() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String previousHome = System.getProperty("user.home");
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        FileHandle directory = new FileHandle(Files.createTempDirectory("gameobject-scene-load").toFile());
        ProjectConfig config = new ProjectConfig();
        config.projectDirectoryPath = directory.path();
        config.projectFileName = "test";
        config.projectTitle = "Game Object lifecycle regression";
        AssetMetaDatabase database = new AssetMetaDatabase();
        database.save(directory.child("assets.json"));
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setWindowedMode(640, 480);
        gl.setInitialVisible(false);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        System.setProperty("user.home", directory.path());
        ProjectConfig.setInstance(config);
        try (var globals = new StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter();
                        studio.create();
                        // The fixture bootstraps project metadata without opening a Scene.
                        var field = SceneService.class.getDeclaredField("assetMetaDatabase");
                        field.setAccessible(true);
                        field.set(studio.getSceneService(), database);
                        FileHandle file = directory.child("car.gameobject");
                        new GameObjectAssetLoader().save(file, asset());
                        var document = studio.openGameObject(file.path());
                        assertNotNull(document);
                        var manager = studio.getEditorDocumentManager();
                        var context = document.context();
                        var world = context.world();
                        var history = context.historyManager();
                        String before = world.getMapper(PixscapeIdentityComponent.class).get(document.rootEntityId()).name;
                        history.execute(new ChangeEntityNameCommand(world, history.historyIds(),
                                history.historyIds().ensureForEntity(document.rootEntityId()), before, "Unsaved car", 0));
                        assertTrue(document.isDirty());
                        assertSame(document, manager.activeDocument());
                        assertTrue(studio.getCanvas().isAttached(context));
                        config.createSceneMeta("Other Scene");
                        var meta = config.getCurrentSceneMeta();
                        var prepared = studio.getCanvas().createSceneContext(config.canonicalSceneTagCurrent(), meta);
                        prepared.layerService().addLayerTop("Layer");
                        prepared.world().process();
                        SceneService.saveScene(prepared.world(), directory.child("scenes").child(meta.getFile()), false);
                        studio.getCanvas().releaseSceneContext(prepared);
                        prepared.dispose();

                        studio.getSceneService().loadScene(config, "Other Scene", directory);

                        assertFalse("A registered Game Object context was destroyed by loadScene", context.isDisposed());
                        assertSame(document, manager.find(document.key()));
                        assertSame(world, context.world());
                        assertTrue(manager.activate(document.key()));
                        assertTrue(studio.getCanvas().isAttached(context));
                        assertEquals("Unsaved car", world.getMapper(PixscapeIdentityComponent.class).get(document.rootEntityId()).name);
                        assertTrue(document.isDirty());
                        history.undo();
                        assertEquals(before, world.getMapper(PixscapeIdentityComponent.class).get(document.rootEntityId()).name);
                        assertFalse(document.isDirty());
                        history.redo();
                        assertEquals("Unsaved car", world.getMapper(PixscapeIdentityComponent.class).get(document.rootEntityId()).name);
                        assertTrue(document.isDirty());
                    } catch (Throwable ex) { failure[0] = ex; }
                    finally { Gdx.app.exit(); }
                }
                @Override public void dispose() {
                    try { if (studio != null) studio.dispose(); }
                    catch (Throwable ex) {
                        if (failure[0] == null) failure[0] = ex;
                        else failure[0].addSuppressed(ex);
                    }
                }
            }, gl);
        } finally {
            System.setProperty("user.home", previousHome);
            ProjectConfig.setInstance(previousConfig);
            directory.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Game Object Scene-load regression", failure[0]);
    }

    private static GameObjectAsset asset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        var root = new GameObjectAsset.GameObjectEntityData();
        root.sourceEntityId = 1;
        root.transform = new GameObjectAsset.TransformData();
        root.transform.scaleX = root.transform.scaleY = 1f;
        root.entityIndex = new GameObjectAsset.EntityIndexData();
        root.meta = new GameObjectAsset.MetaData();
        root.meta.kind = "GAME_OBJECT";
        root.identity = new GameObjectAsset.IdentityData();
        root.identity.name = "Car";
        root.gameObject = new GameObjectAsset.GameObjectData();
        asset.entities.add(root);
        return asset;
    }
}
