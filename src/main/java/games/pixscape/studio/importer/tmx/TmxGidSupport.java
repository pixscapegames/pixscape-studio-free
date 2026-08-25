package games.pixscape.studio.importer.tmx;

import java.util.List;
import java.util.function.ToIntFunction;

public final class TmxGidSupport {

    public static final long FLIPPED_HORIZONTALLY_FLAG = 0x80000000L;
    public static final long FLIPPED_VERTICALLY_FLAG = 0x40000000L;
    public static final long FLIPPED_DIAGONALLY_FLAG = 0x20000000L;
    public static final long ROTATED_HEXAGONAL_120_FLAG = 0x10000000L;
    public static final long GID_FLAGS_MASK =
            FLIPPED_HORIZONTALLY_FLAG
                    | FLIPPED_VERTICALLY_FLAG
                    | FLIPPED_DIAGONALLY_FLAG
                    | ROTATED_HEXAGONAL_120_FLAG;

    private TmxGidSupport() {
    }

    public static int cleanGid(int rawGid) {
        return decode(rawGid).cleanGid;
    }

    public static boolean hasTransformFlags(int rawGid) {
        return (unsigned(rawGid) & GID_FLAGS_MASK) != 0;
    }

    public static boolean horizontalFlip(int rawGid) {
        return decode(rawGid).flipH;
    }

    public static boolean verticalFlip(int rawGid) {
        return decode(rawGid).flipV;
    }

    public static boolean diagonalFlip(int rawGid) {
        return decode(rawGid).flipD;
    }

    public static boolean hexagonal120Flag(int rawGid) {
        return decode(rawGid).hex120;
    }

    public static DecodedGid decode(int rawGid) {
        long raw = unsigned(rawGid);

        boolean flipH = (raw & FLIPPED_HORIZONTALLY_FLAG) != 0;
        boolean flipV = (raw & FLIPPED_VERTICALLY_FLAG) != 0;
        boolean flipD = (raw & FLIPPED_DIAGONALLY_FLAG) != 0;
        boolean hex120 = (raw & ROTATED_HEXAGONAL_120_FLAG) != 0;

        int cleanGid = (int) (raw & ~GID_FLAGS_MASK);

        return new DecodedGid(cleanGid, flipH, flipV, flipD, hex120);
    }

    static <T> T resolveTileset(int cleanGid,
                                List<T> tilesets,
                                ToIntFunction<T> firstGid,
                                ToIntFunction<T> tileCount) {
        T resolved = null;
        for (T tileset : tilesets) {
            if (firstGid.applyAsInt(tileset) <= cleanGid) resolved = tileset;
            else break;
        }
        if (resolved == null) return null;
        int first = firstGid.applyAsInt(resolved);
        long lastExclusive = first + (long) Math.max(tileCount.applyAsInt(resolved), 0);
        return cleanGid >= first && cleanGid < lastExclusive ? resolved : null;
    }

    private static long unsigned(int rawGid) {
        return rawGid & 0xFFFFFFFFL;
    }

    public static final class DecodedGid {
        public final int cleanGid;
        public final boolean flipH;
        public final boolean flipV;
        public final boolean flipD;
        public final boolean hex120;

        private DecodedGid(int cleanGid, boolean flipH, boolean flipV, boolean flipD, boolean hex120) {
            this.cleanGid = cleanGid;
            this.flipH = flipH;
            this.flipV = flipV;
            this.flipD = flipD;
            this.hex120 = hex120;
        }

        public boolean isEmpty() {
            return cleanGid == 0;
        }
    }
}
