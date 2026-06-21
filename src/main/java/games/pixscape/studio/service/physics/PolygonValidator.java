package games.pixscape.studio.service.physics;

public final class PolygonValidator {

    public static final float EPS = 1e-6f;

    private PolygonValidator() {
    }

    public static PolygonValidationResult validate(float[] verts, int count) {
        if (verts == null) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.NULL_VERTICES,
                    "Polygon vertices array is null."
            );
        }

        if (count < 3) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.NOT_ENOUGH_VERTICES,
                    "Polygon must contain at least 3 vertices."
            );
        }

        if (verts.length < count * 2) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.ARRAY_TOO_SMALL,
                    "Polygon vertices array is smaller than vertex count."
            );
        }

        for (int i = 0; i < count; i++) {
            float x = x(verts, i);
            float y = y(verts, i);

            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.NON_FINITE_VERTEX,
                        "Polygon contains a non-finite vertex."
                );
            }
        }

        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;

            if (samePoint(verts, i, next)) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DUPLICATE_VERTEX,
                        "Polygon contains duplicate consecutive vertices."
                );
            }

            float len2 = dist2(verts, i, next);
            if (len2 <= EPS * EPS) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DEGENERATE_EDGE,
                        "Polygon contains a degenerate edge."
                );
            }
        }

        float area = signedArea(verts, count);
        if (Math.abs(area) <= EPS) {
            return PolygonValidationResult.error(
                    PolygonValidationResult.ZERO_AREA,
                    "Polygon area is too small."
            );
        }

        for (int i = 0; i < count; i++) {
            int prev = (i + count - 1) % count;
            int next = (i + 1) % count;

            float cross = cross(verts, prev, i, next);
            if (Math.abs(cross) <= EPS) {
                return PolygonValidationResult.error(
                        PolygonValidationResult.DEGENERATE_ANGLE,
                        "Polygon contains a degenerate angle."
                );
            }
        }

        for (int i = 0; i < count; i++) {
            int i2 = (i + 1) % count;

            for (int j = i + 1; j < count; j++) {
                int j2 = (j + 1) % count;

                // Edges sharing a vertex are adjacent and allowed.
                if (i == j || i == j2 || i2 == j || i2 == j2) {
                    continue;
                }

                if (segmentsIntersect(
                        x(verts, i), y(verts, i),
                        x(verts, i2), y(verts, i2),
                        x(verts, j), y(verts, j),
                        x(verts, j2), y(verts, j2)
                )) {
                    return PolygonValidationResult.error(
                            PolygonValidationResult.SELF_INTERSECTION,
                            "Polygon has self-intersections."
                    );
                }
            }
        }

        return PolygonValidationResult.ok();
    }

    public static boolean isConvex(float[] verts, int count) {
        if (verts == null || count < 3 || verts.length < count * 2) {
            return false;
        }

        int sign = 0;

        for (int i = 0; i < count; i++) {
            int prev = (i + count - 1) % count;
            int next = (i + 1) % count;

            float c = cross(verts, prev, i, next);
            if (Math.abs(c) <= EPS) {
                return false;
            }

            int s = c > 0f ? 1 : -1;
            if (sign == 0) {
                sign = s;
            } else if (sign != s) {
                return false;
            }
        }

        return true;
    }

    public static float signedArea(float[] verts, int count) {
        if (verts == null || count < 3 || verts.length < count * 2) {
            return 0f;
        }

        float sum = 0f;

        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            sum += x(verts, i) * y(verts, j) - x(verts, j) * y(verts, i);
        }

        return sum * 0.5f;
    }

    public static float[] copyCounterClockwise(float[] verts, int count) {
        float[] out = new float[count * 2];

        if (signedArea(verts, count) >= 0f) {
            System.arraycopy(verts, 0, out, 0, count * 2);
            return out;
        }

        for (int i = 0; i < count; i++) {
            int src = count - 1 - i;
            out[i * 2] = x(verts, src);
            out[i * 2 + 1] = y(verts, src);
        }

        return out;
    }

    static float x(float[] verts, int index) {
        return verts[index * 2];
    }

    static float y(float[] verts, int index) {
        return verts[index * 2 + 1];
    }

    static float cross(float[] verts, int a, int b, int c) {
        float abx = x(verts, b) - x(verts, a);
        float aby = y(verts, b) - y(verts, a);
        float bcx = x(verts, c) - x(verts, b);
        float bcy = y(verts, c) - y(verts, b);

        return abx * bcy - aby * bcx;
    }

    private static boolean samePoint(float[] verts, int a, int b) {
        return dist2(verts, a, b) <= EPS * EPS;
    }

    private static float dist2(float[] verts, int a, int b) {
        float dx = x(verts, a) - x(verts, b);
        float dy = y(verts, a) - y(verts, b);
        return dx * dx + dy * dy;
    }

    private static boolean segmentsIntersect(
            float ax, float ay,
            float bx, float by,
            float cx, float cy,
            float dx, float dy
    ) {
        float o1 = orient(ax, ay, bx, by, cx, cy);
        float o2 = orient(ax, ay, bx, by, dx, dy);
        float o3 = orient(cx, cy, dx, dy, ax, ay);
        float o4 = orient(cx, cy, dx, dy, bx, by);

        if (o1 * o2 < -EPS && o3 * o4 < -EPS) {
            return true;
        }

        if (Math.abs(o1) <= EPS && onSegment(ax, ay, bx, by, cx, cy)) return true;
        if (Math.abs(o2) <= EPS && onSegment(ax, ay, bx, by, dx, dy)) return true;
        if (Math.abs(o3) <= EPS && onSegment(cx, cy, dx, dy, ax, ay)) return true;
        return Math.abs(o4) <= EPS && onSegment(cx, cy, dx, dy, bx, by);
    }

    private static float orient(
            float ax, float ay,
            float bx, float by,
            float cx, float cy
    ) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static boolean onSegment(
            float ax, float ay,
            float bx, float by,
            float px, float py
    ) {
        return px >= Math.min(ax, bx) - EPS
                && px <= Math.max(ax, bx) + EPS
                && py >= Math.min(ay, by) - EPS
                && py <= Math.max(ay, by) + EPS;
    }
}