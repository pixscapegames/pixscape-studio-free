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
                             TmxObjectAlignment objectAlignment,
                             int tileOffsetX,
                             int tileOffsetY,
                             String imageSource,
                             int imageWidth,
                             int imageHeight,
                             String resolvedImagePath,
                             boolean imageExists,
                             boolean external,
                             List<TsxTilesetDescriptor.ImageCollectionTile> imageCollectionTiles,
                             List<TsxTilesetDescriptor.TileAnimation> tileAnimations,
                             List<TmxTileDefinitionInfo> tileDefinitions) {

    public TmxTilesetInfo {
        imageCollectionTiles = imageCollectionTiles == null ? List.of() : List.copyOf(imageCollectionTiles);
        tileAnimations = tileAnimations == null ? List.of() : List.copyOf(tileAnimations);
        tileDefinitions = tileDefinitions == null ? List.of() : List.copyOf(tileDefinitions);
    }

    public boolean imageCollection() {
        return !imageCollectionTiles.isEmpty();
    }

    public TmxTileDefinitionInfo tileDefinition(int localTileId) {
        for (TmxTileDefinitionInfo definition : tileDefinitions) {
            if (definition.localTileId() == localTileId) return definition;
        }
        return null;
    }

    public int nativeTileWidth(int localTileId) {
        if (imageCollectionTiles != null) {
            for (TsxTilesetDescriptor.ImageCollectionTile tile : imageCollectionTiles) {
                if (tile != null && tile.localTileId() == localTileId && tile.imageWidth() > 0) {
                    return tile.imageWidth();
                }
            }
        }
        return tileWidth;
    }

    public int nativeTileHeight(int localTileId) {
        if (imageCollectionTiles != null) {
            for (TsxTilesetDescriptor.ImageCollectionTile tile : imageCollectionTiles) {
                if (tile != null && tile.localTileId() == localTileId && tile.imageHeight() > 0) {
                    return tile.imageHeight();
                }
            }
        }
        return tileHeight;
    }

    public int lastGidExclusive() {
        return firstGid + Math.max(tileCount, 0);
    }

    public boolean containsCleanGid(int cleanGid) {
        return cleanGid >= firstGid && cleanGid < lastGidExclusive();
    }
}
