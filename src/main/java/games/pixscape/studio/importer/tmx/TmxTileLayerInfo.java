package games.pixscape.studio.importer.tmx;

public record TmxTileLayerInfo(String name,
                               boolean visible,
                               float opacity,
                               float offsetX,
                               float offsetY,
                               float parallaxX,
                               float parallaxY,
                               int width,
                               int height,
                               String encoding,
                               String compression,
                               int nonEmptyTileCount,
                               boolean hasTransformFlags) implements TmxLayerInfo {

    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.TILE;
    }
}
