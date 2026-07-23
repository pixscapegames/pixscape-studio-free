package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpatialWallOverlayAffordanceTest {

    @Test
    public void joinedLongitudinalEndIsMarkedInternalAndExposesNoResizeHandle() {
        SpatialBlocksComponent walls = new SpatialBlocksComponent();
        SpatialBlockData selected = wall(1, 2f, 2f, 4f, 1f, 2, 6, 2, 3);
        walls.blocks.add(selected);
        walls.blocks.add(wall(2, 5f, 1f, 1f, 2f, 5, 6, 1, 4));
        SpatialWallAttachments attachments = SpatialWallAttachments.derive(walls, selected);
        SpatialWallEditSession session = new SpatialWallEditSession();

        assertTrue(attachments.isSideJoined(SpatialWallAttachments.MAX_X));
        assertFalse(attachments.isSideJoined(SpatialWallAttachments.MIN_X));
        assertTrue(session.begin(3, selected.id, walls, SpatialWallAuthoringValidatorTest.map(12, 12)));
        assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
        assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X));
    }

    @Test
    public void twoJoinedEndsHideBothEndHandlesButKeepValidThicknessHandles() {
        SpatialBlocksComponent walls = new SpatialBlocksComponent();
        SpatialBlockData selected = wall(1, 2f, 2f, 4f, 1f, 2, 6, 2, 3);
        walls.blocks.add(selected);
        walls.blocks.add(wall(2, 5f, 1f, 1f, 2f, 5, 6, 1, 4));
        walls.blocks.add(wall(3, 2f, 1f, 1f, 3f, 2, 3, 1, 4));
        SpatialWallAttachments attachments = SpatialWallAttachments.derive(walls, selected);
        SpatialWallEditSession session = new SpatialWallEditSession();

        assertTrue(attachments.isSideJoined(SpatialWallAttachments.MIN_X));
        assertTrue(attachments.isSideJoined(SpatialWallAttachments.MAX_X));
        assertTrue(session.begin(3, selected.id, walls, SpatialWallAuthoringValidatorTest.map(12, 12)));
        assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X));
        assertFalse(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
        assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y));
        assertTrue(session.isHandleEnabled(SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_Y));
    }

    private static SpatialBlockData wall(int id,
                                         float x,
                                         float y,
                                         float width,
                                         float depth,
                                         int minGx,
                                         int maxGx,
                                         int minGy,
                                         int maxGy) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = 4;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.height = 10f;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy < maxGy; gy++) {
            for (int gx = minGx; gx < maxGx; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }
}
