package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;

public final class MoveFixtureCommand
        implements Command, PreExecutionNoopCommand {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int fixtureId;

    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;

    private final float beforeOffsetX;
    private final float beforeOffsetY;
    private final float afterOffsetX;
    private final float afterOffsetY;

    private final boolean noop;

    public MoveFixtureCommand(World world,
                              HistoryIdRegistry historyIds,
                              PhysicsSelectionService physicsSelectionService,
                              int bodyEntityId,
                              int fixtureId,
                              float beforeOffsetX,
                              float beforeOffsetY,
                              float afterOffsetX,
                              float afterOffsetY) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.fixtureId = fixtureId;

        int previousFocusedBodyEid =
                (physicsSelectionService != null)
                        ? physicsSelectionService.getFocusedBodyEid()
                        : PhysicsSelectionService.NO_BODY;

        this.previousFocusedBodyHistoryId =
                FixtureCommandSupport.toHistoryId(historyIds, previousFocusedBodyEid);

        this.previousSelectedFixtureId =
                (physicsSelectionService != null)
                        ? physicsSelectionService.getSelectedFixtureId()
                        : PhysicsSelectionService.NO_FIXTURE;

        this.beforeOffsetX = beforeOffsetX;
        this.beforeOffsetY = beforeOffsetY;
        this.afterOffsetX = afterOffsetX;
        this.afterOffsetY = afterOffsetY;

        boolean unchanged =
                Math.abs(beforeOffsetX - afterOffsetX) <= EPS
                        && Math.abs(beforeOffsetY - afterOffsetY) <= EPS;

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || fixtureId <= 0L
                || SpatialOwnedFixtureSupport.isOwned(world, bodyEntityId, fixtureId)
                || unchanged);
    }

    @Override
    public String label() {
        return "Move Fixture";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        if (noop) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        FixtureDefData fixture = FixtureCommandSupport.fixtureById(fixtures, fixtureId);
        if (fixture == null) return;

        fixture.offsetX = afterOffsetX;
        fixture.offsetY = afterOffsetY;

        FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, fixtureId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        EventFlow.i().publish(new EventFlow.FixtureParametersChanged(
                bodyEid,
                fixtureId,
                EventFlow.tag(this)
        ));
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    @Override
    public void undo() {
        if (noop) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        FixtureDefData fixture = FixtureCommandSupport.fixtureById(fixtures, fixtureId);
        if (fixture == null) return;

        fixture.offsetX = beforeOffsetX;
        fixture.offsetY = beforeOffsetY;

        FixtureCommandSupport.restoreSelection(
                world,
                historyIds,
                physicsSelectionService,
                previousFocusedBodyHistoryId,
                previousSelectedFixtureId
        );
        FixtureCommandSupport.markDirty(world, bodyEid);
        EventFlow.i().publish(new EventFlow.FixtureParametersChanged(
                bodyEid,
                fixtureId,
                EventFlow.tag(this)
        ));
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    public long getFixtureId() {
        return fixtureId;
    }

    public float getBeforeOffsetX() {
        return beforeOffsetX;
    }

    public float getBeforeOffsetY() {
        return beforeOffsetY;
    }

    public float getAfterOffsetX() {
        return afterOffsetX;
    }

    public float getAfterOffsetY() {
        return afterOffsetY;
    }
}
