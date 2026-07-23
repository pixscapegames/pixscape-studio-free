package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.PackedTileValue;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.TiledBrushCommand;
import games.pixscape.studio.service.SceneService;

import java.util.function.Supplier;

/** Canvas-scoped lifecycle and publication boundary for tiled brush mutations. */
public final class TiledMutationController {
    public enum Status { ACCEPTED, REJECTED, NO_MUTATION, CANCELLED }

    public static final class Result {
        private static final Result ACCEPTED = new Result(Status.ACCEPTED, -1, null);
        private static final Result NO_MUTATION = new Result(Status.NO_MUTATION, -1, null);
        private static final Result CANCELLED = new Result(Status.CANCELLED, -1, null);

        private final Status status;
        private final int layerEntityId;
        private final TiledSpatialMutationRejection rejection;

        private Result(Status status, int layerEntityId, TiledSpatialMutationRejection rejection) {
            this.status = status;
            this.layerEntityId = layerEntityId;
            this.rejection = rejection;
        }

        public Status status() { return status; }
        public int layerEntityId() { return layerEntityId; }
        public TiledSpatialMutationRejection rejection() { return rejection; }
    }

    private final World world;
    private final HistoryManager historyManager;
    private final Supplier<SceneService> sceneServiceSupplier;
    private final TiledSpatialMutationPlanner spatialMutationPlanner;

    private TiledBrushSession activeStroke;
    private boolean hasLastStrokeCell;
    private int lastStrokeGX;
    private int lastStrokeGY;

    public TiledMutationController(World world,
                                   HistoryManager historyManager,
                                   Supplier<SceneService> sceneServiceSupplier) {
        this(world, historyManager, sceneServiceSupplier, new TiledSpatialMutationPlanner());
    }

    TiledMutationController(World world,
                            HistoryManager historyManager,
                            Supplier<SceneService> sceneServiceSupplier,
                            TiledSpatialMutationPlanner spatialMutationPlanner) {
        this.world = world;
        this.historyManager = historyManager;
        this.sceneServiceSupplier = sceneServiceSupplier;
        this.spatialMutationPlanner = spatialMutationPlanner;
    }

    public void beginStroke(int layerEntityId) {
        reset();
        activeStroke = new TiledBrushSession(layerEntityId);
    }

    public void updateStroke(TiledLayerComponent tiled,
                             int gx,
                             int gy,
                             int assetId,
                             byte transformFlags) {
        if (activeStroke == null || tiled == null || tiled.data == null || !tiled.data.isInside(gx, gy)) return;
        if (!hasLastStrokeCell) {
            activeStroke.apply(tiled, gx, gy, assetId, transformFlags);
        } else {
            stageLine(tiled, lastStrokeGX, lastStrokeGY, gx, gy, assetId, transformFlags, activeStroke);
        }
        lastStrokeGX = gx;
        lastStrokeGY = gy;
        hasLastStrokeCell = true;
    }

    public Result commitStroke() {
        if (activeStroke == null) return Result.NO_MUTATION;
        TiledBrushSession completed = activeStroke;
        clearTransientState();
        return publish(completed);
    }

    public Result commitRectangle(int layerEntityId,
                                  TiledLayerComponent tiled,
                                  int minGX,
                                  int minGY,
                                  int maxGX,
                                  int maxGY,
                                  int assetId,
                                  byte transformFlags) {
        reset();
        if (tiled == null || tiled.data == null) return Result.NO_MUTATION;
        TiledBrushSession session = new TiledBrushSession(layerEntityId);
        for (int gx = minGX; gx <= maxGX; gx++) {
            for (int gy = minGY; gy <= maxGY; gy++) {
                session.apply(tiled, gx, gy, assetId, transformFlags);
            }
        }
        return publish(session);
    }

    public Result commitFill(int layerEntityId,
                             TiledLayerComponent tiled,
                             int startGX,
                             int startGY,
                             int replacementAssetId,
                             byte replacementFlags) {
        reset();
        if (tiled == null || tiled.data == null || !tiled.data.isInside(startGX, startGY)) {
            return Result.NO_MUTATION;
        }
        int targetPacked = PackedTileValue.pack(
                tiled.data.getTile(startGX, startGY),
                tiled.data.getTileTransformFlags(startGX, startGY));
        if (targetPacked == PackedTileValue.pack(replacementAssetId, replacementFlags)) {
            return Result.NO_MUTATION;
        }

        TiledBrushSession session = new TiledBrushSession(layerEntityId);
        stageFill(tiled, startGX, startGY, targetPacked,
                replacementAssetId, replacementFlags, session);
        return publish(session);
    }

