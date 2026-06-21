package games.pixscape.studio.service.asset;

public final class AssetUsageReport {

    public int spriteRefs;
    public int tiledRefs;
    public int particleRefs;

    public boolean isUsed() {
        return spriteRefs > 0 || tiledRefs > 0 || particleRefs > 0;
    }

    public int total() {
        return spriteRefs + tiledRefs + particleRefs;
    }

    @Override
    public String toString() {
        return "AssetUsageReport{" +
                "spriteRefs=" + spriteRefs +
                ", tiledRefs=" + tiledRefs +
                ", particleRefs=" + particleRefs +
                '}';
    }
}