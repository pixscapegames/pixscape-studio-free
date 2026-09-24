package games.pixscape.studio.document;

import games.pixscape.studio.scene.SceneEditorContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Sole Studio authority for ordered open documents and the active editor document. */
public final class EditorDocumentManager {
    @FunctionalInterface
    public interface DirtySceneCloseHandler {
        void requestClose(SceneEditorDocument document);
    }
    @FunctionalInterface
    public interface DirtyHudCloseHandler {
        void requestClose(HudScreenEditorDocument document);
    }

    public interface Listener {
        default void documentOpened(OpenEditorDocument document) {}
        default void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {}
        default void documentClosed(OpenEditorDocument document) {}
        default void documentTitleChanged(OpenEditorDocument document) {}
    }

    private final Map<EditorDocumentKey, OpenEditorDocument> documents = new LinkedHashMap<>();
    private final List<EditorDocumentKey> activationHistory = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private OpenEditorDocument activeDocument;
    private DirtySceneCloseHandler dirtySceneCloseHandler;
    private DirtyHudCloseHandler dirtyHudCloseHandler;

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void setDirtySceneCloseHandler(DirtySceneCloseHandler handler) {
        dirtySceneCloseHandler = handler;
    }

    public void setDirtyHudCloseHandler(DirtyHudCloseHandler handler) {
        dirtyHudCloseHandler = handler;
    }

    public List<OpenEditorDocument> documents() {
        return Collections.unmodifiableList(new ArrayList<>(documents.values()));
    }

    public OpenEditorDocument activeDocument() { return activeDocument; }
    public EditorDocumentKey activeKey() { return activeDocument != null ? activeDocument.key() : null; }
    public boolean isActive(EditorDocumentType type) {
        return activeDocument != null && activeDocument.type() == type;
    }

    public OpenEditorDocument find(EditorDocumentKey key) { return documents.get(key); }

    public OpenEditorDocument open(OpenEditorDocument requested) {
        Objects.requireNonNull(requested, "requested");
        if (requested.type() == EditorDocumentType.SCENE) {
            throw new IllegalArgumentException("Use openScene for Scene documents.");
        }
        OpenEditorDocument existing = documents.get(requested.key());
        if (existing != null) {
            if (requested instanceof HudScreenEditorDocument hud) hud.close();
            activate(existing.key());
            return existing;
        }
        documents.put(requested.key(), requested);
        if (requested instanceof HudScreenEditorDocument hud) {
            hud.editSession().addListener(() -> notifyTitleChanged(hud));
        }
        notifyOpened(requested);
        activate(requested.key());
        return requested;
    }

    /** Opens one independently materialized Scene context or activates the existing document. */
    public SceneEditorDocument openScene(String canonicalSceneId,
                                         String title,
                                         SceneEditorContext context) {
        SceneEditorDocument requested = new SceneEditorDocument(canonicalSceneId, title, context);
        OpenEditorDocument same = documents.get(requested.key());
        if (same instanceof SceneEditorDocument existing) {
            if (existing.context() != context) {
                throw new IllegalStateException(
                        "The materialized Scene is already bound to another editor context.");
            }
            if (!existing.title().equals(title)) {
                existing.setTitle(title);
                notifyTitleChanged(existing);
            }
            activate(existing.key());
            return existing;
        }
        documents.put(requested.key(), requested);
        requested.context().setDirtyStateListener(() -> notifyTitleChanged(requested));
        notifyOpened(requested);
        activate(requested.key());
        return requested;
    }

    /** Compatibility name retained for callers compiled against STEP 5.6A. */
    public SceneEditorDocument registerMaterializedScene(String canonicalSceneId,
                                                         String title,
                                                         SceneEditorContext context) {
        return openScene(canonicalSceneId, title, context);
    }

    public HudScreenEditorDocument openHudScreen(String screenId, String title) {
        return (HudScreenEditorDocument) open(new HudScreenEditorDocument(screenId, title));
    }

    public HudScreenEditorDocument openHudScreen(HudScreenEditorDocument document) {
        return (HudScreenEditorDocument) open(document);
    }

