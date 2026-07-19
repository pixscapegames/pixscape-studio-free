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
                             boolean external,
                             int localTileIdStart,
                             int localTileIdEndExclusive,
                             List<TsxTilesetDescriptor.ImageCollectionTile> imageCollectionTiles,
                             List<TsxTilesetDescriptor.TileAnimation> tileAnimations) {

    public TmxTilesetPlan {
        imageCollectionTiles = imageCollectionTiles == null ? List.of() : List.copyOf(imageCollectionTiles);
        tileAnimations = tileAnimations == null ? List.of() : List.copyOf(tileAnimations);
    }

    public boolean imageCollection() {
        return !imageCollectionTiles.isEmpty();
    }
}
