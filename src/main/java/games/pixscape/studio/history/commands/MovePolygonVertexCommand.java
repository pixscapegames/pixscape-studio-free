package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;

public final class MovePolygonVertexCommand
        implements Command, PreExecutionNoopCommand {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int fixtureId;
    private final int vertexIndex;

    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;

    private final float beforeX;
    private final float beforeY;
    private final float afterX;
    private final float afterY;

    private final boolean noop;

    public MovePolygonVertexCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    PhysicsSelectionService physicsSelectionService,
                                    int bodyEntityId,
                                    int fixtureId,
                                    int vertexIndex,
                                    float beforeX,
                                    float beforeY,
                                    float afterX,
                                    float afterY) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.fixtureId = fixtureId;
        this.vertexIndex = vertexIndex;

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

        this.beforeX = beforeX;
        this.beforeY = beforeY;
        this.afterX = afterX;
        this.afterY = afterY;

        boolean unchanged =
                Math.abs(beforeX - afterX) <= EPS
                        && Math.abs(beforeY - afterY) <= EPS;

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || fixtureId <= 0L
                || vertexIndex < 0
                || SpatialOwnedFixtureSupport.isOwned(world, bodyEntityId, fixtureId)
                || unchanged);
    }

    @Override
    public String label() {
        return "Move Polygon Vertex";
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
        if (fixture.shapeType != FixtureDefData.SHAPE_POLYGON) return;
        if (fixture.polyVerts == null) return;

        int base = vertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.polyVerts.length) return;

        fixture.polyVerts[base] = afterX;
        fixture.polyVerts[base + 1] = afterY;

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
        if (fixture.shapeType != FixtureDefData.SHAPE_POLYGON) return;
        if (fixture.polyVerts == null) return;

        int base = vertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.polyVerts.length) return;

        fixture.polyVerts[base] = beforeX;
        fixture.polyVerts[base + 1] = beforeY;

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

    public int getVertexIndex() {
        return vertexIndex;
    }

    public float getBeforeX() {
        return beforeX;
    }

    public float getBeforeY() {
        return beforeY;
    }

    public float getAfterX() {
        return afterX;
    }

    public float getAfterY() {
        return afterY;
    }
}
