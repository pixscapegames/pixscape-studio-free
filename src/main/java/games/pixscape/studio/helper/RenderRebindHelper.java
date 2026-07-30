package games.pixscape.studio.helper;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.ui.main.WorldCanvas;

public final class RenderRebindHelper {

    private RenderRebindHelper() {
    }

    /**
     * Call whenever the atlas for scene `sceneTag`
     * may have changed or been reloaded.
     * <p>
     * - Marque le snapshot GPU dirty (rebuild au safe point)
     * - Rebuild des TextureRegion (UV, tailles)
     * - Rebuild textureHandle values in RenderMaterialComponent
     * - Marks all rendering dirty to force a full pass
     */
    public static void rebindAfterAtlasChange(WorldCanvas canvas, String sceneTag, AtlasStudioService atlasRuntimeService) {
        rebindAfterAtlasChange(canvas, sceneTag, atlasRuntimeService, "render-rebind-after-atlas-change");
    }

    public static void rebindAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            AtlasStudioService atlasRuntimeService,
            String snapshotDirtyReason
    ) {
        rebindEntitiesAfterAtlasChange(canvas, sceneTag, atlasRuntimeService, null, snapshotDirtyReason);
    }

    public static void rebindEntitiesAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            AtlasStudioService atlasRuntimeService,
            IntArray entityIds
    ) {
        rebindEntitiesAfterAtlasChange(canvas, sceneTag, atlasRuntimeService, entityIds, "render-rebind-after-atlas-change");
    }

    public static void rebindEntitiesAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            AtlasStudioService atlasRuntimeService,
            IntArray entityIds,
            String snapshotDirtyReason
    ) {

        World world = canvas.getEcsWorld();

        // 1) Rebuild UV + material handles (single pass)
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        var mSrc = world.getMapper(AssetRefComponent.class);
        var mTR = world.getMapper(TextureRegionComponent.class);
        var mMat = world.getMapper(RenderMaterialComponent.class);

        AssetMetaDatabase assetMetaDb = loadAssetMetaDatabaseIfAvailable();

        if (entityIds == null || entityIds.size == 0) {
            IntBag bag = world.getAspectSubscriptionManager()
                    .get(Aspect.all(AssetRefComponent.class, TextureRegionComponent.class, RenderMaterialComponent.class))
                    .getEntities();
            int[] data = bag.getData();
            for (int i = 0, n = bag.size(); i < n; i++) {
                rebindEntity(data[i], sceneTag, atlasRuntimeService, assetMetaDb, mSrc, mTR, mMat, dirty);
            }
        } else {
            for (int i = 0; i < entityIds.size; i++) {
                rebindEntity(entityIds.get(i), sceneTag, atlasRuntimeService, assetMetaDb, mSrc, mTR, mMat, dirty);
            }
        }

        // 2) snapshot rebind (safe point) via manager
        GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
        if (snapshotManager != null) {
            snapshotManager.markDirty(sceneTag, snapshotDirtyReason);
        }

        // 3) Force a full render rebuild pass
        SceneLoader.forceFullRenderDirty(world);
    }

    public static String rebindHistoryEntityRenderAssets(
            WorldCanvas canvas,
            String sceneTag,
            AtlasStudioService atlasRuntimeService,
            int entityId
    ) {
        if (canvas == null || atlasRuntimeService == null || entityId < 0) {
            return "skipped";
        }

        World world = canvas.getEcsWorld();
        if (world == null || !world.getEntityManager().isActive(entityId)) {
            return "skipped";
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        var mSrc = world.getMapper(AssetRefComponent.class);
        var mTR = world.getMapper(TextureRegionComponent.class);
        var mMat = world.getMapper(RenderMaterialComponent.class);

        AssetMetaDatabase assetMetaDb = loadAssetMetaDatabaseIfAvailable();
        String result = rebindEntity(entityId, sceneTag, atlasRuntimeService, assetMetaDb, mSrc, mTR, mMat, dirty);

        GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
        if (snapshotManager != null) {
            snapshotManager.markDirty(sceneTag, "history-entity-render-rebind");
        }
        SceneLoader.forceFullRenderDirty(world);
        return result;
    }

    static String rebindEntity(
            int e,
            String sceneTag,
            AtlasRuntimeService atlasRuntimeService,
            AssetMetaDatabase assetMetaDb,
            com.artemis.ComponentMapper<AssetRefComponent> mSrc,
            com.artemis.ComponentMapper<TextureRegionComponent> mTR,
            com.artemis.ComponentMapper<RenderMaterialComponent> mMat,
            DirtyTrackerSystem dirty
    ) {
        AssetRefComponent src = mSrc.getSafe(e, null);
        TextureRegionComponent tr = mTR.getSafe(e, null);
        RenderMaterialComponent mat = mMat.getSafe(e, null);
        if (src == null || tr == null || mat == null) return "skipped";

        tr.valid = false;

        String atlasTag = (src.atlasTag != null && !src.atlasTag.isEmpty()) ? src.atlasTag : sceneTag;
        AtlasRegionMetadata ar = src.assetId > 0
                ? atlasRuntimeService.resolveCached(src.assetId, atlasTag)
                : null;
        if (ar == null) {
            if (bindStandaloneFallback(src, tr, mat, assetMetaDb, atlasTag)) {
                if (dirty != null) dirty.material(e);
                return "standalone";
            }
            mat.textureHandle = 0;
            mat.debugAtlasTag = atlasTag;
            if (dirty != null) dirty.material(e);
            return "unbound";
        }

        tr.u1 = ar.u1();
        tr.v1 = ar.v1();
        tr.u2 = ar.u2();
        tr.v2 = ar.v2();
        tr.pixW = ar.pixelWidth();
        tr.pixH = ar.pixelHeight();
        tr.valid = true;

        mat.textureHandle = ar.textureHandle();
        mat.debugAtlasTag = atlasTag;
        if (dirty != null) dirty.material(e);
        return "atlas";
    }

    private static boolean bindStandaloneFallback(
            AssetRefComponent src,
            TextureRegionComponent tr,
            RenderMaterialComponent mat,
            AssetMetaDatabase assetMetaDb,
            String atlasTag
    ) {
        if (assetMetaDb == null || src.assetId <= 0) return false;

        AssetMeta meta = assetMetaDb.findById(src.assetId);
        if (meta == null || meta.sourceRelPath == null || meta.sourceRelPath.isBlank()) return false;
        if (!isStandaloneImageSource(meta.sourceRelPath)) return false;

        Texture tex;
        try {
            tex = StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath);
        } catch (RuntimeException ex) {
            return false;
        }

        if (tex == null) return false;

        mat.textureHandle = TextureRegistry.handleOf(tex);
        mat.debugAtlasTag = atlasTag;

        tr.u1 = 0f;
        tr.v1 = 0f;
        tr.u2 = 1f;
        tr.v2 = 1f;
        tr.pixW = tex.getWidth();
        tr.pixH = tex.getHeight();
        tr.valid = true;
        return true;
    }

    private static boolean isStandaloneImageSource(String sourceRelPath) {
        if (sourceRelPath == null) return false;

        String lower = sourceRelPath.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp");
    }

    private static AssetMetaDatabase loadAssetMetaDatabaseIfAvailable() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            return null;
        }
        return AssetMetaDatabase.load(StudioFs.requireAssetsFile(cfg));
    }

}
