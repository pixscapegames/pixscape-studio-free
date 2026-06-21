package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class DeleteAuthoredPolygonCommand implements Command, HistoryManager.SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long authoringId;

    private final PhysicsAuthoringBodySnapshot before;
    private PhysicsAuthoringBodySnapshot after;

    private boolean firstRedo = true;
    private final boolean noop;

    public DeleteAuthoredPolygonCommand(World world,
                                        HistoryIdRegistry historyIds,
                                        PhysicsSelectionService physicsSelectionService,
                                        int bodyEid,
                                        long authoringId) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEid);
        this.authoringId = authoringId;

        this.before = PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);

        this.noop = world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || authoringId <= 0L;
    }

    public static DeleteAuthoredPolygonCommand fromGeneratedFixture(World world,
                                                                    HistoryIdRegistry historyIds,
                                                                    PhysicsSelectionService physicsSelectionService,
                                                                    int bodyEid,
                                                                    int fixtureId) {
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);
        AuthoredPolygonData authored = service.findByGeneratedFixtureId(bodyEid, fixtureId);

        long authoringId = authored != null ? authored.authoringId : -1L;

        return new DeleteAuthoredPolygonCommand(
                world,
                historyIds,
                physicsSelectionService,
                bodyEid,
                authoringId
        );
    }

    @Override
    public String label() {
        return "Delete Authored Polygon";
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

        if (!firstRedo && after != null) {
            after.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
            return;
        }

        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);
        service.removeAuthoredPolygon(bodyEid, authoringId);

        physicsSelectionService.clearSelectionOnly();

        after = PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);
        firstRedo = false;
    }

    @Override
    public void undo() {
        if (noop) return;
        before.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
    }
}