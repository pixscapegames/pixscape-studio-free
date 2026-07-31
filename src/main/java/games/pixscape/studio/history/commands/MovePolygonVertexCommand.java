package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class MovePolygonVertexCommand
        implements Command, PreExecutionNoopCommand {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int physicsShapeId;
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
                                    int physicsShapeId,
                                    int vertexIndex,
                                    float beforeX,
                                    float beforeY,
                                    float afterX,
                                    float afterY) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.physicsShapeId = physicsShapeId;
        this.vertexIndex = vertexIndex;

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
                || physicsShapeId <= 0L
                || vertexIndex < 0
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

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        if (index < 0) return;
        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, bodyEid);
        PhysicsShapeData fixture = candidate.get(index);
        if (fixture.geometry == null
                || fixture.geometry.shapeType
                != PhysicsGeometryData.SHAPE_POLYGON) return;
        if (fixture.geometry.polygonVertices == null) return;

        int base = vertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.geometry.polygonVertices.length) return;

        fixture.geometry.polygonVertices[base] = afterX;
        fixture.geometry.polygonVertices[base + 1] = afterY;
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
                != PhysicsGeometryData.SHAPE_POLYGON) return;
        if (fixture.geometry.polygonVertices == null) return;

        int base = vertexIndex * 2;
        if (base < 0 || base + 1 >= fixture.geometry.polygonVertices.length) return;

        fixture.geometry.polygonVertices[base] = beforeX;
        fixture.geometry.polygonVertices[base + 1] = beforeY;
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
