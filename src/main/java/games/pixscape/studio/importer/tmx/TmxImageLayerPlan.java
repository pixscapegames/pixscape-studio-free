package games.pixscape.studio.importer.tmx;

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
                                String resolvedImagePath) implements TmxLayerPlan {
}
