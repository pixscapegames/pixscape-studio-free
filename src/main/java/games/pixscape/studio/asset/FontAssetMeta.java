package games.pixscape.studio.asset;

/** BitmapFont descriptor asset; page dependencies remain relative to sourceRelPath. */
public final class FontAssetMeta extends AssetMeta {
    public FontAssetMeta() {
        super(AssetType.FONT);
    }

    public FontAssetMeta(int id, String logicalPath, String sourceRelPath, AssetScope scope) {
        super(id, AssetType.FONT, logicalPath, sourceRelPath, scope);
    }
}
