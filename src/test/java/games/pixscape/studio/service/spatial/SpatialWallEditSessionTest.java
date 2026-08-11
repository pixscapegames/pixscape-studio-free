package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialWallGeometry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class SpatialWallEditSessionTest {
    @Test
    public void resizePreviewAndCancelNeverMutateAuthoritativeStateOrCompiledRevision() {
        Fixture fixture = attachedFixture(0.5f);
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        cache.synchronize(fixture.layer, fixture.walls, fixture.map);
        int compiledRevision = cache.publishedRevision();
        SpatialBlockData authoritative = fixture.walls.blocks.get(0);
        SpatialBlockData before = authoritative.copy();
        SpatialWallEditSession session = new SpatialWallEditSession();

        Assert.assertTrue(session.begin(fixture.layer, authoritative.id, fixture.walls, fixture.map));
        Assert.assertTrue(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, authoritative.x, 2.4f));

        assertFootprint(before, authoritative);
        Assert.assertEquals(before.structureId, authoritative.structureId);
        Assert.assertFalse(cache.synchronize(fixture.layer, fixture.walls, fixture.map).published());
        Assert.assertEquals(compiledRevision, cache.publishedRevision());
        session.cancel();
        assertFootprint(before, authoritative);
        Assert.assertEquals(0, fixture.walls.revision);
    }

    @Test
    public void attachedWallCannotMoveAndInvalidReleaseCreatesNoHistoryEntry() {
        Fixture fixture = attachedFixture(0.5f);
        SpatialWallEditSession session = new SpatialWallEditSession();
        SpatialBlockData wall = fixture.walls.blocks.get(0);
        int structureId = wall.structureId;
        Assert.assertTrue(session.begin(fixture.layer, wall.id, fixture.walls, fixture.map));

        Assert.assertFalse(session.canMove());
        Assert.assertFalse(session.updateMove(0.5f, 0f));
        Assert.assertFalse(session.commit(fixture.world, fixture.history, fixture.selection));

        Assert.assertEquals(0, fixture.history.getCursor());
        Assert.assertEquals(structureId, wall.structureId);
        Assert.assertEquals(2f, wall.x, 0f);
    }

    @Test
    public void isolatedWallRemainsMovable() {
        Fixture fixture = emptyFixture();
        SpatialBlockData isolated = wall(1, 3, 0.5f, 2f, 2f, 0.5f, 0, 4, 2, 3);
        fixture.walls.blocks.add(isolated);
        SpatialWallEditSession session = new SpatialWallEditSession();

        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));
        Assert.assertTrue(session.canMove());
        Assert.assertTrue(session.updateMove(0.5f, 0f));
        Assert.assertEquals(1f, session.candidate().x, 0f);
        Assert.assertEquals(0.5f, isolated.x, 0f);
    }

    @Test
    public void perpendicularBranchSlidesAlongHorizontalHostWithoutDetaching() {
        Fixture fixture = emptyFixture();
        SpatialBlockData host = wall(1, 4, 1f, 2.25f, 6f, 0.5f, 1, 7, 2, 3);
        SpatialBlockData branch = wall(2, 4, 3.25f, 2f, 0.5f, 3f, 2, 7, 2, 5);
        fixture.walls.blocks.add(host);
        fixture.walls.blocks.add(branch);
        SpatialWallEditSession session = new SpatialWallEditSession();

        Assert.assertTrue(session.begin(fixture.layer, branch.id, fixture.walls, fixture.map));
        Assert.assertTrue(session.canMove());
        Assert.assertTrue(session.isSlidingAttachedWall());
        Assert.assertTrue(session.updateMove(1f, 4f));

        Assert.assertEquals(4.25f, session.candidate().x, 0f);
        Assert.assertEquals(2f, session.candidate().y, 0f);
        Assert.assertEquals(branch.width, session.candidate().width, 0f);
        Assert.assertEquals(branch.depth, session.candidate().depth, 0f);
        Assert.assertTrue(session.attachments().preservesAll(
                session.candidate(), component(session.candidateTopology())));
    }

    @Test
    public void slideCommitAndUndoRedoRestoreExactFootprints() {
        Fixture fixture = emptyFixture();
        fixture.walls.blocks.add(wall(1, 4, 1f, 2.25f, 6f, 0.5f, 1, 7, 2, 3));
        SpatialBlockData branch = wall(2, 4, 3.25f, 2f, 0.5f, 3f, 2, 7, 2, 5);
        fixture.walls.blocks.add(branch);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, branch.id, fixture.walls, fixture.map));
        Assert.assertTrue(session.updateMove(1f, 0f));

        Assert.assertTrue(session.commit(fixture.world, fixture.history, fixture.selection));
        Assert.assertEquals(4.25f, fixture.walls.blocks.get(1).x, 0f);
        fixture.history.undo();
        Assert.assertEquals(3.25f, fixture.walls.blocks.get(1).x, 0f);
        fixture.history.redo();
        Assert.assertEquals(4.25f, fixture.walls.blocks.get(1).x, 0f);
    }

    @Test
    public void incompatibleAttachmentAxesDisableBodyMovement() {
        Fixture fixture = emptyFixture();
        SpatialBlockData selected = wall(1, 4, 3f, 3f, 2f, 2f, 2, 7, 2, 7);
        fixture.walls.blocks.add(selected);
        fixture.walls.blocks.add(wall(2, 4, 1f, 3.5f, 6f, 0.5f, 1, 7, 3, 4));
        fixture.walls.blocks.add(wall(3, 4, 3.5f, 1f, 0.5f, 6f, 3, 4, 1, 7));
        SpatialWallEditSession session = new SpatialWallEditSession();

        Assert.assertTrue(session.begin(fixture.layer, selected.id, fixture.walls, fixture.map));
        Assert.assertFalse(session.canMove());
        Assert.assertFalse(session.updateMove(1f, 0f));
    }

    @Test
    public void oneAttachedEndLocksOnlyThatLongitudinalEnd() {
        Fixture fixture = attachedFixture(0.5f);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        Assert.assertTrue(session.attachments().isHorizontal());
        Assert.assertFalse(session.attachments().minLongitudinalLocked());
        Assert.assertTrue(session.attachments().maxLongitudinalLocked());
        Assert.assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X));
        Assert.assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
        Assert.assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y));
        Assert.assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y));
    }

    @Test
    public void twoAttachedEndsLeaveOnlyThicknessHandles() {
        Fixture fixture = attachedFixture(0.5f);
        fixture.walls.blocks.add(wall(3, 4, 2f, 1f, 1f, 3f, 2, 3, 1, 4));
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        Assert.assertTrue(session.attachments().minLongitudinalLocked());
        Assert.assertTrue(session.attachments().maxLongitudinalLocked());
        Assert.assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X));
        Assert.assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
        Assert.assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y));
        Assert.assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y));
    }

    @Test
    public void thicknessStopsAtJunctionBoundaryAndNeverPublishesDetachedCandidate() {
        Fixture fixture = attachedFixture(0.5f);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        Assert.assertTrue(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, 0f, 3f));

        Assert.assertTrue(session.isCandidateValid());
        Assert.assertTrue(session.candidate().y < 2.5f);
        Assert.assertTrue(session.attachments().preservesAll(
                session.candidate(), component(session.candidateTopology())));
        Assert.assertEquals(2f, fixture.walls.blocks.get(0).y, 0f);
    }

    @Test
    public void thicknessHandleWithoutNonZeroValidRangeIsDisabled() {
        Fixture fixture = attachedFixture(SpatialWallGeometry.GEOMETRY_EPSILON * 2f);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        Assert.assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y));
    }

    @Test
    public void heightCommitPublishesOneCompleteSnapshotAndUndoRedoRemainAtomic() {
        Fixture fixture = attachedFixture(0.5f);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));
        Assert.assertTrue(session.updateHeight(15f));
        for (int i = 0; i < session.candidateTopology().walls.size; i++) {
            Assert.assertEquals(15f, session.candidateTopology().walls.get(i).height, 0f);
        }
        assertAllHeight(fixture.walls, 10f);

        Assert.assertTrue(session.commit(fixture.world, fixture.history, fixture.selection));
        Assert.assertEquals(1, fixture.history.getCursor());
        Assert.assertEquals(1, fixture.walls.revision);
        assertAllHeight(fixture.walls, 15f);
        fixture.history.undo();
        assertAllHeight(fixture.walls, 10f);
        fixture.history.redo();
        assertAllHeight(fixture.walls, 15f);
    }

    private static Fixture attachedFixture(float neighborOverlap) {
        Fixture fixture = emptyFixture();
        fixture.walls.blocks.add(wall(1, 4, 2f, 2f, 4f, 1f, 2, 6, 2, 3));
        fixture.walls.blocks.add(wall(2, 4, 5f, 1f, 1f, 1f + neighborOverlap, 5, 6, 1, 4));
        return fixture;
    }

    private static Fixture emptyFixture() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = SpatialWallAuthoringValidatorTest.map(12, 12);
        SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).create(layer);
        HistoryManager history = new HistoryManager(16);
        history.historyIds().ensureForEntity(layer);
        return new Fixture(world, layer, tiled.data, walls, history, new SpatialBlockSelectionService());
    }

    private static SpatialBlockData wall(int id, int structureId,
                                         float x, float y, float width, float depth,
                                         int refMinX, int refMaxX, int refMinY, int refMaxY) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.altitude = 0f;
        wall.height = 10f;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = refMinY; gy < refMaxY; gy++) {
            for (int gx = refMinX; gx < refMaxX; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static SpatialBlocksComponent component(SpatialStructureTopology.Plan plan) {
        SpatialBlocksComponent result = new SpatialBlocksComponent();
        for (int i = 0; i < plan.walls.size; i++) result.blocks.add(plan.walls.get(i).copy());
        return result;
    }

    private static void assertFootprint(SpatialBlockData expected, SpatialBlockData actual) {
        Assert.assertEquals(expected.x, actual.x, 0f);
        Assert.assertEquals(expected.y, actual.y, 0f);
        Assert.assertEquals(expected.width, actual.width, 0f);
        Assert.assertEquals(expected.depth, actual.depth, 0f);
    }

    private static void assertAllHeight(SpatialBlocksComponent walls, float height) {
        for (int i = 0; i < walls.blocks.size; i++) Assert.assertEquals(height, walls.blocks.get(i).height, 0f);
    }

    private record Fixture(World world, int layer, TiledMapLayerData map,
                           SpatialBlocksComponent walls, HistoryManager history,
                           SpatialBlockSelectionService selection) { }
}
