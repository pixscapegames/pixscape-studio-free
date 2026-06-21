package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.service.tiled.TiledVisualCoverage;

public record SpatialBlockPlacementTarget(
        boolean valid,
        int tiledLayerEntity,
        int targetGx,
        int targetGy,
        int coverageMinGx,
        int coverageMaxGx,
        int coverageMinGy,
        int coverageMaxGy,
        float worldX,
        float worldY,
        boolean fallback
) {
    public static SpatialBlockPlacementTarget invalid() {
        return new SpatialBlockPlacementTarget(false, -1, -1, -1, -1, -1, -1, -1, 0f, 0f, true);
    }

    public static SpatialBlockPlacementTarget fromWorld(TiledMapLayerData map,
                                                        int tiledLayerEntity,
                                                        float worldX,
                                                        float worldY,
                                                        TiledVisualCoverage.Coverage coverage,
                                                        boolean fallback) {
        if (map == null || tiledLayerEntity < 0) {
            return invalid();
        }

        int targetGx = map.worldToTileX(worldX, worldY);
        int targetGy = map.worldToTileY(worldX, worldY);
        if (!map.isInside(targetGx, targetGy)) {
            return invalid();
        }

        int minGx = targetGx;
        int maxGx = targetGx;
        int minGy = targetGy;
        int maxGy = targetGy;

        if (coverage != null && coverage.contains(targetGx, targetGy)) {
            minGx = coverage.minGX;
            maxGx = coverage.maxGXExclusive - 1;
            minGy = coverage.minGY;
            maxGy = coverage.maxGYExclusive - 1;
        }

        return new SpatialBlockPlacementTarget(
                true,
                tiledLayerEntity,
                targetGx,
                targetGy,
                minGx,
                maxGx,
                minGy,
                maxGy,
                worldX,
                worldY,
                fallback
        );
    }

    public boolean hasCoverage() {
        return valid && coverageMinGx <= coverageMaxGx && coverageMinGy <= coverageMaxGy;
    }
}
