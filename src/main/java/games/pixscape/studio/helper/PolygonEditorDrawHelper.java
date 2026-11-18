package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.Color;
import games.pixscape.studio.ui.config.EditorOverlayPalette;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.service.geometry.PolygonEditSession;

public final class PolygonEditorDrawHelper {
    private PolygonEditorDrawHelper() { }
    public static void drawContour(StudioDrawContext ctx, float[] vertices, int count, Color color) {
        ctx.drawer.setColor(color);
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            GizmoDrawHelper.drawSolidLine(ctx, vertices[i * 2], vertices[i * 2 + 1],
                    vertices[next * 2], vertices[next * 2 + 1], 1.5f);
        }
    }

    public static void drawSession(StudioDrawContext ctx, PolygonEditSession session, float mouseX, float mouseY) {
        Array<Vector2> pts = session.points();
        int count = session.pointCount();
        if (pts == null || count == 0) {
            return;
        }

        // validated segments
        ctx.drawer.setColor(EditorOverlayPalette.PHYSICS_HOVER_COLOR);
        for (int i = 0; i < count - 1; i++) {
            Vector2 a = pts.get(i);
            Vector2 b = pts.get(i + 1);
            GizmoDrawHelper.drawSolidLine(ctx, a.x, a.y, b.x, b.y, 1.5f);
        }

        // preview segment toward mouse if not closed
        if (!session.isClosed()) {
            Vector2 last = session.lastPoint();

            if (last != null) {
                GizmoDrawHelper.drawSolidLine(
                        ctx,
                        last.x, last.y,
                        mouseX, mouseY,
                        1.0f
                );
            }
        }

        // closing segment if closed
        if (session.isClosed() && count >= 3) {
            Vector2 first = pts.first();
            Vector2 last = pts.peek();
            GizmoDrawHelper.drawSolidLine(ctx, last.x, last.y, first.x, first.y, 1.5f);
        }

        // points
        for (int i = 0; i < count; i++) {
            Vector2 p = pts.get(i);
            if (p == null) {
                continue;
            }

            boolean firstPoint = (i == 0);
            boolean closable = firstPoint && session.canClose();

            Color pointColor;
            if (closable) {
                float closeRadiusWorld = ctx.pxToWorld(
                        GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * 0.5f + 2f);

                float dx = mouseX - p.x;
                float dy = mouseY - p.y;
                boolean nearClose = dx * dx + dy * dy <= closeRadiusWorld * closeRadiusWorld;

                if (nearClose) {
                    pointColor = EditorOverlayPalette.PHYSICS_SELECTED_COLOR;
                } else {
                    pointColor = EditorOverlayPalette.PHYSICS_HOVER_COLOR;
                }
            } else {
                pointColor = EditorOverlayPalette.PHYSICS_HOVER_COLOR;
            }

            GizmoDrawHelper.drawPolygonVertexHandle(ctx, p.x, p.y, pointColor);
        }
    }
}
