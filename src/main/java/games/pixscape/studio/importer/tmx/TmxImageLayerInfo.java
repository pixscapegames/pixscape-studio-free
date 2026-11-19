package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxImageLayerInfo(String name,
                                String originalName,
                                boolean visible,
                                float opacity,
                                float offsetX,
                                float offsetY,
                                float parallaxX,
                                float parallaxY,
                                float x,
                                float y,
                                boolean repeatX,
                                boolean repeatY,
                                String imageSource,
                                int imageWidth,
                                int imageHeight,
                                String resolvedImagePath,
                                boolean imageExists,
                                PropertySet properties,
                                List<TmxObjectPropertyReference> objectPropertyReferences) implements TmxLayerInfo {
    public TmxImageLayerInfo {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
    }


    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.IMAGE;
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
