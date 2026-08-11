package games.pixscape.studio.service.physics;

import games.pixscape.runtime.physics.*;
import org.junit.Assert;
import org.junit.Test;

public class PolygonDecomposerTest {

    @Test
    public void convexPolygonUpToEightVerticesStaysSinglePart() {
        float[] square = {
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, 1f
        };

        PolygonBuildResult result = PolygonDecomposer.build(square, 4);

        Assert.assertTrue(result.isValid());
        Assert.assertEquals(1, result.parts().size);
        assertPartsAreBox2dSafeConvex(result);
    }

    @Test
    public void convexPolygonAboveEightVerticesIsDecomposedIntoMultipleParts() {
        float[] decagon = regularPolygon(10, 2f);

        PolygonBuildResult result = PolygonDecomposer.build(decagon, 10);

        Assert.assertTrue(result.isValid());
        Assert.assertTrue(result.parts().size > 1);
        assertPartsAreBox2dSafeConvex(result);
    }

    @Test
    public void concavePolygonIsTriangulatedIntoMultipleConvexParts() {
        float[] concave = {
                0f, 0f,
                4f, 0f,
                4f, 1f,
                2f, 0.5f,
                4f, 4f,
                0f, 4f
        };

        PolygonBuildResult result = PolygonDecomposer.build(concave, 6);

        Assert.assertTrue(result.isValid());
        Assert.assertTrue(result.parts().size > 1);
        assertPartsAreBox2dSafeConvex(result);
    }

    @Test
    public void invalidPolygonsAreRejectedByValidation() {
        PolygonBuildResult selfIntersecting = PolygonDecomposer.build(new float[] {
                0f, 0f,
                2f, 2f,
                0f, 2f,
                2f, 0f
        }, 4);
        Assert.assertFalse(selfIntersecting.isValid());
        Assert.assertTrue(
                selfIntersecting.validation().code() == PolygonValidationResult.SELF_INTERSECTION
                        || selfIntersecting.validation().code() == PolygonValidationResult.ZERO_AREA
        );

        PolygonBuildResult duplicateConsecutive = PolygonDecomposer.build(new float[] {
                0f, 0f,
                1f, 0f,
                1f, 0f,
                0f, 1f
        }, 4);
        Assert.assertFalse(duplicateConsecutive.isValid());
        Assert.assertEquals(PolygonValidationResult.DUPLICATE_VERTEX, duplicateConsecutive.validation().code());

        PolygonBuildResult zeroArea = PolygonDecomposer.build(new float[] {
                0f, 0f,
                1f, 1f,
                2f, 2f
        }, 3);
        Assert.assertFalse(zeroArea.isValid());
        Assert.assertEquals(PolygonValidationResult.ZERO_AREA, zeroArea.validation().code());
    }

    @Test
    public void windingIsNormalizedToCounterClockwise() {
        float[] clockwiseSquare = {
                -1f, -1f,
                -1f, 1f,
                1f, 1f,
                1f, -1f
        };

        PolygonBuildResult result = PolygonDecomposer.build(clockwiseSquare, 4);

        Assert.assertTrue(result.isValid());
        Assert.assertTrue(PolygonValidator.signedArea(
                result.sourceVertices(), result.sourceVertexCount()) > 0f);
    }

    private static void assertPartsAreBox2dSafeConvex(PolygonBuildResult result) {
        for (int i = 0; i < result.parts().size; i++) {
            PolygonPartData part = result.parts().get(i);
            Assert.assertNotNull(part);
            Assert.assertTrue(part.vertexCount >= 3);
            Assert.assertTrue(
                    part.vertexCount <= PolygonDecomposer.BOX2D_MAX_POLYGON_VERTICES);
            Assert.assertTrue(PolygonValidator.isConvex(part.vertices, part.vertexCount));
        }
    }

    private static float[] regularPolygon(int count, float radius) {
        float[] verts = new float[count * 2];
        for (int i = 0; i < count; i++) {
            double t = (Math.PI * 2d * i) / count;
            verts[i * 2] = (float) (Math.cos(t) * radius);
            verts[i * 2 + 1] = (float) (Math.sin(t) * radius);
        }
        return verts;
    }
}
