package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.TiledSparseStorageHelper;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.tiled.TiledMutationPlan;
import games.pixscape.studio.service.tiled.TiledMutationRejectedException;
import games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner;

/** One complete canonical tiled gesture; neither redo nor undo assumes prior map mutation. */
public final class TiledBrushCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SceneService sceneService;
    private final long layerHistoryId;
    private final TiledMutationPlan plan;
    private final TiledSpatialMutationPlanner planner;

    public TiledBrushCommand(World world, SceneService sceneService, HistoryIdRegistry historyIds,
                             long layerHistoryId, TiledMutationPlan plan,
                             TiledSpatialMutationPlanner planner) {
        this.world = world;
        this.sceneService = sceneService;
        this.historyIds = historyIds;
        this.layerHistoryId = layerHistoryId;
        this.plan = plan;
        this.planner = planner;
    }

    @Override public String label() { return "Brush Tiles"; }
    @Override public void redo() { apply(true); }
    @Override public void undo() { apply(false); }

    private void apply(boolean after) {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (entityId == -1) throw new IllegalStateException("Tiled brush layer no longer exists.");
        TiledLayerComponent comp = world.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
        if (comp == null || comp.data == null) {
            throw new IllegalStateException("Tiled brush layer has no map data.");
        }
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(entityId, null);
        TiledSpatialMutationPlanner.Result result =
                planner.validateAndCommit(entityId, comp.data, blocks, plan, after);
        if (!result.accepted()) throw new TiledMutationRejectedException(result.rejection());

        ProjectConfig cfg = ProjectConfig.getInstance();
        String sceneTag = cfg != null ? cfg.canonicalSceneTagCurrent() : null;
        IntSet uniqueAssetIds = sceneTag != null ? new IntSet() : null;
        TileAnimationLookup lookup = sceneService != null ? sceneService.getTileAnimationRegistry() : null;
        for (int i = 0; i < plan.size(); i++) {
            int gx = plan.gx(i);
            int gy = plan.gy(i);
            int assetId = plan.assetId(i, after);
            byte flags = plan.flags(i, after);
            TiledSparseStorageHelper.setTile(comp, gx, gy, assetId, flags);
            if (lookup != null) {
                int cx = gx / comp.data.chunkSize;
                int cy = gy / comp.data.chunkSize;
                TileChunk chunk = comp.data.getChunk(cx, cy);
                if (chunk != null) {
                    int lx = gx - cx * comp.data.chunkSize;
                    int ly = gy - cy * comp.data.chunkSize;
                    TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, lookup);
                }
            }
            if (uniqueAssetIds != null && assetId > 0) uniqueAssetIds.add(assetId);
        }
        if (sceneService != null) {
            sceneService.requestTiledFallbackValidation();
        }
        if (sceneTag != null && sceneService != null) {
            boolean atlasInputChanged = false;
            IntSet.IntSetIterator it = uniqueAssetIds.iterator();
            while (it.hasNext) {
                if (sceneService.ensureSceneAtlasInputHasAsset(sceneTag, it.next())) atlasInputChanged = true;
            }
            if (atlasInputChanged) sceneService.requestAsyncPack(sceneTag);
        }
    }
}
