package games.pixscape.studio.helper;

import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class TiledAllocatorHelper {

    private TiledAllocatorHelper() {
    }

    public static int computeExactSlots(TiledMapLayerData map) {
        int total = 0;

        int chunksX = Math.max(1, (map.mapWidth + map.chunkSize - 1) / map.chunkSize);
        int chunksY = Math.max(1, (map.mapHeight + map.chunkSize - 1) / map.chunkSize);

        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {

                int worldTileX = cx * map.chunkSize;
                int worldTileY = cy * map.chunkSize;

                int chunkWidth = Math.min(map.chunkSize, map.mapWidth - worldTileX);
                int chunkHeight = Math.min(map.chunkSize, map.mapHeight - worldTileY);

                if (chunkWidth <= 0 || chunkHeight <= 0) continue;

                total += chunkWidth * chunkHeight;
            }
        }

        return total;
    }

    public static void disableSlots(RenderStateSOA state, int start, int end) {
        for (int i = start; i < end; i++) {
            state.disable(i);
        }
    }
}
