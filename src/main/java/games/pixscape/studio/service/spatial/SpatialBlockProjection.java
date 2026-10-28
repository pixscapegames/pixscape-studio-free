package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockProjection {
    private SpatialBlockProjection() {
    }

    public static int snapWorldToTileCellX(TiledMapLayerData map, float worldX, float worldY) {
        return map != null ? map.worldToTileX(worldX, worldY) : 0;
    }

    public static int snapWorldToTileCellY(TiledMapLayerData map, float worldX, float worldY) {
        return map != null ? map.worldToTileY(worldX, worldY) : 0;
    }

    public static void projectBaseFootprint(TiledMapLayerData map, SpatialBlockData block, float[] out8) {
        projectFootprintAtElevation(map, block, block != null ? block.altitude : 0f, out8);
    }

    public static void projectTopFootprint(TiledMapLayerData map, SpatialBlockData block, float[] out8) {
        float altitude = block != null ? block.altitude : 0f;
        float height = block != null ? Math.max(0f, block.height) : 0f;
        projectFootprintAtElevation(map, block, altitude + height, out8);
    }

    public static void projectFootprintAtElevation(TiledMapLayerData map,
                                                   SpatialBlockData block,
                                                   float elevation,
                                                   float[] out8) {
        if (map == null || block == null || out8 == null || out8.length < 8) return;

        float x0 = block.x;
        float y0 = block.y;
        float x1 = block.x + Math.max(0.001f, block.width);
        float y1 = block.y + Math.max(0.001f, block.depth);
        projectTileLocal(map, x0, y0, elevation, out8, 0);
        projectTileLocal(map, x1, y0, elevation, out8, 2);
        projectTileLocal(map, x1, y1, elevation, out8, 4);
        projectTileLocal(map, x0, y1, elevation, out8, 6);
    }

    public static void projectTileLocal(TiledMapLayerData map,
                                        float gx,
                                        float gy,
                                        float yOffset,
                                        float[] out,
                                        int offset) {
        if (map == null || out == null || offset < 0 || offset + 1 >= out.length) return;

        map.projectSpatialPoint(gx, gy, yOffset, out, offset);
    }

    public static void projectStructurePoint(TiledMapLayerData map,
                                             float gx,
                                             float gy,
                                             float elevation,
                                             float[] out,
                                             int offset) {
        if (map == null || out == null || offset < 0 || offset + 1 >= out.length) return;
        map.projectSpatialPoint(gx, gy, elevation, out, offset);
    }

    public static void footprintWorldToTileLocal(TiledMapLayerData map,
                                                 float worldX,
                                                 float worldY,
                                                 float elevation,
                                                 Vector2 out) {
        if (map == null || out == null) return;

        out.set(map.spatialWorldToTileX(worldX, worldY, elevation),
                map.spatialWorldToTileY(worldX, worldY, elevation));
    }
}
