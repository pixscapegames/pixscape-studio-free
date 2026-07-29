package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class DuplicateFixtureCommand implements Command, HistoryManager.SupportsNoop {

    private final AddFixtureCommand delegate;
    private final boolean noop;

    public DuplicateFixtureCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   PhysicsSelectionService physicsSelectionService,
                                   PhysicsService physicsService,
                                   int bodyEntityId,
                                   int sourceFixtureId) {
        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEntityId, false);
        int sourceIndex = FixtureCommandSupport.indexOfFixture(fixtures, sourceFixtureId);
        PhysicsShapeData source = (sourceIndex >= 0) ? fixtures.shapes.get(sourceIndex) : null;
        boolean linked = source != null && source.spatialBlockId > 0;
        PhysicsShapeData duplicate = linked
                ? null
                : FixtureCommandSupport.deepCopyWithFreshId(physicsService, source);

        this.noop = (source == null || linked || duplicate == null);
        this.delegate = noop
                ? null
                : new AddFixtureCommand(
                        world,
                        historyIds,
                        physicsSelectionService,
                        physicsService,
                        bodyEntityId,
                        duplicate,
                        sourceIndex + 1
                );
    }

    @Override
    public String label() {
        return "Duplicate Fixture";
    }

    @Override
    public boolean isNoop() {
        return noop || delegate == null || delegate.isNoop();
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

    public int getCreatedFixtureId() {
        return delegate != null ? delegate.getCreatedFixtureId() : -1;
    }
}
