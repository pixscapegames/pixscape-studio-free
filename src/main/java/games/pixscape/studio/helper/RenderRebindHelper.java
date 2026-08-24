package games.pixscape.studio.helper;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
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
    public static void rebindAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver) {
        rebindAfterAtlasChange(
                canvas,
                sceneTag,
                visualResolver,
                "render-rebind-after-atlas-change"
        );
    }

    /** Rebinds after a prepared GPU snapshot has already been atomically published. */
    public static void rebindAfterPreparedSnapshot(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver
    ) {
        rebindEntitiesAfterAtlasChange(
                canvas,
                sceneTag,
                visualResolver,
                null,
                null
        );
    }

    public static void rebindAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver,
            String snapshotDirtyReason
    ) {
        rebindEntitiesAfterAtlasChange(
                canvas,
                sceneTag,
                visualResolver,
                null,
                snapshotDirtyReason
        );
    }

    public static void rebindEntitiesAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver,
            IntArray entityIds
    ) {
        rebindEntitiesAfterAtlasChange(
                canvas,
                sceneTag,
                visualResolver,
                entityIds,
                "render-rebind-after-atlas-change"
        );
    }

    public static void rebindEntitiesAfterAtlasChange(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver,
            IntArray entityIds,
            String snapshotDirtyReason
    ) {

        World world = canvas.getEcsWorld();

        // 1) Rebuild UV + material handles (single pass)
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        var mSrc = world.getMapper(AssetRefComponent.class);
        var mTR = world.getMapper(TextureRegionComponent.class);
        var mMat = world.getMapper(RenderMaterialComponent.class);
        GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
        boolean snapshotInvalidationRequired = false;
        boolean reboundAnyEntity = false;

        if (entityIds == null || entityIds.size == 0) {
            IntBag bag = world.getAspectSubscriptionManager()
                    .get(Aspect.all(AssetRefComponent.class, TextureRegionComponent.class, RenderMaterialComponent.class))
                    .getEntities();
            int[] data = bag.getData();
            for (int i = 0, n = bag.size(); i < n; i++) {
                String result = rebindEntity(
                        data[i],
                        sceneTag,
                        visualResolver,
                        mSrc,
                        mTR,
                        mMat,
                        dirty
                );
                reboundAnyEntity |= !"skipped".equals(result);
                snapshotInvalidationRequired |= requiresSnapshotInvalidation(
                        snapshotManager, sceneTag, result, mMat.getSafe(data[i], null));
            }
        } else {
            for (int i = 0; i < entityIds.size; i++) {
                int entityId = entityIds.get(i);
                String result = rebindEntity(
                        entityId,
                        sceneTag,
                        visualResolver,
                        mSrc,
                        mTR,
                        mMat,
                        dirty
                );
                snapshotInvalidationRequired |= requiresSnapshotInvalidation(
                        snapshotManager, sceneTag, result, mMat.getSafe(entityId, null));
                reboundAnyEntity |= !"skipped".equals(result);
            }
        }

        // 2) snapshot rebind (safe point) via manager
        if (snapshotManager != null && snapshotDirtyReason != null) {
            publishSnapshotInvalidationDecision(
                    snapshotManager,
                    sceneTag,
                    snapshotDirtyReason,
                    snapshotInvalidationRequired || !reboundAnyEntity
            );
        }

        // 3) Force a full render rebuild pass
        SceneLoader.forceFullRenderDirty(world);
    }

    public static String rebindHistoryEntityRenderAssets(
            WorldCanvas canvas,
            String sceneTag,
            StudioAssetVisualResolver visualResolver,
            int entityId
    ) {
        if (canvas == null || visualResolver == null || entityId < 0) {
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

        String result = rebindEntity(
                entityId,
                sceneTag,
                visualResolver,
                mSrc,
                mTR,
                mMat,
                dirty
        );

        if ("skipped".equals(result)) {
            return result;
        }

        GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
        if (snapshotManager != null) {
            RenderMaterialComponent material = mMat.getSafe(entityId, null);
            boolean invalidationRequired = requiresSnapshotInvalidation(
                    snapshotManager, sceneTag, result, material);
            publishSnapshotInvalidationDecision(
                    snapshotManager,
                    sceneTag,
                    "history-entity-render-rebind",
                    invalidationRequired
            );
        }
        SceneLoader.forceFullRenderDirty(world);
        return result;
    }

    private static boolean requiresSnapshotInvalidation(
            GpuSnapshotManager snapshotManager,
            String sceneTag,
            String rebindResult,
            RenderMaterialComponent material
    ) {
        if ("skipped".equals(rebindResult)) return false;
        int textureHandle = material != null ? material.textureHandle : 0;
        return snapshotManager == null
                || !snapshotManager.isHandlePublishedInCurrentBundle(sceneTag, textureHandle);
    }

    private static void publishSnapshotInvalidationDecision(
            GpuSnapshotManager snapshotManager,
            String sceneTag,
            String reason,
            boolean invalidationRequired
    ) {
        if (invalidationRequired) snapshotManager.markDirty(sceneTag, reason);
    }

    static String rebindEntity(
            int e,
            String sceneTag,
            StudioAssetVisualResolver visualResolver,
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
        StudioAssetVisual visual = visualResolver != null
                ? visualResolver.resolveFirst(src.assetId, atlasTag)
                : null;
        if (visual == null) {
            mat.textureHandle = 0;
            mat.debugAtlasTag = atlasTag;
            if (dirty != null) dirty.material(e);
            return "unbound";
        }

        tr.u1 = visual.u1();
        tr.v1 = visual.v1();
        tr.u2 = visual.u2();
        tr.v2 = visual.v2();
        tr.pixW = visual.pixelWidth();
        tr.pixH = visual.pixelHeight();
        tr.valid = true;

        mat.textureHandle = visual.textureHandle();
        mat.debugAtlasTag = atlasTag;
        if (dirty != null) dirty.material(e);
        return visual.source() == StudioAssetVisual.Source.ATLAS
                ? "atlas"
                : "standalone";
    }

}