    public Result cancel() {
        if (activeStroke == null) return Result.NO_MUTATION;
        activeStroke.cancel();
        clearTransientState();
        return Result.CANCELLED;
    }

    public void reset() {
        if (activeStroke != null) activeStroke.cancel();
        clearTransientState();
    }

    public boolean isActive() { return activeStroke != null; }
    public int activeLayerEntityId() { return activeStroke != null ? activeStroke.getLayerEntityId() : -1; }
    public TiledBrushSession activePreviewSession() { return activeStroke; }

    private Result publish(TiledBrushSession session) {
        if (session == null) return Result.NO_MUTATION;
        TiledMutationPlan plan = session.toPlan();
        if (plan.isEmpty()) return Result.NO_MUTATION;

        int layerEntityId = plan.layerEntityId();
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class)
                .getSafe(layerEntityId, null);
        TiledSpatialMutationPlanner.Result preflight = spatialMutationPlanner.preflight(plan, blocks, true);
        if (!preflight.accepted()) return rejected(layerEntityId, preflight.rejection());

        long historyId = historyManager.historyIds().ensureForEntity(layerEntityId);
        SceneService sceneService = sceneServiceSupplier != null ? sceneServiceSupplier.get() : null;
        try {
            historyManager.execute(new TiledBrushCommand(
                    world, sceneService, historyManager.historyIds(), historyId,
                    plan, spatialMutationPlanner));
            return Result.ACCEPTED;
        } catch (TiledMutationRejectedException rejected) {
            return rejected(layerEntityId, rejected.rejection());
        }
    }

    private static Result rejected(int layerEntityId, TiledSpatialMutationRejection rejection) {
        return new Result(Status.REJECTED, layerEntityId, rejection);
    }

    private void clearTransientState() {
        activeStroke = null;
        hasLastStrokeCell = false;
    }

    private static void stageLine(TiledLayerComponent tiled,
                                  int x0,
                                  int y0,
                                  int x1,
                                  int y1,
                                  int assetId,
                                  byte transformFlags,
                                  TiledBrushSession session) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            if (tiled.data.isInside(x, y)) session.apply(tiled, x, y, assetId, transformFlags);
            if (x == x1 && y == y1) break;
            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static void stageFill(TiledLayerComponent tiled,
                                  int startGX,
                                  int startGY,
                                  int targetPacked,
                                  int replacementAssetId,
                                  byte replacementFlags,
                                  TiledBrushSession session) {
        int mapWidth = tiled.data.mapWidth;
        int mapHeight = tiled.data.mapHeight;
        boolean[] visited = new boolean[mapWidth * mapHeight];
        IntArray stack = new IntArray(false, Math.min(mapWidth * mapHeight, 4096));
        visited[startGY * mapWidth + startGX] = true;
        stack.add(pack(startGX, startGY));

        while (stack.size > 0) {
            int key = stack.pop();
            int gx = unpackX(key);
            int gy = unpackY(key);
            session.apply(tiled, gx, gy, replacementAssetId, replacementFlags);
            pushFillCell(gx + 1, gy, tiled, targetPacked, mapWidth, visited, stack);
            pushFillCell(gx - 1, gy, tiled, targetPacked, mapWidth, visited, stack);
            pushFillCell(gx, gy + 1, tiled, targetPacked, mapWidth, visited, stack);
            pushFillCell(gx, gy - 1, tiled, targetPacked, mapWidth, visited, stack);
        }
    }

    private static void pushFillCell(int gx,
                                     int gy,
                                     TiledLayerComponent tiled,
                                     int targetPacked,
                                     int mapWidth,
                                     boolean[] visited,
                                     IntArray stack) {
        if (!tiled.data.isInside(gx, gy)) return;
        int index = gy * mapWidth + gx;
        if (visited[index]) return;
        int packed = PackedTileValue.pack(
                tiled.data.getTile(gx, gy), tiled.data.getTileTransformFlags(gx, gy));
        if (packed != targetPacked) return;
        visited[index] = true;
        stack.add(pack(gx, gy));
    }

    private static int pack(int x, int y) { return (x << 16) ^ (y & 0xFFFF); }
    private static int unpackX(int packed) { return packed >> 16; }
    private static int unpackY(int packed) { return packed & 0xFFFF; }
}
