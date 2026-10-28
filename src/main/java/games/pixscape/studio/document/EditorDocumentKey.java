package games.pixscape.studio.document;

import java.util.Objects;

/** Stable editor identity. Display titles are deliberately not part of document identity. */
public record EditorDocumentKey(EditorDocumentType type, String domainId) {
    public EditorDocumentKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(domainId, "domainId");
        domainId = domainId.trim();
        if (domainId.isEmpty()) throw new IllegalArgumentException("Document domain ID is required.");
    }
}
