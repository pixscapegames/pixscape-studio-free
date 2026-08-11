package games.pixscape.studio.asset;

public final class ImageAssetMeta extends AssetMeta {

    public ImageAssetMeta() {
        super(AssetType.IMAGE);
    }

    public ImageAssetMeta(int id,
                          String logicalPath,
                          String sourceRelPath,
                          AssetScope scope) {
        super(id, AssetType.IMAGE, logicalPath, sourceRelPath, scope);
    }
}
