package games.pixscape.studio.system;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickingSystemQuadEditInteractionTest {

    @Test
    public void onlyASecondStationaryBodyClickCanToggleQuad() {
        assertFalse(toggle(false, false, false, false, true, true, true));
        assertTrue(toggle(false, false, false, true, true, true, true));
        assertFalse(toggle(true, false, false, true, true, true, true));
        assertFalse(toggle(false, true, false, true, true, true, true));
        assertFalse(toggle(false, false, true, true, true, true, true));
    }

    @Test
    public void eligibilitySelectionAndEditingContextAreRequired() {
        assertFalse(toggle(false, false, false, true, false, true, true));
        assertFalse(toggle(false, false, false, true, true, false, true));
        assertFalse(toggle(false, false, false, true, true, true, false));
    }

    @Test
    public void freeMoveStartsAtFourScreenPixelsAtEveryZoom() {
        assertFalse(PickingSystem.reachedFreeMoveThreshold(1.9f, 0f, 0.5f));
        assertTrue(PickingSystem.reachedFreeMoveThreshold(2f, 0f, 0.5f));

        assertFalse(PickingSystem.reachedFreeMoveThreshold(7.9f, 0f, 2f));
        assertTrue(PickingSystem.reachedFreeMoveThreshold(8f, 0f, 2f));
    }

    @Test
    public void transformHandlesAreCompletelyBypassedInQuadMode() {
        assertTrue(PickingSystem.shouldDetectEntityTransformHandles(1, false));
        assertFalse(PickingSystem.shouldDetectEntityTransformHandles(1, true));
        assertFalse(PickingSystem.shouldDetectEntityTransformHandles(2, false));
    }

    private static boolean toggle(boolean moved,
                                  boolean pressOnHandle,
                                  boolean ctrl,
                                  boolean pressWasSelected,
                                  boolean onlySelected,
                                  boolean eligible,
                                  boolean normalContext) {
        return PickingSystem.shouldToggleQuadEdit(
                moved,
                pressOnHandle,
                ctrl,
                pressWasSelected,
                onlySelected,
                eligible,
                normalContext);
    }
}
