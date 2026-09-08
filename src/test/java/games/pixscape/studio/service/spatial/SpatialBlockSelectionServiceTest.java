package games.pixscape.studio.service.spatial;

import games.pixscape.studio.event.EventFlow;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockSelectionServiceTest {
    @Test
    public void selectionLifecycleTracksLayerBlockAndHover() {
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();

        selection.enterMap(10);
        Assert.assertTrue(selection.isEditingActive());
        Assert.assertEquals(10, selection.getEditingMapEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        selection.selectBlock(10, 4);
        Assert.assertTrue(selection.hasSelectedBlock());
        Assert.assertEquals(4, selection.getSelectedBlockId());
        Assert.assertEquals(4, selection.getHoveredBlockId());

        selection.setHoveredBlock(6);
        Assert.assertEquals(6, selection.getHoveredBlockId());

        selection.clearSelectionOnly();
        Assert.assertEquals(10, selection.getEditingMapEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getHoveredBlockId());
    }

    @Test
    public void tiledMapTargetChangeClearsSpatialAuthoringMode() {
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        selection.selectBlock(10, 4);

        EventFlow.i().publish(new EventFlow.TiledMapEditingTargetChanged(11, 0));
        EventFlow.i().flush();

        Assert.assertFalse(selection.isEditingActive());
        Assert.assertEquals(SpatialBlockSelectionService.NO_MAP, selection.getEditingMapEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
    }

    @Test
    public void hoveredHandleStateIsExplicitAndClearsWithHover() {
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        selection.selectBlock(10, 4);
        selection.setHoveredHandle(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X, false);

        Assert.assertEquals(SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_X,
                selection.getHoveredResizeHandle());
        Assert.assertFalse(selection.isHoveredHeightHandle());

        selection.clearHover();
        Assert.assertNull(selection.getHoveredResizeHandle());
        Assert.assertFalse(selection.isHoveredHeightHandle());
    }

    @Test
    public void authoritativeClearPublishesNoWallStateAndRepeatedClearIsIdempotent() {
        EventFlow.i().flush();
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        selection.selectBlock(10, 4);
        EventFlow.i().flush();
        int[] notifications = {0};
        int[] layer = {-2};
        int[] block = {-2};
        EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class, event -> {
            notifications[0]++;
            layer[0] = event.mapEntityId();
            block[0] = event.blockId();
        });

        selection.clearSelectionOnly();
        EventFlow.i().flush();

        Assert.assertEquals(1, notifications[0]);
        Assert.assertEquals(10, layer[0]);
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, block[0]);
        Assert.assertFalse(selection.hasSelectedBlock());

        selection.clearSelectionOnly();
        EventFlow.i().flush();
        Assert.assertEquals(1, notifications[0]);
    }
}
