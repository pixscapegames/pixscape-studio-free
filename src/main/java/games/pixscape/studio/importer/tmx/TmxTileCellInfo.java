package games.pixscape.studio.importer.tmx;

public record TmxTileCellInfo(int sourceX,
                              int sourceY,
                              int rawGid,
                              int cleanGid,
                              int tilesetFirstGid,
                              int localTileId,
                              boolean hasTransformFlags,
                              boolean horizontalFlip,
                              boolean verticalFlip,
                              boolean diagonalFlip,
                              boolean hexagonal120Flag) {
}
