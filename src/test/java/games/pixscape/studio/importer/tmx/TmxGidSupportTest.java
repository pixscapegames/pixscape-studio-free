package games.pixscape.studio.importer.tmx;

import org.junit.Test;

import static org.junit.Assert.*;

public class TmxGidSupportTest {

    @Test
    public void decodesUnsignedTiledGidsAndClearsAllFlagBits() {
        assertDecoded(0, 0, false, false, false, false, true);
        assertDecoded(1, 1, false, false, false, false, false);
        assertDecoded((int) 0x80000001L, 1, true, false, false, false, false);
        assertDecoded((int) 0x40000001L, 1, false, true, false, false, false);
        assertDecoded((int) 0x20000001L, 1, false, false, true, false, false);
        assertDecoded((int) 0x10000001L, 1, false, false, false, true, false);
        assertDecoded((int) 0xF0000001L, 1, true, true, true, true, false);
        assertDecoded((int) 0x80000000L, 0, true, false, false, false, true);
    }

    private static void assertDecoded(int rawGid,
                                      int cleanGid,
                                      boolean flipH,
                                      boolean flipV,
                                      boolean flipD,
                                      boolean hex120,
                                      boolean empty) {
        TmxGidSupport.DecodedGid decoded = TmxGidSupport.decode(rawGid);
        assertEquals(cleanGid, decoded.cleanGid);
        assertEquals(flipH, decoded.flipH);
        assertEquals(flipV, decoded.flipV);
        assertEquals(flipD, decoded.flipD);
        assertEquals(hex120, decoded.hex120);
        assertEquals(empty, decoded.isEmpty());

        assertEquals(cleanGid, TmxGidSupport.cleanGid(rawGid));
        assertEquals(flipH, TmxGidSupport.horizontalFlip(rawGid));
        assertEquals(flipV, TmxGidSupport.verticalFlip(rawGid));
        assertEquals(flipD, TmxGidSupport.diagonalFlip(rawGid));
        assertEquals(hex120, TmxGidSupport.hexagonal120Flag(rawGid));
        if (flipH || flipV || flipD || hex120) {
            assertTrue(TmxGidSupport.hasTransformFlags(rawGid));
        } else {
            assertFalse(TmxGidSupport.hasTransformFlags(rawGid));
        }
    }
}
