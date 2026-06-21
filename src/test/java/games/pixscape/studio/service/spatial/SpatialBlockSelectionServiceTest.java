package games.pixscape.studio.service.spatial;

import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.SelectionService;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockSelectionServiceTest {
    @Test
    public void selectionLifecycleTracksLayerBlockAndHover() {
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();

        selection.enterLayer(10);
        Assert.assertTrue(selection.isEditingActive());
        Assert.assertEquals(10, selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        selection.selectBlock(10, 4);
        Assert.assertTrue(selection.hasSelectedBlock());
        Assert.assertEquals(4, selection.getSelectedBlockId());
        Assert.assertEquals(4, selection.getHoveredBlockId());

        selection.setHoveredBlock(6);
        Assert.assertEquals(6, selection.getHoveredBlockId());

        selection.clearSelectionOnly();
        Assert.assertEquals(10, selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getHoveredBlockId());
    }

    @Test
    public void currentLayerChangeClearsSpatialAuthoringMode() {
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        selection.selectBlock(10, 4);

        EventFlow.i().publish(new EventFlow.CurrentLayerChanged(
                11,
                SelectionService.SelectionSource.VIEWPORT,
                0
        ));
        EventFlow.i().flush();

        Assert.assertFalse(selection.isEditingActive());
        Assert.assertEquals(SpatialBlockSelectionService.NO_LAYER, selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
    }
}
