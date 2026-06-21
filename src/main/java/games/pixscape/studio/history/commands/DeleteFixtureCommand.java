package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class DeleteFixtureCommand implements Command, HistoryManager.SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;
    private final FixtureDefData deletedSnapshot;
    private final long deletedFixtureId;
    private final int deletedIndex;
    private final boolean noop;

    public DeleteFixtureCommand(World world,
                                HistoryIdRegistry historyIds,
                                PhysicsSelectionService physicsSelectionService,
                                int bodyEntityId,
                                long fixtureId) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;
        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);

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

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEntityId, false);
        this.deletedIndex = FixtureCommandSupport.indexOfFixture(fixtures, fixtureId);
        FixtureDefData deleted = (deletedIndex >= 0) ? fixtures.fixtures.get(deletedIndex) : null;
        this.deletedSnapshot = (deleted != null) ? deleted.copy() : null;
        this.deletedFixtureId = (deleted != null) ? deleted.fixtureId : -1L;

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || fixtures == null
                || deletedSnapshot == null
                || deletedFixtureId <= 0L);
    }

    @Override
    public String label() {
        return "Delete Fixture";
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
        if (fixtures == null) return;

        int index = FixtureCommandSupport.indexOfFixture(fixtures, deletedFixtureId);
        if (index >= 0) {
            fixtures.fixtures.removeIndex(index);
        }

        int fallbackFixtureId = FixtureCommandSupport.pickSelectionAfterDelete(fixtures, Math.max(0, deletedIndex));
        FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, fallbackFixtureId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    @Override
    public void undo() {
        if (noop) return;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, true);
        if (FixtureCommandSupport.indexOfFixture(fixtures, deletedFixtureId) < 0) {
            int index = Math.max(0, Math.min(deletedIndex, fixtures.fixtures.size));
            fixtures.fixtures.insert(index, deletedSnapshot.copy());
        }

        FixtureCommandSupport.restoreSelection(
                world,
                historyIds,
                physicsSelectionService,
                previousFocusedBodyHistoryId,
                previousSelectedFixtureId
        );
        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }
}