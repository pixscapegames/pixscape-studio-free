package games.pixscape.studio.importer.tmx;

public final class TmxGidSupport {

    public static final int FLIPPED_HORIZONTALLY_FLAG = 0x80000000;
    public static final int FLIPPED_VERTICALLY_FLAG = 0x40000000;
    public static final int FLIPPED_DIAGONALLY_FLAG = 0x20000000;
    public static final int ROTATED_HEXAGONAL_120_FLAG = 0x10000000;
    public static final int CLEAN_GID_MASK = 0x0FFFFFFF;

    private TmxGidSupport() {
    }

    public static int cleanGid(int rawGid) {
        return rawGid & CLEAN_GID_MASK;
    }

    public static boolean hasTransformFlags(int rawGid) {
        return (rawGid & ~CLEAN_GID_MASK) != 0;
    }
}
