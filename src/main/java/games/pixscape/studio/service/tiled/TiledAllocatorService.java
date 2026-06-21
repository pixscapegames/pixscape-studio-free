package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.tiled.TiledSoaAllocator;
import games.pixscape.studio.helper.TiledAllocatorHelper;

public final class TiledAllocatorService {

    private final TiledSoaAllocator allocator;
    private final RenderStateSOA renderState;

    public TiledAllocatorService(TiledSoaAllocator allocator,
                                 RenderStateSOA renderState) {
        this.allocator = allocator;
        this.renderState = renderState;
    }

    public void allocateLayer(TiledLayerComponent comp) {
        int required = TiledAllocatorHelper.computeExactSlots(comp.data);
        TiledSoaAllocator.Range r = allocator.allocate(required);

        // Store the real reference
        comp.range = r;
        comp.tiledStart = r.start;
        comp.tiledEnd = r.end;
        comp.data.initSlotRange(r.start, r.end);
    }

    public void freeLayer(TiledLayerComponent comp) {
        if (comp.range == null) return;
        allocator.free(comp.range);
        disableSlots(comp.range.start, comp.range.end);

        comp.range = null;
        comp.tiledStart = 0;
        comp.tiledEnd = 0;
    }

    public void resizeLayer(TiledLayerComponent comp,
                            int newMapWidth,
                            int newMapHeight,
                            int newChunkSize) {

        freeLayer(comp);

        comp.data.mapWidth = newMapWidth;
        comp.data.mapHeight = newMapHeight;
        comp.data.chunkSize = newChunkSize;

        allocateLayer(comp);

        comp.data.markAllChunksContentDirty();
    }

    private void disableSlots(int start, int end) {
        for (int i = start; i < end; i++) {
            renderState.disable(i);
        }
    }

    public void reset() {
        allocator.reset();
    }
}