package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
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
            return other != null
                    && data != null
                    && data.contentEquals(other.data);
        }

        public boolean sameGeometryAs(Snapshot other) {
            if (other == null || data == null || other.data == null) return false;
            return data.geometry != null
                    && data.geometry.contentEquals(other.data.geometry);
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
        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, bodyEid);
        candidate.set(fixtureIndex, replacement);
        FixtureCommandSupport.prepareAndPublish(world, bodyEid, candidate);

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
