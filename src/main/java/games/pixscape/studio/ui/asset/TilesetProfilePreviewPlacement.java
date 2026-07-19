package games.pixscape.studio.ui.asset;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.TilesetAnchor;

final class TilesetProfilePreviewPlacement {

    private TilesetProfilePreviewPlacement() {
    }

    static Placement calculate(int tileWidth,
                               int tileHeight,
                               int referenceCellWidth,
                               int referenceCellHeight,
                               SceneMetaRuntime.TiledProjection projection,
                               TilesetAnchor anchor,
                               int offsetX,
                               int offsetY) {
        int safeTileWidth = Math.max(1, tileWidth);
        int safeTileHeight = Math.max(1, tileHeight);
        int safeReferenceCellWidth = Math.max(1, referenceCellWidth);
        int safeReferenceCellHeight = Math.max(1, referenceCellHeight);
        SceneMetaRuntime.TiledProjection safeProjection = projection != null
                ? projection
                : SceneMetaRuntime.TiledProjection.ORTHO;
        TilesetAnchor safeAnchor = anchor != null ? anchor : TilesetAnchor.TOP_CENTER;

        Bounds cellBounds = new Bounds(
                -safeReferenceCellWidth * 0.5f,
                0f,
                safeReferenceCellWidth,
                safeReferenceCellHeight
        );

        float tileAnchorX = switch (safeAnchor) {
            case BOTTOM_LEFT, TOP_LEFT -> 0f;
            case CENTER, TOP_CENTER, BOTTOM_CENTER -> safeTileWidth * 0.5f;
        };
        float tileAnchorY = switch (safeAnchor) {
            case TOP_LEFT, TOP_CENTER -> safeTileHeight;
            case CENTER -> safeTileHeight * 0.5f;
            case BOTTOM_CENTER, BOTTOM_LEFT -> 0f;
        };
        float cellAnchorX = switch (safeAnchor) {
            case BOTTOM_LEFT, TOP_LEFT -> cellBounds.left();
            case CENTER, TOP_CENTER, BOTTOM_CENTER -> 0f;
        };
        float cellAnchorY = switch (safeAnchor) {
            case TOP_LEFT, TOP_CENTER -> cellBounds.top();
            case CENTER -> cellBounds.bottom() + cellBounds.height() * 0.5f;
            case BOTTOM_CENTER, BOTTOM_LEFT -> cellBounds.bottom();
        };

        // Preview offsets use y-up local coordinates: +X moves right, +Y moves the tile upward.
        Bounds tileBounds = new Bounds(
                cellAnchorX + offsetX - tileAnchorX,
                cellAnchorY + offsetY - tileAnchorY,
                safeTileWidth,
                safeTileHeight
        );
        Bounds unionBounds = cellBounds.union(tileBounds);

        Point[] cellOutline = safeProjection == SceneMetaRuntime.TiledProjection.ISO
                ? new Point[]{
                new Point(0f, 0f),
                new Point(safeReferenceCellWidth * 0.5f, safeReferenceCellHeight * 0.5f),
                new Point(0f, safeReferenceCellHeight),
                new Point(-safeReferenceCellWidth * 0.5f, safeReferenceCellHeight * 0.5f)
        }
                : new Point[]{
                new Point(cellBounds.left(), cellBounds.bottom()),
                new Point(cellBounds.right(), cellBounds.bottom()),
                new Point(cellBounds.right(), cellBounds.top()),
                new Point(cellBounds.left(), cellBounds.top())
        };

        return new Placement(tileBounds, cellBounds, unionBounds, cellOutline, new Point(cellAnchorX, cellAnchorY));
    }

    record Placement(Bounds tileBounds,
                     Bounds cellBounds,
                     Bounds unionBounds,
                     Point[] cellOutline,
                     Point anchorPoint) {
    }

    record Bounds(float x, float y, float width, float height) {
        float left() {
            return x;
        }

        float right() {
            return x + width;
        }

        float bottom() {
            return y;
        }

        float top() {
            return y + height;
        }

        boolean contains(Bounds other) {
            return other != null
                    && left() <= other.left()
                    && right() >= other.right()
                    && bottom() <= other.bottom()
                    && top() >= other.top();
        }

        Bounds union(Bounds other) {
            if (other == null) return this;
            float minX = Math.min(left(), other.left());
            float minY = Math.min(bottom(), other.bottom());
            float maxX = Math.max(right(), other.right());
            float maxY = Math.max(top(), other.top());
            return new Bounds(minX, minY, maxX - minX, maxY - minY);
        }
    }

    record Point(float x, float y) {
    }
}
