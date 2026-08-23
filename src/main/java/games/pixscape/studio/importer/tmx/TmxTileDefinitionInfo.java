package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxTileDefinitionInfo(int localTileId,
                                    String className,
                                    String legacyType,
                                    PropertySet properties,
                                    List<List<String>> propertyPaths) {
    public TmxTileDefinitionInfo {
        properties = properties.copy();
        propertyPaths = propertyPaths.stream().map(List::copyOf).toList();
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }

    PropertySet propertiesForPlanning() {
        return properties;
    }
}
