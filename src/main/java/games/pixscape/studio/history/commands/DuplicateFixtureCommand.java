package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class DuplicateFixtureCommand implements Command, HistoryManager.SupportsNoop {

    private final AddFixtureCommand delegate;
    private final boolean noop;

    public DuplicateFixtureCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   PhysicsSelectionService physicsSelectionService,
                                   int bodyEntityId,
                                   long sourceFixtureId) {
        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEntityId, false);
        int sourceIndex = FixtureCommandSupport.indexOfFixture(fixtures, sourceFixtureId);
        PhysicsShapeData source = (sourceIndex >= 0) ? fixtures.shapes.get(sourceIndex) : null;
        PhysicsShapeData duplicate = FixtureCommandSupport.deepCopyWithFreshId(source);

        this.noop = (source == null || duplicate == null);
        this.delegate = new AddFixtureCommand(
                world,
                historyIds,
                physicsSelectionService,
                bodyEntityId,
                duplicate,
                (sourceIndex >= 0) ? (sourceIndex + 1) : -1
        );
    }

    @Override
    public String label() {
        return "Duplicate Fixture";
    }

    @Override
    public boolean isNoop() {
        return noop || delegate.isNoop();
    }

    @Override
    public void redo() {
        if (isNoop()) return;
        delegate.redo();
    }

    @Override
    public void undo() {
        if (isNoop()) return;
        delegate.undo();
    }

    public long getCreatedFixtureId() {
        return delegate.getCreatedFixtureId();
    }
}