package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxTileDefinitionPlan(int localTileId,
                                    String className,
                                    String legacyType,
                                    PropertySet properties,
                                    List<List<String>> propertyPaths) {
    public TmxTileDefinitionPlan {
        properties = properties.copy();
        propertyPaths = propertyPaths.stream().map(List::copyOf).toList();
    }
}
