package games.pixscape.studio.importer.tmx;

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
                                String imageSource,
                                int imageWidth,
                                int imageHeight,
                                String resolvedImagePath,
                                boolean imageExists) implements TmxLayerInfo {

    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.IMAGE;
    }
}
