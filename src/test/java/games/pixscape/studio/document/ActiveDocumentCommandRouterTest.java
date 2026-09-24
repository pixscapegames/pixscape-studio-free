package games.pixscape.studio.document;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class ActiveDocumentCommandRouterTest {
    @Test
    public void undoRedoAndSaveFollowExactActiveSceneOrHud() {
        EditorDocumentManager manager = new EditorDocumentManager();
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hudA = manager.openHudScreen("hud/a", "A HUD");
        HudScreenEditorDocument hudB = manager.openHudScreen("hud/b", "B HUD");
        edit(hudA, 10f);
        edit(hudB, 20f);
        AtomicReference<SceneEditorDocument> sceneUndo = new AtomicReference<>();
        AtomicReference<SceneEditorDocument> sceneSave = new AtomicReference<>();
        AtomicReference<HudScreenEditorDocument> hudSave = new AtomicReference<>();
        ActiveDocumentCommandRouter router = new ActiveDocumentCommandRouter(
                manager, new ActiveDocumentCommandRouter.SceneCommands() {
            @Override public void save(SceneEditorDocument document, Runnable onSuccess,
                                       Consumer<Throwable> onFailure) {
                sceneSave.set(document);
                onSuccess.run();
            }
            @Override public boolean undo(SceneEditorDocument document) {
                sceneUndo.set(document);
                return true;
            }
            @Override public boolean redo(SceneEditorDocument document) { return true; }
        }, document -> {
            hudSave.set(document);
            document.editSession().markSaved();
        }, unexpectedGameObjectCommands());

        assertTrue(router.undo());
        assertEquals(0f, hudB.document().root.actor.width, 0f);
        assertEquals(10f, hudA.document().root.actor.width, 0f);
        router.save(null, failOnError());
        assertSame(hudB, hudSave.get());
        assertFalse(hudB.isDirty());
        assertTrue(hudA.isDirty());

        manager.activate(hudA.key());
        assertTrue(router.undo());
        assertEquals(0f, hudA.document().root.actor.width, 0f);
        assertTrue(router.redo());
        assertEquals(10f, hudA.document().root.actor.width, 0f);

        manager.activate(scene.key());
        assertTrue(router.undo());
        assertSame(scene, sceneUndo.get());
        router.save(() -> {}, failOnError());
        assertSame(scene, sceneSave.get());
    }

    @Test
    public void undoRedoAndSaveFollowTheActiveGameObjectDocument() {
        EditorDocumentManager manager = new EditorDocumentManager();
        GameObjectEditorDocument gameObject = new GameObjectEditorDocument(
                "enemy", "enemy", new FileHandle("build/test-gameobjects/enemy.gameobject"),
                context("game-object"), 1);
        manager.openGameObject(gameObject);
        AtomicReference<GameObjectEditorDocument> saved = new AtomicReference<>();
        AtomicReference<GameObjectEditorDocument> undone = new AtomicReference<>();
        AtomicReference<GameObjectEditorDocument> redone = new AtomicReference<>();

        ActiveDocumentCommandRouter router = new ActiveDocumentCommandRouter(
                manager, noOpSceneCommands(), document -> { },
                new ActiveDocumentCommandRouter.GameObjectCommands() {
                    @Override public void save(GameObjectEditorDocument document) { saved.set(document); }
                    @Override public boolean undo(GameObjectEditorDocument document) {
                        undone.set(document);
                        return true;
                    }
                    @Override public boolean redo(GameObjectEditorDocument document) {
                        redone.set(document);
                        return true;
                    }
                });

        router.save(null, failOnError());
        assertTrue(router.undo());
        assertTrue(router.redo());
        assertSame(gameObject, saved.get());
        assertSame(gameObject, undone.get());
        assertSame(gameObject, redone.get());
    }

    @Test
    public void failedGameObjectSaveReportsFailureAndLeavesTheDocumentOpen() {
        EditorDocumentManager manager = new EditorDocumentManager();
        GameObjectEditorDocument gameObject = new GameObjectEditorDocument(
                "enemy", "enemy", new FileHandle("build/test-gameobjects/enemy.gameobject"),
                context("game-object"), 1);
        manager.openGameObject(gameObject);
        RuntimeException failure = new RuntimeException("write failed");
        ActiveDocumentCommandRouter router = new ActiveDocumentCommandRouter(
                manager, noOpSceneCommands(), document -> { }, new ActiveDocumentCommandRouter.GameObjectCommands() {
                    @Override public void save(GameObjectEditorDocument document) { throw failure; }
                    @Override public boolean undo(GameObjectEditorDocument document) { return false; }
                    @Override public boolean redo(GameObjectEditorDocument document) { return false; }
                });
        AtomicReference<Throwable> reported = new AtomicReference<>();

        router.save(null, reported::set);

        assertSame(failure, reported.get());
        assertSame(gameObject, manager.find(gameObject.key()));
        assertSame(gameObject, manager.activeDocument());
    }

    private static ActiveDocumentCommandRouter.SceneCommands noOpSceneCommands() {
        return new ActiveDocumentCommandRouter.SceneCommands() {
            @Override public void save(SceneEditorDocument document, Runnable onSuccess,
                                       Consumer<Throwable> onFailure) { }
            @Override public boolean undo(SceneEditorDocument document) { return false; }
            @Override public boolean redo(SceneEditorDocument document) { return false; }
        };
    }

    private static ActiveDocumentCommandRouter.GameObjectCommands unexpectedGameObjectCommands() {
        return new ActiveDocumentCommandRouter.GameObjectCommands() {
            @Override public void save(GameObjectEditorDocument document) {
                throw new AssertionError("Unexpected Game Object save.");
            }
            @Override public boolean undo(GameObjectEditorDocument document) {
                throw new AssertionError("Unexpected Game Object undo.");
            }
            @Override public boolean redo(GameObjectEditorDocument document) {
                throw new AssertionError("Unexpected Game Object redo.");
            }
        };
    }

    private static Consumer<Throwable> failOnError() {
        return failure -> fail(failure.getMessage());
    }

    private static void edit(HudScreenEditorDocument document, float width) {
        document.editSession().edit("Resize root", candidate -> {
            candidate.root.actor.width = width;
            return candidate;
        });
    }

    private static SceneEditorContext context(String identity) {
        return new SceneEditorContext(identity, new StudioEditingModeService());
    }
}
