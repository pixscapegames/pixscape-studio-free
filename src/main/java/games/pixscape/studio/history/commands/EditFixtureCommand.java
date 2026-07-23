package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class EditFixtureCommand
        implements Command, PreExecutionNoopCommand {

    public static final class Snapshot {
        private final PhysicsShapeData data;

        private Snapshot(PhysicsShapeData data) {
            this.data = data;
        }

        public static Snapshot capture(PhysicsShapeData fixture) {
            if (fixture == null) return null;
            return new Snapshot(fixture.copy());
        }

        public PhysicsShapeData copyData() {
            return data != null ? data.copy() : null;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null || data == null || other.data == null) return false;

            return data.shapeType == other.data.shapeType
                    && data.polygonVertexCount == other.data.polygonVertexCount
                    && samePoly(data.polygonVertices, other.data.polygonVertices, data.polygonVertexCount)
                    && Float.compare(data.halfWidth, other.data.halfWidth) == 0
                    && Float.compare(data.halfHeight, other.data.halfHeight) == 0
                    && Float.compare(data.angleDegrees, other.data.angleDegrees) == 0
                    && Float.compare(data.radius, other.data.radius) == 0
                    && Float.compare(data.offsetX, other.data.offsetX) == 0
                    && Float.compare(data.offsetY, other.data.offsetY) == 0
                    && Float.compare(data.density, other.data.density) == 0
                    && Float.compare(data.friction, other.data.friction) == 0
                    && Float.compare(data.restitution, other.data.restitution) == 0
                    && data.sensor == other.data.sensor
                    && data.categoryBits == other.data.categoryBits
                    && data.maskBits == other.data.maskBits
                    && data.groupIndex == other.data.groupIndex;
        }

        public boolean sameGeometryAs(Snapshot other) {
            if (other == null || data == null || other.data == null) return false;
            return data.shapeType == other.data.shapeType
                    && data.polygonVertexCount == other.data.polygonVertexCount
                    && samePoly(data.polygonVertices, other.data.polygonVertices, data.polygonVertexCount)
                    && Float.compare(data.halfWidth, other.data.halfWidth) == 0
                    && Float.compare(data.halfHeight, other.data.halfHeight) == 0
                    && Float.compare(data.angleDegrees, other.data.angleDegrees) == 0
                    && Float.compare(data.radius, other.data.radius) == 0
                    && Float.compare(data.offsetX, other.data.offsetX) == 0
                    && Float.compare(data.offsetY, other.data.offsetY) == 0;
        }

        private static boolean samePoly(float[] a, float[] b, int count) {
            int floatCount = Math.max(0, count) * 2;
            for (int i = 0; i < floatCount; i++) {
                float av = (a != null && i < a.length) ? a[i] : 0f;
                float bv = (b != null && i < b.length) ? b[i] : 0f;
                if (Float.compare(av, bv) != 0) return false;
            }
            return true;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final int physicsShapeId;

    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;

    private final Snapshot before;
    private final Snapshot after;
    private final int dirtyMask;
    private final boolean publishStructureChanged;
    private final boolean noop;

    public EditFixtureCommand(World world,
                              HistoryIdRegistry historyIds,
                              PhysicsSelectionService physicsSelectionService,
                              int bodyEntityId,
                              int physicsShapeId,
                              Snapshot before,
                              Snapshot after,
                              int dirtyMask,
                              boolean publishStructureChanged) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.physicsShapeId = physicsShapeId;

        int previousFocusedBodyEid = physicsSelectionService.getFocusedBodyEid();

        this.previousFocusedBodyHistoryId =
                FixtureCommandSupport.toHistoryId(historyIds, previousFocusedBodyEid);

        this.previousSelectedFixtureId = physicsSelectionService.getSelectedPhysicsShapeId();

        this.before = before;
        this.after = after;
        this.dirtyMask = (dirtyMask != 0) ? dirtyMask : PhysicsDirtyBits.FIXTURE;
        this.publishStructureChanged = publishStructureChanged;

        this.noop = world == null
                || historyIds == null
                || bodyHistoryId <= 0L
                || physicsShapeId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Fixture";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after, true);
    }

    @Override
    public void undo() {
        apply(before, false);
    }

    private void apply(Snapshot snapshot, boolean keepCurrentSelection) {
        if (noop || snapshot == null) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int fixtureIndex = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        if (fixtureIndex < 0) return;

        PhysicsShapeData replacement = snapshot.copyData();
        if (replacement == null) return;
        replacement.physicsShapeId = physicsShapeId;
        fixtures.shapes.set(fixtureIndex, replacement);

        if (keepCurrentSelection) {
            FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, physicsShapeId);
        } else {
            FixtureCommandSupport.restoreSelection(
                    world,
                    historyIds,
                    physicsSelectionService,
                    previousFocusedBodyHistoryId,
                    previousSelectedFixtureId
            );
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(bodyEid, dirtyMask);
        }

        EventFlow.i().publish(new EventFlow.FixtureParametersChanged(
                bodyEid,
                physicsShapeId,
                EventFlow.tag(this)
        ));

        if (publishStructureChanged) {
            FixtureCommandSupport.publishStructureChanged(bodyEid, this);
        }
    }
}
