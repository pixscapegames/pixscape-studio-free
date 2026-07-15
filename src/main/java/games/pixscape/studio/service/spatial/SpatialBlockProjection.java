package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockProjection {
    private SpatialBlockProjection() {
    }

    public static float elevationToWorldYOffset(float elevation) {
        return elevation;
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
        float yOffset = elevationToWorldYOffset(elevation);

        Vector2 cellOriginOffset = tmpCellOriginOffset();
        cellOriginOffset(map, cellOriginOffset);

        // TILE_CELL: top, right, bottom, left corners of the grid-cell range.
        // Other orientations intentionally fall back to this axis-derived box
        // until their authoring semantics are expanded.
        projectTileLocal(map, x0, y0, yOffset, cellOriginOffset, out8, 0);
        projectTileLocal(map, x1, y0, yOffset, cellOriginOffset, out8, 2);
        projectTileLocal(map, x1, y1, yOffset, cellOriginOffset, out8, 4);
        projectTileLocal(map, x0, y1, yOffset, cellOriginOffset, out8, 6);
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
        Vector2 cellOriginOffset = tmpCellOriginOffset();
        cellOriginOffset(map, cellOriginOffset);
        projectTileLocal(map, gx, gy, elevationToWorldYOffset(elevation), cellOriginOffset, out, offset);
    }

    public static void footprintWorldToTileLocal(TiledMapLayerData map,
                                                 float worldX,
                                                 float worldY,
                                                 float elevation,
                                                 Vector2 out) {
        if (map == null || out == null) return;

        Vector2 offset = tmpCellOriginOffset();
        cellOriginOffset(map, offset);
        float logicalX = worldX - offset.x;
        float logicalY = worldY - offset.y - elevationToWorldYOffset(elevation);
        out.set(
                map.projectWorldToTileX(logicalX, logicalY),
                map.projectWorldToTileY(logicalX, logicalY)
        );
    }

    private static void projectTileLocal(TiledMapLayerData map,
                                         float gx,
                                         float gy,
                                         float yOffset,
                                         Vector2 cellOriginOffset,
                                         float[] out,
                                         int offset) {
        projectTileLocal(map, gx, gy, yOffset, out, offset);
    }

    private static void cellOriginOffset(TiledMapLayerData map, Vector2 out) {
        if (map == null || out == null) return;

        float[] cell = tmpCellVerts();
        map.tileToCellVertices(0, 0, cell);
        out.set(cell[0] - map.tileToWorldX(0, 0), cell[1] - map.tileToWorldY(0, 0));
    }

    private static final ThreadLocal<Vector2> TMP_CELL_ORIGIN_OFFSET = ThreadLocal.withInitial(Vector2::new);
    private static final ThreadLocal<float[]> TMP_CELL_VERTS = ThreadLocal.withInitial(() -> new float[8]);

    private static Vector2 tmpCellOriginOffset() {
        return TMP_CELL_ORIGIN_OFFSET.get();
    }

    private static float[] tmpCellVerts() {
        return TMP_CELL_VERTS.get();
    }
}
