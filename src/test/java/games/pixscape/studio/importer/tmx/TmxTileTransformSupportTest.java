package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.tiled.TileTransformFlags;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TmxTileTransformSupportTest {

    @Test
    public void mapsAllTiledFlagCombinationsToPixscapeTransformFlags() {
        assertFlags(false, false, false, TileTransformFlags.NONE);
        assertFlags(true, false, false, TileTransformFlags.FLIP_H);
        assertFlags(false, true, false, TileTransformFlags.FLIP_V);
        assertFlags(true, true, false, TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V);
        assertFlags(false, false, true, TileTransformFlags.FLIP_D);
        assertFlags(true, false, true, TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_D);
        assertFlags(false, true, true, TileTransformFlags.FLIP_V | TileTransformFlags.FLIP_D);
        assertFlags(true, true, true, TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V | TileTransformFlags.FLIP_D);
    }

    @Test
    public void tiledLocalTransformAppliesDiagonalThenHorizontalThenVertical() {
        assertTransform(false, false, false, 0f, 0f, 0f, 0f);
        assertTransform(true, false, false, 0f, 0f, 1f, 0f);
        assertTransform(false, true, false, 0f, 0f, 0f, 1f);
        assertTransform(true, true, false, 0f, 0f, 1f, 1f);
        assertTransform(false, false, true, 0f, 1f, 1f, 0f);
        assertTransform(true, false, true, 0f, 1f, 0f, 0f);
        assertTransform(false, true, true, 0f, 1f, 1f, 1f);
        assertTransform(true, true, true, 0f, 1f, 0f, 1f);
    }

    @Test
    public void decomposedTileObjectTransformMatchesAllEightAuthoritativeMatrices() {
        for (int bits = 0; bits < 8; bits++) {
            boolean h = (bits & 1) != 0;
            boolean v = (bits & 2) != 0;
            boolean d = (bits & 4) != 0;
            assertObjectTransform(h, v, d);
        }
    }

    private static void assertObjectTransform(boolean h, boolean v, boolean d) {
        float width = 34f;
        float height = 18f;
        float baseOriginX = 12f;
        float baseOriginY = 7f;
        float objectRotation = 0.37f;
        float anchorX = 81f;
        float anchorY = 43f;
        TmxTransformPlan plan = new TmxTransformPlan(h || v || d, h, v, d, false);
        TmxTileTransformSupport.TileObjectTransform decomposition =
                TmxTileTransformSupport.decomposeTileObject(
                        width, height, baseOriginX, baseOriginY, plan);

        float cosObject = (float) Math.cos(objectRotation);
        float sinObject = (float) Math.sin(objectRotation);
        float totalRotation = objectRotation + decomposition.rotationOffsetRad();
        float cosActual = (float) Math.cos(totalRotation);
        float sinActual = (float) Math.sin(totalRotation);
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float relativeCenterX = centerX - baseOriginX;
        float relativeCenterY = centerY - baseOriginY;
        float[] corners = {0f, 0f, 0f, height, width, height, width, 0f};
        for (int i = 0; i < corners.length; i += 2) {
            float centeredX = corners[i] - centerX;
            float centeredY = corners[i + 1] - centerY;
            float transformedX = decomposition.matrix00() * centeredX
                    + decomposition.matrix01() * centeredY + relativeCenterX;
            float transformedY = decomposition.matrix10() * centeredX
                    + decomposition.matrix11() * centeredY + relativeCenterY;
            float expectedX = anchorX + cosObject * transformedX - sinObject * transformedY;
            float expectedY = anchorY + sinObject * transformedX + cosObject * transformedY;

            float dx = (corners[i] - decomposition.originX()) * decomposition.scaleX();
            float dy = (corners[i + 1] - decomposition.originY()) * decomposition.scaleY();
            float actualX = anchorX + cosActual * dx - sinActual * dy;
            float actualY = anchorY + sinActual * dx + cosActual * dy;
            assertEquals("x for HVD bits " + (h ? 1 : 0) + (v ? 1 : 0) + (d ? 1 : 0),
                    expectedX, actualX, 0.001f);
            assertEquals("y for HVD bits " + (h ? 1 : 0) + (v ? 1 : 0) + (d ? 1 : 0),
                    expectedY, actualY, 0.001f);
        }
    }

    private static void assertFlags(boolean flipH, boolean flipV, boolean flipD, int expectedFlags) {
        assertEquals(
                TileTransformFlags.sanitize((byte) expectedFlags),
                TmxTileTransformSupport.toTileTransformFlags(flipH, flipV, flipD)
        );
    }

    private static void assertTransform(boolean flipH,
                                        boolean flipV,
                                        boolean flipD,
                                        float x,
                                        float y,
                                        float expectedX,
                                        float expectedY) {
        float[] out = new float[2];
        TmxTileTransformSupport.applyTiledTransform(x, y, flipH, flipV, flipD, out);
        assertEquals(expectedX, out[0], 0.0001f);
        assertEquals(expectedY, out[1], 0.0001f);
    }
}
