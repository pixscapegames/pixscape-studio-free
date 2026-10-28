package games.pixscape.studio.helper;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.render.lighting.LightingCoordinates;

/** Editor representation of the Runtime light's native-pixel XY/Z contract. */
public final class LightGizmoGeometry {
    public static final float HEIGHT_OFFSET_PX = 32f;
    public static final float HEIGHT_HALF_PX = 7f;
    public static final float INTENSITY_OFFSET_X_PX = 32f;
    public static final float INTENSITY_OFFSET_Y_PX = 24f;
    public static final float INTENSITY_HALF_PX = 10f;
    public static final float PICK_TOL_PX = 3f;

    private LightGizmoGeometry() {}

    /** Matches UpdateWorldGeometrySystem's bounds center, also with noncentral origins. */
    public static Vector2 ground(WorldTransformState state, int e, TransformComponent t,
                                 DimensionsComponent dim, Vector2 out) {
        boolean resolved = state != null && state.isResolved(e);
        float x = resolved ? state.x[e] : t.x;
        float y = resolved ? state.y[e] : t.y;
        float angle = resolved ? state.rotationRad[e] : t.rotationRad;
        float sx = resolved ? state.scaleX[e] : t.scaleX;
        float sy = resolved ? state.scaleY[e] : t.scaleY;
        float dx = dim == null ? 0f : (dim.width * .5f - t.originX) * sx;
        float dy = dim == null ? 0f : (dim.height * .5f - t.originY) * sy;
        float cos = MathUtils.cos(angle), sin = MathUtils.sin(angle);
        return out.set(x + cos * dx - sin * dy, y + sin * dx + cos * dy);
    }

    public static Vector2 source(float groundX, float groundY, float height, Vector2 out) {
        float x = LightingCoordinates.worldX(groundX, groundY, 0f);
        float y = LightingCoordinates.worldY(groundX, groundY, 0f);
        return out.set(LightingCoordinates.screenX(x, y), LightingCoordinates.screenY(x, y, height));
    }

    /** Samples the source-height range circle, starting at its rightmost projected point. */
    public static Vector2 pointSectionPoint(float sourceX, float sourceY, float angle,
                                            float radius, Vector2 out) {
        double worldAngle = angle - Math.PI * .25;
        float x = (float)Math.cos(worldAngle) * radius;
        float y = (float)Math.sin(worldAngle) * radius;
        return out.set(sourceX + LightingCoordinates.screenX(x, y),
                sourceY + LightingCoordinates.screenY(x, y, 0f));
    }

    /** Inverse of a horizontal section's projection, in the Runtime's range units. */
    public static float radiusFromScreenDelta(float dx, float dy) {
        float x = LightingCoordinates.worldX(dx, dy, 0f);
        float y = LightingCoordinates.worldY(dx, dy, 0f);
        return (float)Math.sqrt(x * x + y * y);
    }

    public static boolean heightHit(float dxPx, float dyPx) {
        return Math.abs(dxPx) <= 4f + PICK_TOL_PX
                && Math.abs(dyPx + HEIGHT_OFFSET_PX) <= HEIGHT_HALF_PX + PICK_TOL_PX;
    }

    public static boolean intensityHit(float dxPx, float dyPx) {
        return Math.abs(dxPx - INTENSITY_OFFSET_X_PX) <= INTENSITY_HALF_PX + PICK_TOL_PX
                && Math.abs(dyPx - INTENSITY_OFFSET_Y_PX) <= 5f + PICK_TOL_PX;
    }

    /** Keep the radius endpoint reachable when it coincides with a source control. */
    public static Vector2 radiusControl(float sourceX, float sourceY, float radiusX, float radiusY,
                                        float wpp, Vector2 out) {
        float dx=(radiusX-sourceX)/wpp,dy=(radiusY-sourceY)/wpp;
        // Only the small control moves; its contour and physical endpoint stay fixed.
        float radiusPickHalf=8f;
        for(int step=0;step<28 && (Math.abs(dx)<=24f && Math.abs(dy)<=24f
                || Math.abs(dx)<=4f+PICK_TOL_PX+radiusPickHalf
                    && Math.abs(dy+HEIGHT_OFFSET_PX)<=HEIGHT_HALF_PX+PICK_TOL_PX+radiusPickHalf
                || Math.abs(dx-INTENSITY_OFFSET_X_PX)<=INTENSITY_HALF_PX+PICK_TOL_PX+radiusPickHalf
                    && Math.abs(dy-INTENSITY_OFFSET_Y_PX)<=5f+PICK_TOL_PX+radiusPickHalf
                || ConeLightGizmoGeometry.inclinationControlNear(dx,dy,radiusPickHalf+PICK_TOL_PX));step++) dx+=8f;
        return out.set(sourceX+dx*wpp,sourceY+dy*wpp);
    }

    /** Converts a displayed ground translation through the resolved parent frame only. */
    public static Vector2 localGroundDelta(WorldTransformState state, int parent,
                                           float dx, float dy, Vector2 out) {
        if (state == null || parent < 0 || !state.isResolved(parent)) return out.set(dx, dy);
        float a=state.m00[parent], b=state.m01[parent], c=state.m10[parent], d=state.m11[parent];
        float determinant=a*d-b*c;
        return out.set((d*dx-b*dy)/determinant, (a*dy-c*dx)/determinant);
    }
}
