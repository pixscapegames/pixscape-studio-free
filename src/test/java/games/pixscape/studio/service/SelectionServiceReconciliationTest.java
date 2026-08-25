package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.studio.event.EventFlow;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionServiceReconciliationTest {

    @Test
    public void reconcileKeepsActiveSingleSelectionWithoutPublishing() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        int entity = world.create();
        world.process();
        selection.selectOnly(entity);
        EventFlow.i().flush();

        List<EventFlow.SelectionChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.SelectionChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            selection.reconcileActiveSelection();
            EventFlow.i().flush();

            assertEquals(1, selection.getSelectionSet().size);
            assertTrue(selection.getSelectionSet().contains(entity));
            assertEquals(entity, selection.getFirstSelectedEntityId());
            assertTrue(events.isEmpty());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void reconcileKeepsActiveMultiSelectionWithoutPublishing() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        int first = world.create();
        int second = world.create();
        world.process();
        selection.selectOnly(first);
        selection.selectAdd(second);
        EventFlow.i().flush();

        List<EventFlow.SelectionChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.SelectionChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            selection.reconcileActiveSelection();
            EventFlow.i().flush();

            assertEquals(2, selection.getSelectionSet().size);
            assertTrue(selection.getSelectionSet().contains(first));
            assertTrue(selection.getSelectionSet().contains(second));
            assertEquals(first, selection.getFirstSelectedEntityId());
            assertTrue(events.isEmpty());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void reconcileRemovesInactiveSelectionRepairsPrimaryAndPublishesOnce() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        int first = world.create();
        int second = world.create();
        world.process();
        selection.selectOnly(first);
        selection.selectAdd(second);
        EventFlow.i().flush();

        List<EventFlow.SelectionChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.SelectionChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.SelectionChanged.class, listener);
        try {
            world.delete(first);
            world.process();
            selection.reconcileActiveSelection();
            EventFlow.i().flush();

            assertEquals(1, selection.getSelectionSet().size);
            assertFalse(selection.getSelectionSet().contains(first));
            assertTrue(selection.getSelectionSet().contains(second));
            assertEquals(second, selection.getFirstSelectedEntityId());
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).ids().size);
            assertEquals(second, events.get(0).primaryId());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, listener);
            EventFlow.i().flush();
            world.dispose();
        }
    }

    @Test
    public void reconcileClearsPrimaryWhenTheOnlySelectedEntityWasDeleted() {
        EventFlow.i().flush();
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        int entity = world.create();
        world.process();
        selection.selectOnly(entity);

        world.delete(entity);
        world.process();
        selection.reconcileActiveSelection();
        EventFlow.i().flush();

        assertTrue(selection.getSelectionSet().isEmpty());
        assertEquals(-1, selection.getFirstSelectedEntityId());
        world.dispose();
    }
}
