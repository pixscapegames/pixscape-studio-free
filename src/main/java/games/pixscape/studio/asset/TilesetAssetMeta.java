package games.pixscape.studio.asset;

import games.pixscape.runtime.loading.SceneMetaRuntime;

public final class TilesetAssetMeta extends AssetMeta {

    public int imageWidth;
    public int imageHeight;
    public int tileWidth;
    public int tileHeight;
    public int columns;
    public int rows;
    public int spacing;
    public int margin;
    public int referenceCellWidth;
    public int referenceCellHeight;
    public SceneMetaRuntime.TiledProjection projection = SceneMetaRuntime.TiledProjection.ORTHO;
    public TilesetAnchor anchor = TilesetAnchor.TOP_CENTER;
    public int offsetX;
    public int offsetY;
    public TilesetRenderSize renderSize = TilesetRenderSize.NATIVE;

    public TilesetAssetMeta() {
        // required for Json
    }

    public TilesetAssetMeta(int id,
                            String logicalPath,
                            String sourceRelPath,
                            AssetScope scope) {
        super(id, AssetType.TILESET, logicalPath, sourceRelPath, scope);
    }

    public void normalizeProfileDefaults() {
        if (referenceCellWidth <= 0) {
            referenceCellWidth = tileWidth > 0 ? tileWidth : 32;
        }
        if (referenceCellHeight <= 0) {
            referenceCellHeight = tileHeight > 0 ? tileHeight : 32;
        }
        if (projection == null) {
            projection = SceneMetaRuntime.TiledProjection.ORTHO;
        }
        if (anchor == null) {
            anchor = TilesetAnchor.TOP_CENTER;
        }
        if (renderSize == null) {
            renderSize = TilesetRenderSize.NATIVE;
        }
    }
}
