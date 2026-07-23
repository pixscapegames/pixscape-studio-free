package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class AddFixtureCommand implements Command, HistoryManager.SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long previousFocusedBodyHistoryId;
    private final int previousSelectedFixtureId;
    private final PhysicsShapeData template;
    private final int insertIndex;
    private final int createdFixtureId;
    private final boolean noop;

    public AddFixtureCommand(World world,
                             HistoryIdRegistry historyIds,
                             PhysicsSelectionService physicsSelectionService,
                             int bodyEntityId) {
        this(world, historyIds, physicsSelectionService, bodyEntityId,
                FixtureCommandSupport.createDefaultFixture(), -1);
    }

    public AddFixtureCommand(World world,
                             HistoryIdRegistry historyIds,
                             PhysicsSelectionService physicsSelectionService,
                             int bodyEntityId,
                             PhysicsShapeData template,
                             int insertIndex) {
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
                        ? physicsSelectionService.getSelectedPhysicsShapeId()
                        : PhysicsSelectionService.NO_SHAPE;

        PhysicsShapeData base = (template != null)
                ? template.copy()
                : FixtureCommandSupport.createDefaultFixture();

        this.createdFixtureId =
                games.pixscape.studio.service.physics.PhysicsShapeIdService
                        .allocateNewPhysicsShapeId();
        base.physicsShapeId = createdFixtureId;
        this.template = base;
        this.insertIndex = insertIndex;
        this.noop = (world == null || historyIds == null || physicsSelectionService == null || bodyHistoryId <= 0L);
    }

    @Override
    public String label() {
        return "Add Fixture";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, true);
        if (FixtureCommandSupport.indexOfFixture(fixtures, createdFixtureId) < 0) {
            PhysicsShapeData created = template.copy();
            created.physicsShapeId = createdFixtureId;
            int index = (insertIndex < 0)
                    ? fixtures.shapes.size
                    : Math.max(0, Math.min(insertIndex, fixtures.shapes.size));
            fixtures.shapes.insert(index, created);
        }

        FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, createdFixtureId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    @Override
    public void undo() {
        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsShapesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, createdFixtureId);
        if (index >= 0) {
            fixtures.shapes.removeIndex(index);
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

    public int getCreatedFixtureId() {
        return createdFixtureId;
    }
}
