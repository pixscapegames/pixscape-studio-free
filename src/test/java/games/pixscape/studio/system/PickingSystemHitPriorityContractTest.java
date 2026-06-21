package games.pixscape.studio.system;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickingSystemHitPriorityContractTest {

    @Test
    public void isBetterHit_acceptsFirstCandidateWhenNoBestExists() {
        assertTrue(PickingSystem.isBetterHit(-1, Integer.MIN_VALUE, Integer.MIN_VALUE, 5, 0, 0));
    }

    @Test
    public void isBetterHit_rejectsLowerLayerCandidate() {
        assertFalse(PickingSystem.isBetterHit(10, 3, 4, 11, 2, 999));
    }

    @Test
    public void isBetterHit_rejectsLowerZWithinSameLayer() {
        assertFalse(PickingSystem.isBetterHit(10, 3, 6, 11, 3, 5));
    }

    @Test
    public void isBetterHit_rejectsLowerEntityIdWhenLayerAndZAreEqual() {
        assertFalse(PickingSystem.isBetterHit(10, 3, 6, 9, 3, 6));
    }

    @Test
    public void isBetterHit_acceptsHigherEntityIdWhenLayerAndZAreEqual() {
        assertTrue(PickingSystem.isBetterHit(10, 3, 6, 12, 3, 6));
    }
}
