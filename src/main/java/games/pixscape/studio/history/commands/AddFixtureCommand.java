package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
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
    private final FixtureDefData template;
    private final int insertIndex;
    private final int createdFixtureId;
    private final boolean noop;

    public AddFixtureCommand(World world,
                             HistoryIdRegistry historyIds,
                             PhysicsSelectionService physicsSelectionService,
                             int bodyEntityId) {
        this(world, historyIds, physicsSelectionService, bodyEntityId,
                null, -1);
    }

    public AddFixtureCommand(World world,
                             HistoryIdRegistry historyIds,
                             PhysicsSelectionService physicsSelectionService,
                             int bodyEntityId,
                             FixtureDefData template,
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
                        ? physicsSelectionService.getSelectedFixtureId()
                        : PhysicsSelectionService.NO_FIXTURE;

        FixtureDefData base = (template != null)
                ? template.copy()
                : FixtureCommandSupport.createDefaultFixtureTemplate();

        this.noop = (world == null || historyIds == null || physicsSelectionService == null || bodyHistoryId <= 0L);
        this.createdFixtureId = noop ? 0 : FixtureCommandSupport.allocateNewFixtureId(world);
        base.fixtureId = createdFixtureId;
        this.template = base;
        this.insertIndex = insertIndex;
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

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, true);
        if (FixtureCommandSupport.indexOfFixture(fixtures, createdFixtureId) < 0) {
            FixtureDefData created = template.copy();
            created.fixtureId = createdFixtureId;
            int index = (insertIndex < 0)
                    ? fixtures.fixtures.size
                    : Math.max(0, Math.min(insertIndex, fixtures.fixtures.size));
            fixtures.fixtures.insert(index, created);
        }

        FixtureCommandSupport.focusAndSelect(physicsSelectionService, bodyEid, createdFixtureId);
        FixtureCommandSupport.markDirty(world, bodyEid);
        FixtureCommandSupport.publishStructureChanged(bodyEid, this);
    }

    @Override
    public void undo() {
        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsFixturesComponent fixtures = FixtureCommandSupport.getFixtures(world, bodyEid, false);
        int index = FixtureCommandSupport.indexOfFixture(fixtures, createdFixtureId);
        if (index >= 0) {
            fixtures.fixtures.removeIndex(index);
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
