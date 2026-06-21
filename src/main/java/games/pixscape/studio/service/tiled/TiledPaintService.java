package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.helper.TiledSparseStorageHelper;

public final class TiledPaintService {

    private int activeTileAssetId = -1;

    public void setActiveTileAssetId(int assetId) {
        this.activeTileAssetId = assetId;
    }

    public int getActiveTileAssetId() {
        return activeTileAssetId;
    }

    public boolean hasActiveTile() {
        return activeTileAssetId >= 0;
    }

    /**
     * Legacy plain paint helper kept for compatibility with older call sites.
     * This path writes tiles without transform variants.
     */
    public void paintTile(TiledLayerComponent comp,
                          int gx,
                          int gy) {

        if (!hasActiveTile()) return;

        comp.data.setTile(gx, gy, activeTileAssetId, TileTransformFlags.NONE);
        TiledSparseStorageHelper.setTile(comp, gx, gy, activeTileAssetId, TileTransformFlags.NONE);
    }
}
