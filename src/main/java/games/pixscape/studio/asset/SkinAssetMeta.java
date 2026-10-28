package games.pixscape.studio.asset;

/** Scene2D Skin JSON asset whose dependencies remain inside its owned bundle. */
public final class SkinAssetMeta extends AssetMeta {
    public SkinAssetMeta() { super(AssetType.SKIN); }

    public SkinAssetMeta(int id, String logicalPath, String sourceRelPath, AssetScope scope) {
        super(id, AssetType.SKIN, logicalPath, sourceRelPath, scope);
    }
}
