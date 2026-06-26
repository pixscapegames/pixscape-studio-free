package games.pixscape.studio.importer.tmx;

import java.util.List;

public record TmxTileLayerInfo(String name,
                               String originalName,
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
                               boolean hasTransformFlags,
                               List<TmxTileCellInfo> cells) implements TmxLayerInfo {

    public TmxTileLayerInfo {
        cells = List.copyOf(cells);
    }

    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.TILE;
    }
}
