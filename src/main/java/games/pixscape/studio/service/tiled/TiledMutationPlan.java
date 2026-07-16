package games.pixscape.studio.service.tiled;

import java.util.Arrays;

/** Immutable canonical before/after snapshot for one tiled gesture. */
public final class TiledMutationPlan {
    private final int layerEntityId;
    private final int[] gx;
    private final int[] gy;
    private final int[] beforeAssetId;
    private final byte[] beforeFlags;
    private final int[] afterAssetId;
    private final byte[] afterFlags;

    TiledMutationPlan(int layerEntityId, int[] gx, int[] gy,
                      int[] beforeAssetId, byte[] beforeFlags,
                      int[] afterAssetId, byte[] afterFlags, int count) {
        this.layerEntityId = layerEntityId;
        this.gx = Arrays.copyOf(gx, count);
        this.gy = Arrays.copyOf(gy, count);
        this.beforeAssetId = Arrays.copyOf(beforeAssetId, count);
        this.beforeFlags = Arrays.copyOf(beforeFlags, count);
        this.afterAssetId = Arrays.copyOf(afterAssetId, count);
        this.afterFlags = Arrays.copyOf(afterFlags, count);
    }

    public int layerEntityId() { return layerEntityId; }
    public int size() { return gx.length; }
    public boolean isEmpty() { return gx.length == 0; }
    public int gx(int index) { return gx[index]; }
    public int gy(int index) { return gy[index]; }
    public int assetId(int index, boolean after) { return after ? afterAssetId[index] : beforeAssetId[index]; }
    public byte flags(int index, boolean after) { return after ? afterFlags[index] : beforeFlags[index]; }
}
