package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class ResizeBoxFixtureCommand implements Command, HistoryManager.SupportsNoop {

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
    private final float beforeHalfW;
    private final float beforeHalfH;

    private final float afterOffsetX;
    private final float afterOffsetY;
    private final float afterHalfW;
    private final float afterHalfH;

    private final boolean noop;

    public ResizeBoxFixtureCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   PhysicsSelectionService physicsSelectionService,
                                   int bodyEntityId,
                                   int fixtureId,
                                   float beforeOffsetX,
                                   float beforeOffsetY,
                                   float beforeHalfW,
                                   float beforeHalfH,
                                   float afterOffsetX,
                                   float afterOffsetY,
                                   float afterHalfW,
                                   float afterHalfH) {
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
        this.beforeHalfW = beforeHalfW;
        this.beforeHalfH = beforeHalfH;

        this.afterOffsetX = afterOffsetX;
        this.afterOffsetY = afterOffsetY;
        this.afterHalfW = afterHalfW;
        this.afterHalfH = afterHalfH;

        boolean unchanged =
                Math.abs(beforeOffsetX - afterOffsetX) <= EPS
                        && Math.abs(beforeOffsetY - afterOffsetY) <= EPS
                        && Math.abs(beforeHalfW - afterHalfW) <= EPS
                        && Math.abs(beforeHalfH - afterHalfH) <= EPS;

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || fixtureId <= 0L
                || unchanged);
    }

    @Override
    public String label() {
        return "Resize Box Fixture";
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
        if (fixture.shapeType != FixtureDefData.SHAPE_BOX) return;

        fixture.offsetX = afterOffsetX;
        fixture.offsetY = afterOffsetY;
        fixture.halfW = afterHalfW;
        fixture.halfH = afterHalfH;

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
        if (fixture.shapeType != FixtureDefData.SHAPE_BOX) return;

        fixture.offsetX = beforeOffsetX;
        fixture.offsetY = beforeOffsetY;
        fixture.halfW = beforeHalfW;
        fixture.halfH = beforeHalfH;

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

    public float getBeforeHalfW() {
        return beforeHalfW;
    }

    public float getBeforeHalfH() {
        return beforeHalfH;
    }

    public float getAfterOffsetX() {
        return afterOffsetX;
    }

    public float getAfterOffsetY() {
        return afterOffsetY;
    }

    public float getAfterHalfW() {
        return afterHalfW;
    }

    public float getAfterHalfH() {
        return afterHalfH;
    }
}