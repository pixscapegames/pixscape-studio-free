package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;

public final class DeleteFixtureCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final FixtureDefData deletedSnapshot;
    private final long deletedFixtureId;
    private final int deletedIndex;
    private final EditSpatialBlockCommand spatialOwnedDelete;
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

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEntityId, false);
        this.deletedIndex = FixtureCommandSupport.indexOfFixture(fixtures, fixtureId);
        FixtureDefData deleted = (deletedIndex >= 0) ? fixtures.fixtures.get(deletedIndex) : null;
        this.deletedSnapshot = (deleted != null) ? deleted.copy() : null;
        this.deletedFixtureId = (deleted != null) ? deleted.fixtureId : -1L;

        SpatialBlockData owner = SpatialOwnedFixtureSupport.findEnabledOwner(
                world, bodyEntityId, deletedFixtureId);
        if (owner != null) {
            SpatialBlockData disabled = owner.copy();
            disabled.physicsCollision = false;
            this.spatialOwnedDelete = new EditSpatialBlockCommand(
                    world,
                    historyIds,
                    null,
                    bodyEntityId,
                    owner.id,
                    owner.copy(),
                    disabled
            );
        } else {
            this.spatialOwnedDelete = null;
        }

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
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        if (noop) return CommandOutcome.NO_CHANGE;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return CommandOutcome.NO_CHANGE;

        if (spatialOwnedDelete != null) {
            CommandOutcome outcome = spatialOwnedDelete.redoOutcome();
            if (outcome != CommandOutcome.APPLIED) return outcome;
            physicsSelectionService.clearSelectedFixtureIfMatches(bodyEid, deletedFixtureId);
            return CommandOutcome.APPLIED;
        }

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        if (fixtures == null) return CommandOutcome.NO_CHANGE;

        int index = FixtureCommandSupport.indexOfFixture(fixtures, deletedFixtureId);
        if (index < 0) return CommandOutcome.NO_CHANGE;
        fixtures.fixtures.removeIndex(index);

        physicsSelectionService.clearSelectedFixtureIfMatches(bodyEid, deletedFixtureId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
        return CommandOutcome.APPLIED;
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        if (noop) return CommandOutcome.NO_CHANGE;

        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return CommandOutcome.NO_CHANGE;

        if (spatialOwnedDelete != null) {
            CommandOutcome outcome = spatialOwnedDelete.undoOutcome();
            if (outcome != CommandOutcome.APPLIED) return outcome;
            return CommandOutcome.APPLIED;
        }

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, true);
        if (FixtureCommandSupport.indexOfFixture(fixtures, deletedFixtureId) < 0) {
            int index = Math.max(0, Math.min(deletedIndex, fixtures.fixtures.size));
            fixtures.fixtures.insert(index, deletedSnapshot.copy());
        }

        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
        return CommandOutcome.APPLIED;
    }
}
