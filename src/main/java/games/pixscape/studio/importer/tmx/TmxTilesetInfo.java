package games.pixscape.studio.importer.tmx;

import games.pixscape.studio.service.asset.TsxTilesetDescriptor;

import java.util.List;

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
                             boolean external,
                             List<TsxTilesetDescriptor.TileAnimation> tileAnimations) {

    public TmxTilesetInfo {
        tileAnimations = tileAnimations == null ? List.of() : List.copyOf(tileAnimations);
    }

    public int lastGidExclusive() {
        return firstGid + Math.max(tileCount, 0);
    }

    public boolean containsCleanGid(int cleanGid) {
        return cleanGid >= firstGid && cleanGid < lastGidExclusive();
    }
}
