package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Strict Spatial V3 authored-wall and layer topology validation. */
public final class SpatialWallAuthoringValidator {
    private SpatialWallAuthoringValidator() {
    }

    public enum Status { VALID, INVALID_WALL, INVALID_TOPOLOGY, LINKED_ASSET_ID_MISMATCH }

    public static Result validateWall(SpatialBlockData wall, TiledMapLayerData map) {
        SpatialWallGeometry.CoverageValidation validation = SpatialWallGeometry.validateAuthoredWall(
                wall, map, new SpatialWallGeometry.Bounds());
        return validation == SpatialWallGeometry.CoverageValidation.VALID
                ? Result.valid()
                : new Result(Status.INVALID_WALL, wall != null ? wall.id : -1, -1, -1,
                "authored wall validation failed: " + validation);
    }

    public static Result validateLayer(SpatialBlocksComponent walls, TiledMapLayerData map) {
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.validate(walls, map);
        return plan.valid ? Result.valid()
                : new Result(Status.INVALID_TOPOLOGY, -1, -1, -1, plan.error);
    }

    public static Result diagnoseAssetIdMismatch(SpatialBlockData wall, TiledMapLayerData map) {
        Result blocking = validateWall(wall, map);
        if (!blocking.isValid() || wall.linkedTileRefs == null) return Result.valid();
        for (int i = 0; i < wall.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(i);
            int current = map.getTile(ref.gx, ref.gy);
            if (current > 0 && current != ref.tileAssetId) {
                return new Result(Status.LINKED_ASSET_ID_MISMATCH, wall.id, ref.gx, ref.gy,
                        "linked ref asset id differs from the occupied map cell");
            }
        }
        return Result.valid();
    }

    public static final class Result {
        public final Status status;
        public final int blockId;
        public final int gx;
        public final int gy;
        public final String detail;

        private Result(Status status, int blockId, int gx, int gy, String detail) {
            this.status = status;
            this.blockId = blockId;
            this.gx = gx;
            this.gy = gy;
            this.detail = detail;
        }

        public static Result valid() { return new Result(Status.VALID, -1, -1, -1, "valid"); }
        public boolean isValid() { return status == Status.VALID; }

        public String message(String layerName, int layerEntityId) {
            StringBuilder message = new StringBuilder("invalid Spatial V3 authored wall topology");
            if (layerName != null && !layerName.isBlank()) message.append(" on layer '").append(layerName).append("'");
            else if (layerEntityId >= 0) message.append(" on layer entity ").append(layerEntityId);
            if (blockId > 0) message.append(", wall ").append(blockId);
            message.append(": ").append(detail);
            if (gx >= 0 && gy >= 0) message.append(" at cell ").append(gx).append(',').append(gy);
            return message.toString();
        }
    }
}
