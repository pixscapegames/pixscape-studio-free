package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialStructureTopologyTest {
    @Test
    public void isolatedWallKeepsAssignedIdentityAndExactGeometry() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(8, 8);
        SpatialBlockData candidate = SpatialWallAuthoringValidatorTest.wall(1, 0, 2, 3, 3, 2, 4f, 12f);
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(new SpatialBlocksComponent(), candidate, map);
        Assert.assertTrue(plan.error, plan.valid);
        SpatialBlockData wall = plan.walls.first();
        Assert.assertTrue(wall.id > 0);
        Assert.assertTrue(wall.structureId > 0);
        Assert.assertEquals(2f, wall.x, 0f);
        Assert.assertEquals(3f, wall.y, 0f);
        Assert.assertEquals(3f, wall.width, 0f);
        Assert.assertEquals(2f, wall.depth, 0f);
        Assert.assertEquals(6, wall.linkedTileRefs.size);
    }

    @Test
    public void thickJunctionMergesStructuresAndSmallestIdentityWins() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(12, 12);
        SpatialBlocksComponent existing = SpatialWallAuthoringValidatorTest.component(
                SpatialWallAuthoringValidatorTest.wall(5, 7, 0, 2, 5, 3, 1f, 10f),
                SpatialWallAuthoringValidatorTest.wall(9, 3, 7, 2, 5, 3, 1f, 10f));
        SpatialBlockData bridge = SpatialWallAuthoringValidatorTest.wall(10, 0, 3, 0, 6, 6, 99f, 99f);
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(existing, bridge, map);
        Assert.assertTrue(plan.error, plan.valid);
        for (int i = 0; i < plan.walls.size; i++) {
            Assert.assertEquals(3, plan.walls.get(i).structureId);
            Assert.assertEquals(1f, plan.walls.get(i).altitude, 0f);
            Assert.assertEquals(10f, plan.walls.get(i).height, 0f);
        }
    }

    @Test
    public void everySupportedJunctionThicknessJoinsTheExistingStructure() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(16, 16);
        assertJoins(map, wall(1, 4, 0, 2, 5, 1), wall(0, 0, 2, 0, 1, 5));
        assertJoins(map, wall(1, 4, 0, 0, 5, 4), wall(0, 0, 2, 0, 1, 6));
        assertJoins(map, wall(1, 4, 0, 2, 6, 1), wall(0, 0, 1, 0, 4, 4));
        assertJoins(map, wall(1, 4, 0, 0, 4, 4), wall(0, 0, 2, 2, 4, 4));
        assertJoins(map, wall(1, 4, 0, 0, 8, 7), wall(0, 0, 5, 2, 6, 5));
    }

    @Test
    public void tCrossClosedRoomAndCyclicLabyrinthRemainConnected() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(16, 16);
        SpatialBlocksComponent walls = SpatialWallAuthoringValidatorTest.component(
                wall(1, 6, 1, 1, 7, 1),
                wall(2, 6, 1, 7, 7, 1),
                wall(3, 6, 1, 1, 1, 7),
                wall(4, 6, 7, 1, 1, 7),
                wall(5, 6, 4, 1, 1, 7),
                wall(6, 6, 1, 4, 7, 1),
                wall(7, 6, 4, 4, 6, 1),
                wall(8, 6, 9, 4, 1, 6),
                wall(9, 6, 6, 9, 4, 1));
        walls.blocks.get(0).linkedTileRefs.reverse();
        Assert.assertTrue(SpatialStructureTopology.validate(walls, map).error,
                SpatialStructureTopology.validate(walls, map).valid);
    }

    @Test
    public void incompatibleStructuresCannotMerge() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(12, 12);
        SpatialBlocksComponent existing = SpatialWallAuthoringValidatorTest.component(
                SpatialWallAuthoringValidatorTest.wall(1, 1, 0, 2, 4, 1, 0f, 10f),
                SpatialWallAuthoringValidatorTest.wall(2, 2, 6, 2, 4, 1, 1f, 10f));
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(existing,
                SpatialWallAuthoringValidatorTest.wall(3, 0, 2, 0, 6, 5, 0f, 10f), map);
        Assert.assertFalse(plan.valid);
        Assert.assertEquals(1, existing.blocks.get(0).structureId);
        Assert.assertEquals(2, existing.blocks.get(1).structureId);
    }

    @Test
    public void deletingBridgeSplitsDeterministicallyAbovePreviousMaximum() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(12, 12);
        SpatialBlocksComponent structure = SpatialWallAuthoringValidatorTest.component(
                SpatialWallAuthoringValidatorTest.wall(2, 4, 0, 2, 4, 1, 0f, 10f),
                SpatialWallAuthoringValidatorTest.wall(8, 4, 6, 2, 4, 1, 0f, 10f),
                SpatialWallAuthoringValidatorTest.wall(10, 4, 2, 0, 6, 5, 0f, 10f));
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.delete(structure, 10, map);
        Assert.assertTrue(plan.error, plan.valid);
        Assert.assertEquals(4, find(plan, 2).structureId);
        Assert.assertEquals(5, find(plan, 8).structureId);
    }

    @Test
    public void arrayOrderDoesNotChangeConnectivityOrAssignedIds() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(10, 10);
        SpatialBlockData a = SpatialWallAuthoringValidatorTest.wall(3, 2, 0, 2, 5, 1, 0f, 10f);
        SpatialBlockData b = SpatialWallAuthoringValidatorTest.wall(7, 2, 2, 0, 1, 5, 0f, 10f);
        SpatialBlocksComponent first = SpatialWallAuthoringValidatorTest.component(a.copy(), b.copy());
        SpatialBlocksComponent second = SpatialWallAuthoringValidatorTest.component(b.copy(), a.copy());
        Assert.assertTrue(SpatialStructureTopology.validate(first, map).valid);
        Assert.assertTrue(SpatialStructureTopology.validate(second, map).valid);
    }

    @Test
    public void linkedCellOverlapWithoutPhysicalOverlapDoesNotMerge() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(8, 8);
        SpatialBlockData horizontal = wall(1, 4, 0, 1, 3, 1);
        horizontal.width = 2.1f;
        horizontal.depth = 0.2f;
        SpatialBlockData vertical = wall(2, 0, 2, 0, 1, 3);
        vertical.x = 2.5f;
        vertical.y = 0.2f;
        vertical.width = 0.2f;
        vertical.depth = 0.7f;

        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(
                SpatialWallAuthoringValidatorTest.component(horizontal), vertical, map);

        Assert.assertTrue(plan.error, plan.valid);
        Assert.assertNotEquals(find(plan, 1).structureId, find(plan, 2).structureId);
    }

    @Test
    public void continuousEditCanSplitAndReMergeWithoutChangingLinkedRefs() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(8, 8);
        SpatialBlockData horizontal = wall(1, 4, 0, 1, 3, 1);
        horizontal.width = 2.4f;
        horizontal.depth = 0.3f;
        SpatialBlockData vertical = wall(2, 0, 2, 0, 1, 3);
        vertical.x = 2.2f;
        vertical.y = 0.5f;
        vertical.width = 0.2f;
        vertical.depth = 1f;
        SpatialStructureTopology.Plan merged = SpatialStructureTopology.add(
                SpatialWallAuthoringValidatorTest.component(horizontal), vertical, map);
        Assert.assertTrue(merged.error, merged.valid);
        Assert.assertEquals(find(merged, 1).structureId, find(merged, 2).structureId);

        SpatialBlocksComponent mergedComponent = new SpatialBlocksComponent();
        for (int i = 0; i < merged.walls.size; i++) mergedComponent.blocks.add(merged.walls.get(i).copy());
        SpatialBlockData separated = find(merged, 2).copy();
        int refCount = separated.linkedTileRefs.size;
        separated.x = 2.6f;
        SpatialStructureTopology.Plan split = SpatialStructureTopology.edit(
                mergedComponent, 2, separated, map);
        Assert.assertTrue(split.error, split.valid);
        Assert.assertNotEquals(find(split, 1).structureId, find(split, 2).structureId);
        Assert.assertEquals(refCount, find(split, 2).linkedTileRefs.size);

        SpatialBlocksComponent splitComponent = new SpatialBlocksComponent();
        for (int i = 0; i < split.walls.size; i++) splitComponent.blocks.add(split.walls.get(i).copy());
        SpatialBlockData touchingAgain = find(split, 2).copy();
        touchingAgain.x = 2.2f;
        SpatialStructureTopology.Plan remerged = SpatialStructureTopology.edit(
                splitComponent, 2, touchingAgain, map);
        Assert.assertTrue(remerged.error, remerged.valid);
        Assert.assertEquals(4, find(remerged, 1).structureId);
        Assert.assertEquals(4, find(remerged, 2).structureId);
    }

    private static SpatialBlockData find(SpatialStructureTopology.Plan plan, int id) {
        for (int i = 0; i < plan.walls.size; i++) if (plan.walls.get(i).id == id) return plan.walls.get(i);
        return null;
    }

    private static SpatialBlockData wall(int id, int structureId, int x, int y, int width, int depth) {
        return SpatialWallAuthoringValidatorTest.wall(id, structureId, x, y, width, depth, 0f, 10f);
    }

    private static void assertJoins(TiledMapLayerData map, SpatialBlockData existing, SpatialBlockData candidate) {
        SpatialBlocksComponent component =
                SpatialWallAuthoringValidatorTest.component(existing);
        component.nextSpatialBlockId = existing.id + 1;
        candidate.id = component.peekNextSpatialBlockId();
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(
                component, candidate, map);
        Assert.assertTrue(plan.error, plan.valid);
        Assert.assertEquals(2, plan.walls.size);
        Assert.assertEquals(4, plan.walls.get(0).structureId);
        Assert.assertEquals(4, plan.walls.get(1).structureId);
    }

    @Test
    public void addRejectsUnassignedIdentityWithoutAllocating() {
        TiledMapLayerData map = SpatialWallAuthoringValidatorTest.map(8, 8);
        SpatialBlockData candidate = wall(0, 0, 1, 1, 2, 1);

        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(
                new SpatialBlocksComponent(), candidate, map);

        Assert.assertFalse(plan.valid);
        Assert.assertTrue(plan.error.contains("strictly positive"));
        Assert.assertEquals(0, candidate.id);
        Assert.assertEquals(0, plan.walls.size);
    }
}
