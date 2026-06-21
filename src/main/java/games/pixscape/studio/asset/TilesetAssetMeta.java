package games.pixscape.studio.asset;

public final class TilesetAssetMeta extends AssetMeta {

    public int imageWidth;
    public int imageHeight;
    public int tileWidth;
    public int tileHeight;
    public int columns;
    public int rows;
    public int spacing;
    public int margin;

    public TilesetAssetMeta() {
        // required for Json
    }

    public TilesetAssetMeta(int id,
                            String logicalPath,
                            String sourceRelPath,
                            AssetScope scope) {
        super(id, AssetType.TILESET, logicalPath, sourceRelPath, scope);
    }
}