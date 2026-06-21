package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;

public final class SpatialBlockInteractiveEditSupport {
    private static final float OVERLAP_EPSILON = 0.0001f;

    public enum CornerHandle {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }

    private SpatialBlockInteractiveEditSupport() {
    }

    public static boolean moveByIfValid(SpatialBlockData block,
                                        SpatialBlocksComponent blocks,
                                        int ignoredBlockId,
                                        float dx,
                                        float dy) {
        if (block == null) return false;
        SpatialBlockData before = block.copy();
        float beforeOverlap = overlapArea(block, blocks, ignoredBlockId);

        if (tryMove(block, blocks, ignoredBlockId, before, beforeOverlap, dx, dy)) return true;

        boolean movedX = dx != 0f && tryMove(block, blocks, ignoredBlockId, before, beforeOverlap, dx, 0f);
        float xOverlap = movedX ? overlapArea(block, blocks, ignoredBlockId) : Float.MAX_VALUE;

        applySnapshot(block, before);
        boolean movedY = dy != 0f && tryMove(block, blocks, ignoredBlockId, before, beforeOverlap, 0f, dy);
        float yOverlap = movedY ? overlapArea(block, blocks, ignoredBlockId) : Float.MAX_VALUE;

        if (movedX && (!movedY || xOverlap <= yOverlap)) {
            applySnapshot(block, before);
            block.x += dx;
            return true;
        }

        if (movedY) {
            return true;
        }

        applySnapshot(block, before);
        return false;
    }

    public static boolean resizeCornerIfValid(SpatialBlockData block,
                                              SpatialBlocksComponent blocks,
                                              int ignoredBlockId,
                                              CornerHandle handle,
                                              float gx,
                                              float gy) {
        if (block == null || handle == null) return false;
        SpatialBlockData before = block.copy();
        float beforeOverlap = overlapArea(block, blocks, ignoredBlockId);

        applyCornerResize(block, handle, gx, gy);
        if (isOverlapAcceptable(beforeOverlap, overlapArea(block, blocks, ignoredBlockId))) return true;

        applySnapshot(block, before);

        boolean resizedFirstAxis = tryResizeSingleAxis(block, blocks, ignoredBlockId, before, beforeOverlap, handle, gx, gy, true);
        float firstAxisOverlap = resizedFirstAxis ? overlapArea(block, blocks, ignoredBlockId) : Float.MAX_VALUE;

        applySnapshot(block, before);
        boolean resizedSecondAxis = tryResizeSingleAxis(block, blocks, ignoredBlockId, before, beforeOverlap, handle, gx, gy, false);
        float secondAxisOverlap = resizedSecondAxis ? overlapArea(block, blocks, ignoredBlockId) : Float.MAX_VALUE;

        if (resizedFirstAxis && (!resizedSecondAxis || firstAxisOverlap <= secondAxisOverlap)) {
            applySnapshot(block, before);
            applyCornerResize(block, handle, firstAxisGx(before, handle, gx), firstAxisGy(before, handle, gy));
            return true;
        }

        if (resizedSecondAxis) {
            return true;
        }

        applySnapshot(block, before);
        return false;
    }

    public static void applyCornerResize(SpatialBlockData block,
                                         CornerHandle handle,
                                         float gx,
                                         float gy) {
        if (block == null || handle == null) return;

        float minX = block.x;
        float minY = block.y;
        float maxX = block.x + Math.max(0.1f, block.width);
        float maxY = block.y + Math.max(0.1f, block.depth);

        switch (handle) {
            case TOP -> {
                minX = gx;
                minY = gy;
            }
            case RIGHT -> {
                maxX = gx;
                minY = gy;
            }
            case BOTTOM -> {
                maxX = gx;
                maxY = gy;
            }
            case LEFT -> {
                minX = gx;
                maxY = gy;
            }
        }

        float normalizedMinX = Math.min(minX, maxX - 0.1f);
        float normalizedMinY = Math.min(minY, maxY - 0.1f);
        float normalizedMaxX = Math.max(maxX, normalizedMinX + 0.1f);
        float normalizedMaxY = Math.max(maxY, normalizedMinY + 0.1f);

        block.x = normalizedMinX;
        block.y = normalizedMinY;
        block.width = normalizedMaxX - normalizedMinX;
        block.depth = normalizedMaxY - normalizedMinY;
    }

