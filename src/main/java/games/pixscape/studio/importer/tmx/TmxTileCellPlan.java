package games.pixscape.studio.importer.tmx;

public record TmxTileCellPlan(int sourceX,
                              int sourceY,
                              int cleanGid,
                              int rawGid,
                              int tilesetPlanIndex,
                              int tilesetFirstGid,
                              int localTileId,
                              TmxTransformPlan transform) {
}
