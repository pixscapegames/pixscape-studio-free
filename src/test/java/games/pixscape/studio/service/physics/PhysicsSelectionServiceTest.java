package games.pixscape.studio.service.physics;

import games.pixscape.studio.event.EventFlow;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSelectionServiceTest {
    @Test
    public void storesAndTransitionsSelectionWithoutWorld() {
        PhysicsSelectionService selection = new PhysicsSelectionService();

        selection.setSelectedShape(17, 23, 4);

        Assert.assertEquals(17, selection.getFocusedBodyEid());
        Assert.assertEquals(23, selection.getSelectedPhysicsShapeId());
        Assert.assertEquals(4, selection.getSelectedPartIndex());
        Assert.assertEquals(17, selection.getHoveredBodyEid());
        Assert.assertEquals(23, selection.getHoveredPhysicsShapeId());
        Assert.assertEquals(4, selection.getHoveredPartIndex());

        Assert.assertTrue(selection.resetSelectedPartIndex());
        Assert.assertTrue(selection.resetHoveredPartIndex());
        Assert.assertEquals(PhysicsSelectionService.NO_PART, selection.getSelectedPartIndex());
        Assert.assertEquals(PhysicsSelectionService.NO_PART, selection.getHoveredPartIndex());
    }

    @Test
    public void matchingShapeClearIsExplicitAndDeterministic() {
        PhysicsSelectionService selection = new PhysicsSelectionService();
        selection.setSelectedShape(17, 23);

        Assert.assertFalse(selection.clearSelectedShapeIfMatches(18, 23));
        Assert.assertFalse(selection.clearSelectedShapeIfMatches(17, 24));
        Assert.assertEquals(23, selection.getSelectedPhysicsShapeId());

        Assert.assertTrue(selection.clearSelectedShapeIfMatches(17, 23));
        Assert.assertEquals(PhysicsSelectionService.NO_SHAPE,
                selection.getSelectedPhysicsShapeId());
        Assert.assertFalse(selection.hasHoveredShape());
        Assert.assertTrue(selection.isFocusedBody(17));
    }

    @Test
    public void changingTiledMapTargetClearsFocusedMapPhysicsContext() {
        PhysicsSelectionService selection = new PhysicsSelectionService();
        selection.setSelectedShape(17, 23);

        EventFlow.i().publish(new EventFlow.TiledMapEditingTargetChanged(18, 0));
        EventFlow.i().flush();

        Assert.assertEquals(PhysicsSelectionService.NO_BODY, selection.getFocusedBodyEid());
        Assert.assertEquals(PhysicsSelectionService.NO_SHAPE, selection.getSelectedPhysicsShapeId());
    }
}
