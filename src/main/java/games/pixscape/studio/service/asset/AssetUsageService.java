package games.pixscape.studio.service.asset;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;

public final class AssetUsageService {

    private AssetUsageService() {
    }

    public static AssetUsageReport scan(World world, int assetId) {

        AssetUsageReport report = new AssetUsageReport();

        if (world == null) return report;

        scanSprites(world, assetId, report);
        scanTiled(world, assetId, report);

        return report;
    }

    // ------------------------------------------------------------
    // Sprites
    // ------------------------------------------------------------

    private static void scanSprites(World world, int assetId, AssetUsageReport report) {

        ComponentMapper<AssetRefComponent> mAsset =
                world.getMapper(AssetRefComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AssetRefComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            AssetRefComponent comp = mAsset.get(e);
            if (comp != null && comp.assetId == assetId) {
                report.spriteRefs++;
            }
        }
    }

    // ------------------------------------------------------------
    // Tiled
    // ------------------------------------------------------------

    private static void scanTiled(World world, int assetId, AssetUsageReport report) {

        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();

        for (int i = 0; i < bag.size(); i++) {

            int e = data[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null || tiled.data == null) continue;

            // Sparse lists (studio side)
            if (tiled.tileAssetIds != null) {
                for (int j = 0; j < tiled.tileAssetIds.size; j++) {
                    if (tiled.tileAssetIds.get(j) == assetId) {
                        report.tiledRefs++;
                    }
                }
            }

            // Dense chunks (runtime safety)
            for (TileChunk chunk : tiled.data.getChunks()) {
                int[] ids = chunk.assetIds;
                for (int k = 0; k < ids.length; k++) {
                    if (ids[k] == assetId) {
                        report.tiledRefs++;
                    }
                }
            }
        }
    }
}