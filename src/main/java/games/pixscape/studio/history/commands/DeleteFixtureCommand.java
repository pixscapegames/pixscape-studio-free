package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class DeleteFixtureCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final PhysicsShapeData deletedSnapshot;
    private final int deletedPhysicsShapeId;
    private final int deletedIndex;
    private final boolean noop;

    public DeleteFixtureCommand(World world,
                                HistoryIdRegistry historyIds,
                                PhysicsSelectionService physicsSelectionService,
                                int bodyEntityId,
                                long physicsShapeId) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;
        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEntityId);

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEntityId, false);
        this.deletedIndex = FixtureCommandSupport.indexOfFixture(fixtures, physicsShapeId);
        PhysicsShapeData deleted = (deletedIndex >= 0) ? fixtures.shapes.get(deletedIndex) : null;
        this.deletedSnapshot = (deleted != null) ? deleted.copy() : null;
        this.deletedPhysicsShapeId = (deleted != null) ? deleted.physicsShapeId : -1;

        this.noop = (world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || fixtures == null
                || deletedSnapshot == null
                || deletedPhysicsShapeId <= 0);
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

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        if (fixtures == null) return CommandOutcome.NO_CHANGE;

        int index = FixtureCommandSupport.indexOfFixture(fixtures, deletedPhysicsShapeId);
        if (index < 0) return CommandOutcome.NO_CHANGE;
        fixtures.shapes.removeIndex(index);

        if (physicsSelectionService.clearSelectedShapeIfMatches(
                bodyEid, deletedPhysicsShapeId)) {
            EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(
                    EventFlow.tag(physicsSelectionService)));
        }
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

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, true);
        if (FixtureCommandSupport.indexOfFixture(fixtures, deletedPhysicsShapeId) < 0) {
            int index = Math.max(0, Math.min(deletedIndex, fixtures.shapes.size));
            fixtures.shapes.insert(index, deletedSnapshot.copy());
        }

        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
        return CommandOutcome.APPLIED;
    }
}
