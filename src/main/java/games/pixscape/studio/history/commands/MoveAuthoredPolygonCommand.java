package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class MoveAuthoredPolygonCommand implements Command, HistoryManager.SupportsNoop {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long authoringId;
    private final int selectedFixtureId;

    private final float beforeOffsetX;
    private final float beforeOffsetY;
    private final float afterOffsetX;
    private final float afterOffsetY;

    private final boolean noop;

    public MoveAuthoredPolygonCommand(World world,
                                      HistoryIdRegistry historyIds,
                                      PhysicsSelectionService physicsSelectionService,
                                      int bodyEid,
                                      long authoringId,
                                      int selectedFixtureId,
                                      float beforeOffsetX,
                                      float beforeOffsetY,
                                      float afterOffsetX,
                                      float afterOffsetY) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEid);
        this.authoringId = authoringId;
        this.selectedFixtureId = selectedFixtureId;

        this.beforeOffsetX = beforeOffsetX;
        this.beforeOffsetY = beforeOffsetY;
        this.afterOffsetX = afterOffsetX;
        this.afterOffsetY = afterOffsetY;

        this.noop = world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || authoringId <= 0L
                || same(beforeOffsetX, afterOffsetX)
                && same(beforeOffsetY, afterOffsetY);
    }

    @Override
    public String label() {
        return "Move Authored Polygon";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(afterOffsetX, afterOffsetY);
    }

    @Override
    public void undo() {
        apply(beforeOffsetX, beforeOffsetY);
    }

    private void apply(float offsetX, float offsetY) {
        if (noop) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        ComponentMapper<PhysicsAuthoringComponent> mAuthoring =
                world.getMapper(PhysicsAuthoringComponent.class);

        PhysicsAuthoringComponent authoring = mAuthoring.getSafe(bodyEid, null);
        if (authoring == null || authoring.polygons == null) return;

        AuthoredPolygonData polygon = null;

        for (int i = 0; i < authoring.polygons.size; i++) {
            AuthoredPolygonData candidate = authoring.polygons.get(i);
            if (candidate != null && candidate.authoringId == authoringId) {
                polygon = candidate;
                break;
            }
        }

        if (polygon == null) return;

        polygon.offsetX = offsetX;
        polygon.offsetY = offsetY;

        applyOffsetToGeneratedFixtures(bodyEid, polygon, offsetX, offsetY);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(bodyEid, PhysicsDirtyBits.FIXTURE);
        }

        physicsSelectionService.focusBody(bodyEid);

        if (selectedFixtureId > 0 && fixtureExists(bodyEid, selectedFixtureId)) {
            physicsSelectionService.setSelectedFixture(bodyEid, selectedFixtureId);
        } else if (polygon.generatedFixtureIds != null && polygon.generatedFixtureIds.length > 0) {
            physicsSelectionService.setSelectedFixture(bodyEid, polygon.generatedFixtureIds[0]);
        } else {
            physicsSelectionService.clearSelectionOnly();
        }

        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(
                bodyEid,
                EventFlow.tag(this)
        ));

        long selected = physicsSelectionService.getSelectedFixtureId();
        if (selected > 0L) {
            EventFlow.i().publish(new EventFlow.FixtureParametersChanged(
                    bodyEid,
                    selected,
                    EventFlow.tag(this)
            ));
        }
    }

    private void applyOffsetToGeneratedFixtures(int bodyEid,
                                                AuthoredPolygonData polygon,
                                                float offsetX,
                                                float offsetY) {
        if (polygon == null || polygon.generatedFixtureIds == null) return;

        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(bodyEid, null);

        if (fixtures == null || fixtures.fixtures == null) return;

        for (int i = 0; i < fixtures.fixtures.size; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;

            if (containsFixtureId(polygon.generatedFixtureIds, fixture.fixtureId)) {
                fixture.offsetX = offsetX;
                fixture.offsetY = offsetY;
            }
        }
    }

    private boolean fixtureExists(int bodyEid, long fixtureId) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(bodyEid, null);

        if (fixtures == null || fixtures.fixtures == null) return false;

        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsFixtureId(int[] ids, long fixtureId) {
        if (ids == null || fixtureId <= 0L) return false;

        for (int id : ids) {
            if (id == fixtureId) return true;
        }

        return false;
    }

    private static boolean same(float a, float b) {
        return Math.abs(a - b) <= EPS;
    }
}