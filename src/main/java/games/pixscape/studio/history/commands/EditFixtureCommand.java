package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class EditFixtureCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final FixtureDefData data;

        private Snapshot(FixtureDefData data) {
            this.data = data;
        }

        public static Snapshot capture(FixtureDefData fixture) {
            if (fixture == null) return null;
            return new Snapshot(fixture.copy());
        }

        public FixtureDefData copyData() {
            return data != null ? data.copy() : null;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null || data == null || other.data == null) return false;

            return data.shapeType == other.data.shapeType
                    && data.polyCount == other.data.polyCount
                    && samePoly(data.polyVerts, other.data.polyVerts, data.polyCount)
                    && Float.compare(data.halfW, other.data.halfW) == 0
                    && Float.compare(data.halfH, other.data.halfH) == 0
                    && Float.compare(data.angleDeg, other.data.angleDeg) == 0
                    && Float.compare(data.radius, other.data.radius) == 0
                    && Float.compare(data.offsetX, other.data.offsetX) == 0
                    && Float.compare(data.offsetY, other.data.offsetY) == 0
                    && Float.compare(data.density, other.data.density) == 0
                    && Float.compare(data.friction, other.data.friction) == 0
                    && Float.compare(data.restitution, other.data.restitution) == 0
                    && data.isSensor == other.data.isSensor
                    && data.categoryBits == other.data.categoryBits
                    && data.maskBits == other.data.maskBits
                    && data.groupIndex == other.data.groupIndex;
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
    private final int fixtureId;

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
                              int fixtureId,
                              Snapshot before,
                              Snapshot after,
                              int dirtyMask,
                              boolean publishStructureChanged) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);
        this.fixtureId = fixtureId;

        int previousFocusedBodyEid = physicsSelectionService.getFocusedBodyEid();

        this.previousFocusedBodyHistoryId =
                FixtureCommandSupport.toHistoryId(historyIds, previousFocusedBodyEid);

        this.previousSelectedFixtureId = physicsSelectionService.getSelectedFixtureId();

        this.before = before;
        this.after = after;
        this.dirtyMask = (dirtyMask != 0) ? dirtyMask : PhysicsDirtyBits.FIXTURE;
        this.publishStructureChanged = publishStructureChanged;

        this.noop = world == null
                || historyIds == null
                || bodyHistoryId <= 0L
                || fixtureId <= 0L
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

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int fixtureIndex = FixtureCommandSupport.indexOfFixture(fixtures, fixtureId);
        if (fixtureIndex < 0) return;

        FixtureDefData replacement = snapshot.copyData();
        if (replacement == null) return;
        replacement.fixtureId = fixtureId;
        fixtures.fixtures.set(fixtureIndex, replacement);

        if (keepCurrentSelection) {
            FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, fixtureId);
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
                fixtureId,
                EventFlow.tag(this)
        ));

        if (publishStructureChanged) {
            FixtureCommandSupport.publishStructureChanged(bodyEid, this);
        }
    }
}
