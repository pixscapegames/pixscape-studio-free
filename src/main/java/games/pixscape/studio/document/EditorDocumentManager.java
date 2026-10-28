package games.pixscape.studio.document;

import games.pixscape.studio.scene.SceneEditorContext;

import java.util.*;

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
    @FunctionalInterface
    public interface DirtyGameObjectCloseHandler {
        void requestClose(GameObjectEditorDocument document);
    }

    public interface Listener {
        default void documentOpened(OpenEditorDocument document) {}
        default void validateActivation(OpenEditorDocument current) {}
        default void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {}
        /** Explicit compensation, including partial work by the failing listener. */
        default void activationFailed(OpenEditorDocument previous, OpenEditorDocument attempted) {}
        default void documentClosed(OpenEditorDocument document) {}
        default void documentTitleChanged(OpenEditorDocument document) {}
    }

    private final Map<EditorDocumentKey, OpenEditorDocument> documents = new LinkedHashMap<>();
    private final List<EditorDocumentKey> activationHistory = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private OpenEditorDocument activeDocument;
    private DirtySceneCloseHandler dirtySceneCloseHandler;
    private DirtyHudCloseHandler dirtyHudCloseHandler;
    private DirtyGameObjectCloseHandler dirtyGameObjectCloseHandler;

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

    public void setDirtyGameObjectCloseHandler(DirtyGameObjectCloseHandler handler) {
        dirtyGameObjectCloseHandler = handler;
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

    /** Cold load/close boundary: every context-owning document participates in ownership. */
    public boolean ownsContext(SceneEditorContext context) {
        if (context == null) return false;
        return documents.values().stream().anyMatch(document ->
                document instanceof ContextEditorDocument owner && owner.ownsContext(context));
    }

    public OpenEditorDocument open(OpenEditorDocument requested) {
        Objects.requireNonNull(requested, "requested");
        if (requested.type() == EditorDocumentType.SCENE) {
            throw new IllegalArgumentException("Use openScene for Scene documents.");
        }
        OpenEditorDocument existing = documents.get(requested.key());
        if (existing != null) {
            if (existing != requested) disposeDocument(requested);
            activate(existing.key());
            return existing;
        }
        publishOpened(requested);
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
        publishOpened(requested);
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

    public GameObjectEditorDocument openGameObject(GameObjectEditorDocument document) {
        return (GameObjectEditorDocument) open(document);
    }

    public boolean activate(EditorDocumentKey key) {
        OpenEditorDocument next = documents.get(key);
        if (next == null) return false;
        validateContext(next);
        if (activeDocument == next) return true;
        transitionTo(next);
        return true;
    }

    private void transitionTo(OpenEditorDocument next) {
        validateContext(next);
        List<Listener> observers = List.copyOf(listeners);
        for (Listener listener : observers) listener.validateActivation(next);
        OpenEditorDocument previous = activeDocument;
        List<EditorDocumentKey> previousHistory = List.copyOf(activationHistory);
        activeDocument = next;
        if (next != null) {
            activationHistory.remove(next.key());
            activationHistory.add(next.key());
        }
        int attempted = 0;
        try {
            for (Listener listener : observers) {
                attempted++;
                listener.documentActivated(previous, next);
            }
        } catch (RuntimeException failure) {
            activeDocument = previous;
            activationHistory.clear();
            activationHistory.addAll(previousHistory);
            for (int i = attempted - 1; i >= 0; i--) {
                try { observers.get(i).activationFailed(previous, next); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }

    private static void validateContext(OpenEditorDocument next) {
        if (next instanceof ContextEditorDocument owner
                && (owner.isClosed() || owner.context().isDisposed())) {
            throw new IllegalStateException("Cannot activate a closed editor context.");
        }
    }

    /** Registration transfers ownership; unsuccessful publication releases the new document. */
    private void publishOpened(OpenEditorDocument requested) {
        try {
            validateContext(requested);
            for (Listener listener : List.copyOf(listeners)) listener.validateActivation(requested);
            documents.put(requested.key(), requested);
            if (requested instanceof ContextEditorDocument owner) {
                owner.context().setDirtyStateListener(() -> notifyTitleChanged(requested));
            }
            if (requested instanceof HudScreenEditorDocument hud) {
                hud.editSession().addListener(() -> notifyTitleChanged(hud));
            }
            notifyOpened(requested);
            activate(requested.key());
        } catch (RuntimeException failure) {
            documents.remove(requested.key());
            activationHistory.remove(requested.key());
            for (Listener listener : List.copyOf(listeners)) {
                try { listener.documentClosed(requested); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { disposeDocument(requested); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
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
        if (document instanceof GameObjectEditorDocument gameObject && gameObject.isDirty()) {
            if (dirtyGameObjectCloseHandler != null) dirtyGameObjectCloseHandler.requestClose(gameObject);
            return false;
        }
        return closeNow(key);
    }

    /** Completes an already-authorized close (clean, saved, or explicitly discarded). */
    public boolean closeNow(EditorDocumentKey key) {
        OpenEditorDocument document = documents.get(key);
        if (document == null || !document.closeable()) return false;
        boolean wasActive = document == activeDocument;
        if (wasActive) activateFallback(document);
        documents.remove(key);
        activationHistory.remove(key);
        try { notifyClosed(document); }
        finally { disposeDocument(document); }
        return true;
    }

    public void clear() {
        if (documents.isEmpty() && activeDocument == null) return;
        List<OpenEditorDocument> closing = new ArrayList<>(documents.values());
        transitionTo(null);
        documents.clear();
        activationHistory.clear();
        RuntimeException failure = null;
        for (OpenEditorDocument document : closing) {
            try { notifyClosed(document); }
            catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            } finally { disposeDocument(document); }
        }
        if (failure != null) throw failure;
    }

    /** Final teardown path: no fallback/UI callbacks may run while services are being disposed. */
    public void clearForTeardown() {
        List<OpenEditorDocument> closing = new ArrayList<>(documents.values());
        documents.clear();
        activationHistory.clear();
        activeDocument = null;
        dirtySceneCloseHandler = null;
        dirtyHudCloseHandler = null;
        dirtyGameObjectCloseHandler = null;
        listeners.clear();
        for (OpenEditorDocument document : closing) disposeDocument(document);
    }

    private void activateFallback(OpenEditorDocument previous) {
        OpenEditorDocument fallback = null;
        for (int i = activationHistory.size() - 1; i >= 0 && fallback == null; i--) {
            OpenEditorDocument candidate = documents.get(activationHistory.get(i));
            if (candidate != previous) fallback = candidate;
        }
        if (fallback == null) fallback = documents.values().stream()
                .filter(document -> document != previous).findFirst().orElse(null);
        transitionTo(fallback);
    }

    private static void disposeDocument(OpenEditorDocument document) {
        if (document instanceof ContextEditorDocument owner) owner.close();
        if (document instanceof HudScreenEditorDocument hud) hud.close();
    }

    private void notifyOpened(OpenEditorDocument document) {
        for (Listener listener : List.copyOf(listeners)) listener.documentOpened(document);
    }
    private void notifyClosed(OpenEditorDocument document) {
        RuntimeException failure = null;
        for (Listener listener : List.copyOf(listeners)) {
            try { listener.documentClosed(document); }
            catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
    }
    private void notifyTitleChanged(OpenEditorDocument document) {
        for (Listener listener : List.copyOf(listeners)) listener.documentTitleChanged(document);
    }
}
