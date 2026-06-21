package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class ApplyAuthoredPolygonCommand implements Command, HistoryManager.SupportsNoop {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long authoringId;
    private final float[] sourceVerts;
    private final int sourceCount;
    private final FixtureDefData materialSource;
    private final long fixtureToReplaceId;

    private final PhysicsAuthoringBodySnapshot before;
    private PhysicsAuthoringBodySnapshot after;

    private boolean firstRedo = true;
    private final boolean noop;

    public ApplyAuthoredPolygonCommand(World world,
                                       HistoryIdRegistry historyIds,
                                       PhysicsSelectionService physicsSelectionService,
                                       int bodyEid,
                                       long authoringId,
                                       float[] sourceVerts,
                                       int sourceCount,
                                       FixtureDefData materialSource,
                                       long fixtureToReplaceId) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEid);
        this.authoringId = authoringId;
        this.sourceVerts = copyVerts(sourceVerts, sourceCount);
        this.sourceCount = Math.max(0, sourceCount);
        this.materialSource = materialSource != null ? materialSource.copy() : null;
        this.fixtureToReplaceId = fixtureToReplaceId;

        this.before = PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);

        this.noop = world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || this.sourceCount < 3
                || this.sourceVerts.length < this.sourceCount * 2;
    }

    @Override
    public String label() {
        return "Apply Authored Polygon";
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

        AuthoredPolygonData applied = service.applyAuthoredPolygonReplacingFixture(
                bodyEid,
                authoringId,
                sourceVerts,
                sourceCount,
                materialSource,
                fixtureToReplaceId
        );

        if (applied != null
                && applied.generatedFixtureIds != null
                && applied.generatedFixtureIds.length > 0) {
            physicsSelectionService.focusBody(bodyEid);
            physicsSelectionService.setSelectedFixture(bodyEid, applied.generatedFixtureIds[0]);
        }

        after = PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);
        firstRedo = false;
    }

    @Override
    public void undo() {
        if (noop) return;
        before.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
    }

    private static float[] copyVerts(float[] source, int count) {
        int n = Math.max(0, count) * 2;
        float[] out = new float[n];

        if (source != null && n > 0) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, n));
        }

        return out;
    }
}