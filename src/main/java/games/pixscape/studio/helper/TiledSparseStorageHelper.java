package games.pixscape.studio.helper;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileTransformFlags;

public final class TiledSparseStorageHelper {

    private TiledSparseStorageHelper() {
    }

    /**
     * Updates the sparse persistent tiled storage for one cell.
     * <p>
     * Rules:
     * - assetId <= 0 removes the cell from sparse storage
     * - otherwise the cell is inserted or updated
     */
    public static void setTile(TiledLayerComponent comp,
                               int gx,
                               int gy,
                               int assetId,
                               byte transformFlags) {
        if (comp == null) {
            return;
        }

        comp.ensureSparseTileStorageConsistency();

        int index = findIndex(comp, gx, gy);

        if (assetId <= 0) {
            if (index >= 0) {
                removeAt(comp, index);
            }
            return;
        }

        byte safeFlags = TileTransformFlags.sanitize(transformFlags);

        if (index >= 0) {
            comp.tileAssetIds.set(index, assetId);
            comp.tileTransformFlags.set(index, safeFlags);
            return;
        }

        comp.tileXs.add(gx);
        comp.tileYs.add(gy);
        comp.tileAssetIds.add(assetId);
        comp.tileTransformFlags.add(safeFlags);
    }

    /**
     * Starts linear sparse construction for a brand-new tiled layer.
     * <p>
     * The caller must append each source coordinate at most once. General editing and replacement
     * must continue to use {@link #setTile(TiledLayerComponent, int, int, int, byte)}.
     */
    public static NewLayerStorageBuilder beginNewLayerStorage(TiledLayerComponent comp,
                                                               int expectedCellCount) {
        if (comp == null) {
            throw new IllegalArgumentException("comp is null");
        }

        comp.ensureSparseTileStorageConsistency();
        if (comp.tileXs.size != 0
                || comp.tileYs.size != 0
                || comp.tileAssetIds.size != 0
                || comp.tileTransformFlags.size != 0) {
            throw new IllegalStateException("New tiled layer sparse storage must be empty.");
        }

        int capacity = Math.max(0, expectedCellCount);
        comp.tileXs.ensureCapacity(capacity);
        comp.tileYs.ensureCapacity(capacity);
        comp.tileAssetIds.ensureCapacity(capacity);
        comp.tileTransformFlags.ensureCapacity(capacity);
        return new NewLayerStorageBuilder(comp);
    }

    public static final class NewLayerStorageBuilder {
        private final TiledLayerComponent comp;

        private NewLayerStorageBuilder(TiledLayerComponent comp) {
            this.comp = comp;
        }

        /** Appends one unique, non-empty source cell in deterministic traversal order. */
        public void append(int gx, int gy, int assetId, byte transformFlags) {
            if (assetId <= 0) {
                return;
            }
            comp.tileXs.add(gx);
            comp.tileYs.add(gy);
            comp.tileAssetIds.add(assetId);
            comp.tileTransformFlags.add(TileTransformFlags.sanitize(transformFlags));
        }
    }

    public static int findIndex(TiledLayerComponent comp, int gx, int gy) {
        if (comp == null) {
            return -1;
        }

        int size = comp.tileXs.size;
        for (int i = 0; i < size; i++) {
            if (comp.tileXs.get(i) == gx && comp.tileYs.get(i) == gy) {
                return i;
            }
        }
        return -1;
    }

    public static void removeAt(TiledLayerComponent comp, int index) {
        if (comp == null) {
            return;
        }
        if (index < 0 || index >= comp.tileXs.size) {
            return;
        }

        comp.tileXs.removeIndex(index);
        comp.tileYs.removeIndex(index);
        comp.tileAssetIds.removeIndex(index);
        comp.tileTransformFlags.removeIndex(index);
    }
}
