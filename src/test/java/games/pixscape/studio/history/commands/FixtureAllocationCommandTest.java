package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.studio.FixtureIdentityTestSupport;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Test;

public class FixtureAllocationCommandTest {
    @Test
    public void templateIdCannotOverrideAllocatorAndRedoRestoresHistoricalId() {
        World world = FixtureIdentityTestSupport.newWorld();
        HistoryManager history = new HistoryManager(16);
        PhysicsSelectionService selection = new PhysicsSelectionService();
        int body = world.create();
        history.historyIds().ensureForEntity(body);
        world.getMapper(PhysicsFixturesComponent.class).create(body);

        FixtureDefData template = new FixtureDefData();
        template.fixtureId = 999;
        template.friction = 0.73f;
        AddFixtureCommand command = new AddFixtureCommand(
                world, history.historyIds(), selection, body, template, -1);
        history.execute(command);
        int createdId = command.getCreatedFixtureId();

        Assert.assertEquals(1, createdId);
        Assert.assertNotEquals(999, createdId);
        Assert.assertEquals(2, world.getSystem(FixtureIdAllocatorSystem.class)
                .sceneMeta().nextFixtureId);
        Assert.assertEquals(0.73f, fixture(world, body, createdId).friction, 0f);

        history.undo();
        Assert.assertNull(fixture(world, body, createdId));
        history.redo();
        Assert.assertNotNull(fixture(world, body, createdId));
        Assert.assertEquals(2, world.getSystem(FixtureIdAllocatorSystem.class)
                .sceneMeta().nextFixtureId);
    }

    @Test
    public void undoThenNewCreationDoesNotReuseIdentity() {
        World world = FixtureIdentityTestSupport.newWorld();
        HistoryManager history = new HistoryManager(16);
        PhysicsSelectionService selection = new PhysicsSelectionService();
        int body = world.create();
        history.historyIds().ensureForEntity(body);
        world.getMapper(PhysicsFixturesComponent.class).create(body);

        AddFixtureCommand first = new AddFixtureCommand(
                world, history.historyIds(), selection, body);
        history.execute(first);
        history.undo();
        AddFixtureCommand second = new AddFixtureCommand(
                world, history.historyIds(), selection, body);
        history.execute(second);

        Assert.assertEquals(1, first.getCreatedFixtureId());
        Assert.assertEquals(2, second.getCreatedFixtureId());
    }

    private static FixtureDefData fixture(World world, int body, int id) {
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).get(body);
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == id) return fixture;
        }
        return null;
    }
}
