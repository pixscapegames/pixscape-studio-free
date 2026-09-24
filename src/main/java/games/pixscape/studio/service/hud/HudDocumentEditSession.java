package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.hud.document.HudDocumentValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Per-document transaction, history, and save-baseline authority for one authored HUD.
 * Live preview resources remain owned by {@link HudEditorSession}.
 */
public final class HudDocumentEditSession implements AutoCloseable {
    public enum HistoryNavigation { UNDO, REDO }
    public record HistoryNavigationEvent(HistoryNavigation navigation,
                                         long fromRevision, long toRevision) {}
    @FunctionalInterface
    public interface Mutation {
        /** Receives an isolated current-format candidate and returns the candidate to publish. */
        HudDocumentV1 apply(HudDocumentV1 candidate);
    }

    @FunctionalInterface
    public interface ActivePreview {
        /** Validates current resources, materializes, and installs before model publication. */
        void installCandidate(HudScreenAsset candidateAsset, HudDocumentV1 candidate,
                              HudValidationResult structuralValidation);
    }

    /** Observes a successfully published authored document state, after history has advanced. */
    @FunctionalInterface
    public interface AuthoredPublicationListener {
        void authoredDocumentPublished(HudDocumentEditSession session);
    }

    /** Observes a completed undo/redo transition after its document has been published. */
    @FunctionalInterface
    public interface HistoryNavigationListener {
        void historyNavigated(HistoryNavigationEvent event);
    }

    private record State(long revision, String label, String serializedDocument, String skinId) {}

    private final HudDocumentCodec codec = new HudDocumentCodec();
    private final HudDocumentValidator validator = new HudDocumentValidator();
    private final HudScreenAsset asset;
    private final List<State> states = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<AuthoredPublicationListener> publicationListeners = new ArrayList<>();
    private final List<HistoryNavigationListener> historyNavigationListeners = new ArrayList<>();
    private HudDocumentV1 authoredDocument;
    private int cursor;
    private long nextRevision = 1L;
    private long savedRevision;
    private ActivePreview activePreview;
    private boolean closed;

    public HudDocumentEditSession(HudScreenAsset asset, HudDocumentV1 initialDocument) {
        this.asset = copyAsset(Objects.requireNonNull(asset, "asset"));
        this.asset.validate();
        HudDocumentV1 isolated = copy(Objects.requireNonNull(initialDocument, "initialDocument"));
        validateStructural(isolated);
        authoredDocument = isolated;
        states.add(new State(0L, "Initial", serialize(isolated), this.asset.skinId));
        savedRevision = 0L;
    }

    /** Returns defensive metadata so callers cannot mutate persisted state out of band. */
    public HudScreenAsset asset() { return copyAsset(asset); }

    /** Returns a defensive document snapshot; all changes must use {@link #edit(String, Mutation)}. */
    public HudDocumentV1 document() { return copy(authoredDocument); }

