package games.pixscape.studio.importer.tmx;

public record TmxTilesetPlan(int planIndex,
                             int firstGid,
                             String name,
                             String sourceTsxPath,
                             String resolvedImagePath,
                             String imageSource,
                             int imageWidth,
                             int imageHeight,
                             int tileWidth,
                             int tileHeight,
                             int tileCount,
                             int columns,
                             int spacing,
                             int margin,
                             boolean external,
                             int localTileIdStart,
                             int localTileIdEndExclusive) {
}
