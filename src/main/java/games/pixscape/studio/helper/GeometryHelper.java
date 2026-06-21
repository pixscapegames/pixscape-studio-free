package games.pixscape.studio.helper;

import com.badlogic.gdx.math.MathUtils;

public final class GeometryHelper {
    private GeometryHelper() {
    }

    public static float radToDeg(float rad) {
        return rad * MathUtils.radiansToDegrees;
    }

    public static float degToRad(float deg) {
        return deg * MathUtils.degreesToRadians;
    }

    /**
     * Editor conversion: engine radians -> UI degrees (clockwise).
     */
    public static float rotationRadToEditorDeg(float rotationRad) {
        float deg = radToDeg(rotationRad);
        if (deg > 180f) deg -= 360f;
        return -deg;
    }

    /**
     * Editor conversion: UI degrees (clockwise) -> engine radians.
     */
    public static float editorDegToRotationRad(float deg) {
        float normalized = ((-deg % 360f) + 360f) % 360f;
        return degToRad(normalized);
    }

    // AABB
    public static boolean pointInAABB(float px, float py, float minX, float minY, float maxX, float maxY) {
        return px >= minX && px <= maxX && py >= minY && py <= maxY;
    }

    // Oriented quad: center (cx,cy), unit axes u=(ux,uy), v=(vx,vy), half-extents hx,hy
    public static boolean pointInOrientedQuad(float px, float py,
                                              float cx, float cy,
                                              float ux, float uy,
                                              float vx, float vy,
                                              float hx, float hy) {
        float dx = px - cx, dy = py - cy;
        float du = dx * ux + dy * uy; // project onto u
        float dv = dx * vx + dy * vy; // project onto v
        return Math.abs(du) <= hx && Math.abs(dv) <= hy;
    }

    // World -> Local (coords in the quad frame, -hx..+hx / -hy..+hy)
    public static void worldToLocal(float px, float py,
                                    float cx, float cy,
                                    float ux, float uy,
                                    float vx, float vy,
                                    float[] out2) {
        float dx = px - cx, dy = py - cy;
        out2[0] = dx * ux + dy * uy; // u
        out2[1] = dx * vx + dy * vy; // v
    }

    // Fast SAT: AABB vs Quad (useful for rectangle selection)
    public static boolean aabbIntersectsQuad(float minX, float minY, float maxX, float maxY,
                                             float cx, float cy, float ux, float uy, float vx, float vy, float hx, float hy) {
        // axes to test: X, Y (AABB) + u, v (quad)
        // 1) AABB axes (X,Y): false if there is no immediate overlap
        if (maxX < cx - Math.abs(hx * ux) - Math.abs(hy * vx)) return false;
        if (minX > cx + Math.abs(hx * ux) + Math.abs(hy * vx)) return false;
        if (maxY < cy - Math.abs(hx * uy) - Math.abs(hy * vy)) return false;
        if (minY > cy + Math.abs(hx * uy) + Math.abs(hy * vy)) return false;
        // 2) quad axes (u,v): project AABB onto u/v and test interval overlap
        // quad interval on u/v is [-hx, +hx] and [-hy, +hy] around 0
        // project the 4 AABB corners onto u then v
        float[] uproj = new float[4];
        float[] vproj = new float[4];
        float[] xs = {minX, maxX, maxX, minX};
        float[] ys = {minY, minY, maxY, maxY};
        float umin = Float.POSITIVE_INFINITY, umax = Float.NEGATIVE_INFINITY;
        float vmin = Float.POSITIVE_INFINITY, vmax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float dx = xs[i] - cx, dy = ys[i] - cy;
            float pu = dx * ux + dy * uy;
            float pv = dx * vx + dy * vy;
            if (pu < umin) umin = pu;
            if (pu > umax) umax = pu;
            if (pv < vmin) vmin = pv;
            if (pv > vmax) vmax = pv;
        }
        return !(umax < -hx || umin > +hx || vmax < -hy || vmin > +hy);
    }

    // Ray vs segment (for gizmo, if needed)
    public static boolean rayIntersectSegment(float rx, float ry, float rdx, float rdy,
                                              float x1, float y1, float x2, float y2,
                                              float[] outT) {
        float sx = x2 - x1, sy = y2 - y1;
        float denom = rdx * sy - rdy * sx;
        if (Math.abs(denom) < 1e-6f) return false; // parallel
        float dx = x1 - rx, dy = y1 - ry;
        float t = (dx * sy - dy * sx) / denom;
        float u = (dx * rdy - dy * rdx) / denom;
        if (t >= 0f && u >= 0f && u <= 1f) {
            if (outT != null) outT[0] = t;
            return true;
        }
        return false;
    }

    /**
     * Squared distance from point P to segment AB. Optional: outT[0]=t (0..1) of the projected point.
     */
    public static float dst2PointSegment(float px, float py,
                                         float ax, float ay, float bx, float by,
                                         float[] outT /* nullable */) {
        float abx = bx - ax, aby = by - ay;
        float apx = px - ax, apy = py - ay;
        float ab2 = abx * abx + aby * aby;
        if (ab2 <= 1e-12f) {
            if (outT != null) outT[0] = 0f;
            return apx * apx + apy * apy;
        }
        float t = (apx * abx + apy * aby) / ab2;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;
        float cx = ax + abx * t;
        float cy = ay + aby * t;
        float dx = px - cx, dy = py - cy;
        if (outT != null) outT[0] = t;
        return dx * dx + dy * dy;
    }
}
