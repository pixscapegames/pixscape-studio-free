package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxImageLayerPlan(String name,
                                String originalName,
                                int sourceLayerIndex,
                                boolean visible,
                                float parallaxX,
                                float parallaxY,
                                float offsetX,
                                float offsetY,
                                float opacity,
                                float x,
                                float y,
                                boolean repeatX,
                                boolean repeatY,
                                String imageSource,
                                int imageWidth,
                                int imageHeight,
                                String resolvedImagePath,
                                PropertySet properties,
                                List<TmxObjectPropertyReference> objectPropertyReferences) implements TmxLayerPlan {
    public TmxImageLayerPlan {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
