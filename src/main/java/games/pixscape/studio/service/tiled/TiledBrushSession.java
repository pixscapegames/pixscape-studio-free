package games.pixscape.studio.service.tiled;

import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.PackedTileValue;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Transaction-local tiled brush state. The live map is untouched until history execution. */
public final class TiledBrushSession {
    private final int layerEntityId;
    private final IntIntMap indexByCell = new IntIntMap();
    private TiledMapLayerData map;
    private int[] gx = new int[16];
    private int[] gy = new int[16];
    private int[] beforeAssetId = new int[16];
    private byte[] beforeTransformFlags = new byte[16];
    private int[] afterAssetId = new int[16];
    private byte[] afterTransformFlags = new byte[16];
    private int mutationCount;

    public TiledBrushSession(int layerEntityId) {
        this.layerEntityId = layerEntityId;
    }

    public void apply(TiledLayerComponent comp, int cellX, int cellY, int assetId) {
        apply(comp, cellX, cellY, assetId, TileTransformFlags.NONE);
    }

    public void apply(TiledLayerComponent comp, int cellX, int cellY, int assetId, byte flags) {
        if (comp == null || comp.data == null || !comp.data.isInside(cellX, cellY)) return;
        if (map == null) map = comp.data;
        else if (map != comp.data) {
            throw new IllegalStateException("A tiled brush session cannot span multiple maps.");
        }

        int key = pack(cellX, cellY);
        int index = indexByCell.get(key, -1);
        if (index < 0) {
            ensureCapacity(mutationCount + 1);
            index = mutationCount++;
            indexByCell.put(key, index);
            gx[index] = cellX;
            gy[index] = cellY;
            beforeAssetId[index] = map.getTile(cellX, cellY);
            beforeTransformFlags[index] = map.getTileTransformFlags(cellX, cellY);
        }
        afterAssetId[index] = assetId;
        afterTransformFlags[index] = TileTransformFlags.sanitize(flags);
    }

    /** Canonicalizes the final gesture by removing cells whose final value equals the original. */
    public void commit() {
        int write = 0;
        for (int read = 0; read < mutationCount; read++) {
            if (beforeAssetId[read] == afterAssetId[read]
                    && beforeTransformFlags[read] == afterTransformFlags[read]) continue;
            if (write != read) copy(read, write);
            write++;
        }
        mutationCount = write;
        rebuildIndex();
    }

    public void cancel() {
        mutationCount = 0;
        indexByCell.clear();
        map = null;
    }

    public TiledMutationPlan toPlan() {
        commit();
        return new TiledMutationPlan(layerEntityId, gx, gy, beforeAssetId,
                beforeTransformFlags, afterAssetId, afterTransformFlags, mutationCount);
    }

    public int pendingAssetId(int cellX, int cellY, int fallback) {
        int index = indexByCell.get(pack(cellX, cellY), -1);
        return index >= 0 ? afterAssetId[index] : fallback;
    }

    public byte pendingTransformFlags(int cellX, int cellY, byte fallback) {
        int index = indexByCell.get(pack(cellX, cellY), -1);
        return index >= 0 ? afterTransformFlags[index] : fallback;
    }

    public boolean isEmpty() { return mutationCount == 0; }
    public int mutationCount() { return mutationCount; }
    public int getLayerEntityId() { return layerEntityId; }
    public int gx(int index) { return gx[index]; }
    public int gy(int index) { return gy[index]; }
    public int beforePacked(int index) { return PackedTileValue.pack(beforeAssetId[index], beforeTransformFlags[index]); }
    public int afterPacked(int index) { return PackedTileValue.pack(afterAssetId[index], afterTransformFlags[index]); }
    public int afterAssetId(int index) { return afterAssetId[index]; }
    public byte afterTransformFlags(int index) { return afterTransformFlags[index]; }

    private void copy(int source, int target) {
        gx[target] = gx[source];
        gy[target] = gy[source];
        beforeAssetId[target] = beforeAssetId[source];
        beforeTransformFlags[target] = beforeTransformFlags[source];
        afterAssetId[target] = afterAssetId[source];
        afterTransformFlags[target] = afterTransformFlags[source];
    }

    private void rebuildIndex() {
        indexByCell.clear();
        for (int i = 0; i < mutationCount; i++) indexByCell.put(pack(gx[i], gy[i]), i);
    }

    private void ensureCapacity(int required) {
        if (gx.length >= required) return;
        int capacity = Math.max(required, gx.length * 2);
        gx = java.util.Arrays.copyOf(gx, capacity);
        gy = java.util.Arrays.copyOf(gy, capacity);
        beforeAssetId = java.util.Arrays.copyOf(beforeAssetId, capacity);
        beforeTransformFlags = java.util.Arrays.copyOf(beforeTransformFlags, capacity);
        afterAssetId = java.util.Arrays.copyOf(afterAssetId, capacity);
        afterTransformFlags = java.util.Arrays.copyOf(afterTransformFlags, capacity);
    }

    private static int pack(int x, int y) {
        return (x << 16) ^ (y & 0xFFFF);
    }
}
