package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockInteractiveEditSupportTest {
    @Test
    public void edgesResizeContinuouslyAndKeepOppositeEdgeFixed() {
        SpatialBlockData original = wall(0, 0, 4, 3);

        SpatialBlockData minX = original.copy();
        SpatialBlockInteractiveEditSupport.resize(minX, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X, 1.37f, 0f);
        Assert.assertEquals(1.37f, minX.x, 0f);
        Assert.assertEquals(4f, minX.x + minX.width, 0f);

        SpatialBlockData maxX = original.copy();
        SpatialBlockInteractiveEditSupport.resize(maxX, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X, 0.2f, 0f);
        Assert.assertEquals(0f, maxX.x, 0f);
        Assert.assertEquals(0.2f, maxX.width, 0f);

        SpatialBlockData minY = original.copy();
        SpatialBlockInteractiveEditSupport.resize(minY, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, 0f, 2.85f);
        Assert.assertEquals(2.85f, minY.y, 0f);
        Assert.assertEquals(3f, minY.y + minY.depth, 0f);

        SpatialBlockData maxY = original.copy();
        SpatialBlockInteractiveEditSupport.resize(maxY, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y, 0f, 0.15f);
        Assert.assertEquals(0f, maxY.y, 0f);
        Assert.assertEquals(0.15f, maxY.depth, 0f);
    }

    @Test
    public void cornerResizeChangesTwoAxesWithoutChangingLinkedRefs() {
        SpatialBlockData original = wall(0, 0, 4, 3);
        SpatialBlockData edited = original.copy();

        SpatialBlockInteractiveEditSupport.resize(edited, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MIN_Y, 1.18f, 0.42f);

        Assert.assertEquals(1.18f, edited.x, 0f);
        Assert.assertEquals(0.42f, edited.y, 0f);
        Assert.assertEquals(2.82f, edited.width, 0.00001f);
        Assert.assertEquals(2.58f, edited.depth, 0.00001f);
        assertSameRefs(original, edited);
    }

    @Test
    public void resizeAndMoveClampInsideLinkedEnvelope() {
        SpatialBlockData original = wall(0, 0, 2, 2);
        original.x = 0.25f;
        original.y = 0.25f;
        original.width = 0.5f;
        original.depth = 0.5f;

        SpatialBlockData resized = original.copy();
        SpatialBlockInteractiveEditSupport.resize(resized, original,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X_MIN_Y, -9f, -7f);
        Assert.assertEquals(0f, resized.x, 0f);
        Assert.assertEquals(0f, resized.y, 0f);

        SpatialBlockData moved = original.copy();
        SpatialBlockInteractiveEditSupport.move(moved, original, 99f, 99f);
        Assert.assertEquals(1.5f, moved.x, 0f);
        Assert.assertEquals(1.5f, moved.y, 0f);
        assertSameRefs(original, moved);
    }

    private static SpatialBlockData wall(int minX, int minY, int maxXExclusive, int maxYExclusive) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 1;
        wall.structureId = 1;
        wall.x = minX;
        wall.y = minY;
        wall.width = maxXExclusive - minX;
        wall.depth = maxYExclusive - minY;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = minY; gy < maxYExclusive; gy++) {
            for (int gx = minX; gx < maxXExclusive; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static void assertSameRefs(SpatialBlockData expected, SpatialBlockData actual) {
        Assert.assertEquals(expected.linkedTileRefs.size, actual.linkedTileRefs.size);
        for (int i = 0; i < expected.linkedTileRefs.size; i++) {
            Assert.assertEquals(expected.linkedTileRefs.get(i).gx, actual.linkedTileRefs.get(i).gx);
            Assert.assertEquals(expected.linkedTileRefs.get(i).gy, actual.linkedTileRefs.get(i).gy);
        }
    }
}
