package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Exact, deterministic, allocation-free pointer-to-cell resolution for spatial authoring. */
public final class SpatialCellPicker {
    private SpatialCellPicker() {
    }

    public static final class Result {
        public int gx;
        public int gy;
    }

    public static boolean pick(TiledMapLayerData map, float worldX, float worldY, Result out) {
        if (map == null || out == null) return false;

        int candidateX = map.worldToTileX(worldX, worldY);
        int candidateY = map.worldToTileY(worldX, worldY);
        boolean found = false;
        int bestX = 0;
        int bestY = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = candidateX + dx;
                int gy = candidateY + dy;
                if (!map.isPointInsideCell(gx, gy, worldX, worldY)) continue;
                if (!found || gy < bestY || gy == bestY && gx < bestX) {
                    found = true;
                    bestX = gx;
                    bestY = gy;
                }
            }
        }
        if (found) {
            out.gx = bestX;
            out.gy = bestY;
        }
        return found;
    }

    /**
     * Resolves a spatial-authoring endpoint. An occupied logical cell wins normally;
     * when it is empty or outside the map, the topmost visible occupied sprite quad
     * under the pointer is preferred. The bounded search uses the map visual padding.
     */
    public static boolean pickForSpatialSelection(TiledMapLayerData map,
                                                  TiledMapRenderState tiledState,
                                                  float worldX,
                                                  float worldY,
                                                  Result out) {
        if (map == null || out == null) return false;

        boolean logicalFound = pick(map, worldX, worldY, out);
        int logicalGx = logicalFound ? out.gx : 0;
        int logicalGy = logicalFound ? out.gy : 0;
        if (logicalFound && map.getTile(logicalGx, logicalGy) > 0) return true;

        if (pickTopmostVisibleOccupiedSprite(map, tiledState, worldX, worldY, out)) return true;
        if (logicalFound) {
            out.gx = logicalGx;
            out.gy = logicalGy;
        }
        return logicalFound;
    }

    private static boolean pickTopmostVisibleOccupiedSprite(TiledMapLayerData map,
                                                             TiledMapRenderState tiledState,
                                                             float worldX,
                                                             float worldY,
                                                             Result out) {
        if (tiledState == null || map.mapWidth <= 0 || map.mapHeight <= 0) return false;

        int centerGx = map.worldToTileX(worldX, worldY);
        int centerGy = map.worldToTileY(worldX, worldY);
        float maxPadding = Math.max(
                Math.max(map.visualPaddingLeft, map.visualPaddingRight),
                Math.max(map.visualPaddingTop, map.visualPaddingBottom));
        float cellExtent = Math.max(1f, Math.min(map.tileWidth, map.tileHeight) * 0.5f);
        int radius = Math.max(2, (int) Math.ceil(maxPadding / cellExtent) + 2);

        int minGx = Math.max(0, centerGx - radius);
        int maxGx = Math.min(map.mapWidth - 1, centerGx + radius);
        int minGy = Math.max(0, centerGy - radius);
        int maxGy = Math.min(map.mapHeight - 1, centerGy + radius);
        boolean found = false;
        long bestSortKey = Long.MIN_VALUE;
        int bestRef = -1;
        int bestGx = 0;
        int bestGy = 0;

        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) {
                if (map.getTile(gx, gy) <= 0) continue;
                int ref = map.tiledRenderRefForTile(gx, gy);
                if (!tiledState.isRenderableRef(ref)
                        || !containsRenderedQuad(tiledState, ref, worldX, worldY)) continue;
                long sortKey = tiledState.sortKey[ref];
                if (!found || compareUnsigned(sortKey, bestSortKey) > 0
                        || sortKey == bestSortKey && ref > bestRef) {
                    found = true;
                    bestSortKey = sortKey;
                    bestRef = ref;
                    bestGx = gx;
                    bestGy = gy;
                }
            }
        }

        if (found) {
            out.gx = bestGx;
            out.gy = bestGy;
        }
        return found;
    }

    private static boolean containsRenderedQuad(TiledMapRenderState state,
                                                int ref,
                                                float worldX,
                                                float worldY) {
        float minX = Math.min(Math.min(state.x1[ref], state.x2[ref]), Math.min(state.x3[ref], state.x4[ref]));
        float maxX = Math.max(Math.max(state.x1[ref], state.x2[ref]), Math.max(state.x3[ref], state.x4[ref]));
        float minY = Math.min(Math.min(state.y1[ref], state.y2[ref]), Math.min(state.y3[ref], state.y4[ref]));
        float maxY = Math.max(Math.max(state.y1[ref], state.y2[ref]), Math.max(state.y3[ref], state.y4[ref]));
        return worldX >= minX && worldX <= maxX && worldY >= minY && worldY <= maxY;
    }

    private static int compareUnsigned(long first, long second) {
        long biasedFirst = first ^ Long.MIN_VALUE;
        long biasedSecond = second ^ Long.MIN_VALUE;
        return biasedFirst < biasedSecond ? -1 : biasedFirst == biasedSecond ? 0 : 1;
    }
}
