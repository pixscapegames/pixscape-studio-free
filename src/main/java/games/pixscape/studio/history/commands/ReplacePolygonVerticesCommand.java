package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class ReplacePolygonVerticesCommand
        implements Command, PreExecutionNoopCommand {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int physicsShapeId;

    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;

    private final float[] beforeVerts;
    private final int beforeCount;

    private final float[] afterVerts;
    private final int afterCount;

    private final boolean noop;

    public ReplacePolygonVerticesCommand(World world,
                                         HistoryIdRegistry historyIds,
                                         PhysicsSelectionService physicsSelectionService,
                                         int bodyEntityId,
                                         int physicsShapeId,
                                         float[] beforeVerts,
                                         int beforeCount,
                                         float[] afterVerts,
                                         int afterCount) {
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

        this.beforeCount = Math.max(0, beforeCount);
        this.afterCount = Math.max(0, afterCount);

        this.beforeVerts = copyVerts(beforeVerts, this.beforeCount);
        this.afterVerts = copyVerts(afterVerts, this.afterCount);

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || physicsShapeId <= 0L
                || this.afterCount < 3
                || samePolygon(this.beforeVerts, this.beforeCount, this.afterVerts, this.afterCount));
    }

    @Override
    public String label() {
        return "Replace Polygon Vertices";
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

        applyPolygon(fixture, afterVerts, afterCount);
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

        applyPolygon(fixture, beforeVerts, beforeCount);
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

    public int getBeforeCount() {
        return beforeCount;
    }

    public int getAfterCount() {
        return afterCount;
    }

    private static void applyPolygon(PhysicsShapeData fixture, float[] verts, int count) {
        fixture.geometry.polygonVertexCount = count;
        fixture.geometry.polygonVertices = copyVerts(verts, count);
    }

    private static float[] copyVerts(float[] verts, int count) {
        int floatCount = Math.max(0, count) * 2;
        float[] out = new float[floatCount];
        if (verts != null && floatCount > 0) {
            System.arraycopy(verts, 0, out, 0, Math.min(floatCount, verts.length));
        }
        return out;
    }

    private static boolean samePolygon(float[] a, int aCount, float[] b, int bCount) {
        if (aCount != bCount) return false;
        int n = aCount * 2;
        for (int i = 0; i < n; i++) {
            float av = (a != null && i < a.length) ? a[i] : 0f;
            float bv = (b != null && i < b.length) ? b[i] : 0f;
            if (Math.abs(av - bv) > EPS) return false;
        }
        return true;
    }
}
