package games.pixscape.studio.service.property;

import games.pixscape.runtime.property.PropertySet;

/**
 * Recursively remaps OBJECT values while preserving every non-reference property value.
 */
public final class PropertyReferenceMapper {
    private PropertyReferenceMapper() {
    }

    public interface ObjectReferenceMapper {
        int map(int value);
    }

    public static PropertySet remap(PropertySet source, ObjectReferenceMapper objectReferenceMapper) {
        if (source == null) return null;
        if (objectReferenceMapper == null) {
            throw new IllegalArgumentException("OBJECT reference mapper is required.");
        }
        return source.remapObjectReferences(objectReferenceMapper::map);
    }
}
