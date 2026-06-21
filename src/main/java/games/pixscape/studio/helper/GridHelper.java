package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class GridHelper {

    private static final float[] TMP_VISIBLE = new float[4];
    private static final float[] TMP_CELL = new float[8];

    private GridHelper() {
    }

    /**
     * Returns visible world bounds as {minX, maxX, minY, maxY}.
     */
    public static float[] visibleWorld(StudioDrawContext ctx) {
        float hw = ctx.cam.viewportWidth * ctx.cam.zoom * 0.5f;
        float hh = ctx.cam.viewportHeight * ctx.cam.zoom * 0.5f;

        TMP_VISIBLE[0] = ctx.cam.position.x - hw;
        TMP_VISIBLE[1] = ctx.cam.position.x + hw;
        TMP_VISIBLE[2] = ctx.cam.position.y - hh;
        TMP_VISIBLE[3] = ctx.cam.position.y + hh;

        return TMP_VISIBLE;
    }

    public static void drawGrid(
            StudioDrawContext ctx,
            float cellWorld, int majorEvery,
            Color minorColor, Color majorColor, Color axisColor,
            float minorPx, float majorPx, float axisPx
    ) {
        float[] v = visibleWorld(ctx);
        float minX = v[0], maxX = v[1], minY = v[2], maxY = v[3];

        float pxPerCell = cellWorld / ctx.cam.zoom;
        int skip = 1;
        if (pxPerCell < 8f) skip = 8;
        else if (pxPerCell < 16) skip = 4;
        else if (pxPerCell < 24) skip = 2;

        float minorW = ctx.pxToWorld(minorPx);
        float majorW = ctx.pxToWorld(majorPx);
        float axisW = ctx.pxToWorld(axisPx);

        int startXi = MathUtils.floor(minX / cellWorld);
        int endXi = MathUtils.ceil(maxX / cellWorld);
        int startYi = MathUtils.floor(minY / cellWorld);
        int endYi = MathUtils.ceil(maxY / cellWorld);

        for (int i = startXi; i <= endXi; i++) {
            if (i % skip != 0) continue;
            float x = i * cellWorld;

            boolean isAxis = i == 0;
            boolean isMajor = (i % majorEvery) == 0;

            if (isAxis) {
                ctx.drawer.setColor(axisColor);
                ctx.drawer.line(x, minY, x, maxY, axisW);
            } else if (isMajor) {
                ctx.drawer.setColor(majorColor);
                ctx.drawer.line(x, minY, x, maxY, majorW);
            } else {
                ctx.drawer.setColor(minorColor);
                ctx.drawer.line(x, minY, x, maxY, minorW);
            }
        }

        for (int j = startYi; j <= endYi; j++) {
            if (j % skip != 0) continue;
            float y = j * cellWorld;

            boolean isAxis = j == 0;
            boolean isMajor = (j % majorEvery) == 0;

            if (isAxis) {
                ctx.drawer.setColor(axisColor);
                ctx.drawer.line(minX, y, maxX, y, axisW);
            } else if (isMajor) {
                ctx.drawer.setColor(majorColor);
                ctx.drawer.line(minX, y, maxX, y, majorW);
            } else {
                ctx.drawer.setColor(minorColor);
                ctx.drawer.line(minX, y, maxX, y, minorW);
            }
        }
    }

    public static void drawTiledOrthoGrid(
            StudioDrawContext ctx,
            float originX,
            float originY,
            float tileWidth,
            float tileHeight,
            Color lineColor,
            float linePx,
            boolean hasBounds,
            float mapMinX,
            float mapMinY,
            float mapMaxX,
            float mapMaxY
    ) {

        float[] v = visibleWorld(ctx);
        float camMinX = v[0], camMaxX = v[1];
        float camMinY = v[2], camMaxY = v[3];

        float minX = camMinX;
        float maxX = camMaxX;
        float minY = camMinY;
        float maxY = camMaxY;

        if (hasBounds) {
            minX = Math.max(camMinX, mapMinX);
            maxX = Math.min(camMaxX, mapMaxX);
            minY = Math.max(camMinY, mapMinY);
            maxY = Math.min(camMaxY, mapMaxY);

            if (minX >= maxX || minY >= maxY) return;
        }

        float lineW = ctx.pxToWorld(linePx);

        ctx.drawer.setColor(lineColor);

        int startXi = MathUtils.floor((minX - originX) / tileWidth);
        int endXi = MathUtils.ceil((maxX - originX) / tileWidth);

        int startYi = MathUtils.floor((minY - originY) / tileHeight);
        int endYi = MathUtils.ceil((maxY - originY) / tileHeight);

        for (int i = startXi; i <= endXi; i++) {
            float x = originX + i * tileWidth;
            ctx.drawer.line(x, minY, x, maxY, lineW);
        }

        for (int j = startYi; j <= endYi; j++) {
            float y = originY + j * tileHeight;
            ctx.drawer.line(minX, y, maxX, y, lineW);
        }
    }

    public static void drawTiledIsoGrid(
            StudioDrawContext ctx,
            TiledMapLayerData map,
            Color lineColor,
            float linePx
    ) {
        if (map == null || map.mapWidth <= 0 || map.mapHeight <= 0) return;

        float[] v = visibleWorld(ctx);
        float minX = v[0];
        float maxX = v[1];
        float minY = v[2];
        float maxY = v[3];

        int minGX = Integer.MAX_VALUE;
        int maxGX = Integer.MIN_VALUE;
        int minGY = Integer.MAX_VALUE;
        int maxGY = Integer.MIN_VALUE;

        minGX = includeTileRangeX(map, minX, minY, minGX, true);
        maxGX = includeTileRangeX(map, minX, minY, maxGX, false);
        minGY = includeTileRangeY(map, minX, minY, minGY, true);
        maxGY = includeTileRangeY(map, minX, minY, maxGY, false);

        minGX = includeTileRangeX(map, maxX, minY, minGX, true);
        maxGX = includeTileRangeX(map, maxX, minY, maxGX, false);
        minGY = includeTileRangeY(map, maxX, minY, minGY, true);
        maxGY = includeTileRangeY(map, maxX, minY, maxGY, false);

        minGX = includeTileRangeX(map, minX, maxY, minGX, true);
        maxGX = includeTileRangeX(map, minX, maxY, maxGX, false);
        minGY = includeTileRangeY(map, minX, maxY, minGY, true);
        maxGY = includeTileRangeY(map, minX, maxY, maxGY, false);

        minGX = includeTileRangeX(map, maxX, maxY, minGX, true);
        maxGX = includeTileRangeX(map, maxX, maxY, maxGX, false);
        minGY = includeTileRangeY(map, maxX, maxY, minGY, true);
        maxGY = includeTileRangeY(map, maxX, maxY, maxGY, false);

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;

        minGX = includeTileRangeX(map, centerX, centerY, minGX, true);
        maxGX = includeTileRangeX(map, centerX, centerY, maxGX, false);
        minGY = includeTileRangeY(map, centerX, centerY, minGY, true);
        maxGY = includeTileRangeY(map, centerX, centerY, maxGY, false);

        if (minGX == Integer.MAX_VALUE || minGY == Integer.MAX_VALUE) {
            return;
        }

        minGX = Math.max(0, minGX - 2);
        minGY = Math.max(0, minGY - 2);
        maxGX = Math.min(map.mapWidth - 1, maxGX + 2);
        maxGY = Math.min(map.mapHeight - 1, maxGY + 2);

        float lineW = ctx.pxToWorld(linePx);
        ctx.drawer.setColor(lineColor);

        for (int gy = minGY; gy <= maxGY; gy++) {
            for (int gx = minGX; gx <= maxGX; gx++) {
                if (!map.isInside(gx, gy)) continue;
                map.tileToCellVertices(gx, gy, TMP_CELL);
                drawPolygonOutline(ctx, TMP_CELL, lineW);
            }
        }
    }

    private static int includeTileRangeX(TiledMapLayerData map,
                                         float worldX,
                                         float worldY,
                                         int current,
                                         boolean minimum) {
        int gx = map.worldToTileX(worldX, worldY);
        return minimum ? Math.min(current, gx) : Math.max(current, gx);
    }

    private static int includeTileRangeY(TiledMapLayerData map,
                                         float worldX,
                                         float worldY,
                                         int current,
                                         boolean minimum) {
        int gy = map.worldToTileY(worldX, worldY);
        return minimum ? Math.min(current, gy) : Math.max(current, gy);
    }

    private static void drawPolygonOutline(StudioDrawContext ctx, float[] verts, float lineW) {
        for (int i = 0; i < 4; i++) {
            int ni = (i + 1) & 3;
            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[ni * 2];
            float by = verts[ni * 2 + 1];
            ctx.drawer.line(ax, ay, bx, by, lineW);
        }
    }
}