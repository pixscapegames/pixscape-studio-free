package games.pixscape.studio.document;

import games.pixscape.studio.scene.SceneEditorContext;

/** A document that owns an independently materialized editor World. */
public interface ContextEditorDocument extends AutoCloseable {
    SceneEditorContext context();
    boolean isClosed();

    default boolean ownsContext(SceneEditorContext candidate) {
        return !isClosed() && context() == candidate;
    }

    @Override void close();
}
