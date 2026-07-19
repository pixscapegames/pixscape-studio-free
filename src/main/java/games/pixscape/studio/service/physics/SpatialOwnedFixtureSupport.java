package games.pixscape.studio.service.physics;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;

/** Resolves the existing deterministic Spatial wall-to-fixture ownership relation. */
public final class SpatialOwnedFixtureSupport {
    private static final int SPATIAL_BLOCK_FIXTURE_ID_BASE = 1_000_000;

    private SpatialOwnedFixtureSupport() {
    }

    public static int fixtureIdForBlock(int blockId) {
        return SPATIAL_BLOCK_FIXTURE_ID_BASE + Math.max(1, blockId);
    }

    public static SpatialBlockData findEnabledOwner(World world, int bodyEntityId, long fixtureId) {
        if (world == null || bodyEntityId < 0 || fixtureId <= 0L) return null;
        SpatialBlocksComponent blocks =
                world.getMapper(SpatialBlocksComponent.class).getSafe(bodyEntityId, null);
        if (blocks == null || blocks.blocks == null) return null;
        for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            if (block != null
                    && block.physicsCollision
                    && fixtureIdForBlock(block.id) == fixtureId) {
                return block;
            }
        }
        return null;
    }

    public static boolean isOwned(World world, int bodyEntityId, long fixtureId) {
        return findEnabledOwner(world, bodyEntityId, fixtureId) != null;
    }

}
