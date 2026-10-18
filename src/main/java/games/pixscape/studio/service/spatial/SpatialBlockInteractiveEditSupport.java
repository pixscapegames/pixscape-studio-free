package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialWallGeometry;

/** Continuous transient editing within the linked-cell or existing physical envelope. */
public final class SpatialBlockInteractiveEditSupport {
    public enum ResizeHandle {
        MIN_Y, MAX_X, MAX_Y, MIN_X,
        MIN_X_MIN_Y, MAX_X_MIN_Y, MAX_X_MAX_Y, MIN_X_MAX_Y
    }

    private SpatialBlockInteractiveEditSupport() {
    }

    public static boolean move(SpatialBlockData wall, SpatialBlockData original, float dx, float dy) {
        if (wall == null || original == null || !SpatialWallGeometry.isFinite(dx) || !SpatialWallGeometry.isFinite(dy)) {
            return false;
        }
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        if (!SpatialWallGeometry.extractLinkedCellBounds(original, linked)) return false;
        // A vertical join can place physical faces outside their image-anchor cells.
        // Preserve that authored envelope instead of snapping a joined wall on gesture start.
        float maxX = Math.max(linked.maxGxExclusive, original.x + original.width) - original.width;
        float maxY = Math.max(linked.maxGyExclusive, original.y + original.depth) - original.depth;
        wall.x = clamp(original.x + dx, Math.min(linked.minGx, original.x), maxX);
        wall.y = clamp(original.y + dy, Math.min(linked.minGy, original.y), maxY);
        return Float.compare(wall.x, original.x) != 0 || Float.compare(wall.y, original.y) != 0;
    }

    public static boolean resize(SpatialBlockData wall,
                                 SpatialBlockData original,
                                 ResizeHandle handle,
                                 float gx,
                                 float gy) {
        if (wall == null || original == null || handle == null
                || !SpatialWallGeometry.isFinite(gx) || !SpatialWallGeometry.isFinite(gy)) return false;
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        if (!SpatialWallGeometry.extractLinkedCellBounds(original, linked)) return false;

        float minX = original.x;
        float minY = original.y;
        float maxX = original.x + original.width;
        float maxY = original.y + original.depth;
        float limitMinX = Math.min(linked.minGx, minX);
        float limitMinY = Math.min(linked.minGy, minY);
        float limitMaxX = Math.max(linked.maxGxExclusive, maxX);
        float limitMaxY = Math.max(linked.maxGyExclusive, maxY);
        switch (handle) {
            case MIN_Y:
                minY = clamp(gy, limitMinY, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxX);
                break;
            case MAX_Y:
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxY);
                break;
            case MIN_X:
                minX = clamp(gx, limitMinX, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MIN_X_MIN_Y:
                minX = clamp(gx, limitMinX, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                minY = clamp(gy, limitMinY, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X_MIN_Y:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxX);
                minY = clamp(gy, limitMinY, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X_MAX_Y:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxX);
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxY);
                break;
            case MIN_X_MAX_Y:
                minX = clamp(gx, limitMinX, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, limitMaxY);
                break;
        }
        wall.x = minX;
        wall.y = minY;
        wall.width = maxX - minX;
        wall.depth = maxY - minY;
        return Float.compare(wall.x, original.x) != 0 || Float.compare(wall.y, original.y) != 0
                || Float.compare(wall.width, original.width) != 0
                || Float.compare(wall.depth, original.depth) != 0;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
