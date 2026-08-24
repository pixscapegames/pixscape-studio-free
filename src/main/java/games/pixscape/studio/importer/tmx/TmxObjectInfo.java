package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

public record TmxObjectInfo(int id,
                            String name,
                            String className,
                            String legacyType,
                            float x,
                            float y,
                            float width,
                            float height,
                            float rotation,
                            boolean visible,
                            String template,
                            Long gid,
                            TmxObjectKind kind,
                            java.util.List<TmxObjectPoint> points,
                            PropertySet properties,
                            java.util.List<java.util.List<String>> propertyPaths,
                            java.util.List<TmxObjectPropertyReference> objectPropertyReferences) {

    public static final int NO_SOURCE_ID = -1;

    public TmxObjectInfo {
        properties = properties.copy();
        points = java.util.List.copyOf(points);
        propertyPaths = propertyPaths.stream().map(java.util.List::copyOf).toList();
        objectPropertyReferences = java.util.List.copyOf(objectPropertyReferences);
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }

    PropertySet propertiesForPlanning() {
        return properties;
    }

    public boolean hasPositiveSourceId() {
        return id > 0;
    }
}
