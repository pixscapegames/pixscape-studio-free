package games.pixscape.studio.helper;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.render.lighting.LightingCoordinates;

/** Projected cone sections and the screen-sized inclination control, never ground footprints. */
public final class ConeLightGizmoGeometry {
    public static final float INCLINATION_OFFSET_X_PX = -80f;
    public static final float INCLINATION_OFFSET_Y_PX = 48f;
    public static final float INCLINATION_RADIUS_PX = 28f;
    public static final float INCLINATION_PICK_PX = 8f;
    private ConeLightGizmoGeometry() {}

    public static Vector2 sectionPoint(float sourceX, float sourceY, float screenDirection,
                                       float angularOffset, float radius, Vector2 out) {
        float sx=MathUtils.cos(screenDirection),sy=MathUtils.sin(screenDirection);
        // Same inverse projection and normalization as LightingGeometrySystem.publishLight.
        float dx=LightingCoordinates.worldX(sx,sy,0f),dy=LightingCoordinates.worldY(sx,sy,0f);
        float length=(float)Math.sqrt(dx*dx+dy*dy);
        dx/=length;dy/=length;
        float cos=(float)Math.cos(angularOffset),sin=(float)Math.sin(angularOffset);
        float x=(cos*dx-sin*dy)*radius,y=(sin*dx+cos*dy)*radius;
        return out.set(sourceX+LightingCoordinates.screenX(x,y),
                sourceY+LightingCoordinates.screenY(x,y,0f));
    }

    /** Meridian containing the inclined axis and its horizontal transverse direction. */
    public static Vector2 tiltedSectionPoint(float sourceX, float sourceY, float screenDirection,
                                             float inclinationDeg, float angularOffset,
                                             float radius, Vector2 out) {
        float sx = MathUtils.cos(screenDirection), sy = MathUtils.sin(screenDirection);
        float dx = LightingCoordinates.worldX(sx, sy, 0f), dy = LightingCoordinates.worldY(sx, sy, 0f);
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        dx /= length; dy /= length;
        float horizontal = Math.abs(inclinationDeg) == 90f ? 0f : MathUtils.cosDeg(inclinationDeg);
        float cos = (float)Math.cos(angularOffset), sin = (float)Math.sin(angularOffset);
        float x = (dx * horizontal * cos - dy * sin) * radius;
        float y = (dy * horizontal * cos + dx * sin) * radius;
        float z = MathUtils.sinDeg(inclinationDeg) * cos * radius;
        return out.set(sourceX + LightingCoordinates.screenX(x, y),
                sourceY + LightingCoordinates.screenY(x, y, z));
    }

    public static Vector2 inclinationArcPoint(float sourceX, float sourceY, float screenDirection,
                                               float inclinationDeg, float wpp, Vector2 out) {
        return tiltedSectionPoint(sourceX + INCLINATION_OFFSET_X_PX * wpp,
                sourceY + INCLINATION_OFFSET_Y_PX * wpp, screenDirection, inclinationDeg,
                0f, INCLINATION_RADIUS_PX * wpp, out);
    }

    /** A vertical meridian can be viewed edge-on; then use relative vertical dragging. */
    public static boolean inclinationEdgeOn(float screenDirection) {
        return Math.abs(MathUtils.cos(screenDirection)) < .15f;
    }

    public static boolean inclinationControlNear(float dxPx, float dyPx, float extraPx) {
        float half = INCLINATION_RADIUS_PX * 1.5f + extraPx;
        return Math.abs(dxPx - INCLINATION_OFFSET_X_PX) <= half
                && Math.abs(dyPx - INCLINATION_OFFSET_Y_PX) <= half;
    }

    public static float inclinationPointerAngle(float sourceX, float sourceY, float screenDirection,
                                                 float wpp, float pointerX, float pointerY) {
        float sx = MathUtils.cos(screenDirection), sy = MathUtils.sin(screenDirection);
        float length = (float)Math.sqrt(2f * sy * sy + .5f * sx * sx);
        float axisX = sx / length, axisY = sy / length;
        float x = (pointerX - sourceX) / wpp - INCLINATION_OFFSET_X_PX;
        float y = (pointerY - sourceY) / wpp - INCLINATION_OFFSET_Y_PX;
        float along = x / axisX;
        return (float)Math.atan2(y - along * axisY, along);
    }
}
