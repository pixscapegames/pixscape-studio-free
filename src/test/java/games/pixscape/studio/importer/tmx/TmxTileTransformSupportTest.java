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
