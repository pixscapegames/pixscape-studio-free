package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.PackedTileValue;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.TiledSparseStorageHelper;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.SceneService;

public final class TiledBrushCommand implements Command {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SceneService sceneService;
    private final long layerHistoryId;

    private final IntIntMap previous;
    private final IntIntMap next;

    public TiledBrushCommand(World world,
                             SceneService sceneService,
                             HistoryIdRegistry historyIds,
                             long layerHistoryId,
                             IntIntMap previous,
                             IntIntMap next) {

        this.world = world;
        this.sceneService = sceneService;
        this.historyIds = historyIds;
        this.layerHistoryId = layerHistoryId;

        this.previous = new IntIntMap(previous);
        this.next = new IntIntMap(next);
    }

    @Override
    public String label() {
        return "Brush Tiles";
    }

    @Override
    public void redo() {
        apply(next);
    }

    @Override
    public void undo() {
        apply(previous);
    }

    private void apply(IntIntMap map) {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (entityId == -1) return;

        TiledLayerComponent comp =
                world.getMapper(TiledLayerComponent.class)
                        .getSafe(entityId, null);

        if (comp == null || comp.data == null) return;

        ProjectConfig cfg = ProjectConfig.getInstance();
        String sceneTag = cfg != null ? cfg.canonicalSceneTagCurrent() : null;

        IntSet uniqueAssetIds = sceneTag != null ? new IntSet() : null;
        TileAnimationLookup lookup = sceneService.getTileAnimationRegistry();

        IntIntMap.Entries entries = map.entries();
        while (entries.hasNext) {
            IntIntMap.Entry e = entries.next();

            int gx = unpackX(e.key);
            int gy = unpackY(e.key);

            int packed = e.value;
            int assetId = PackedTileValue.assetId(packed);
            byte flags = PackedTileValue.flags(packed);

            comp.data.setTile(gx, gy, assetId, flags);
            TiledSparseStorageHelper.setTile(comp, gx, gy, assetId, flags);

            if (lookup != null) {
                int cx = gx / comp.data.chunkSize;
                int cy = gy / comp.data.chunkSize;

                TileChunk chunk = comp.data.getChunk(cx, cy);
                if (chunk != null) {
                    int lx = gx - (cx * comp.data.chunkSize);
                    int ly = gy - (cy * comp.data.chunkSize);
                    TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, lookup);
                }
            }

            if (uniqueAssetIds != null && assetId > 0) {
                uniqueAssetIds.add(assetId);
            }
        }

        if (sceneTag != null) {
            boolean atlasInputChanged = false;

            IntSet.IntSetIterator it = uniqueAssetIds.iterator();
            while (it.hasNext) {
                int assetId = it.next();
                if (sceneService.ensureSceneAtlasInputHasAsset(sceneTag, assetId)) {
                    atlasInputChanged = true;
                }
            }

            if (atlasInputChanged) {
                sceneService.requestAsyncPack(sceneTag);
            }
        }
    }

    private static int unpackX(int key) {
        return key >> 16;
    }

    private static int unpackY(int key) {
        return key & 0xFFFF;
    }
}