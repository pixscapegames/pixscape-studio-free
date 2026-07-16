package games.pixscape.studio.service.tiled;

import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialCompiledLayerCache;
import games.pixscape.runtime.spatial.SpatialTileOrderCache;
import games.pixscape.runtime.spatial.SpatialTileOrderInvariantException;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;

/** Shared preflight and atomic publication boundary for tiled mutations affecting Spatial V3. */
public final class TiledSpatialMutationPlanner {
    public enum Status { ACCEPTED, REJECTED_LINKED_ANCHOR, REJECTED_SPATIAL_POST_STATE }

    public static final class Result {
        private final Status status;
        private final TiledSpatialMutationRejection rejection;

        private Result(Status status, TiledSpatialMutationRejection rejection) {
            this.status = status;
            this.rejection = rejection;
        }

        public Status status() { return status; }
        public boolean accepted() { return status == Status.ACCEPTED; }
        public TiledSpatialMutationRejection rejection() { return rejection; }
    }

    public Result preflight(TiledMutationPlan plan, SpatialBlocksComponent blocks, boolean after) {
        if (plan == null || plan.isEmpty() || blocks == null || !blocks.hasBlocks()) return accepted();
        IntSet emptiedCells = new IntSet(plan.size());
        for (int i = 0; i < plan.size(); i++) {
            if (plan.assetId(i, after) <= 0) emptiedCells.add(pack(plan.gx(i), plan.gy(i)));
        }
        if (emptiedCells.size == 0) return accepted();

        IntSet affectedCells = new IntSet();
        int affectedWalls = 0;
        int firstBlock = -1;
        int firstStructure = -1;
        for (int wallIndex = 0; wallIndex < blocks.blocks.size; wallIndex++) {
            SpatialBlockData wall = blocks.blocks.get(wallIndex);
            if (wall == null || wall.linkedTileRefs == null) continue;
            boolean wallAffected = false;
            for (int refIndex = 0; refIndex < wall.linkedTileRefs.size; refIndex++) {
                SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(refIndex);
                if (ref == null || !emptiedCells.contains(pack(ref.gx, ref.gy))) continue;
                affectedCells.add(pack(ref.gx, ref.gy));
                wallAffected = true;
            }
            if (wallAffected) {
                affectedWalls++;
                if (firstBlock < 0) {
                    firstBlock = wall.id;
                    firstStructure = wall.structureId;
                }
            }
        }
        if (affectedWalls == 0) return accepted();
        TiledSpatialMutationRejection rejection = new TiledSpatialMutationRejection(
                TiledSpatialMutationRejection.Kind.LINKED_ANCHOR,
                affectedCells.size, affectedWalls, firstBlock, firstStructure,
                "A required Spatial-linked tile would become empty.", null);
        return new Result(Status.REJECTED_LINKED_ANCHOR, rejection);
    }

    public Result validateAndCommit(int layerEntityId, TiledMapLayerData map,
                                    SpatialBlocksComponent blocks, TiledMutationPlan plan,
                                    boolean after) {
        Result linked = preflight(plan, blocks, after);
        if (!linked.accepted()) return linked;
        if (plan == null || plan.isEmpty()) return accepted();

        map.beginAtomicMutation();
        boolean open = true;
        try {
            for (int i = 0; i < plan.size(); i++) {
                map.setTileStaged(plan.gx(i), plan.gy(i), plan.assetId(i, after), plan.flags(i, after));
            }
            SpatialStructureTopology.Plan topology = SpatialStructureTopology.validate(blocks, map);
            if (!topology.valid) {
                map.rollbackAtomicMutation();
                open = false;
                return postState(topology.error, null);
            }
            if (blocks != null && blocks.hasBlocks()) {
                SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
                compiled.ensure(blocks);
                new SpatialTileOrderCache().ensure(layerEntityId, map, blocks, compiled);
            }
            map.commitAtomicMutation();
            open = false;
            return accepted();
        } catch (SpatialTileOrderInvariantException invalidCandidate) {
            if (open) {
                map.rollbackAtomicMutation();
                open = false;
            }
            return postState(invalidCandidate.getMessage(), invalidCandidate);
        } finally {
            if (open && map.isAtomicMutationOpen()) map.rollbackAtomicMutation();
        }
    }

    private static Result accepted() {
        return new Result(Status.ACCEPTED, null);
    }

    private static Result postState(String detail, RuntimeException cause) {
        TiledSpatialMutationRejection rejection = new TiledSpatialMutationRejection(
                TiledSpatialMutationRejection.Kind.SPATIAL_POST_STATE,
                0, 0, -1, -1,
                detail != null ? detail : "Invalid Spatial V3 candidate.", cause);
        return new Result(Status.REJECTED_SPATIAL_POST_STATE, rejection);
    }

    private static int pack(int gx, int gy) {
        return (gx << 16) ^ (gy & 0xFFFF);
    }
}
