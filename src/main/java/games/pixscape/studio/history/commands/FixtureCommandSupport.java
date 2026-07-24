package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
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

    static PhysicsShapesComponent getFixtures(World world, int entityId, boolean create) {
        if (world == null || entityId < 0) return null;
        ComponentMapper<PhysicsShapesComponent> mapper = world.getMapper(PhysicsShapesComponent.class);
        if (mapper.has(entityId)) return mapper.get(entityId);
        return create ? mapper.create(entityId) : null;
    }

    static int indexOfFixture(PhysicsShapesComponent fixtures, int physicsShapeId) {
        if (fixtures == null || physicsShapeId <= 0L) return -1;
        for (int i = 0, n = fixtures.shapes.size; i < n; i++) {
            PhysicsShapeData fixture = fixtures.shapes.get(i);
            if (fixture == null) continue;
            if (fixture.physicsShapeId == physicsShapeId) return i;
        }
        return -1;
    }

    static PhysicsShapeData fixtureById(PhysicsShapesComponent fixtures, int physicsShapeId) {
        int index = indexOfFixture(fixtures, physicsShapeId);
        return index >= 0 ? fixtures.shapes.get(index) : null;
    }

    public static PhysicsShapeData createDefaultFixture() {
        PhysicsShapeData fixture = new PhysicsShapeData();
        fixture.shapeType = PhysicsShapeData.SHAPE_BOX;
        fixture.polygonVertices = new float[0];
        fixture.polygonVertexCount = 0;
        fixture.halfWidth = 0.5f;
        fixture.halfHeight = 0.5f;
        fixture.angleDegrees = 0f;
        fixture.radius = 0.5f;
        fixture.offsetX = 0f;
        fixture.offsetY = 0f;
        fixture.density = 1f;
        fixture.friction = 0.2f;
        fixture.restitution = 0f;
        fixture.sensor = false;
        fixture.categoryBits = 0x0001;
        fixture.maskBits = (short) 0xFFFF;
        fixture.groupIndex = 0;
        fixture.physicsShapeId = 0;
        return fixture;
    }

    static PhysicsShapeData deepCopyWithFreshId(
            games.pixscape.runtime.service.PhysicsService physicsService,
            PhysicsShapeData source) {
        if (source == null) return null;
        PhysicsShapeData copy = source.copy();
        copy.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
        return copy;
    }

    static void focusAndSelect(PhysicsSelectionService selection, int bodyEid, int physicsShapeId) {
        if (selection == null) return;

        selection.focusBody(bodyEid);
        if (physicsShapeId > 0L) {
            selection.setSelectedShape(bodyEid, physicsShapeId);
            EventFlow.i().publish(new EventFlow.FixtureSelectionChanged(bodyEid, physicsShapeId, EventFlow.tag(selection)));
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

        PhysicsShapesComponent fixtures = getFixtures(world, focusedBodyEid, false);
        if (indexOfFixture(fixtures, previousSelectedFixtureId) >= 0) {
            selection.setSelectedShape(focusedBodyEid, previousSelectedFixtureId);
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
