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

    /**
     * Decomposes the same centered world-space H/V/D matrix used by TileQuadTransforms into
     * the rotation, signed scale and compensated local origin used by regular sprite entities.
     */
    static TileObjectTransform decomposeTileObject(float width,
                                                   float height,
                                                   float baseOriginX,
                                                   float baseOriginY,
                                                   TmxTransformPlan transform) {
        boolean h = transform != null && transform.horizontalFlip();
        boolean v = transform != null && transform.verticalFlip();
        boolean d = transform != null && transform.diagonalFlip();

        float m00;
        float m01;
        float m10;
        float m11;
        if (d) {
            // Tiled diagonal in Pixscape's Y-up basis: (x,y) -> (-y,-x), then H, then V.
            m00 = 0f;
            m01 = h ? 1f : -1f;
            m10 = v ? 1f : -1f;
            m11 = 0f;
        } else {
            m00 = h ? -1f : 1f;
            m01 = 0f;
            m10 = 0f;
            m11 = v ? -1f : 1f;
        }

        float scaleX;
        float scaleY;
        float rotationOffset;
        if (d) {
            scaleX = m00 * m11 - m01 * m10;
            scaleY = 1f;
            float rotationM00 = m00 * scaleX;
            float rotationM10 = m10 * scaleX;
            rotationOffset = (float) Math.atan2(rotationM10, rotationM00);
        } else {
            scaleX = m00;
            scaleY = m11;
            rotationOffset = 0f;
        }

        float centerX = width * 0.5f;
        float centerY = height * 0.5f;
        float relativeCenterX = centerX - baseOriginX;
        float relativeCenterY = centerY - baseOriginY;
        // M is orthogonal, so M^-1 = transpose(M).
        float inverseCenterX = m00 * relativeCenterX + m10 * relativeCenterY;
        float inverseCenterY = m01 * relativeCenterX + m11 * relativeCenterY;
        float originX = centerX - inverseCenterX;
        float originY = centerY - inverseCenterY;

        return new TileObjectTransform(
                rotationOffset, scaleX, scaleY, originX, originY,
                m00, m01, m10, m11);
    }

    record TileObjectTransform(float rotationOffsetRad,
                               float scaleX,
                               float scaleY,
                               float originX,
                               float originY,
                               float matrix00,
                               float matrix01,
                               float matrix10,
                               float matrix11) {
    }
}
