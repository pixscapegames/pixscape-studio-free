package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class FixtureCommandSupport {

    private FixtureCommandSupport() {
    }

    static int resolveBodyEntityId(World world, HistoryIdRegistry historyIds, long bodyHistoryId) {
        if (world == null || historyIds == null || bodyHistoryId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        if (entityId < 0) return -1;
        if (!world.getEntityManager().isActive(entityId)) return -1;
        return entityId;
    }

    static long toHistoryId(HistoryIdRegistry historyIds, int entityId) {
        if (historyIds == null || entityId < 0) return -1L;
        return historyIds.ensureForEntity(entityId);
    }

    static PhysicsFixturesComponent getFixtures(World world, int entityId, boolean create) {
        if (world == null || entityId < 0) return null;
        ComponentMapper<PhysicsFixturesComponent> mapper = world.getMapper(PhysicsFixturesComponent.class);
        if (mapper.has(entityId)) return mapper.get(entityId);
        return create ? mapper.create(entityId) : null;
    }

    static int indexOfFixture(PhysicsFixturesComponent fixtures, long fixtureId) {
        if (fixtures == null || fixtureId <= 0L) return -1;
        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;
            if (fixture.fixtureId == fixtureId) return i;
        }
        return -1;
    }

    static FixtureDefData fixtureById(PhysicsFixturesComponent fixtures, long fixtureId) {
        int index = indexOfFixture(fixtures, fixtureId);
        return index >= 0 ? fixtures.fixtures.get(index) : null;
    }

    public static FixtureDefData createDefaultFixtureTemplate() {
        FixtureDefData fixture = new FixtureDefData();
        PhysicsService.initDefaultFixture(fixture);
        return fixture;
    }

    public static FixtureDefData createDefaultFixture(World world) {
        FixtureDefData fixture = createDefaultFixtureTemplate();
        fixture.fixtureId = allocateNewFixtureId(world);
        return fixture;
    }

    static int allocateNewFixtureId(World world) {
        FixtureIdAllocatorSystem allocator = world != null
                ? world.getSystem(FixtureIdAllocatorSystem.class) : null;
        if (allocator == null) {
            throw new IllegalStateException("FixtureIdAllocatorSystem is required to create a fixture.");
        }
        return allocator.allocateNewFixtureId();
    }

    static void focusAndSelect(PhysicsSelectionService selection, int bodyEid, int fixtureId) {
        if (selection == null) return;

        selection.focusBody(bodyEid);
        if (fixtureId > 0L) {
            selection.setSelectedFixture(bodyEid, fixtureId);
            EventFlow.i().publish(new EventFlow.FixtureSelectionChanged(bodyEid, fixtureId, EventFlow.tag(selection)));
        } else {
            selection.clearSelectionOnly();
            EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(EventFlow.tag(selection)));
        }
    }

    static void restoreSelection(World world,
                                 HistoryIdRegistry historyIds,
                                 PhysicsSelectionService selection,
                                 long previousFocusedBodyHistoryId,
                                 int previousSelectedFixtureId) {
        if (selection == null) return;

        if (previousFocusedBodyHistoryId <= 0L) {
            selection.clear();
            return;
        }

        int focusedBodyEid = resolveBodyEntityId(world, historyIds, previousFocusedBodyHistoryId);
        if (focusedBodyEid < 0) {
            selection.clear();
            return;
        }

        selection.focusBody(focusedBodyEid);
        if (previousSelectedFixtureId <= 0L) {
            selection.clearSelectionOnly();
            return;
        }

        PhysicsFixturesComponent fixtures = getFixtures(world, focusedBodyEid, false);
        if (indexOfFixture(fixtures, previousSelectedFixtureId) >= 0) {
            selection.setSelectedFixture(focusedBodyEid, previousSelectedFixtureId);
        } else {
            selection.clearSelectionOnly();
        }
    }

    public static void markDirty(World world, int entityId) {
        if (world == null || entityId < 0) return;
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(entityId, PhysicsDirtyBits.FIXTURE);
        }
    }

    public static void publishStructureChanged(int entityId, Object source) {
        if (entityId < 0) return;
        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(entityId, EventFlow.tag(source)));
    }
}
