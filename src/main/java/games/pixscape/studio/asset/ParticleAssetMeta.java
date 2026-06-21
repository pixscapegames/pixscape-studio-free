package games.pixscape.studio.asset;

public final class ParticleAssetMeta extends AssetMeta {

    public ParticleAssetMeta() {
        // required for Json
    }

    public ParticleAssetMeta(int id,
                             String logicalPath,
                             String sourceRelPath,
                             AssetScope scope) {
        super(id, AssetType.PARTICLE, logicalPath, sourceRelPath, scope);
    }
}