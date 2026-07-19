package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class SpatialWallCreationServiceTest {
    @Test
    public void productionCreationMergesOneCellOneByNAndTwoByTwoJunctions() {
        assertMerged(rect(0, 1, 2, 1, 4), rect(1, 1, 1, 3, 0));
        assertMerged(rect(0, 1, 2, 2, 4), rect(2, 0, 3, 2, 0));
        assertMerged(rect(0, 1, 2, 2, 4), rect(1, 0, 3, 2, 0));
    }

    @Test
    public void productionCreationConnectingTwoStructuresUsesSmallestId() {
        Fixture f = fixture();
        addExisting(f, rect(0, 2, 2, 2, 9));
        addExisting(f, rect(4, 2, 6, 2, 3));

        select(f, rect(2, 1, 4, 3, 0));
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));

        Assert.assertEquals(3, f.walls.blocks.size);
        for (int i = 0; i < f.walls.blocks.size; i++) Assert.assertEquals(3, f.walls.blocks.get(i).structureId);
        Assert.assertEquals(3, f.blockSelection.getSelectedBlockId());
    }

    @Test
    public void productionCreationRejectsIncompatibleStructureConnection() {
        Fixture f = fixture();
        SpatialBlockData first = addExisting(f, rect(0, 2, 2, 2, 2));
        SpatialBlockData second = addExisting(f, rect(4, 2, 6, 2, 5));
        second.altitude = 4f;
        select(f, rect(2, 1, 4, 3, 0));

        Assert.assertFalse(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(2, f.walls.blocks.size);
        Assert.assertEquals(2, first.structureId);
        Assert.assertEquals(5, second.structureId);
        Assert.assertFalse(f.tileSelection.hasSelection());
    }

    @Test
    public void rejectedCreationReleaseAlwaysConsumesTransientSelection() {
        Fixture f = fixture();
        addExisting(f, rect(2, 2, 4, 2, 7));
        select(f, rect(2, 2, 4, 2, 0));

        Assert.assertTrue(f.tileSelection.hasSelection());
        Assert.assertFalse(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));

        Assert.assertFalse(f.tileSelection.hasSelection());
        Assert.assertFalse(f.tileSelection.isDragging());
        Assert.assertEquals(1, f.walls.blocks.size);
        Assert.assertEquals(0, f.history.getCursor());
    }

    @Test
    public void ignoredPeripheralEmptyRowDoesNotPreventStructureMerge() {
        Fixture f = fixture();
        for (int gy = 0; gy < 8; gy++) for (int gx = 0; gx < 8; gx++) f.map.setTile(gx, gy, 0);
        for (int gx = 2; gx <= 4; gx++) f.map.setTile(gx, 3, 1);
        for (int gy = 3; gy <= 5; gy++) f.map.setTile(3, gy, 1);
        addExisting(f, rect(3, 3, 3, 5, 7));
        f.tileSelection.beginDrag(f.layerId, 2, 2);
        f.tileSelection.updateDrag(4, 3);
        f.tileSelection.finishDrag();

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));

        Assert.assertEquals(2, f.walls.blocks.size);
        Assert.assertEquals(7, f.walls.blocks.get(0).structureId);
        Assert.assertEquals(7, f.walls.blocks.get(1).structureId);
        SpatialBlockData created = f.walls.blocks.get(1);
        Assert.assertEquals(3, created.linkedTileRefs.size);
        Assert.assertEquals(2f, created.x, 0f);
        Assert.assertEquals(3f, created.y, 0f);
        Assert.assertEquals(3f, created.width, 0f);
        Assert.assertEquals(1f, created.depth, 0f);
    }

    @Test
    public void internalEmptyCellNeverBecomesAnImplicitJunction() {
        Fixture f = fixture();
        for (int gy = 0; gy < 8; gy++) for (int gx = 0; gx < 8; gx++) f.map.setTile(gx, gy, 0);
        for (int gy = 2; gy <= 4; gy++) {
            for (int gx = 2; gx <= 4; gx++) f.map.setTile(gx, gy, 1);
        }
        f.map.setTile(3, 3, 0);
        f.tileSelection.beginDrag(f.layerId, 2, 2);
        f.tileSelection.updateDrag(4, 4);
        f.tileSelection.finishDrag();

        Assert.assertFalse(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(0, f.walls.blocks.size);
    }

    @Test
    public void perpendicularCreationInheritsPreciseThicknessAndUndoRedoPreservesIt() {
        Fixture f = fixture();
        SpatialBlockData neighbor = addExisting(f, rect(1, 3, 5, 3, 4));
        neighbor.y = 3.35f;
        neighbor.depth = 0.25f;
        select(f, rect(3, 2, 3, 5, 0));

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        SpatialBlockData created = f.walls.blocks.get(1);
        Assert.assertEquals(0.25f, created.width, 0f);
        Assert.assertEquals(3.375f, created.x, 0f);
        f.history.undo();
        Assert.assertEquals(1, f.walls.blocks.size);
        f.history.redo();
        Assert.assertEquals(0.25f, f.walls.blocks.get(1).width, 0f);
        Assert.assertEquals(3.375f, f.walls.blocks.get(1).x, 0f);
    }

    @Test
    public void collinearCreationInheritsAndAlignsPreciseThickness() {
        Fixture f = fixture();
        SpatialBlockData neighbor = addExisting(f, rect(1, 3, 3, 3, 4));
        neighbor.y = 3.4f;
        neighbor.depth = 0.2f;
        select(f, rect(3, 3, 5, 3, 0));

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        SpatialBlockData created = f.walls.blocks.get(1);
        Assert.assertEquals(0.2f, created.depth, 0f);
        Assert.assertEquals(3.4f, created.y, 0f);
    }

    @Test
    public void multipleCompatibleNeighborsUseCanonicalThickness() {
        Fixture f = fixture();
        SpatialBlockData first = addExisting(f, rect(1, 2, 5, 2, 7));
        first.y = 2.3f;
        first.depth = 0.25f;
        SpatialBlockData second = addExisting(f, rect(1, 5, 5, 5, 9));
        second.y = 5.4f;
        second.depth = 0.25f;
        select(f, rect(3, 2, 3, 5, 0));

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(0.25f, f.walls.blocks.get(2).width, 0f);
    }

    @Test
    public void incompatibleNeighborThicknessesRejectBeforeMutation() {
        Fixture f = fixture();
        SpatialBlockData first = addExisting(f, rect(1, 2, 5, 2, 7));
        first.y = 2.3f;
        first.depth = 0.2f;
        SpatialBlockData second = addExisting(f, rect(1, 5, 5, 5, 9));
        second.y = 5.25f;
        second.depth = 0.5f;
        select(f, rect(3, 2, 3, 5, 0));

        Assert.assertFalse(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(2, f.walls.blocks.size);
        Assert.assertEquals(0, f.history.getCursor());
    }

    @Test
    public void oneCellGestureCreatesSymmetricPerpendicularBranchesOnHorizontalHost() {
        SpatialBlockData left = createOneCellBranch(2, SpatialWallAttachments.Axis.VERTICAL);
        SpatialBlockData right = createOneCellBranch(4, SpatialWallAttachments.Axis.VERTICAL);

        Assert.assertEquals(0.25f, left.width, 0f);
        Assert.assertEquals(0.25f, right.width, 0f);
        Assert.assertEquals(1f, left.depth, 0f);
        Assert.assertEquals(1f, right.depth, 0f);
        Assert.assertEquals(SpatialWallAttachments.Axis.VERTICAL, SpatialWallAttachments.determineAxis(left));
        Assert.assertEquals(SpatialWallAttachments.Axis.VERTICAL, SpatialWallAttachments.determineAxis(right));
    }

    @Test
    public void oneCellGestureCreatesSymmetricPerpendicularBranchesOnVerticalHost() {
        SpatialBlockData upper = createHorizontalOneCellBranch(2);
        SpatialBlockData lower = createHorizontalOneCellBranch(4);

        Assert.assertEquals(0.25f, upper.depth, 0f);
        Assert.assertEquals(0.25f, lower.depth, 0f);
        Assert.assertEquals(1f, upper.width, 0f);
        Assert.assertEquals(1f, lower.width, 0f);
        Assert.assertEquals(SpatialWallAttachments.Axis.HORIZONTAL, SpatialWallAttachments.determineAxis(upper));
        Assert.assertEquals(SpatialWallAttachments.Axis.HORIZONTAL, SpatialWallAttachments.determineAxis(lower));
    }

    @Test
    public void reverseOneCellGestureUsesTheSameCanonicalAxisAndGeometry() {
        Fixture positive = fixture();
        SpatialBlockData host = addExisting(positive, rect(1, 3, 5, 3, 4));
        host.y = 3.35f;
        host.depth = 0.25f;
        positive.tileSelection.beginDrag(positive.layerId, 3, 3);
        positive.tileSelection.updateGesture(0f, 0.75f);
        positive.tileSelection.finishDrag();
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                positive.world, positive.history, positive.blockSelection, positive.tileSelection));

        Fixture negative = fixture();
        SpatialBlockData reverseHost = addExisting(negative, rect(1, 3, 5, 3, 4));
        reverseHost.y = 3.35f;
        reverseHost.depth = 0.25f;
        negative.tileSelection.beginDrag(negative.layerId, 3, 3);
        negative.tileSelection.updateGesture(0f, -0.75f);
        negative.tileSelection.finishDrag();
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                negative.world, negative.history, negative.blockSelection, negative.tileSelection));

        SpatialBlockData a = positive.walls.blocks.get(1);
        SpatialBlockData b = negative.walls.blocks.get(1);
        Assert.assertEquals(a.x, b.x, 0f);
        Assert.assertEquals(a.y, b.y, 0f);
        Assert.assertEquals(a.width, b.width, 0f);
        Assert.assertEquals(a.depth, b.depth, 0f);
        Assert.assertEquals(a.structureId, b.structureId);
    }

    @Test
    public void ambiguousOneCellGestureDefaultsPerpendicularToItsOnlyHost() {
        Fixture f = fixture();
        SpatialBlockData host = addExisting(f, rect(1, 3, 5, 3, 4));
        host.y = 3.35f;
        host.depth = 0.25f;
        select(f, rect(3, 3, 3, 3, 0));

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(SpatialWallAttachments.Axis.VERTICAL,
                SpatialWallAttachments.determineAxis(f.walls.blocks.get(1)));
    }

    private static SpatialBlockData createOneCellBranch(int gx, SpatialWallAttachments.Axis gestureAxis) {
        Fixture f = fixture();
        SpatialBlockData host = addExisting(f, rect(1, 3, 5, 3, 4));
        host.y = 3.35f;
        host.depth = 0.25f;
        f.tileSelection.beginDrag(f.layerId, gx, 3);
        f.tileSelection.updateGesture(gestureAxis == SpatialWallAttachments.Axis.HORIZONTAL ? 1f : 0f,
                gestureAxis == SpatialWallAttachments.Axis.VERTICAL ? 1f : 0f);
        f.tileSelection.finishDrag();
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        SpatialBlockData created = f.walls.blocks.get(1);
        Assert.assertEquals(host.structureId, created.structureId);
        return created;
    }

    private static SpatialBlockData createHorizontalOneCellBranch(int gy) {
        Fixture f = fixture();
        SpatialBlockData host = addExisting(f, rect(3, 1, 3, 5, 4));
        host.x = 3.35f;
        host.width = 0.25f;
        f.tileSelection.beginDrag(f.layerId, 3, gy);
        f.tileSelection.updateGesture(1f, 0f);
        f.tileSelection.finishDrag();
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        SpatialBlockData created = f.walls.blocks.get(1);
        Assert.assertEquals(host.structureId, created.structureId);
        return created;
    }

    private static void assertMerged(int[] existing, int[] candidate) {
        Fixture f = fixture();
        addExisting(f, existing);
        select(f, candidate);
        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                f.world, f.history, f.blockSelection, f.tileSelection));
        Assert.assertEquals(2, f.walls.blocks.size);
        Assert.assertEquals(f.walls.blocks.get(0).structureId, f.walls.blocks.get(1).structureId);
        Assert.assertFalse(f.tileSelection.hasSelection());
    }

    private static SpatialBlockData addExisting(Fixture f, int[] rect) {
        SpatialBlockData wall = SpatialTileSelectionService.fromOccupiedRect(
                f.map, rect[0], rect[1], rect[2], rect[3], 0f, 10f);
        wall.id = f.walls.blocks.size + 1;
        wall.structureId = rect[4];
        f.walls.blocks.add(wall);
        return wall;
    }

    private static void select(Fixture f, int[] rect) {
        f.tileSelection.beginDrag(f.layerId, rect[0], rect[1]);
        f.tileSelection.updateDrag(rect[2], rect[3]);
        f.tileSelection.finishDrag();
    }

    private static int[] rect(int minX, int minY, int maxX, int maxY, int structureId) {
        return new int[]{minX, minY, maxX, maxY, structureId};
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        f.world = new World(new WorldConfiguration());
        f.history = new HistoryManager(16);
        f.layerId = f.world.create();
        f.history.historyIds().ensureForEntity(f.layerId);
        TiledLayerComponent tiled = f.world.getMapper(TiledLayerComponent.class).create(f.layerId);
        tiled.data = new TiledMapLayerData(8, 8, 16, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
        tiled.defaultTileHeight = 10f;
        f.map = tiled.data;
        for (int gy = 0; gy < 8; gy++) for (int gx = 0; gx < 8; gx++) f.map.setTile(gx, gy, 1);
        f.walls = f.world.getMapper(SpatialBlocksComponent.class).create(f.layerId);
        f.blockSelection = new SpatialBlockSelectionService();
        f.tileSelection = new SpatialTileSelectionService();
        return f;
    }

    private static final class Fixture {
        World world;
        HistoryManager history;
        int layerId;
        TiledMapLayerData map;
        SpatialBlocksComponent walls;
        SpatialBlockSelectionService blockSelection;
        SpatialTileSelectionService tileSelection;
    }
}
