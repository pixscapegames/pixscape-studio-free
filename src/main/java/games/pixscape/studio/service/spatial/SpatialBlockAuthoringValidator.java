package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.HashSet;
import java.util.Set;

public final class SpatialBlockAuthoringValidator {
    private SpatialBlockAuthoringValidator() {
    }

    public enum Status {
        VALID,
        MISSING_MAP,
        MISSING_REFS,
        EMPTY_REFS,
        NULL_REF,
        DUPLICATE_COORDINATE,
        LINKED_CELL_OUTSIDE_MAP,
        LINKED_CELL_EMPTY,
        LINKED_ASSET_ID_MISMATCH
    }

    public static Result validateEnabledActorOccluder(SpatialBlockData block, TiledMapLayerData map) {
        if (block == null || !block.enabled || !block.actorOccluder) {
            return Result.valid();
        }
        if (map == null) {
            return new Result(Status.MISSING_MAP, -1, -1, -1, 0);
        }
        if (!block.linkedTileRefsAuthored) {
            return new Result(Status.MISSING_REFS, block.id, -1, -1, 0);
        }
        if (block.linkedTileRefs == null || block.linkedTileRefs.size == 0) {
            return new Result(Status.EMPTY_REFS, block.id, -1, -1, 0);
        }

        Set<Long> coordinates = new HashSet<>();
        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null) {
                return new Result(Status.NULL_REF, block.id, -1, -1, 0);
            }

            long key = (((long) ref.gx) << 32) ^ (ref.gy & 0xffffffffL);
            if (!coordinates.add(key)) {
                return new Result(Status.DUPLICATE_COORDINATE, block.id, ref.gx, ref.gy, ref.tileAssetId);
            }
            if (!map.isInside(ref.gx, ref.gy)) {
                return new Result(Status.LINKED_CELL_OUTSIDE_MAP, block.id, ref.gx, ref.gy, ref.tileAssetId);
            }

            int currentTileAssetId = map.getTile(ref.gx, ref.gy);
            if (currentTileAssetId <= 0) {
                return new Result(Status.LINKED_CELL_EMPTY, block.id, ref.gx, ref.gy, ref.tileAssetId);
            }
        }

        return Result.valid();
    }

    public static Result diagnoseAssetIdMismatch(SpatialBlockData block, TiledMapLayerData map) {
        Result blocking = validateEnabledActorOccluder(block, map);
        if (!blocking.isValid()) {
            return Result.valid();
        }
        if (block == null || !block.enabled || !block.actorOccluder || map == null || block.linkedTileRefs == null) {
            return Result.valid();
        }

        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null) continue;
            int currentTileAssetId = map.getTile(ref.gx, ref.gy);
            if (currentTileAssetId > 0 && currentTileAssetId != ref.tileAssetId) {
                return new Result(Status.LINKED_ASSET_ID_MISMATCH, block.id, ref.gx, ref.gy, ref.tileAssetId);
            }
        }
        return Result.valid();
    }

    public static final class Result {
        public final Status status;
        public final int blockId;
        public final int gx;
        public final int gy;
        public final int tileAssetId;

        private Result(Status status, int blockId, int gx, int gy, int tileAssetId) {
            this.status = status;
            this.blockId = blockId;
            this.gx = gx;
            this.gy = gy;
            this.tileAssetId = tileAssetId;
        }

        public static Result valid() {
            return new Result(Status.VALID, -1, -1, -1, 0);
        }

        public boolean isValid() {
            return status == Status.VALID;
        }

        public String message(String layerName, int layerEntityId) {
            StringBuilder sb = new StringBuilder("Invalid Spatial V2 actor-occluder");
            if (layerName != null && !layerName.isBlank()) {
                sb.append(" on layer '").append(layerName).append("'");
            } else if (layerEntityId >= 0) {
                sb.append(" on layer entity ").append(layerEntityId);
            }
            if (blockId > 0) {
                sb.append(", block ").append(blockId);
            }
            sb.append(": ").append(statusText());
            if (gx >= 0 && gy >= 0) {
                sb.append(" at cell ").append(gx).append(",").append(gy);
            }
            return sb.toString();
        }

        private String statusText() {
            return switch (status) {
                case VALID -> "valid";
                case MISSING_MAP -> "owning tiled map data is missing";
                case MISSING_REFS -> "authored linkedTileRefs are missing";
                case EMPTY_REFS -> "authored linkedTileRefs are empty";
                case NULL_REF -> "linkedTileRefs contains a null ref";
                case DUPLICATE_COORDINATE -> "linkedTileRefs contains a duplicate coordinate";
                case LINKED_CELL_OUTSIDE_MAP -> "linked tile ref is outside the map";
                case LINKED_CELL_EMPTY -> "linked tile ref points to an empty cell";
                case LINKED_ASSET_ID_MISMATCH -> "linked tile ref asset id differs from the current tiled cell";
            };
        }
    }
}
