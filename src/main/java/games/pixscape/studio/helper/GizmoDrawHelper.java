package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.Color;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.service.spatial.SpatialBlockPlacementTarget;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.config.EditorOverlayPalette;

public final class GizmoDrawHelper {

    private GizmoDrawHelper() {
    }

    public static float thicknessPx = 1f;
    public static float thicknessLassoPx = 1f;
    public static float dashPx = 6f;
    public static float gapPx = 4f;

    public static final float HANDLE_SIZE_PX = 8f;
    public static final float SHAPE_VERTEX_HANDLE_SIZE_PX = 8f;
    public static final float ROTATE_OFFSET_PX = 22f;
    public static final float ROTATE_RADIUS_PX = 6f;
    public static final float QUAD_HANDLE_DIAMETER_PX = 8f;

    private static final float[] tmp2 = new float[2];
    private static final float[] tmpCell = new float[8];

    public static void drawLasso(StudioDrawContext ctx, float x0, float y0, float x1, float y1) {
        ctx.drawer.setColor(0.8f, 0.8f, 0.8f, 1f);
        ShapeHelper.drawRectWorld(ctx.drawer, ctx.cam, thicknessLassoPx, x0, y0, x1, y1, ctx.screenWidth());
    }

    public static void drawDashedObb(StudioDrawContext ctx, float[] obb) {
        ctx.drawer.setColor(0.8f, 0.8f, 0.8f, 1f);
        float x0 = obb[0], y0 = obb[1], x1 = obb[2], y1 = obb[3], x2 = obb[4], y2 = obb[5], x3 = obb[6], y3 = obb[7];
        drawDashedLine(ctx, x0, y0, x1, y1);
        drawDashedLine(ctx, x1, y1, x2, y2);
        drawDashedLine(ctx, x2, y2, x3, y3);
        drawDashedLine(ctx, x3, y3, x0, y0);
    }

    private static void drawDashedLine(StudioDrawContext ctx, float x1, float y1, float x2, float y2) {
        ShapeHelper.drawDashedLineWorld(
                ctx.drawer, ctx.cam,
                thicknessPx, dashPx, gapPx,
                x1, y1, x2, y2,
                ctx.screenWidth()
        );
    }

    public static void drawRectWorld(
            StudioDrawContext ctx,
            float x0, float y0,
            float x1, float y1
    ) {
        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);

