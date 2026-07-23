package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Deterministic authored-volume picking from continuous footprint projection. */
public final class SpatialBlockPicking {
    public static final int NO_BLOCK = -1;

    private SpatialBlockPicking() {
    }

    public static int find(SpatialBlocksComponent component,
                           TiledMapLayerData map,
                           int selectedBlockId,
                           float worldX,
                           float worldY,
                           float[] base8,
                           float[] top8) {
        if (component == null || component.blocks == null || map == null
                || base8 == null || base8.length < 8 || top8 == null || top8.length < 8) return NO_BLOCK;
        SpatialBlockData selected = findById(component, selectedBlockId);
        if (contains(map, selected, worldX, worldY, base8, top8)) return selectedBlockId;
        for (int i = component.blocks.size - 1; i >= 0; i--) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall == null || wall.id == selectedBlockId) continue;
            if (contains(map, wall, worldX, worldY, base8, top8)) return wall.id;
        }
        return NO_BLOCK;
    }

    public static boolean containsBase(TiledMapLayerData map,
                                       SpatialBlockData wall,
                                       float worldX,
                                       float worldY,
                                       float[] base8) {
        if (map == null || wall == null || base8 == null || base8.length < 8) return false;
        SpatialBlockProjection.projectBaseFootprint(map, wall, base8);
        return pointInQuad(worldX, worldY, base8[0], base8[1], base8[2], base8[3],
                base8[4], base8[5], base8[6], base8[7]);
    }

    static boolean contains(TiledMapLayerData map,
                            SpatialBlockData wall,
                            float worldX,
                            float worldY,
                            float[] base8,
                            float[] top8) {
        if (wall == null) return false;
        SpatialBlockProjection.projectBaseFootprint(map, wall, base8);
        SpatialBlockProjection.projectTopFootprint(map, wall, top8);
        if (pointInQuad(worldX, worldY, base8[0], base8[1], base8[2], base8[3],
                base8[4], base8[5], base8[6], base8[7])) return true;
        if (pointInQuad(worldX, worldY, top8[0], top8[1], top8[2], top8[3],
                top8[4], top8[5], top8[6], top8[7])) return true;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            if (pointInQuad(worldX, worldY,
                    base8[i * 2], base8[i * 2 + 1],
                    base8[next * 2], base8[next * 2 + 1],
                    top8[next * 2], top8[next * 2 + 1],
                    top8[i * 2], top8[i * 2 + 1])) return true;
        }
        return false;
    }

    private static SpatialBlockData findById(SpatialBlocksComponent component, int blockId) {
        if (blockId <= 0) return null;
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall != null && wall.id == blockId) return wall;
        }
        return null;
    }

    private static boolean pointInQuad(float px, float py,
                                       float x0, float y0, float x1, float y1,
                                       float x2, float y2, float x3, float y3) {
        float twiceArea = x0 * y1 - y0 * x1
                + x1 * y2 - y1 * x2
                + x2 * y3 - y2 * x3
                + x3 * y0 - y3 * x0;
        if (Math.abs(twiceArea) <= 0.000001f) return false;
        boolean positive = false;
        boolean negative = false;
        float cross = cross(x0, y0, x1, y1, px, py);
        positive |= cross > 0f; negative |= cross < 0f;
        cross = cross(x1, y1, x2, y2, px, py);
        positive |= cross > 0f; negative |= cross < 0f;
        cross = cross(x2, y2, x3, y3, px, py);
        positive |= cross > 0f; negative |= cross < 0f;
        cross = cross(x3, y3, x0, y0, px, py);
        positive |= cross > 0f; negative |= cross < 0f;
        return !(positive && negative);
    }

    private static float cross(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