    public boolean activate(EditorDocumentKey key) {
        OpenEditorDocument next = documents.get(key);
        if (next == null) return false;
        if (activeDocument == next) return true;
        OpenEditorDocument previous = activeDocument;
        activeDocument = next;
        activationHistory.remove(key);
        activationHistory.add(key);
        for (Listener listener : List.copyOf(listeners)) listener.documentActivated(previous, next);
        return true;
    }

    public boolean updateTitle(EditorDocumentKey key, String title) {
        OpenEditorDocument document = documents.get(key);
        if (document == null) return false;
        document.setTitle(title);
        notifyTitleChanged(document);
        return true;
    }

    public boolean activateMaterializedScene() {
        for (OpenEditorDocument document : documents.values()) {
            if (document.type() == EditorDocumentType.SCENE) return activate(document.key());
        }
        return false;
    }

    public boolean requestClose(EditorDocumentKey key) {
        OpenEditorDocument document = documents.get(key);
        if (document == null || !document.closeable()) return false;
        if (document instanceof SceneEditorDocument scene && scene.isDirty()) {
            if (dirtySceneCloseHandler != null) dirtySceneCloseHandler.requestClose(scene);
            return false;
        }
        if (document instanceof HudScreenEditorDocument hud && hud.isDirty()) {
            if (dirtyHudCloseHandler != null) dirtyHudCloseHandler.requestClose(hud);
            return false;
        }
        return closeNow(key);
    }

    /** Completes an already-authorized close (clean, saved, or explicitly discarded). */
    public boolean closeNow(EditorDocumentKey key) {
        OpenEditorDocument document = documents.get(key);
        if (document == null || !document.closeable()) return false;
        boolean wasActive = document == activeDocument;
        documents.remove(key);
        activationHistory.remove(key);
        notifyClosed(document);
        if (wasActive) activateFallback(document);
        disposeDocument(document);
        return true;
    }

    public void clear() {
        if (documents.isEmpty() && activeDocument == null) return;
        OpenEditorDocument previous = activeDocument;
        List<OpenEditorDocument> closing = new ArrayList<>(documents.values());
        documents.clear();
        activationHistory.clear();
        activeDocument = null;
        for (OpenEditorDocument document : closing) notifyClosed(document);
        if (previous != null) {
            for (Listener listener : List.copyOf(listeners)) listener.documentActivated(previous, null);
        }
        for (OpenEditorDocument document : closing) disposeDocument(document);
    }

    /** Final teardown path: no fallback/UI callbacks may run while services are being disposed. */
    public void clearForTeardown() {
        List<OpenEditorDocument> closing = new ArrayList<>(documents.values());
        documents.clear();
        activationHistory.clear();
        activeDocument = null;
        dirtySceneCloseHandler = null;
        dirtyHudCloseHandler = null;
        listeners.clear();
        for (OpenEditorDocument document : closing) disposeDocument(document);
    }

    private void activateFallback(OpenEditorDocument previous) {
        OpenEditorDocument fallback = null;
        for (int i = activationHistory.size() - 1; i >= 0 && fallback == null; i--) {
            fallback = documents.get(activationHistory.get(i));
        }
        if (fallback == null && !documents.isEmpty()) fallback = documents.values().iterator().next();
        activeDocument = fallback;
        if (fallback != null) {
            activationHistory.remove(fallback.key());
            activationHistory.add(fallback.key());
        }
        for (Listener listener : List.copyOf(listeners)) listener.documentActivated(previous, fallback);
    }

    private static void disposeDocument(OpenEditorDocument document) {
        if (document instanceof SceneEditorDocument scene) scene.close();
        if (document instanceof HudScreenEditorDocument hud) hud.close();
    }

    private void notifyOpened(OpenEditorDocument document) {
        for (Listener listener : List.copyOf(listeners)) listener.documentOpened(document);
    }
    private void notifyClosed(OpenEditorDocument document) {
        for (Listener listener : List.copyOf(listeners)) listener.documentClosed(document);
    }
    private void notifyTitleChanged(OpenEditorDocument document) {
        for (Listener listener : List.copyOf(listeners)) listener.documentTitleChanged(document);
    }
}
