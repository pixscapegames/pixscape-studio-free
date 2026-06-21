package games.pixscape.studio.asset;

public final class TileAssetMeta extends AssetMeta {

    public int tilesetId = -1;
    public int sheetIndex = -1;
    public int cellX = -1;
    public int cellY = -1;

    public TileAssetMeta() {
        // required for Json
    }

    public TileAssetMeta(int id,
                         String logicalPath,
                         String sourceRelPath,
                         AssetScope scope) {
        super(id, AssetType.TILE, logicalPath, sourceRelPath, scope);
    }
}