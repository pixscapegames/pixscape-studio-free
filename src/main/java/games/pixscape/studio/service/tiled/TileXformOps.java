package games.pixscape.studio.service.tiled;

public final class TileXformOps {
    private TileXformOps() {
    }

    private static final byte[] FLIP_H = {
            1, 0, 3, 2, 5, 4, 7, 6
    };
    private static final byte[] FLIP_V = {
            2, 3, 0, 1, 6, 7, 4, 5
    };
    private static final byte[] ROT_CW = {
            5, 7, 4, 6, 1, 3, 0, 2
    };
    private static final byte[] ROT_CCW = {
            6, 4, 7, 5, 2, 0, 3, 1
    };

    public static byte flipH(byte flags) {
        return FLIP_H[flags & 0x7];
    }

    public static byte flipV(byte flags) {
        return FLIP_V[flags & 0x7];
    }

    public static byte rotCW(byte flags) {
        return ROT_CW[flags & 0x7];
    }

    public static byte rotCCW(byte flags) {
        return ROT_CCW[flags & 0x7];
    }
}
