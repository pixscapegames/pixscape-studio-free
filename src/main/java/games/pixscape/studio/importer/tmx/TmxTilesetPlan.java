package games.pixscape.studio.importer.tmx;

import games.pixscape.studio.service.asset.TsxTilesetDescriptor;

import java.util.List;

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
                             TmxObjectAlignment objectAlignment,
                             int tileOffsetX,
                             int tileOffsetY,
                             boolean external,
                             int localTileIdStart,
                             int localTileIdEndExclusive,
                             List<TsxTilesetDescriptor.ImageCollectionTile> imageCollectionTiles,
                             List<TsxTilesetDescriptor.TileAnimation> tileAnimations,
                             List<TmxTileDefinitionPlan> tileDefinitions) {

    public TmxTilesetPlan {
        imageCollectionTiles = imageCollectionTiles == null ? List.of() : List.copyOf(imageCollectionTiles);
        tileAnimations = tileAnimations == null ? List.of() : List.copyOf(tileAnimations);
        tileDefinitions = tileDefinitions == null ? List.of() : List.copyOf(tileDefinitions);
    }

    public boolean imageCollection() {
        return !imageCollectionTiles.isEmpty();
    }

    public TmxTileDefinitionPlan tileDefinition(int localTileId) {
        for (TmxTileDefinitionPlan definition : tileDefinitions) {
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
}
