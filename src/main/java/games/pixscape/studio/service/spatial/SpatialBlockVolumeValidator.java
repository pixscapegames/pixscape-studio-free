package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;

public final class SpatialBlockVolumeValidator {
    private SpatialBlockVolumeValidator() {
    }

    public static boolean overlapsAnyBlockVolume(SpatialBlockData candidate,
                                                 SpatialBlocksComponent existingBlocks,
                                                 int ignoredBlockId) {
        if (candidate == null || existingBlocks == null || existingBlocks.blocks == null) return false;
        for (int i = 0; i < existingBlocks.blocks.size; i++) {
            SpatialBlockData existing = existingBlocks.blocks.get(i);
            if (existing == null || existing.id == ignoredBlockId) continue;
            if (overlaps2d(candidate.x, candidate.y, candidate.width, candidate.depth,
                    existing.x, existing.y, existing.width, existing.depth)) {
                return true;
            }
        }
        return false;
    }

    public static boolean overlaps2d(float ax, float ay, float aw, float ah,
                                     float bx, float by, float bw, float bh) {
        float ax1 = ax + Math.max(0f, aw);
        float ay1 = ay + Math.max(0f, ah);
        float bx1 = bx + Math.max(0f, bw);
        float by1 = by + Math.max(0f, bh);
        return ax < bx1 && ax1 > bx && ay < by1 && ay1 > by;
    }
}
