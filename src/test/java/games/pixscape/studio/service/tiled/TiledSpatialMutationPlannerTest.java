package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledSpatialMutationPlannerTest {
    private final TiledSpatialMutationPlanner planner = new TiledSpatialMutationPlanner();

    @Test
    public void linkedEraseIsRejectedWithoutAnyPublishedChange() {
        Fixture fixture = fixture();
        int mapRevision = fixture.tiled.data.contentRevision();
        int spatialRevision = fixture.blocks.revision;
        TiledMutationPlan plan = erase(fixture.tiled, 2, 2, 3, 2);

        TiledSpatialMutationPlanner.Result result = planner.preflight(plan, fixture.blocks, true);

        Assert.assertEquals(TiledSpatialMutationPlanner.Status.REJECTED_LINKED_ANCHOR, result.status());
        Assert.assertEquals(2, result.rejection().affectedCellCount());
        Assert.assertEquals(1, result.rejection().affectedWallCount());
        Assert.assertEquals(41, result.rejection().firstBlockId());
        Assert.assertEquals(7, result.rejection().firstStructureId());
        Assert.assertEquals(1, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(mapRevision, fixture.tiled.data.contentRevision());
        Assert.assertEquals(spatialRevision, fixture.blocks.revision);
    }

    @Test
    public void eraseThenRepaintAndOccupiedReplacementAreAccepted() {
        Fixture fixture = fixture();
        TiledBrushSession session = new TiledBrushSession(3);
        session.apply(fixture.tiled, 2, 2, 0);
        session.apply(fixture.tiled, 2, 2, 9, TileTransformFlags.FLIP_H);
        TiledMutationPlan plan = session.toPlan();

        TiledSpatialMutationPlanner.Result result = planner.validateAndCommit(
                3, fixture.tiled.data, fixture.blocks, plan, true);

        Assert.assertTrue(result.accepted());
        Assert.assertEquals(9, fixture.tiled.data.getTile(2, 2));
        Assert.assertEquals(TileTransformFlags.FLIP_H, fixture.tiled.data.getTileTransformFlags(2, 2));
    }

    @Test
    public void acceptedMultiCellChangePublishesExactlyOneRevision() {
        Fixture fixture = fixture();
        int revision = fixture.tiled.data.contentRevision();
        TiledBrushSession session = new TiledBrushSession(3);
        session.apply(fixture.tiled, 0, 0, 4);
        session.apply(fixture.tiled, 1, 0, 5);

        TiledSpatialMutationPlanner.Result result = planner.validateAndCommit(
                3, fixture.tiled.data, fixture.blocks, session.toPlan(), true);

        Assert.assertTrue(result.accepted());
        Assert.assertEquals(revision + 1, fixture.tiled.data.contentRevision());
        Assert.assertEquals(4, fixture.tiled.data.getTile(0, 0));
        Assert.assertEquals(5, fixture.tiled.data.getTile(1, 0));
    }

    private static TiledMutationPlan erase(TiledLayerComponent tiled, int... coordinates) {
        TiledBrushSession session = new TiledBrushSession(3);
        for (int i = 0; i < coordinates.length; i += 2) {
            session.apply(tiled, coordinates[i], coordinates[i + 1], 0);
        }
        return session.toPlan();
    }

    private static Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.tiled = new TiledLayerComponent();
        fixture.tiled.data = new TiledMapLayerData(8, 8, 64, 32, 4, SceneMetaRuntime.TiledProjection.ISO);
        fixture.tiled.data.setTile(2, 2, 1);
        fixture.tiled.data.setTile(3, 2, 1);
        fixture.blocks = new SpatialBlocksComponent();
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 41;
        wall.structureId = 7;
        wall.x = 2;
        wall.y = 2;
        wall.width = 2;
        wall.depth = 1;
        wall.height = 16;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(2, 2, 1);
        wall.addLinkedTileRef(3, 2, 1);
        fixture.blocks.blocks.add(wall);
        return fixture;
    }

    private static final class Fixture {
        TiledLayerComponent tiled;
        SpatialBlocksComponent blocks;
    }
}