        ctx.drawer.setColor(0.2f, 0.8f, 1f, 0.12f);
        ctx.drawer.filledRectangle(minX, minY, maxX - minX, maxY - minY);
    }

    public static void drawHandlesObb(StudioDrawContext ctx, float[] obb) {
        drawHandleSquare(ctx, HandleLayout.swX(obb), HandleLayout.swY(obb));
        drawHandleSquare(ctx, HandleLayout.seX(obb), HandleLayout.seY(obb));
        drawHandleSquare(ctx, HandleLayout.neX(obb), HandleLayout.neY(obb));
        drawHandleSquare(ctx, HandleLayout.nwX(obb), HandleLayout.nwY(obb));

        drawHandleSquare(ctx, HandleLayout.midSX(obb), HandleLayout.midSY(obb));
        drawHandleSquare(ctx, HandleLayout.midEX(obb), HandleLayout.midEY(obb));
        drawHandleSquare(ctx, HandleLayout.midNX(obb), HandleLayout.midNY(obb));
        drawHandleSquare(ctx, HandleLayout.midWX(obb), HandleLayout.midWY(obb));

        drawRotateHandle(ctx, obb);
    }

    public static void drawUniformScaleHandlesObb(StudioDrawContext ctx, float[] obb) {
        drawHandleSquare(ctx, HandleLayout.swX(obb), HandleLayout.swY(obb));
        drawHandleSquare(ctx, HandleLayout.seX(obb), HandleLayout.seY(obb));
        drawHandleSquare(ctx, HandleLayout.neX(obb), HandleLayout.neY(obb));
        drawHandleSquare(ctx, HandleLayout.nwX(obb), HandleLayout.nwY(obb));
        drawRotateHandle(ctx, obb);
    }

    /** Draws BL, BR, TR and TL quad edges with one round handle per corner. */
    public static void drawQuadEditGizmo(StudioDrawContext ctx, float[] quad) {
        float lineWorld = thicknessPx * ctx.wpp();
        ctx.drawer.setColor(EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.line(quad[0], quad[1], quad[2], quad[3], lineWorld);
        ctx.drawer.line(quad[2], quad[3], quad[4], quad[5], lineWorld);
        ctx.drawer.line(quad[4], quad[5], quad[6], quad[7], lineWorld);
        ctx.drawer.line(quad[6], quad[7], quad[0], quad[1], lineWorld);

        drawQuadHandle(ctx, quad[0], quad[1]);
        drawQuadHandle(ctx, quad[2], quad[3]);
        drawQuadHandle(ctx, quad[4], quad[5]);
        drawQuadHandle(ctx, quad[6], quad[7]);
    }

    private static void drawQuadHandle(StudioDrawContext ctx, float cx, float cy) {
        float borderWorld = ctx.wpp();
        float radiusWorld = QUAD_HANDLE_DIAMETER_PX * 0.5f * borderWorld;

        ctx.drawer.setColor(EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.filledCircle(cx, cy, radiusWorld);
        ctx.drawer.setColor(0f, 0f, 0f, 1f);
        ctx.drawer.circle(cx, cy, radiusWorld, borderWorld);
    }

    public static void drawHandleSquare(StudioDrawContext ctx, float cx, float cy) {
        float borderWorld = ctx.wpp();
        float half = (HANDLE_SIZE_PX * 0.5f) * borderWorld;

        ctx.drawer.setColor(EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.filledRectangle(cx - half, cy - half, half * 2f, half * 2f);

        ctx.drawer.setColor(0f, 0f, 0f, 1f);
        ctx.drawer.rectangle(cx - half, cy - half, half * 2f, half * 2f, borderWorld);
    }

    private static void drawRotateHandle(StudioDrawContext ctx, float[] obb) {
        float wpp = ctx.wpp();
        float offsetWorld = ROTATE_OFFSET_PX * wpp;

        HandleLayout.rotateHandle(obb, offsetWorld, tmp2);

        ctx.drawer.setColor(EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.filledCircle(tmp2[0], tmp2[1], ROTATE_RADIUS_PX * wpp);
    }

    public static void drawSolidLine(StudioDrawContext ctx, float x1, float y1, float x2, float y2, float thicknessPx) {
        float thicknessWorld = thicknessPx * ctx.wpp();
        ctx.drawer.line(x1, y1, x2, y2, thicknessWorld);
    }

    public static void drawTiledOverlay(
            StudioDrawContext ctx,
            TiledMapLayerData map,
            SpatialBlockPlacementTarget target
    ) {
        if (target == null || !target.valid()) return;
        drawOverlayCell(ctx, map, target.targetGx(), target.targetGy(), 1f, 1f, 1f, 0.95f, ctx.pxToWorld(2.5f));
    }

    public static void drawTiledRectPreview(
            StudioDrawContext ctx,
            TiledMapLayerData map,
            int minGX,
            int minGY,
            int maxGX,
            int maxGY
    ) {
        if (map == null) return;

        if (map.projection == TiledProjection.ORTHO) {
            float worldX0 = map.tileToWorldX(minGX, minGY);
            float worldY0 = map.tileToWorldY(minGX, minGY);
            float worldX1 = map.tileToWorldX(maxGX, maxGY) + map.tileWidth;
            float worldY1 = map.tileToWorldY(maxGX, maxGY) + map.tileHeight;
            drawRectWorld(ctx, worldX0, worldY0, worldX1, worldY1);
            return;
        }

        float offsetX = map.tileWidth * 0.5f;
        float minX = minGX;
        float minY = minGY;
        float maxX = maxGX + 1f;
        float maxY = maxGY + 1f;

        tmpCell[0] = map.tileToWorldX(minX, minY) + offsetX;
        tmpCell[1] = map.tileToWorldY(minX, minY);
        tmpCell[2] = map.tileToWorldX(maxX, minY) + offsetX;
        tmpCell[3] = map.tileToWorldY(maxX, minY);
        tmpCell[4] = map.tileToWorldX(maxX, maxY) + offsetX;
        tmpCell[5] = map.tileToWorldY(maxX, maxY);
        tmpCell[6] = map.tileToWorldX(minX, maxY) + offsetX;
        tmpCell[7] = map.tileToWorldY(minX, maxY);

        ctx.drawer.setColor(0.2f, 0.8f, 1f, 0.75f);
        drawPolygonOutline(ctx, tmpCell, ctx.pxToWorld(1.5f));
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

    private static void drawOverlayCell(StudioDrawContext ctx,
                                        TiledMapLayerData map,
                                        int gx,
                                        int gy,
                                        float r,
                                        float g,
                                        float b,
                                        float a,
                                        float lineW) {
        if (map.projection == TiledProjection.ORTHO) {
            float x = map.tileToWorldX(gx, gy);
            float y = map.tileToWorldY(gx, gy);
            ctx.drawer.setColor(r, g, b, Math.min(a, 0.2f));
            ctx.drawer.filledRectangle(x, y, map.tileWidth, map.tileHeight);
            ctx.drawer.setColor(r, g, b, a);
            ctx.drawer.rectangle(x, y, map.tileWidth, map.tileHeight, lineW);
            return;
        }

        map.tileToCellVertices(gx, gy, tmpCell);
        ctx.drawer.setColor(r, g, b, a);
        drawPolygonOutline(ctx, tmpCell, lineW);
    }

    public static void drawFixtureCircle(StudioDrawContext ctx,
                                         float cx,
                                         float cy,
                                         float radiusWU,
                                         boolean focusedBody,
                                         boolean hovered,
                                         boolean selected,
                                         boolean sensor) {
        if (radiusWU <= 0f) return;
        applyFixtureColor(ctx, focusedBody, hovered, selected, sensor);
        float thicknessWorld = fixtureThicknessWU(ctx, hovered, selected);
        ctx.drawer.circle(cx, cy, radiusWU, thicknessWorld);
    }

    public static void drawFixturePolygon(StudioDrawContext ctx,
                                          float[] verts,
                                          int vertexCount,
                                          boolean focusedBody,
                                          boolean hovered,
                                          boolean selected,
                                          boolean sensor) {
        if (verts == null || vertexCount < 2) return;
        applyFixtureColor(ctx, focusedBody, hovered, selected, sensor);
        float thicknessWorld = fixtureThicknessWU(ctx, hovered, selected);
        for (int i = 0; i < vertexCount; i++) {
            int j = (i + 1) % vertexCount;
            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[j * 2];
            float by = verts[j * 2 + 1];
            ctx.drawer.line(ax, ay, bx, by, thicknessWorld);
        }
    }

    public static void drawShapeVertices(StudioDrawContext ctx, float[] verts, int vertexCount) {
        if (verts == null || vertexCount <= 0) return;

        for (int i = 0; i < vertexCount; i++) {
            float x = verts[i * 2];
            float y = verts[i * 2 + 1];
            drawShapeVertexHandle(ctx, x, y);
        }
    }

    public static void drawShapeVertexHandle(StudioDrawContext ctx, float cx, float cy) {
        drawShapeVertexHandle(ctx, cx, cy, EditorOverlayPalette.HANDLE_COLOR);
    }

    public static void drawShapeVertexHandle(StudioDrawContext ctx,
                                             float cx,
                                             float cy,
                                             Color fillColor) {
        float borderWorld = ctx.wpp();
        float half = (SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f) * borderWorld;

        ctx.drawer.setColor(fillColor != null ? fillColor : EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.filledRectangle(cx - half, cy - half, half * 2f, half * 2f);

        ctx.drawer.setColor(0f, 0f, 0f, 1f);
        ctx.drawer.rectangle(cx - half, cy - half, half * 2f, half * 2f, borderWorld);
    }

    public static void drawPolygonVertices(StudioDrawContext ctx, float[] verts, int vertexCount) {
        if (verts == null || vertexCount <= 0) return;
        for (int i = 0; i < vertexCount; i++) {
            drawPolygonVertexHandle(ctx, verts[i * 2], verts[i * 2 + 1]);
        }
    }

    public static void drawPolygonVertexHandle(StudioDrawContext ctx, float cx, float cy) {
        drawPolygonVertexHandle(ctx, cx, cy, EditorOverlayPalette.HANDLE_COLOR);
    }

    public static void drawPolygonVertexHandle(StudioDrawContext ctx,
                                               float cx,
                                               float cy,
                                               Color fillColor) {
        float borderWorld = ctx.wpp();
        float radius = (SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f) * borderWorld;
        ctx.drawer.setColor(fillColor != null ? fillColor : EditorOverlayPalette.HANDLE_COLOR);
        ctx.drawer.filledCircle(cx, cy, radius);
        ctx.drawer.setColor(0f, 0f, 0f, 1f);
        ctx.drawer.circle(cx, cy, radius, borderWorld);
    }

    private static float fixtureThicknessWU(StudioDrawContext ctx, boolean hovered, boolean selected) {
        float px = selected ? 2.75f : (hovered ? 2f : 1.25f);
        return px * ctx.wpp();
    }

    private static void applyFixtureColor(StudioDrawContext ctx,
                                          boolean focusedBody,
                                          boolean hovered,
                                          boolean selected,
                                          boolean sensor) {
        if (selected) {
            ctx.drawer.setColor(EditorOverlayPalette.PHYSICS_SELECTED_COLOR);
        } else if (hovered) {
            ctx.drawer.setColor(EditorOverlayPalette.PHYSICS_HOVER_COLOR);
        } else if (focusedBody) {
            ctx.drawer.setColor(EditorOverlayPalette.PHYSICS_FOCUSED_BODY_COLOR);
        } else if (sensor) {
            ctx.drawer.setColor(CommonLayout.PHYSICS_SENSOR_COLOR);
        } else {
            ctx.drawer.setColor(CommonLayout.PHYSICS_BASE_COLOR);
        }
    }
}
