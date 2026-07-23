package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class TiledMutationControllerTest {
    @Test
    public void validStrokeExecutesOnceAndKeepsUndoRedoBehavior() {
        Fixture fixture = fixture();
        int revision = fixture.tiled.data.contentRevision();

        fixture.controller.beginStroke(fixture.layer);
        fixture.controller.updateStroke(fixture.tiled, 1, 1, 7, TileTransformFlags.FLIP_H);
        fixture.controller.updateStroke(fixture.tiled, 3, 1, 7, TileTransformFlags.FLIP_H);
        TiledMutationController.Result accepted = fixture.controller.commitStroke();

        Assert.assertEquals(TiledMutationController.Status.ACCEPTED, accepted.status());
        Assert.assertEquals(1, fixture.history.getCursor());
        Assert.assertEquals(revision + 1, fixture.tiled.data.contentRevision());
        Assert.assertEquals(7, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(7, fixture.tiled.data.getTile(2, 1));
        Assert.assertEquals(7, fixture.tiled.data.getTile(3, 1));

        TiledMutationController.Result secondCommit = fixture.controller.commitStroke();
        Assert.assertEquals(TiledMutationController.Status.NO_MUTATION, secondCommit.status());
        Assert.assertEquals(1, fixture.history.getCursor());
        Assert.assertEquals(revision + 1, fixture.tiled.data.contentRevision());

        fixture.history.undo();
        Assert.assertEquals(0, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(0, fixture.history.getCursor());

        fixture.history.redo();
        Assert.assertEquals(7, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(1, fixture.history.getCursor());
    }

    @Test
    public void linkedAnchorRejectionLeavesMapSpatialDataHistoryAndDirtyStateUnchanged() {
        Fixture fixture = fixture();
        fixture.tiled.data.setTile(2, 2, 3);
        SpatialBlocksComponent blocks = fixture.world.getMapper(SpatialBlocksComponent.class)
                .create(fixture.layer);
        SpatialBlockData wall = wallLinkedTo(2, 2, 3);
        blocks.blocks.add(wall);
        int revision = fixture.tiled.data.contentRevision();
        int linkedRefCount = wall.linkedTileRefs.size;

        fixture.controller.beginStroke(fixture.layer);
        fixture.controller.updateStroke(fixture.tiled, 2, 2, 0, TileTransformFlags.NONE);
        TiledMutationController.Result rejected = fixture.controller.commitStroke();

        Assert.assertEquals(TiledMutationController.Status.REJECTED, rejected.status());
        Assert.assertEquals(fixture.layer, rejected.layerEntityId());
        Assert.assertNotNull(rejected.rejection());
        Assert.assertEquals(12, rejected.rejection().firstBlockId());
        Assert.assertEquals(3, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(revision, fixture.tiled.data.contentRevision());
        Assert.assertSame(wall, blocks.blocks.first());
        Assert.assertEquals(linkedRefCount, wall.linkedTileRefs.size);
        Assert.assertEquals(0, fixture.history.getCursor());
        Assert.assertFalse(fixture.history.isDirty());
    }

    @Test
    public void cancelAndResetPublishNothingAndDiscardStagedCells() {
        Fixture fixture = fixture();

        fixture.controller.beginStroke(fixture.layer);
        fixture.controller.updateStroke(fixture.tiled, 1, 1, 4, TileTransformFlags.NONE);
        Assert.assertEquals(TiledMutationController.Status.CANCELLED, fixture.controller.cancel().status());
        Assert.assertFalse(fixture.controller.isActive());
        Assert.assertEquals(0, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(0, fixture.history.getCursor());

        fixture.controller.beginStroke(fixture.layer);
        fixture.controller.updateStroke(fixture.tiled, 2, 2, 5, TileTransformFlags.NONE);
        fixture.controller.reset();
        Assert.assertFalse(fixture.controller.isActive());
        Assert.assertEquals(TiledMutationController.Status.NO_MUTATION,
                fixture.controller.commitStroke().status());
        Assert.assertEquals(0, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(0, fixture.history.getCursor());

        fixture.controller.beginStroke(fixture.layer);
        fixture.controller.updateStroke(fixture.tiled, 3, 3, 6, TileTransformFlags.NONE);
        Assert.assertEquals(TiledMutationController.Status.ACCEPTED,
                fixture.controller.commitStroke().status());
        Assert.assertEquals(0, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(0, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(6, fixture.tiled.data.getTile(3, 3));
    }

    @Test
    public void rectangleFillAndEraseUseTheSameAtomicPublicationBoundary() {
        Fixture fixture = fixture();

        Assert.assertEquals(TiledMutationController.Status.ACCEPTED,
                fixture.controller.commitRectangle(
                        fixture.layer, fixture.tiled, 1, 1, 2, 2, 8, TileTransformFlags.NONE).status());
        Assert.assertEquals(8, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(8, fixture.tiled.data.getTile(2, 2));

        Assert.assertEquals(TiledMutationController.Status.ACCEPTED,
                fixture.controller.commitFill(
                        fixture.layer, fixture.tiled, 1, 1, 9, TileTransformFlags.FLIP_V).status());
        Assert.assertEquals(9, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(9, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(TileTransformFlags.FLIP_V,
                fixture.tiled.data.getTileTransformFlags(2, 2));

        Assert.assertEquals(TiledMutationController.Status.ACCEPTED,
                fixture.controller.commitRectangle(
                        fixture.layer, fixture.tiled, 1, 1, 1, 1, 0, TileTransformFlags.NONE).status());
        Assert.assertEquals(0, fixture.tiled.data.getTile(1, 1));
        Assert.assertEquals(3, fixture.history.getCursor());
    }

    private static Fixture fixture() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        HistoryManager history = new HistoryManager(16);
        TiledMutationController controller = new TiledMutationController(world, history, () -> null);
        return new Fixture(world, layer, tiled, history, controller);
    }

    private static SpatialBlockData wallLinkedTo(int gx, int gy, int assetId) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 12;
        wall.structureId = 4;
        wall.x = gx;
        wall.y = gy;
        wall.width = 1;
        wall.depth = 1;
        wall.height = 16;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(gx, gy, assetId);
        return wall;
    }

    private record Fixture(World world,
                           int layer,
                           TiledLayerComponent tiled,
                           HistoryManager history,
                           TiledMutationController controller) {
    }
}
