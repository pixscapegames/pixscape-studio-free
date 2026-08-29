package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.tiled.TiledProjection;

public record TmxScenePlan(String proposedSceneName,
                           String sourceTmxPath,
                           String orientation,
                           TiledProjection tiledProjection,
                           int mapWidthCells,
                           int mapHeightCells,
                           int tileWidth,
                           int tileHeight,
                           long requiredTiledCells,
                           int tileLayerCount,
                           long nonEmptyTileCount) {
}
