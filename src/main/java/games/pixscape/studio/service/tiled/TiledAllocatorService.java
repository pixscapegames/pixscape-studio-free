package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;

public final class TiledAllocatorService {
    private final TileAnimationLookup animationLookup;

    public TiledAllocatorService() {
        this(null);
    }

    public TiledAllocatorService(TileAnimationLookup animationLookup) {
        this.animationLookup = animationLookup;
    }

    public void allocateLayer(TiledLayerComponent comp) {
        if (comp == null || comp.data == null) return;
        comp.data.initializeChunks();
        comp.data.originX = comp.originX;
        comp.data.originY = comp.originY;
        comp.data.spatialEnabled = comp.spatialEnabled;
        comp.data.defaultTileAltitude = comp.defaultTileAltitude;
        comp.data.defaultTileHeight = comp.defaultTileHeight;
        comp.data.markAllChunksContentDirty();
    }

    public void freeLayer(TiledLayerComponent comp) {
        if (comp == null || comp.data == null) return;
        comp.data.markAllChunksContentDirty();
    }

    /** Rebuilds authored per-cell animation playback after deep map restoration. */
    public void synchronizeAnimations(TiledLayerComponent comp) {
        if (comp == null || comp.data == null || animationLookup == null) return;
        comp.ensureSparseTileStorageConsistency();
        for (int i = 0; i < comp.tileAssetIds.size; i++) {
            int gx = comp.tileXs.get(i);
            int gy = comp.tileYs.get(i);
            int cx = gx / comp.data.chunkSize;
            int cy = gy / comp.data.chunkSize;
            TileChunk chunk = comp.data.getChunk(cx, cy);
            if (chunk == null) continue;
            TileAnimationStateSupport.syncWorldCell(
                    chunk, gx - cx * comp.data.chunkSize, gy - cy * comp.data.chunkSize,
                    animationLookup);
        }
    }

    public void resizeLayer(TiledLayerComponent comp,
                            int newMapWidth,
                            int newMapHeight,
                            int newChunkSize) {
        if (comp == null || comp.data == null) return;

        comp.data.chunkSize = newChunkSize;
        comp.data.rebuildWithNewSize(newMapWidth, newMapHeight);
        comp.data.originX = comp.originX;
        comp.data.originY = comp.originY;
        comp.data.spatialEnabled = comp.spatialEnabled;
        comp.data.defaultTileAltitude = comp.defaultTileAltitude;
        comp.data.defaultTileHeight = comp.defaultTileHeight;
        comp.data.markAllChunksContentDirty();
    }

    public void reset() {
    }
}
