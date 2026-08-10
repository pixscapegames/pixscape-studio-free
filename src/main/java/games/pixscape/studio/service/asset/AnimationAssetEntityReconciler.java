package games.pixscape.studio.service.asset;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.AnimationAssetMeta;

import java.util.Objects;
import java.util.function.IntConsumer;

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
            if (animation.currentClip != null
                    && editedAsset.clips.get(animation.currentClip) != null) {
                continue;
            }

            String replacement = StudioAnimationAssets.initialClip(editedAsset);
            if (replacement == null) {
                throw new IllegalArgumentException(
                        "Animation asset has no valid authored clip: " + editedAssetId);
            }
            animation.currentClip = replacement;
            animation.stateTime = 0f;
            animation.frame = -1;
            reconciled++;

            if (previewRefresh != null) previewRefresh.accept(entityId);
            if (changeNotification != null) changeNotification.accept(entityId);
        }
        return reconciled;
    }
}
