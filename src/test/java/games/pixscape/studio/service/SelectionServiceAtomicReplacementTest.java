package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.event.EventFlow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionServiceAtomicReplacementTest {
    @Test
    public void replaceSelectionPublishesOnlyOneFinalOrderedSnapshot() {
        EventFlow.i().flush();
        World world = new World();
        SelectionService selection = new SelectionService(world, null);
        int old = world.create();
        int first = world.create();
        int second = world.create();
        int inactive = world.create();
        world.process();
        world.delete(inactive);
        world.process();
        selection.selectOnly(old);
        EventFlow.i().flush();

        List<EventFlow.SelectionChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.SelectionChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            selection.replaceSelection(
                    new IntArray(new int[]{first, inactive, second, first}),
                    SelectionService.SelectionSource.TREE);

            assertTrue(events.isEmpty());
            assertEquals(2, selection.getSelectionSet().size);
            assertTrue(selection.getSelectionSet().contains(first));
            assertTrue(selection.getSelectionSet().contains(second));
            assertFalse(selection.getSelectionSet().contains(old));
            assertEquals(first, selection.getFirstSelectedEntityId());

            EventFlow.i().flush();
            assertEquals(1, events.size());
            EventFlow.SelectionChanged event = events.get(0);
            assertEquals(new IntArray(new int[]{first, second}), event.ids());
            assertEquals(first, event.primaryId());
            assertEquals(SelectionService.SelectionSource.TREE, event.source());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }
}
