package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockAuthoringValidatorTest {
    @Test
    public void validAuthoredActorOccluderPasses() {
        TiledMapLayerData map = map();
        map.setTile(2, 3, 101);

        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.VALID,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map).status
        );
    }

    @Test
    public void missingRefsFailClearly() {
        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.MISSING_REFS,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block(), map()).status
        );
    }

    @Test
    public void emptyRefsFailClearly() {
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.EMPTY_REFS,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map()).status
        );
    }

    @Test
    public void nullRefsFailClearly() {
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.linkedTileRefs.add(null);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.NULL_REF,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map()).status
        );
    }

    @Test
    public void duplicateCoordinatesFailClearly() {
        TiledMapLayerData map = map();
        map.setTile(2, 3, 101);
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);
        block.addLinkedTileRef(2, 3, 101);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.DUPLICATE_COORDINATE,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map).status
        );
    }

    @Test
    public void linkedCellOutsideMapFailsClearly() {
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(99, 3, 101);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.LINKED_CELL_OUTSIDE_MAP,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map()).status
        );
    }

    @Test
    public void emptyLinkedCellFailsClearly() {
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.LINKED_CELL_EMPTY,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map()).status
        );
    }

    @Test
    public void linkedAssetMismatchFailsClearly() {
        TiledMapLayerData map = map();
        map.setTile(2, 3, 202);
        SpatialBlockData block = block();
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);

        Assert.assertEquals(
                SpatialBlockAuthoringValidator.Status.LINKED_ASSET_ID_MISMATCH,
                SpatialBlockAuthoringValidator.validateEnabledActorOccluder(block, map).status
        );
    }

    @Test
    public void disabledOrNonActorBlocksAreOutsideThisContract() {
        SpatialBlockData disabled = block();
        disabled.enabled = false;
        SpatialBlockData nonActor = block();
        nonActor.actorOccluder = false;

        Assert.assertTrue(SpatialBlockAuthoringValidator.validateEnabledActorOccluder(disabled, null).isValid());
        Assert.assertTrue(SpatialBlockAuthoringValidator.validateEnabledActorOccluder(nonActor, null).isValid());
    }

    private static SpatialBlockData block() {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 4;
        block.enabled = true;
        block.x = 2f;
        block.y = 3f;
        block.width = 1f;
        block.depth = 1f;
        block.height = 8f;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        return block;
    }

    private static TiledMapLayerData map() {
        return new TiledMapLayerData(8, 8, 16, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
    }
}
