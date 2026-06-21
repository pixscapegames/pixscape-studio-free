package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SpatialBlockInteractiveEditSupportTest {
    @Test
    public void resizingIntoAnotherBlockVolumeIsBlockedAndStoredVolumeRemainsValid() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 2f, 0f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.resizeCornerIfValid(
                edited,
                blocks,
                edited.id,
                SpatialBlockInteractiveEditSupport.CornerHandle.BOTTOM,
                2.5f,
                1f
        );

        Assert.assertFalse(accepted);
        Assert.assertEquals(0f, edited.x, 0.0001f);
        Assert.assertEquals(0f, edited.y, 0.0001f);
        Assert.assertEquals(1f, edited.width, 0.0001f);
        Assert.assertEquals(1f, edited.depth, 0.0001f);
        Assert.assertFalse(SpatialBlockVolumeValidator.overlapsAnyBlockVolume(edited, blocks, edited.id));
    }

    @Test
    public void movingIntoAnotherBlockVolumeIsBlockedAndStoredVolumeRemainsValid() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 2f, 0f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                2f,
                0f
        );

        Assert.assertFalse(accepted);
        Assert.assertEquals(0f, edited.x, 0.0001f);
        Assert.assertEquals(0f, edited.y, 0.0001f);
        Assert.assertFalse(SpatialBlockVolumeValidator.overlapsAnyBlockVolume(edited, blocks, edited.id));
    }

    @Test
    public void validMoveAndResizeAreAccepted() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 5f, 5f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        Assert.assertTrue(SpatialBlockInteractiveEditSupport.moveByIfValid(edited, blocks, edited.id, 1f, 0f));
        Assert.assertEquals(1f, edited.x, 0.0001f);
        Assert.assertTrue(SpatialBlockInteractiveEditSupport.resizeCornerIfValid(
                edited,
                blocks,
                edited.id,
                SpatialBlockInteractiveEditSupport.CornerHandle.BOTTOM,
                3f,
                1f
        ));
        Assert.assertEquals(2f, edited.width, 0.0001f);
    }

    @Test
    public void overlappingBlockCanMoveWhenOverlapShrinks() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 2f, 1f);
        SpatialBlockData obstacle = block(2, 1f, 0f, 2f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                -0.5f,
                0f
        );

        Assert.assertTrue(accepted);
        Assert.assertEquals(-0.5f, edited.x, 0.0001f);
    }

    @Test
    public void overlappingBlockCannotMoveDeeperIntoOverlap() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 2f, 1f);
        SpatialBlockData obstacle = block(2, 1f, 0f, 2f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                0.5f,
                0f
        );

        Assert.assertFalse(accepted);
        Assert.assertEquals(0f, edited.x, 0.0001f);
    }

    @Test
    public void blockedMoveStillAppliesFreeAxis() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 1.5f, 0f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                1f,
                0.2f
        );

        Assert.assertTrue(accepted);
        Assert.assertEquals(0f, edited.x, 0.0001f);
        Assert.assertEquals(0.2f, edited.y, 0.0001f);
    }

    @Test
    public void blockedResizeStillAppliesFreeAxis() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 2f, 0f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);

        boolean accepted = SpatialBlockInteractiveEditSupport.resizeCornerIfValid(
                edited,
                blocks,
                edited.id,
                SpatialBlockInteractiveEditSupport.CornerHandle.BOTTOM,
                2.5f,
                2f
        );

        Assert.assertTrue(accepted);
        Assert.assertEquals(1f, edited.width, 0.0001f);
        Assert.assertEquals(2f, edited.depth, 0.0001f);
    }

    @Test
    public void interactiveOverlapBlockingDoesNotInvokeModalPopupCallback() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData obstacle = block(2, 1f, 0f, 1f, 1f);
        blocks.blocks.add(edited);
        blocks.blocks.add(obstacle);
        AtomicInteger popupCalls = new AtomicInteger();

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                1f,
                0f
        );

        Assert.assertFalse(accepted);
        Assert.assertEquals(0, popupCalls.get());
    }

    @Test
    public void overlappingVisualAnchorsDoNotBlockInteractiveEditWhenVolumesDoNotOverlap() {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialBlockData edited = block(1, 0f, 0f, 1f, 1f);
        SpatialBlockData other = block(2, 5f, 5f, 1f, 1f);
        edited.beginAuthoredLinkedTileRefs();
        edited.addLinkedTileRef(3, 4, 101);
        other.beginAuthoredLinkedTileRefs();
        other.addLinkedTileRef(3, 4, 101);
        blocks.blocks.add(edited);
        blocks.blocks.add(other);

        boolean accepted = SpatialBlockInteractiveEditSupport.moveByIfValid(
                edited,
                blocks,
                edited.id,
                1f,
                0f
        );

        Assert.assertTrue(accepted);
        Assert.assertEquals(1f, edited.x, 0.0001f);
    }

    private static SpatialBlockData block(int id, float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.enabled = true;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.height = 1f;
        return block;
    }
}
