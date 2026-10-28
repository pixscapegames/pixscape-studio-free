package games.pixscape.studio.asset;

public final class TileAssetMeta extends AssetMeta {

    public int tilesetId = -1;
    public int sheetIndex = -1;
    public int cellX = -1;
    public int cellY = -1;
    /** Relative path in the imported collection, or a stable grid cell key. */
    public String importSourceKey;
    /** Original, untrimmed normal pixels. Never included in the color atlas. */
    public String normalSourceRelPath;

    public TileAssetMeta() {
        super(AssetType.TILE);
    }

    public TileAssetMeta(int id,
                         String logicalPath,
                         String sourceRelPath,
                         AssetScope scope) {
        super(id, AssetType.TILE, logicalPath, sourceRelPath, scope);
    }
}
