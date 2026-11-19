package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxMapInfo(String orientation,
                         int width,
                         int height,
                         int tileWidth,
                         int tileHeight,
                         boolean infinite,
                         PropertySet properties,
                         List<TmxObjectPropertyReference> objectPropertyReferences) {
    public TmxMapInfo {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