    private static void applySnapshot(SpatialBlockData target, SpatialBlockData source) {
        target.x = source.x;
        target.y = source.y;
        target.width = source.width;
        target.depth = source.depth;
        target.height = source.height;
    }

    private static boolean tryMove(SpatialBlockData block,
                                   SpatialBlocksComponent blocks,
                                   int ignoredBlockId,
                                   SpatialBlockData before,
                                   float beforeOverlap,
                                   float dx,
                                   float dy) {
        applySnapshot(block, before);
        block.x += dx;
        block.y += dy;
        if (isOverlapAcceptable(beforeOverlap, overlapArea(block, blocks, ignoredBlockId))) {
            return true;
        }
        applySnapshot(block, before);
        return false;
    }

    private static boolean tryResizeSingleAxis(SpatialBlockData block,
                                               SpatialBlocksComponent blocks,
                                               int ignoredBlockId,
                                               SpatialBlockData before,
                                               float beforeOverlap,
                                               CornerHandle handle,
                                               float gx,
                                               float gy,
                                               boolean firstAxis) {
        float partialGx = firstAxis ? firstAxisGx(before, handle, gx) : secondAxisGx(before, handle, gx);
        float partialGy = firstAxis ? firstAxisGy(before, handle, gy) : secondAxisGy(before, handle, gy);
        applyCornerResize(block, handle, partialGx, partialGy);
        if (!geometryChanged(block, before)) {
            applySnapshot(block, before);
            return false;
        }
        if (isOverlapAcceptable(beforeOverlap, overlapArea(block, blocks, ignoredBlockId))) {
            return true;
        }
        applySnapshot(block, before);
        return false;
    }

    private static float firstAxisGx(SpatialBlockData before, CornerHandle handle, float gx) {
        return switch (handle) {
            case TOP, LEFT -> gx;
            case RIGHT, BOTTOM -> gx;
        };
    }

    private static float firstAxisGy(SpatialBlockData before, CornerHandle handle, float gy) {
        return switch (handle) {
            case TOP -> before.y;
            case RIGHT -> before.y;
            case BOTTOM -> before.y + before.depth;
            case LEFT -> before.y + before.depth;
        };
    }

    private static float secondAxisGx(SpatialBlockData before, CornerHandle handle, float gx) {
        return switch (handle) {
            case TOP -> before.x;
            case RIGHT -> before.x + before.width;
            case BOTTOM -> before.x + before.width;
            case LEFT -> before.x;
        };
    }

    private static float secondAxisGy(SpatialBlockData before, CornerHandle handle, float gy) {
        return switch (handle) {
            case TOP, RIGHT, BOTTOM, LEFT -> gy;
        };
    }

    private static boolean isOverlapAcceptable(float beforeOverlap, float afterOverlap) {
        if (beforeOverlap <= OVERLAP_EPSILON) {
            return afterOverlap <= OVERLAP_EPSILON;
        }
        return afterOverlap <= beforeOverlap + OVERLAP_EPSILON;
    }

    private static float overlapArea(SpatialBlockData candidate,
                                     SpatialBlocksComponent existingBlocks,
                                     int ignoredBlockId) {
        if (candidate == null || existingBlocks == null || existingBlocks.blocks == null) return 0f;
        float total = 0f;
        for (int i = 0; i < existingBlocks.blocks.size; i++) {
            SpatialBlockData existing = existingBlocks.blocks.get(i);
            if (existing == null || existing.id == ignoredBlockId) continue;
            total += overlapArea(candidate, existing);
        }
        return total;
    }

    private static float overlapArea(SpatialBlockData a, SpatialBlockData b) {
        float ax1 = a.x + Math.max(0f, a.width);
        float ay1 = a.y + Math.max(0f, a.depth);
        float bx1 = b.x + Math.max(0f, b.width);
        float by1 = b.y + Math.max(0f, b.depth);
        float overlapW = Math.min(ax1, bx1) - Math.max(a.x, b.x);
        float overlapH = Math.min(ay1, by1) - Math.max(a.y, b.y);
        if (overlapW <= 0f || overlapH <= 0f) return 0f;
        return overlapW * overlapH;
    }

    private static boolean geometryChanged(SpatialBlockData a, SpatialBlockData b) {
        return Float.compare(a.x, b.x) != 0
                || Float.compare(a.y, b.y) != 0
                || Float.compare(a.width, b.width) != 0
                || Float.compare(a.depth, b.depth) != 0;
    }
}
