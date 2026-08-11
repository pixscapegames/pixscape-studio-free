package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class ResizeBoxFixtureCommand
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
                                   int physicsShapeId,
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
                || physicsShapeId <= 0L
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

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        if (index < 0) return;
        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, bodyEid);
        PhysicsShapeData fixture = candidate.get(index);
        if (fixture.geometry == null
                || fixture.geometry.shapeType
                != PhysicsGeometryData.SHAPE_BOX) return;

        fixture.geometry.offsetX = afterOffsetX;
        fixture.geometry.offsetY = afterOffsetY;
        fixture.geometry.halfWidth = afterHalfW;
        fixture.geometry.halfHeight = afterHalfH;
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
        if (fixture.geometry == null
                || fixture.geometry.shapeType
                != PhysicsGeometryData.SHAPE_BOX) return;

        fixture.geometry.offsetX = beforeOffsetX;
        fixture.geometry.offsetY = beforeOffsetY;
        fixture.geometry.halfWidth = beforeHalfW;
        fixture.geometry.halfHeight = beforeHalfH;
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
