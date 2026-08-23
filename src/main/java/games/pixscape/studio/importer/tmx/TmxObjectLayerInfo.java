package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxObjectLayerInfo(String name,
                                 String originalName,
                                 boolean visible,
                                 float opacity,
                                 float offsetX,
                                 float offsetY,
                                 float parallaxX,
                                 float parallaxY,
                                 PropertySet properties,
                                 List<TmxObjectInfo> objects) implements TmxLayerInfo {

    public TmxObjectLayerInfo {
        properties = properties.copy();
        objects = List.copyOf(objects);
    }

    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.OBJECT;
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }

    PropertySet propertiesForPlanning() {
        return properties;
    }
}
