package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class MoveFixtureCommand
        implements Command, PreExecutionNoopCommand {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int physicsShapeId;

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
                              int physicsShapeId,
                              float beforeOffsetX,
                              float beforeOffsetY,
                              float afterOffsetX,
                              float afterOffsetY) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.physicsShapeId = physicsShapeId;

        int previousFocusedBodyEid =
                (physicsSelectionService != null)
                        ? physicsSelectionService.getFocusedBodyEid()
                        : PhysicsSelectionService.NO_BODY;

        this.previousFocusedBodyHistoryId =
                FixtureCommandSupport.toHistoryId(historyIds, previousFocusedBodyEid);

        this.previousSelectedFixtureId =
                (physicsSelectionService != null)
                        ? physicsSelectionService.getSelectedPhysicsShapeId()
                        : PhysicsSelectionService.NO_SHAPE;

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
                || physicsShapeId <= 0L
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

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        if (index < 0) return;
        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, bodyEid);
        PhysicsShapeData fixture = candidate.get(index);
        fixture.directGeometry.offsetX = afterOffsetX;
        fixture.directGeometry.offsetY = afterOffsetY;
        FixtureCommandSupport.prepareAndPublish(world, bodyEid, candidate);

        FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, physicsShapeId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        EventFlow.i().publish(new EventFlow.FixtureParametersChanged(
                bodyEid,
                physicsShapeId,
                EventFlow.tag(this)
        ));
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    @Override
    public void undo() {
        if (noop) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        if (index < 0) return;
        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, bodyEid);
        PhysicsShapeData fixture = candidate.get(index);
        fixture.directGeometry.offsetX = beforeOffsetX;
        fixture.directGeometry.offsetY = beforeOffsetY;
        FixtureCommandSupport.prepareAndPublish(world, bodyEid, candidate);

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
                physicsShapeId,
                EventFlow.tag(this)
        ));
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    public long getFixtureId() {
        return physicsShapeId;
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
