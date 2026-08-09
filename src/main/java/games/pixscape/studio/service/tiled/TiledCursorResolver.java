package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Resolves the logical Tiled cell used by Studio editing without allocating per update. */
public final class TiledCursorResolver {
    private TiledCursorResolver() {
    }

    public static void resolve(TiledMapLayerData map, float worldX, float worldY, Result out) {
        int gx = map.worldToTileX(worldX, worldY);
        int gy = map.worldToTileY(worldX, worldY);
        out.set(map.isInside(gx, gy), gx, gy);
    }

    public static final class Result {
        public boolean valid;
        public int gx;
        public int gy;

        private void set(boolean valid, int gx, int gy) {
            this.valid = valid;
            this.gx = gx;
            this.gy = gy;
        }
    }
}
