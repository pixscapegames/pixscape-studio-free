package games.pixscape.studio.service.physics;

import games.pixscape.studio.event.EventFlow;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSelectionServiceTest {

    @Test
    public void matchingFixtureClearPreservesBodyAndPublishesOnce() {
        PhysicsSelectionService selection = new PhysicsSelectionService();
        selection.setSelectedFixture(17, 23);
        selection.setHoveredFixture(17, 23);
        EventFlow.i().flush();
        int[] publications = {0};
        EventFlow.Listener<EventFlow.FixtureSelectionCleared> listener = event -> publications[0]++;
        EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class, listener);

        try {
            Assert.assertFalse(selection.clearSelectedFixtureIfMatches(18, 23));
            Assert.assertFalse(selection.clearSelectedFixtureIfMatches(17, 24));
            EventFlow.i().flush();
            Assert.assertEquals(0, publications[0]);
            Assert.assertEquals(23, selection.getSelectedFixtureId());

            Assert.assertTrue(selection.clearSelectedFixtureIfMatches(17, 23));
            Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                    selection.getSelectedFixtureId());
            Assert.assertFalse(selection.hasHoveredFixture());
            Assert.assertTrue(selection.isFocusedBody(17));

            EventFlow.i().flush();
            Assert.assertEquals(1, publications[0]);
        } finally {
            EventFlow.i().unsubscribe(EventFlow.FixtureSelectionCleared.class, listener);
        }
    }
}
