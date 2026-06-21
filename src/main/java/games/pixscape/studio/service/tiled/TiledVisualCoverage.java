package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class TiledVisualCoverage {
    private static final float TILE_EPSILON = 0.0001f;

    private TiledVisualCoverage() {
    }

    public static Coverage compute(TiledMapLayerData map,
                                   int anchorGX,
                                   int anchorGY,
                                   int pixW,
                                   int pixH,
                                   byte flags) {
        Coverage out = new Coverage();
        out.anchorGX = anchorGX;
        out.anchorGY = anchorGY;

        if (map == null || !map.isInside(anchorGX, anchorGY)) {
            out.setRange(anchorGX, anchorGY, anchorGX + 1, anchorGY + 1);
            return out;
        }

        if (pixW <= map.tileWidth && pixH <= map.tileHeight) {
            out.setRange(anchorGX, anchorGY, anchorGX + 1, anchorGY + 1);
            return out;
        }

        float[] quad = new float[8];
        TileQuadTransforms.buildSpriteQuad(
                map,
                anchorGX,
                anchorGY,
                Math.max(1, pixW),
                Math.max(1, pixH),
                TileTransformFlags.sanitize(flags),
                quad
        );

        float minX = min(quad[0], quad[2], quad[4], quad[6]);
        float maxX = max(quad[0], quad[2], quad[4], quad[6]);
        float minY = min(quad[1], quad[3], quad[5], quad[7]);
        float maxY = max(quad[1], quad[3], quad[5], quad[7]);

        float[] xs = {
                map.projectWorldToTileX(minX, minY),
                map.projectWorldToTileX(minX, maxY),
                map.projectWorldToTileX(maxX, minY),
                map.projectWorldToTileX(maxX, maxY)
        };
        float[] ys = {
                map.projectWorldToTileY(minX, minY),
                map.projectWorldToTileY(minX, maxY),
                map.projectWorldToTileY(maxX, minY),
                map.projectWorldToTileY(maxX, maxY)
        };

        int minGX = clamp(minInclusive(min(xs)), 0, Math.max(0, map.mapWidth - 1));
        int maxGXExclusive = clamp(maxExclusive(max(xs)), minGX + 1, map.mapWidth);
        int minGY = clamp(minInclusive(min(ys)), 0, Math.max(0, map.mapHeight - 1));
        int maxGYExclusive = clamp(maxExclusive(max(ys)), minGY + 1, map.mapHeight);

        out.setRange(minGX, minGY, maxGXExclusive, maxGYExclusive);
        return out;
    }

    private static int minInclusive(float projectedMin) {
        return (int) Math.floor(projectedMin + TILE_EPSILON);
    }

    private static int maxExclusive(float projectedMax) {
        return (int) Math.ceil(projectedMax - TILE_EPSILON);
    }

    private static float min(float... values) {
        float out = Float.POSITIVE_INFINITY;
        for (float value : values) out = Math.min(out, value);
        return out;
    }

    private static float max(float... values) {
        float out = Float.NEGATIVE_INFINITY;
        for (float value : values) out = Math.max(out, value);
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Coverage {
        public int anchorGX;
        public int anchorGY;
        public int minGX;
        public int minGY;
        public int maxGX;
        public int maxGY;
        public int maxGXExclusive;
        public int maxGYExclusive;

        public void setRange(int minGX, int minGY, int maxGXExclusive, int maxGYExclusive) {
            this.minGX = minGX;
            this.minGY = minGY;
            this.maxGXExclusive = Math.max(minGX + 1, maxGXExclusive);
            this.maxGYExclusive = Math.max(minGY + 1, maxGYExclusive);
            this.maxGX = this.maxGXExclusive - 1;
            this.maxGY = this.maxGYExclusive - 1;
        }

        public boolean isAnchorInside() {
            return anchorGX >= minGX && anchorGX <= maxGX && anchorGY >= minGY && anchorGY <= maxGY;
        }

        public boolean contains(int gx, int gy) {
            return gx >= minGX && gx < maxGXExclusive && gy >= minGY && gy < maxGYExclusive;
        }
    }
}
