package games.pixscape.studio.importer.tmx;

import java.util.List;

/** Unresolved map-object-domain reference retained until imported entities have stable IDs. */
public record TmxObjectPropertyReference(List<String> path, int sourceObjectId, String location) {
    public TmxObjectPropertyReference {
        path = List.copyOf(path);
    }
}
