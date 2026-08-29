package games.pixscape.studio.ui.asset;

import games.pixscape.runtime.tiled.TiledProjection;

final class TilesetProfileReferenceDefaults {

    private boolean widthFollowsTile;
    private boolean heightFollowsTile;
    private TiledProjection projection;

    TilesetProfileReferenceDefaults(int tileWidth,
                                    int tileHeight,
                                    int referenceCellWidth,
                                    int referenceCellHeight) {
        this(tileWidth, tileHeight, referenceCellWidth, referenceCellHeight, TiledProjection.ORTHO);
    }

    TilesetProfileReferenceDefaults(int tileWidth,
                                    int tileHeight,
                                    int referenceCellWidth,
                                    int referenceCellHeight,
                                    TiledProjection projection) {
        this.projection = projection != null ? projection : TiledProjection.ORTHO;
        ReferenceSize defaultSize = defaultReferenceSize(tileWidth, tileHeight, this.projection);
        boolean followsDefaultSize = referenceCellWidth == defaultSize.width()
                && referenceCellHeight == defaultSize.height();
        widthFollowsTile = followsDefaultSize;
        heightFollowsTile = followsDefaultSize;
    }

    boolean widthFollowsTile() {
        return widthFollowsTile;
    }

    boolean heightFollowsTile() {
        return heightFollowsTile;
    }

    void markReferenceWidthEdited() {
        widthFollowsTile = false;
        heightFollowsTile = false;
    }

    void markReferenceHeightEdited() {
        widthFollowsTile = false;
        heightFollowsTile = false;
    }

    ReferenceSize referenceSizeAfterTileSizeChange(int tileWidth,
                                                   int tileHeight,
                                                   int currentReferenceWidth,
                                                   int currentReferenceHeight) {
        ReferenceSize defaultSize = defaultReferenceSize(tileWidth, tileHeight, projection);
        return new ReferenceSize(
                widthFollowsTile ? defaultSize.width() : currentReferenceWidth,
                heightFollowsTile ? defaultSize.height() : currentReferenceHeight
        );
    }

    ReferenceSize referenceSizeAfterProjectionChange(TiledProjection newProjection,
                                                     int tileWidth,
                                                     int tileHeight,
                                                     int currentReferenceWidth,
                                                     int currentReferenceHeight) {
        projection = newProjection != null ? newProjection : TiledProjection.ORTHO;
        return referenceSizeAfterTileSizeChange(tileWidth, tileHeight, currentReferenceWidth, currentReferenceHeight);
    }

    private static ReferenceSize defaultReferenceSize(int tileWidth,
                                                      int tileHeight,
                                                      TiledProjection projection) {
        int safeTileWidth = Math.max(1, tileWidth);
        int safeTileHeight = Math.max(1, tileHeight);
        if (projection == TiledProjection.ISO) {
            return new ReferenceSize(safeTileWidth, Math.max(1, safeTileWidth / 2));
        }
        return new ReferenceSize(safeTileWidth, safeTileHeight);
    }

    record ReferenceSize(int width, int height) {
    }
}
