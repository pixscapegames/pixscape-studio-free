package games.pixscape.studio.document;

import java.util.Objects;

/** Minimal open-view state shared by Studio editor documents. */
public abstract class OpenEditorDocument {
    private final EditorDocumentKey key;
    private String title;

    protected OpenEditorDocument(EditorDocumentKey key, String title) {
        this.key = Objects.requireNonNull(key, "key");
        setTitle(title);
    }

    public final EditorDocumentKey key() { return key; }
    public final EditorDocumentType type() { return key.type(); }
    public final String title() { return title; }
    public final void setTitle(String title) {
        String next = Objects.requireNonNull(title, "title").trim();
        if (next.isEmpty()) throw new IllegalArgumentException("Document title is required.");
        this.title = next;
    }
    public boolean isDirty() { return false; }
    public abstract boolean closeable();
}
