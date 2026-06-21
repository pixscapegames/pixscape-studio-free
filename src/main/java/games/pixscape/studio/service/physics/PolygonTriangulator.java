package games.pixscape.studio.service.physics;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;

public final class PolygonTriangulator {

    private PolygonTriangulator() {
    }

    public static PolygonBuildResult triangulate(float[] sourceVerts, int sourceCount) {
        PolygonValidationResult validation = PolygonValidator.validate(sourceVerts, sourceCount);
        if (!validation.isValid()) {
            return PolygonBuildResult.failure(validation);
        }

        float[] verts = PolygonValidator.copyCounterClockwise(sourceVerts, sourceCount);

        int[] indices = new int[sourceCount];
        for (int i = 0; i < sourceCount; i++) {
            indices[i] = i;
        }

        int remaining = sourceCount;
        int guard = sourceCount * sourceCount;

        Array<ConvexPolygonPartData> parts =
                new Array<>(true, Math.max(1, sourceCount - 2), ConvexPolygonPartData.class);

        while (remaining > 3) {
            boolean clipped = false;

            for (int i = 0; i < remaining; i++) {
                int prevSlot = (i + remaining - 1) % remaining;
                int nextSlot = (i + 1) % remaining;

                int prev = indices[prevSlot];
                int curr = indices[i];
                int next = indices[nextSlot];

                if (!isConvexCorner(verts, prev, curr, next)) {
                    continue;
                }

                if (containsAnyPointInsideTriangle(verts, indices, remaining, prev, curr, next)) {
                    continue;
                }

                parts.add(makeTriangle(verts, prev, curr, next));

                for (int k = i; k < remaining - 1; k++) {
                    indices[k] = indices[k + 1];
                }

                remaining--;
                clipped = true;
                break;
            }

            guard--;
            if (!clipped || guard <= 0) {
                return PolygonBuildResult.failure(
                        PolygonValidationResult.error(
                                PolygonValidationResult.TRIANGULATION_FAILED,
                                "Polygon triangulation failed."
                        )
                );
            }
        }

        parts.add(makeTriangle(verts, indices[0], indices[1], indices[2]));

        return PolygonBuildResult.success(
                verts,
                sourceCount,
                PolygonDecomposer.ALGORITHM_VERSION,
                PolygonHash.hash(verts, sourceCount),
                parts
        );
    }

    private static boolean isConvexCorner(float[] verts, int prev, int curr, int next) {
        float ax = PolygonValidator.x(verts, prev);
        float ay = PolygonValidator.y(verts, prev);

        float bx = PolygonValidator.x(verts, curr);
        float by = PolygonValidator.y(verts, curr);

        float cx = PolygonValidator.x(verts, next);
        float cy = PolygonValidator.y(verts, next);

        float abx = bx - ax;
        float aby = by - ay;
        float bcx = cx - bx;
        float bcy = cy - by;

        float cross = abx * bcy - aby * bcx;
        return cross > PolygonValidator.EPS;
    }

    private static boolean containsAnyPointInsideTriangle(
            float[] verts,
            int[] indices,
            int remaining,
            int a,
            int b,
            int c
    ) {
        float ax = PolygonValidator.x(verts, a);
        float ay = PolygonValidator.y(verts, a);

        float bx = PolygonValidator.x(verts, b);
        float by = PolygonValidator.y(verts, b);

        float cx = PolygonValidator.x(verts, c);
        float cy = PolygonValidator.y(verts, c);

        for (int i = 0; i < remaining; i++) {
            int p = indices[i];

            if (p == a || p == b || p == c) {
                continue;
            }

            float px = PolygonValidator.x(verts, p);
            float py = PolygonValidator.y(verts, p);

            if (pointInTriangle(ax, ay, bx, by, cx, cy, px, py)) {
                return true;
            }
        }

        return false;
    }

    private static boolean pointInTriangle(
            float ax, float ay,
            float bx, float by,
            float cx, float cy,
            float px, float py
    ) {
        float c1 = cross(ax, ay, bx, by, px, py);
        float c2 = cross(bx, by, cx, cy, px, py);
        float c3 = cross(cx, cy, ax, ay, px, py);

        return c1 >= -PolygonValidator.EPS
                && c2 >= -PolygonValidator.EPS
                && c3 >= -PolygonValidator.EPS;
    }

    private static float cross(
            float ax, float ay,
            float bx, float by,
            float cx, float cy
    ) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static ConvexPolygonPartData makeTriangle(float[] verts, int a, int b, int c) {
        ConvexPolygonPartData part = new ConvexPolygonPartData();
        part.count = 3;
        part.verts = new float[]{
                PolygonValidator.x(verts, a), PolygonValidator.y(verts, a),
                PolygonValidator.x(verts, b), PolygonValidator.y(verts, b),
                PolygonValidator.x(verts, c), PolygonValidator.y(verts, c)
        };
        return part;
    }
}