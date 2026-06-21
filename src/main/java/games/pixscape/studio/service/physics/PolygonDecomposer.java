package games.pixscape.studio.service.physics;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;

public final class PolygonDecomposer {

    public static final int ALGORITHM_VERSION = 1;
    public static final int BOX2D_MAX_POLYGON_VERTICES = 8;

    private PolygonDecomposer() {
    }

    public static PolygonBuildResult build(float[] sourceVerts, int sourceCount) {
        PolygonValidationResult validation = PolygonValidator.validate(sourceVerts, sourceCount);
        if (!validation.isValid()) {
            return PolygonBuildResult.failure(validation);
        }

        float[] ccw = PolygonValidator.copyCounterClockwise(sourceVerts, sourceCount);
        long hash = PolygonHash.hash(ccw, sourceCount);

        if (sourceCount <= BOX2D_MAX_POLYGON_VERTICES
                && PolygonValidator.isConvex(ccw, sourceCount)) {

            Array<ConvexPolygonPartData> parts =
                    new Array<>(true, 1, ConvexPolygonPartData.class);

            ConvexPolygonPartData part = new ConvexPolygonPartData();
            part.count = sourceCount;
            part.verts = copy(ccw, sourceCount);

            parts.add(part);

            return PolygonBuildResult.success(
                    ccw,
                    sourceCount,
                    ALGORITHM_VERSION,
                    hash,
                    parts
            );
        }

        return PolygonTriangulator.triangulate(ccw, sourceCount);
    }

    private static float[] copy(float[] verts, int count) {
        float[] out = new float[count * 2];
        System.arraycopy(verts, 0, out, 0, out.length);
        return out;
    }
}