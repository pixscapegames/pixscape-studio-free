package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialWallGeometry;

/** Continuous transient footprint editing constrained by the authored linked-cell envelope. */
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
        float maxX = linked.maxGxExclusive - original.width;
        float maxY = linked.maxGyExclusive - original.depth;
        wall.x = clamp(original.x + dx, linked.minGx, maxX);
        wall.y = clamp(original.y + dy, linked.minGy, maxY);
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
        switch (handle) {
            case MIN_Y:
                minY = clamp(gy, linked.minGy, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGxExclusive);
                break;
            case MAX_Y:
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGyExclusive);
                break;
            case MIN_X:
                minX = clamp(gx, linked.minGx, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MIN_X_MIN_Y:
                minX = clamp(gx, linked.minGx, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                minY = clamp(gy, linked.minGy, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X_MIN_Y:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGxExclusive);
                minY = clamp(gy, linked.minGy, maxY - SpatialWallGeometry.GEOMETRY_EPSILON);
                break;
            case MAX_X_MAX_Y:
                maxX = clamp(gx, minX + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGxExclusive);
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGyExclusive);
                break;
            case MIN_X_MAX_Y:
                minX = clamp(gx, linked.minGx, maxX - SpatialWallGeometry.GEOMETRY_EPSILON);
                maxY = clamp(gy, minY + SpatialWallGeometry.GEOMETRY_EPSILON, linked.maxGyExclusive);
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
