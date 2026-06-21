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