    public boolean isDirty() { return currentState().revision() != savedRevision; }
    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor + 1 < states.size(); }
    public int historySize() { return Math.max(0, states.size() - 1); }

    public void addListener(Runnable listener) {
        ensureOpen();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Adds an observer for authored edit/undo/redo publication only. */
    public void addAuthoredPublicationListener(AuthoredPublicationListener listener) {
        ensureOpen();
        publicationListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeAuthoredPublicationListener(AuthoredPublicationListener listener) {
        publicationListeners.remove(listener);
    }

    public void addHistoryNavigationListener(HistoryNavigationListener listener) {
        ensureOpen();
        historyNavigationListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeHistoryNavigationListener(HistoryNavigationListener listener) {
        historyNavigationListeners.remove(listener);
    }

    /** Binds the one active authoring preview. Inactive documents have no binding or GL ownership. */
    public void bindActivePreview(ActivePreview preview) {
        ensureOpen();
        activePreview = Objects.requireNonNull(preview, "preview");
    }

    public void unbindActivePreview(ActivePreview preview) {
        if (activePreview == preview) activePreview = null;
    }

    public void edit(String label, Mutation mutation) {
        edit(label, mutation, null);
    }

    /**
     * Applies one isolated logical edit. A supplied catalog adds Runtime resource-aware validation;
     * the active authoring preview materializes its own candidate before publication.
     */
    public void edit(String label, Mutation mutation, HudResourceCatalog resources) {
        ensureOpen();
        if (label == null || label.isBlank()) throw new IllegalArgumentException("HUD edit label is required.");
        Objects.requireNonNull(mutation, "mutation");
        HudDocumentV1 candidate;
        try {
            candidate = mutation.apply(copy(authoredDocument));
        } catch (HudEditRejectedException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new HudEditRejectedException("HUD edit '" + label + "' failed.", failure);
        }
        if (candidate == null) {
            throw new HudEditRejectedException("HUD edit '" + label + "' must return a document.");
        }
        candidate = copy(candidate);
        HudValidationResult structural = validateStructural(candidate);
        if (resources != null) validateResources(candidate, resources);

        String serialized = serialize(candidate);
        if (Objects.equals(serialized, currentState().serializedDocument())) return;

        installPreview(copyAsset(asset), candidate, structural);
        while (states.size() > cursor + 1) states.remove(states.size() - 1);
        State next = new State(nextRevision++, label.trim(), serialized, asset.skinId);
        states.add(next);
        cursor = states.size() - 1;
        authoredDocument = candidate;
        notifyListeners();
        notifyAuthoredPublicationListeners();
    }

    /** Changes the HUD Screen Skin through the same atomic preview and history boundary. */
    public void editSkin(String label, String skinId) {
        ensureOpen();
        if (label == null || label.isBlank()) throw new IllegalArgumentException("HUD edit label is required.");
        HudScreenAsset candidateAsset = copyAsset(asset);
        candidateAsset.skinId = skinId;
        try {
            candidateAsset.validate();
        } catch (IllegalArgumentException failure) {
            throw new HudEditRejectedException("The HUD Screen Skin reference is invalid: "
                    + message(failure), failure);
        }
        if (Objects.equals(candidateAsset.skinId, asset.skinId)) return;

        HudDocumentV1 candidateDocument = copy(authoredDocument);
        HudValidationResult structural = validateStructural(candidateDocument);
        installPreview(candidateAsset, candidateDocument, structural);

        while (states.size() > cursor + 1) states.remove(states.size() - 1);
        State next = new State(nextRevision++, label.trim(), serialize(candidateDocument),
                candidateAsset.skinId);
        states.add(next);
        cursor = states.size() - 1;
        asset.skinId = candidateAsset.skinId;
        authoredDocument = candidateDocument;
        notifyListeners();
        notifyAuthoredPublicationListeners();
    }

    public boolean undo() {
        ensureOpen();
        if (!canUndo()) return false;
        return moveTo(cursor - 1, HistoryNavigation.UNDO);
    }

    public boolean redo() {
        ensureOpen();
        if (!canRedo()) return false;
        return moveTo(cursor + 1, HistoryNavigation.REDO);
    }

    /** Stable only within this edit session; useful for UI state coupled to one authored revision. */
    public long currentRevision() {
        ensureOpen();
        return currentState().revision();
    }

    /** Advances the baseline only after persistence has succeeded. */
    public void markSaved() {
        ensureOpen();
        long previous = savedRevision;
        savedRevision = currentState().revision();
        if (previous != savedRevision) notifyListeners();
    }

    private boolean moveTo(int nextCursor, HistoryNavigation navigation) {
        long previousRevision = currentState().revision();
        State nextState = states.get(nextCursor);
        HudDocumentV1 candidate = deserialize(nextState.serializedDocument());
        HudScreenAsset candidateAsset = copyAsset(asset);
        candidateAsset.skinId = nextState.skinId();
        HudValidationResult structural = validateStructural(candidate);
        installPreview(candidateAsset, candidate, structural);
        cursor = nextCursor;
        asset.skinId = candidateAsset.skinId;
        authoredDocument = candidate;
        notifyListeners();
        notifyAuthoredPublicationListeners();
        notifyHistoryNavigation(new HistoryNavigationEvent(navigation, previousRevision,
                currentState().revision()));
        return true;
    }

    private void installPreview(HudScreenAsset candidateAsset, HudDocumentV1 candidate,
                                HudValidationResult structural) {
        if (activePreview == null) return;
        try {
            activePreview.installCandidate(copyAsset(candidateAsset), copy(candidate), structural);
        } catch (HudEditRejectedException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new HudEditRejectedException("HUD preview rejected the candidate: "
                    + message(failure), failure);
        }
    }

    private HudValidationResult validateStructural(HudDocumentV1 candidate) {
        HudValidationResult result = validator.validate(candidate);
        if (!result.isValid()) throw new HudEditRejectedException(validationMessage(result));
        return result;
    }

    private void validateResources(HudDocumentV1 candidate, HudResourceCatalog resources) {
        HudValidationResult result = validator.validate(candidate, resources);
        if (!result.isValid()) throw new HudEditRejectedException(validationMessage(result));
    }

    private State currentState() { return states.get(cursor); }
    private String serialize(HudDocumentV1 document) { return codec.write(document); }
    private HudDocumentV1 deserialize(String serialized) {
        return codec.read(serialized);
    }
    private HudDocumentV1 copy(HudDocumentV1 document) {
        return codec.read(codec.write(document));
    }

    private static HudScreenAsset copyAsset(HudScreenAsset source) {
        HudScreenAsset copy = new HudScreenAsset();
        copy.schemaVersion = source.schemaVersion;
        copy.documentId = source.documentId;
        copy.skinId = source.skinId;
        copy.atlasId = source.atlasId;
        copy.textureProfileId = source.textureProfileId;
        return copy;
    }

    private static String validationMessage(HudValidationResult result) {
        StringBuilder out = new StringBuilder("HUD candidate validation failed:");
        result.issues().forEach(issue -> out.append("\n").append(issue.code())
                .append(" at ").append(issue.path()).append(": ").append(issue.message()));
        return out.toString();
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                if (com.badlogic.gdx.Gdx.app != null)
                    com.badlogic.gdx.Gdx.app.error("HudDocumentEditSession", "HUD edit observer failed after publication", failure);
            }
        }
    }
    private void notifyAuthoredPublicationListeners() {
        for (AuthoredPublicationListener listener : List.copyOf(publicationListeners)) {
            try { listener.authoredDocumentPublished(this); }
            catch (RuntimeException failure) {
                if (com.badlogic.gdx.Gdx.app != null)
                    com.badlogic.gdx.Gdx.app.error("HudDocumentEditSession",
                            "HUD authored-publication observer failed after publication", failure);
            }
        }
    }
    private void notifyHistoryNavigation(HistoryNavigationEvent event) {
        for (HistoryNavigationListener listener : List.copyOf(historyNavigationListeners)) {
            try { listener.historyNavigated(event); }
            catch (RuntimeException failure) {
                if (com.badlogic.gdx.Gdx.app != null)
                    com.badlogic.gdx.Gdx.app.error("HudDocumentEditSession",
                            "HUD history-navigation observer failed after publication", failure);
            }
        }
    }
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("HUD edit session is closed.");
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        activePreview = null;
        listeners.clear();
        publicationListeners.clear();
        historyNavigationListeners.clear();
        states.clear();
        authoredDocument = null;
    }
}
