package games.pixscape.studio.service.asset;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;

import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Repairs loaded entities whose active authored animation definition changed. */
public final class AnimationAssetEntityReconciler {
    private AnimationAssetEntityReconciler() {
    }

    public static int reconcile(World world,
                                int editedAssetId,
                                AnimationAssetMeta editedAsset,
                                IntConsumer previewRefresh,
                                IntConsumer changeNotification) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(editedAsset, "editedAsset");

        ComponentMapper<AnimationComponent> animations =
                world.getMapper(AnimationComponent.class);
        ComponentMapper<AssetRefComponent> assetRefs =
                world.getMapper(AssetRefComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(AnimationComponent.class, AssetRefComponent.class))
                .getEntities();
        int[] ids = entities.getData();
        int reconciled = 0;

        for (int i = 0; i < entities.size(); i++) {
            int entityId = ids[i];
            AssetRefComponent assetRef = assetRefs.get(entityId);
            if (assetRef.assetId != editedAssetId) continue;
            AnimationComponent animation = animations.get(entityId);
            if (reconcileEntity(world, entityId, animation, assetRef, editedAsset,
                    previewRefresh, changeNotification)) reconciled++;
        }
        return reconciled;
    }

    /** Re-establishes active-definition clip and fps invariants after scene activation. */
    public static int reconcileAll(World world,
                                   IntFunction<AssetMeta> assetMetaLookup,
                                   IntConsumer previewRefresh,
                                   IntConsumer changeNotification) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");

        ComponentMapper<AnimationComponent> animations =
                world.getMapper(AnimationComponent.class);
        ComponentMapper<AssetRefComponent> assetRefs =
                world.getMapper(AssetRefComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(AnimationComponent.class, AssetRefComponent.class))
                .getEntities();
        int[] ids = entities.getData();
        int reconciled = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entityId = ids[i];
            AssetRefComponent assetRef = assetRefs.get(entityId);
            AssetMeta rawMeta = assetMetaLookup.apply(assetRef.assetId);
            if (!(rawMeta instanceof AnimationAssetMeta animationMeta)) {
                throw new IllegalStateException(
                        "Active animation asset metadata is missing: " + assetRef.assetId);
            }
            if (reconcileEntity(world, entityId, animations.get(entityId), assetRef,
                    animationMeta, previewRefresh, changeNotification)) reconciled++;
        }
        return reconciled;
    }

    private static boolean reconcileEntity(World world,
                                           int entityId,
                                           AnimationComponent animation,
                                           AssetRefComponent assetRef,
                                           AnimationAssetMeta authored,
                                           IntConsumer previewRefresh,
                                           IntConsumer changeNotification) {
        if (animation.animationAssetIds == null
                || !animation.animationAssetIds.contains(assetRef.assetId)) {
            throw new IllegalStateException(
                    "Active animation asset is not owned by entity " + entityId + ": "
                            + assetRef.assetId);
        }

        boolean clipChanged = animation.currentClip == null
                || authored.clips == null
                || authored.clips.get(animation.currentClip) == null;
        boolean fpsChanged = Float.compare(animation.fps, authored.fps) != 0;
        if (!clipChanged && !fpsChanged) return false;

        if (clipChanged) {
            String replacement = StudioAnimationAssets.initialClip(authored);
            if (replacement == null) {
                throw new IllegalArgumentException(
                        "Animation asset has no valid authored clip: " + authored.id());
            }
            animation.currentClip = replacement;
            animation.stateTime = 0f;
            animation.frame = -1;
        }
        animation.fps = authored.fps;

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.mark(entityId, DirtyBits.MATERIAL);
        if (previewRefresh != null) previewRefresh.accept(entityId);
        if (changeNotification != null) changeNotification.accept(entityId);
        return true;
    }
}
