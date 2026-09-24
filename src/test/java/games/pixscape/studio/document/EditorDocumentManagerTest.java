package games.pixscape.studio.document;

import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class EditorDocumentManagerTest {
    @Test
    public void sceneAndDistinctHudDocumentsHaveStableOrderAndDuplicateOpenActivates() {
        EditorDocumentManager manager = new EditorDocumentManager();
        SceneEditorDocument scene = manager.registerMaterializedScene("scene0", "World", context());
        HudScreenEditorDocument first = manager.openHudScreen("hud/main", "main");
        HudScreenEditorDocument second = manager.openHudScreen("hud/pause", "pause");

        assertEquals(List.of(scene, first, second), manager.documents());
        assertSame(second, manager.activeDocument());

        HudScreenEditorDocument reopened = manager.openHudScreen("hud/main", "ignored title");
        assertSame(first, reopened);
        assertSame(first, manager.activeDocument());
        assertEquals(3, manager.documents().size());
    }

    @Test
    public void closingDocumentsUsesRecentFallbackAndCleanSceneDisposesItsContext() {
        EditorDocumentManager manager = new EditorDocumentManager();
        SceneEditorDocument scene = manager.registerMaterializedScene("scene0", "World", context());
        HudScreenEditorDocument first = manager.openHudScreen("hud/main", "main");
        HudScreenEditorDocument second = manager.openHudScreen("hud/pause", "pause");
        manager.activate(first.key());
        manager.activate(second.key());

        assertTrue(manager.requestClose(second.key()));
        assertSame(first, manager.activeDocument());
        assertNull(manager.find(second.key()));
        assertTrue(manager.requestClose(scene.key()));
        assertTrue(scene.context().isDisposed());
        assertSame(first, manager.activeDocument());
    }

    @Test
    public void multipleSceneDocumentsKeepDistinctLiveContextsAndReopenActivatesExisting() {
        EditorDocumentManager manager = new EditorDocumentManager();
        SceneEditorContext firstContext = context("scene0");
        SceneEditorContext secondContext = context("scene1");
        SceneEditorDocument oldScene = manager.openScene("scene0", "First", firstContext);
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "main");

        SceneEditorDocument newScene = manager.openScene("scene1", "Second", secondContext);

        assertSame(oldScene, manager.find(oldScene.key()));
        assertEquals(List.of(oldScene, hud, newScene), manager.documents());
        assertSame(newScene, manager.activeDocument());
        assertNotSame(firstContext, secondContext);
        assertEquals(2, manager.documents().stream()
                .filter(document -> document.type() == EditorDocumentType.SCENE).count());

        assertSame(oldScene, manager.openScene("scene0", "First", firstContext));
        assertSame(oldScene, manager.activeDocument());
        assertEquals(3, manager.documents().size());
    }

    @Test
    public void listenerOrderingIsOpenThenActivateAndCloseThenFallbackActivate() {
        EditorDocumentManager manager = new EditorDocumentManager();
        List<String> events = new ArrayList<>();
        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentOpened(OpenEditorDocument document) {
                events.add("open:" + document.key().domainId());
            }
            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                events.add("active:" + (current == null ? "none" : current.key().domainId()));
            }
            @Override public void documentClosed(OpenEditorDocument document) {
                events.add("close:" + document.key().domainId());
            }
        });

        manager.registerMaterializedScene("scene0", "World", context());
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "main");
        manager.requestClose(hud.key());

        assertEquals(List.of("open:scene0", "active:scene0", "open:hud/main", "active:hud/main",
                "close:hud/main", "active:scene0"), events);
    }

    @Test
    public void dirtySceneCloseWaitsForSaveDiscardOrCancelDecision() {
        EditorDocumentManager manager = new EditorDocumentManager();
        SceneEditorContext context = context();
        context.markExplicitSaveRequired();
        SceneEditorDocument scene = manager.openScene("scene0", "World", context);
        AtomicReference<SceneEditorDocument> pending = new AtomicReference<>();
        manager.setDirtySceneCloseHandler(pending::set);

        assertFalse(manager.requestClose(scene.key()));
        assertSame(scene, pending.get());
        assertSame(scene, manager.find(scene.key()));
        assertFalse(context.isDisposed());

        // CANCEL is represented by doing nothing; DISCARD/SAVE authorize closeNow.
        assertTrue(manager.closeNow(scene.key()));
        assertNull(manager.find(scene.key()));
        assertTrue(context.isDisposed());
    }

    @Test
    public void dirtyHudCloseTargetsExactDocumentAndWaitsForDecision() {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument first = manager.openHudScreen("hud/first", "First");
        HudScreenEditorDocument second = manager.openHudScreen("hud/second", "Second");
        first.editSession().edit("Resize root", candidate -> { candidate.root.actor.width = 1f; return candidate; });
        AtomicReference<HudScreenEditorDocument> pending = new AtomicReference<>();
        manager.setDirtyHudCloseHandler(pending::set);
        assertFalse(manager.requestClose(first.key()));
        assertSame(first, pending.get());
        assertSame(first, manager.find(first.key()));
        assertSame(second, manager.activeDocument());
        assertTrue(manager.closeNow(first.key()));
        assertNull(manager.find(first.key()));
        assertSame(second, manager.activeDocument());
    }

    @Test
    public void dirtyHudSaveFailureOrCancelLeavesDocumentAndHistoryAlive() {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        hud.editSession().edit("Resize root", candidate -> { candidate.root.actor.width = 1f; return candidate; });
        AtomicReference<HudScreenEditorDocument> pending = new AtomicReference<>();
        manager.setDirtyHudCloseHandler(pending::set);

        assertFalse(manager.requestClose(hud.key()));
        assertSame(hud, pending.get());
        // A failed save and Cancel both deliberately omit closeNow.
        assertSame(hud, manager.find(hud.key()));
        assertTrue(hud.isDirty());
        assertTrue(hud.editSession().canUndo());
    }

    @Test
    public void teardownClearsAndDisposesWithoutActivationOrUiCallbacks() {
        EditorDocumentManager manager = new EditorDocumentManager();
        List<String> events = new ArrayList<>();
        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                events.add("activate");
            }
            @Override public void documentClosed(OpenEditorDocument document) {
                events.add("close");
            }
        });
        SceneEditorContext first = context("scene0");
        SceneEditorContext second = context("scene1");
        manager.openScene("scene0", "First", first);
        manager.openScene("scene1", "Second", second);
        manager.openHudScreen("hud/main", "HUD");
        int eventsBeforeTeardown = events.size();

        manager.clearForTeardown();

        assertTrue(manager.documents().isEmpty());
        assertNull(manager.activeDocument());
        assertNull(manager.activeKey());
        assertEquals(eventsBeforeTeardown, events.size());
        assertTrue(first.isDisposed());
        assertTrue(second.isDisposed());
        assertEquals(1, first.disposeCount());
        assertEquals(1, second.disposeCount());

        manager.clearForTeardown();
        assertEquals(1, first.disposeCount());
        assertEquals(1, second.disposeCount());
    }

    private static SceneEditorContext context() {
        return context("scene0");
    }

    private static SceneEditorContext context(String identity) {
        return new SceneEditorContext(identity, new StudioEditingModeService());
    }
}
