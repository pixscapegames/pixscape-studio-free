package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.tiled.TileTransformFlags;

final class TmxTileTransformSupport {
    private TmxTileTransformSupport() {
    }

    static byte toTileTransformFlags(TmxTransformPlan transform) {
        if (transform == null || !transform.hasTransformFlags()) {
            return TileTransformFlags.NONE;
        }
        return toTileTransformFlags(
                transform.horizontalFlip(),
                transform.verticalFlip(),
                transform.diagonalFlip()
        );
    }

    static byte toTileTransformFlags(boolean flipH, boolean flipV, boolean flipD) {
        byte flags = TileTransformFlags.NONE;
        if (flipH) flags |= TileTransformFlags.FLIP_H;
        if (flipV) flags |= TileTransformFlags.FLIP_V;
        if (flipD) flags |= TileTransformFlags.FLIP_D;
        return TileTransformFlags.sanitize(flags);
    }

    static void applyTiledTransform(float x,
                                    float y,
                                    boolean flipH,
                                    boolean flipV,
                                    boolean flipD,
                                    float[] out2) {
        if (out2 == null || out2.length < 2) {
            throw new IllegalArgumentException("out2 must contain at least two floats");
        }
        if (flipD) {
            float tmp = x;
            x = y;
            y = tmp;
        }
        if (flipH) {
            x = 1f - x;
        }
        if (flipV) {
            y = 1f - y;
        }
        out2[0] = x;
        out2[1] = y;
    }
}
