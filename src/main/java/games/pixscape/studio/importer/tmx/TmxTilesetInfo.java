package games.pixscape.studio.importer.tmx;

public record TmxTilesetInfo(int firstGid,
                             String sourcePath,
                             String name,
                             int tileWidth,
                             int tileHeight,
                             int tileCount,
                             int columns,
                             int spacing,
                             int margin,
                             String imageSource,
                             int imageWidth,
                             int imageHeight,
                             String resolvedImagePath,
                             boolean imageExists,
                             boolean external) {

    public int lastGidExclusive() {
        return firstGid + Math.max(tileCount, 0);
    }

    public boolean containsCleanGid(int cleanGid) {
        return cleanGid >= firstGid && cleanGid < lastGidExclusive();
    }
}
