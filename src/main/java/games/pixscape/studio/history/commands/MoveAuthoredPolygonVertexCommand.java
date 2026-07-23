package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class MoveAuthoredPolygonVertexCommand implements Command, HistoryManager.SupportsNoop {

    private static final float EPS = 1e-6f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsSelectionService physicsSelectionService;

    private final long bodyHistoryId;
    private final long authoringId;
    private final int selectedFixtureId;

    private final float[] beforeVerts;
    private final int beforeCount;

    private final float[] afterVerts;
    private final int afterCount;

    private final FixtureDefData materialSource;

    private final PhysicsAuthoringBodySnapshot before;
    private PhysicsAuthoringBodySnapshot after;
    private boolean firstRedo = true;
    private final boolean afterAlreadyApplied;
    private final boolean noop;

    public MoveAuthoredPolygonVertexCommand(World world,
                                            HistoryIdRegistry historyIds,
                                            PhysicsSelectionService physicsSelectionService,
                                            int bodyEid,
                                            long authoringId,
                                            int selectedFixtureId,
                                            float[] beforeVerts,
                                            int beforeCount,
                                            float[] afterVerts,
                                            int afterCount,
                                            FixtureDefData materialSource,
                                            boolean afterAlreadyApplied) {
        this(world, historyIds, physicsSelectionService, bodyEid, authoringId,
                selectedFixtureId, beforeVerts, beforeCount, afterVerts, afterCount,
                materialSource, afterAlreadyApplied, null);
    }

    public MoveAuthoredPolygonVertexCommand(World world,
                                            HistoryIdRegistry historyIds,
                                            PhysicsSelectionService physicsSelectionService,
                                            int bodyEid,
                                            long authoringId,
                                            int selectedFixtureId,
                                            float[] beforeVerts,
                                            int beforeCount,
                                            float[] afterVerts,
                                            int afterCount,
                                            FixtureDefData materialSource,
                                            boolean afterAlreadyApplied,
                                            PhysicsAuthoringBodySnapshot beforeSnapshot) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsSelectionService = physicsSelectionService;

        this.bodyHistoryId = FixtureCommandSupport.toHistoryId(historyIds, bodyEid);
        this.authoringId = authoringId;
        this.selectedFixtureId = selectedFixtureId;

        this.beforeCount = Math.max(0, beforeCount);
        this.beforeVerts = copyVerts(beforeVerts, this.beforeCount);

        this.afterCount = Math.max(0, afterCount);
        this.afterVerts = copyVerts(afterVerts, this.afterCount);

        this.materialSource = materialSource != null ? materialSource.copy() : null;
        if (afterAlreadyApplied && beforeSnapshot == null) {
            throw new IllegalArgumentException(
                    "A pre-drag body snapshot is required when the vertex edit is already applied.");
        }
        this.before = beforeSnapshot != null
                ? beforeSnapshot
                : PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);
        this.afterAlreadyApplied = afterAlreadyApplied;
        this.after = afterAlreadyApplied
                ? PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid)
                : null;

        this.noop = world == null
                || historyIds == null
                || physicsSelectionService == null
                || bodyHistoryId <= 0L
                || authoringId <= 0L
                || this.beforeCount < 3
                || this.afterCount < 3
                || samePolygon(this.beforeVerts, this.beforeCount, this.afterVerts, this.afterCount);
    }

    @Override
    public String label() {
        return "Move Authored Polygon Vertex";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        if (noop) return;

        if (firstRedo) {
            firstRedo = false;
            if (afterAlreadyApplied) {
                if (after != null) {
                    after.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
                }
                return;
            }

            applyInitialChange();
            int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
            after = PhysicsAuthoringBodySnapshot.capture(world, physicsSelectionService, bodyEid);
            return;
        }

        if (after != null) {
            after.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
        }
    }

    @Override
    public void undo() {
        if (noop) return;
        before.restore(world, historyIds, physicsSelectionService, bodyHistoryId);
    }

    private void applyInitialChange() {
        int bodyEid = FixtureCommandSupport.resolveBodyEntityId(world, historyIds, bodyHistoryId);
        if (bodyEid < 0) return;

        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);

        AuthoredPolygonData applied = service.applyAuthoredPolygonReplacingFixture(
                bodyEid,
                authoringId,
                afterVerts,
                afterCount,
                materialSource,
                -1L
        );

        if (applied != null
                && applied.generatedFixtureIds != null
                && applied.generatedFixtureIds.length > 0) {
            physicsSelectionService.focusBody(bodyEid);

            int fixtureToSelect = selectedFixtureId > 0
                    && containsFixtureId(applied.generatedFixtureIds, selectedFixtureId)
                    ? selectedFixtureId
                    : applied.generatedFixtureIds[0];

            physicsSelectionService.setSelectedFixture(bodyEid, fixtureToSelect);
        }
    }

    private static boolean samePolygon(float[] a, int ac, float[] b, int bc) {
        if (ac != bc) return false;
        if (a == null || b == null) return false;
        if (a.length < ac * 2 || b.length < bc * 2) return false;

        for (int i = 0; i < ac * 2; i++) {
            if (Math.abs(a[i] - b[i]) > EPS) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsFixtureId(int[] ids, long fixtureId) {
        if (ids == null || fixtureId <= 0) return false;

        for (int id : ids) {
            if (id == fixtureId) return true;
        }

        return false;
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
