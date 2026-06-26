package games.pixscape.studio.importer.tmx;

public record TmxScenePlan(String proposedSceneName,
                           String sourceTmxPath,
                           String orientation,
                           TmxTiledProjectionPlan tiledProjection,
                           int mapWidthCells,
                           int mapHeightCells,
                           int tileWidth,
                           int tileHeight,
                           long requiredTiledCells,
                           int tileLayerCount,
                           long nonEmptyTileCount) {
}
