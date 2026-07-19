package games.pixscape.studio.service.spatial;

import org.junit.Assert;
import org.junit.Test;

public class SpatialPointerInteractionTest {
    @Test
    public void clickOnHandleRemainsPressedAndNeverBecomesResize() {
        SpatialPointerInteraction interaction = new SpatialPointerInteraction();
        interaction.press(SpatialPointerInteraction.Target.SELECTED_HANDLE, 100f, 200f);

        Assert.assertFalse(interaction.crossedDragThreshold(103f, 203f));
        Assert.assertEquals(SpatialPointerInteraction.State.PRESSED_HANDLE, interaction.state());
        interaction.release();
        Assert.assertEquals(SpatialPointerInteraction.State.IDLE, interaction.state());
    }

    @Test
    public void thresholdIsExactlyScreenSpaceAndStartsStoredTargetOperation() {
        SpatialPointerInteraction interaction = new SpatialPointerInteraction();
        interaction.press(SpatialPointerInteraction.Target.SELECTED_FOOTPRINT, 10f, 20f);

        Assert.assertFalse(interaction.crossedDragThreshold(14f, 22f));
        Assert.assertTrue(interaction.crossedDragThreshold(15f, 20f));
        interaction.beginMove(true);
        Assert.assertEquals(SpatialPointerInteraction.State.SLIDING_ATTACHED_WALL, interaction.state());
    }

    @Test
    public void wallVolumeCannotTransitionToResizeOrMoveByThresholdInference() {
        SpatialPointerInteraction interaction = new SpatialPointerInteraction();
        interaction.press(SpatialPointerInteraction.Target.OTHER_WALL, 0f, 0f);

        Assert.assertTrue(interaction.crossedDragThreshold(50f, 0f));
        Assert.assertEquals(SpatialPointerInteraction.State.PRESSED_WALL, interaction.state());
    }

    @Test
    public void occupiedTileHasPredictiveHoverBeforePress() {
        SpatialPointerInteraction interaction = new SpatialPointerInteraction();
        interaction.hover(SpatialPointerInteraction.Target.OCCUPIED_TILE);
        Assert.assertEquals(SpatialPointerInteraction.State.HOVER_TILE, interaction.state());
        Assert.assertFalse(interaction.isDragging());
    }

    @Test
    public void targetPriorityIsHandleThenFootprintThenVolumesThenTile() {
        Assert.assertEquals(SpatialPointerInteraction.Target.SELECTED_HANDLE,
                SpatialPointerInteraction.resolveTarget(true, true, true, true, true));
        Assert.assertEquals(SpatialPointerInteraction.Target.SELECTED_FOOTPRINT,
                SpatialPointerInteraction.resolveTarget(false, true, true, true, true));
        Assert.assertEquals(SpatialPointerInteraction.Target.SELECTED_WALL,
                SpatialPointerInteraction.resolveTarget(false, false, true, true, true));
        Assert.assertEquals(SpatialPointerInteraction.Target.OTHER_WALL,
                SpatialPointerInteraction.resolveTarget(false, false, false, true, true));
        Assert.assertEquals(SpatialPointerInteraction.Target.OCCUPIED_TILE,
                SpatialPointerInteraction.resolveTarget(false, false, false, false, true));
    }

    @Test
    public void onlyNonWallPressTargetsClearWallSelection() {
        Assert.assertTrue(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.NONE));
        Assert.assertTrue(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.OCCUPIED_TILE));
        Assert.assertFalse(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.SELECTED_HANDLE));
        Assert.assertFalse(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.SELECTED_FOOTPRINT));
        Assert.assertFalse(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.SELECTED_WALL));
        Assert.assertFalse(SpatialPointerInteraction.clearsWallSelection(
                SpatialPointerInteraction.Target.OTHER_WALL));
    }
}
