package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SelectionServiceFirstSelectedEntityIdTest {

    @Test
    public void selectOnly_setsFirstSelectedEntityId() {
        World world = new World(new WorldConfiguration());
        SelectionService service = new SelectionService(world, null);

        int entityId = world.create();
        service.selectOnly(entityId);

        assertEquals(entityId, service.getFirstSelectedEntityId());
    }

    @Test
    public void clearSelection_resetsFirstSelectedEntityId() {
        World world = new World(new WorldConfiguration());
        SelectionService service = new SelectionService(world, null);

        int entityId = world.create();
        service.selectOnly(entityId);

        service.clearSelection();

        assertEquals(-1, service.getFirstSelectedEntityId());
    }

    @Test
    public void selectAdd_onEmptySelection_setsFirstSelectedEntityId() {
        World world = new World(new WorldConfiguration());
        SelectionService service = new SelectionService(world, null);

        int entityId = world.create();
        service.selectAdd(entityId);

        assertEquals(entityId, service.getFirstSelectedEntityId());
    }

    @Test
    public void toggle_removingFirstSelected_reassignsFirstSelectedEntityIdToRemainingSelection() {
        World world = new World(new WorldConfiguration());
        SelectionService service = new SelectionService(world, null);

        int first = world.create();
        int second = world.create();

        service.selectOnly(first);
        service.selectAdd(second);

        service.toggle(first);

        assertEquals(second, service.getValidFirstSelectedEntityId());
    }
}
