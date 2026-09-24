package games.pixscape.studio.document;

import games.pixscape.studio.scene.SceneEditorContext;

import java.util.Objects;

/** One open Scene tab and the live editor context whose lifecycle it owns. */
public final class SceneEditorDocument extends OpenEditorDocument implements AutoCloseable {
    private final SceneEditorContext context;
    private boolean closed;

    public SceneEditorDocument(String canonicalSceneId,
                               String title,
                               SceneEditorContext context) {
        super(new EditorDocumentKey(EditorDocumentType.SCENE, canonicalSceneId), title);
        this.context = Objects.requireNonNull(context, "context");
    }

    public SceneEditorContext context() { return context; }
    public boolean isDirty() { return !closed && context.isDirty(); }
    public boolean isClosed() { return closed; }

    @Override public boolean closeable() { return true; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        context.dispose();
    }
}
