package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.GameObjectEditorDocument;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Opt-in desktop integration exercise; never opens the user's project or settings. */
public class GameObjectDocumentGlSmokeTest {
    @Test public void isolatedAssetActivation() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String originalHome = System.getProperty("user.home");
        ProjectConfig originalConfig = ProjectConfig.getInstance();
        FileHandle directory = new FileHandle(Files.createTempDirectory("pixscape-documents").toFile());
        System.setProperty("user.home", directory.path());
        ProjectConfig config = new ProjectConfig();
        config.projectDirectoryPath = directory.path();
        config.projectFileName = "test.pixscape";
        ProjectConfig.setInstance(config);
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Pixscape document regression test");
        gl.setWindowedMode(640, 480);
        gl.setInitialVisible(false);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        try (var globals = new games.pixscape.studio.service.StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter();
                        studio.create();
                        var database = new games.pixscape.studio.asset.AssetMetaDatabase();
                        database.save(directory.child("assets.json"));
                        var databaseField = games.pixscape.studio.service.SceneService.class.getDeclaredField("assetMetaDatabase");
                        databaseField.setAccessible(true);
                        databaseField.set(studio.getSceneService(), database);
                        var manager = studio.getEditorDocumentManager();
                        FileHandle file = directory.child("car.gameobject");
                        String referenceAsset = System.getenv("PIXSCAPE_DOCUMENT_ASSET");
                        new GameObjectAssetLoader().save(file, referenceAsset != null
                                ? new GameObjectAssetLoader().load(new FileHandle(referenceAsset)) : asset());
                        var document = studio.openGameObject(file.path());
                        assertNotNull(studio.getUiStage().getRoot().toString(), document);
                        assertTrue(document.context().isInitialized());
                        assertTrue(studio.getCanvas().isAttached(document.context()));
                        assertTrue(studio.getCanvas().isScenePhysicsEnabled());
                        assertTrue(studio.getCanvas().getPhysicsService().isAvailable());
                        assertNull(document.context().sceneIdentity());
                        assertTrue(config.getSceneNames().isEmpty());
                        studio.render();
                        studio.getCanvas().detach();
                        studio.getCanvas().attach(document.context());
                        assertSame(document, studio.openGameObject(file.path()));

                        FileHandle secondFile = directory.child("second.gameobject");
                        new GameObjectAssetLoader().save(secondFile, asset());
                        var second = studio.openGameObject(secondFile.path());
                        assertNotSame(document.context().world(), second.context().world());
                        rename(second, "second edited");
                        assertTrue(second.isDirty());
                        assertFalse(document.isDirty());
                        manager.activate(document.key());
                        rename(document, "edited car");
                        studio.undoActiveDocument();
                        assertFalse(document.isDirty());
                        assertTrue(second.isDirty());
                        studio.redoActiveDocument();
                        assertTrue(document.isDirty());
                        studio.saveActiveDocumentWithProgress(() -> {}, ex -> { throw new AssertionError(ex); });
                        assertFalse(document.isDirty());
                        assertEquals("edited car", new GameObjectAssetLoader().load(file).entities.get(0).identity.name);
                        rename(document, "unsaved car");
                        var originalWorld = document.context().world();
                        var assetOps = studio.getCanvas().getEditorOps();
                        var bodies = originalWorld.getAspectSubscriptionManager().get(
                                com.artemis.Aspect.all(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class))
                                .getEntities();
                        int body = bodies.get(0);

                        config.createSceneMeta("Scene");
                        SceneMeta sceneMeta = config.getCurrentSceneMeta();
                        sceneMeta.pixelsPerMeter = 32f;
                        var sceneContext = studio.getCanvas().createSceneContext(
                                config.canonicalSceneTagCurrent(), sceneMeta);
                        sceneContext.layerService().addLayerTop("Layer");
                        sceneContext.world().process();
                        games.pixscape.studio.service.SceneService.saveScene(sceneContext.world(),
                                directory.child("scenes").child(sceneMeta.getFile()), false);
                        studio.getCanvas().releaseSceneContext(sceneContext);
                        sceneContext.dispose();
                        // Use the real Scene materialization path while the asset document is attached.
                        var loadScene = games.pixscape.studio.service.SceneService.class.getDeclaredMethod(
                                "loadScene", ProjectConfig.class, String.class, FileHandle.class);
                        loadScene.setAccessible(true);
                        loadScene.invoke(studio.getSceneService(), config, "Scene", directory);
                        assertFalse("Loading a Scene must preserve the asset context", document.context().isDisposed());
                        assertSame(document, manager.find(document.key()));
                        assertSame(originalWorld, document.context().world());
                        assertTrue(document.isDirty());
                        assertEquals("unsaved car", originalWorld.getMapper(PixscapeIdentityComponent.class)
                                .get(document.rootEntityId()).name);
                        var scene = (games.pixscape.studio.document.SceneEditorDocument) manager.activeDocument();
                        // A retained operation must target its own World and 100 PPM, even with a 32 PPM Scene active.
                        int sceneEntitiesBefore = scene.context().world().getAspectSubscriptionManager()
                                .get(com.artemis.Aspect.all()).getEntities().size();
                        assetOps.addBoxFixture(body, 100f, 100f);
                        originalWorld.process();
                        var shapes = originalWorld.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class)
                                .get(body).shapes;
                        assertEquals(2, shapes.size);
                        assertEquals(1f, shapes.peek().geometry.offsetX, 0.001f);
                        assertEquals(1f, shapes.peek().geometry.offsetY, 0.001f);
                        document.context().historyManager().undo();
                        originalWorld.process();
                        assertEquals(1, originalWorld.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class)
                                .get(body).shapes.size);
                        document.context().historyManager().redo();
                        originalWorld.process();
                        assertEquals(2, originalWorld.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class)
                                .get(body).shapes.size);
                        document.context().historyManager().undo();
                        originalWorld.process();
                        assertEquals(sceneEntitiesBefore, scene.context().world().getAspectSubscriptionManager()
                                .get(com.artemis.Aspect.all()).getEntities().size());
                        assertFalse(scene.isDirty());
                        for (int i = 0; i < 3; i++) {
                            manager.activate(document.key());
                            assertTrue(studio.getCanvas().isScenePhysicsEnabled());
                            assertEquals(100f, 1f / studio.getCanvas().getPhysicsService().pxToM(1f), 0.01f);
                            studio.render();
                            manager.activate(scene.key());
                            assertFalse(studio.getCanvas().isScenePhysicsEnabled());
                        }
                        manager.activate(document.key());
                        assertFalse(scene.isDirty());
                        studio.undoActiveDocument();
                        assertFalse(document.isDirty());
                        studio.redoActiveDocument();
                        assertTrue(document.isDirty());
                        var sceneFile = directory.child("scenes").child(sceneMeta.getFile());
                        byte[] sceneBytes = sceneFile.readBytes();
                        studio.saveActiveDocumentWithProgress(() -> {}, ex -> { throw new AssertionError(ex); });
                        assertArrayEquals(sceneBytes, sceneFile.readBytes());
                        manager.closeNow(scene.key());
                        assertTrue(document.context().isInitialized());
                        manager.closeNow(second.key());
                        assertTrue(second.context().isDisposed());
                        assertTrue(studio.getCanvas().isAttached(document.context()));

                        EditorDocumentManager.Listener rejectOpening = new EditorDocumentManager.Listener() {
                            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                                if (current instanceof GameObjectEditorDocument candidate
                                        && candidate.assetId().contains("rejected")) {
                                    throw new IllegalStateException("Injected inspector activation failure");
                                }
                            }
                        };
                        manager.addListener(rejectOpening);
                        FileHandle rejected = directory.child("rejected.gameobject");
                        new GameObjectAssetLoader().save(rejected, asset());
                        assertNull(studio.openGameObject(rejected.path()));
                        assertEquals(1, manager.documents().size());
                        assertSame(document, manager.activeDocument());
                        assertTrue(studio.getCanvas().isAttached(document.context()));
                        manager.removeListener(rejectOpening);

                        manager.closeNow(document.key());
                        assertTrue(document.context().isDisposed());
                        config.setCurrentSceneByName(null);
                        var reopened = studio.openGameObject(file.path());
                        assertNotNull(reopened);
                        assertEquals("unsaved car", reopened.context().world().getMapper(PixscapeIdentityComponent.class)
                                .get(reopened.rootEntityId()).name);
                        int point = studio.getCanvas().getEditorOps().createPointLightInGameObject(reopened.rootEntityId());
                        assertTrue(point >= 0);
                        reopened.context().world().process();
                        assertTrue(reopened.isDirty());
                        studio.undoActiveDocument();
                        reopened.context().world().process();
                        assertFalse(reopened.context().world().getEntityManager().isActive(point));
                    } catch (Throwable ex) { failure[0] = ex; }
                    finally { Gdx.app.exit(); }
                }
                @Override public void dispose() {
                    try { if (studio != null) studio.dispose(); }
                    catch (Throwable ex) { if (failure[0] == null) failure[0] = ex; else failure[0].addSuppressed(ex); }
                }
            }, gl);
        } finally {
            System.setProperty("user.home", originalHome);
            ProjectConfig.setInstance(originalConfig);
            directory.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Document workflow failed", failure[0]);
    }

    private static void rename(GameObjectEditorDocument document, String name) {
        var context = document.context();
        String before = context.world().getMapper(PixscapeIdentityComponent.class).get(document.rootEntityId()).name;
        var history = context.historyManager();
        history.execute(new ChangeEntityNameCommand(context.world(), history.historyIds(),
                history.historyIds().ensureForEntity(document.rootEntityId()), before, name, 0));
    }

    private static GameObjectAsset asset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData root = new GameObjectAsset.GameObjectEntityData();
        root.sourceEntityId = 1;
        root.transform = new GameObjectAsset.TransformData();
        root.transform.scaleX = root.transform.scaleY = 1f;
        root.entityIndex = new GameObjectAsset.EntityIndexData();
        root.meta = new GameObjectAsset.MetaData();
        root.meta.kind = "GAME_OBJECT";
        root.identity = new GameObjectAsset.IdentityData();
        root.identity.name = "car";
        root.gameObject = new GameObjectAsset.GameObjectData();
        asset.entities.add(root);
        var member = new GameObjectAsset.GameObjectEntityData();
        member.sourceEntityId = 2;
        member.parentSourceEntityId = 1;
        member.transform = new GameObjectAsset.TransformData();
        member.transform.scaleX = member.transform.scaleY = 1f;
        member.entityIndex = new GameObjectAsset.EntityIndexData();
        member.meta = new GameObjectAsset.MetaData();
        member.meta.kind = "SPRITE";
        member.physicsBody = new GameObjectAsset.PhysicsBodyData();
        member.physicsBody.type = games.pixscape.runtime.component.physics.PhysicsBodyComponent.DYNAMIC;
        var shape = new GameObjectAsset.PhysicsShapeData();
        shape.localShapeId = 1;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = 0.5f;
        member.physicsShapes.add(shape);
        asset.entities.add(member);
        return asset;
    }
}
