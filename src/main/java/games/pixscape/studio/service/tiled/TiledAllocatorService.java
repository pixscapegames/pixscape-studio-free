package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;

public final class TiledAllocatorService {

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
