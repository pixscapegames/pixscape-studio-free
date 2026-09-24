package games.pixscape.studio.document;

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
        });

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
