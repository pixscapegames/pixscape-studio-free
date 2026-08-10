package games.pixscape.studio.service.asset;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;

import java.util.Objects;
import java.util.function.IntFunction;

/** Applies the selected clip's first frame for immediate editor feedback. */
public final class StudioAnimationPreviewRefresher {
    private final DynamicEntityRenderState state;
    private final StudioAssetVisualResolver visualResolver;
    private IntFunction<AssetMeta> assetMetaLookup;
    private World world;

    public StudioAnimationPreviewRefresher(DynamicEntityRenderState state,
                                           StudioAssetVisualResolver visualResolver,
                                           IntFunction<AssetMeta> assetMetaLookup) {
        this.state = Objects.requireNonNull(state, "state");
        this.visualResolver = Objects.requireNonNull(visualResolver, "visualResolver");
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
    }

    public void bindWorld(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
    }

    public void refreshSelectedFrame(int entityId) {
        if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) return;
        ComponentMapper<AnimationComponent> mAnimation = world.getMapper(AnimationComponent.class);
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        AnimationComponent animation = mAnimation.getSafe(entityId, null);
        AssetRefComponent assetRef = mAssetRef.getSafe(entityId, null);
        if (animation == null || assetRef == null) return;

        AssetMeta rawMeta = assetMetaLookup.apply(assetRef.assetId);
        if (!(rawMeta instanceof AnimationAssetMeta meta) || meta.clips == null) return;
        AnimationClipMeta clip = meta.clips.get(animation.currentClip);
        if (clip == null) return;

        int frameIndex = animation.frame >= 0 ? animation.frame : clip.start;
        StudioAssetVisual visual = visualResolver.resolveFrame(
                assetRef.assetId, assetRef.atlasTag, frameIndex);
        if (visual == null) return;
        animation.frame = visual.frameIndex();
        applyFrame(world, entityId, clip, visual, state, assetRef.atlasTag);
    }

    public static void applyFrame(World world,
                                  int entityId,
                                  AnimationClipMeta clip,
                                  StudioAssetVisual visual,
                                  DynamicEntityRenderState state,
                                  String atlasTag) {
        ComponentMapper<TextureRegionComponent> mTextureRegion =
                world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMaterial =
                world.getMapper(RenderMaterialComponent.class);
        TextureRegionComponent textureRegion = mTextureRegion.getSafe(entityId, null);
        RenderMaterialComponent material = mMaterial.getSafe(entityId, null);
        if (textureRegion == null || material == null) return;

        float u1 = visual.u1();
        float v1 = visual.v1();
        float u2 = visual.u2();
        float v2 = visual.v2();
        if (clip.flipX) {
            float swap = u1;
            u1 = u2;
            u2 = swap;
        }

        textureRegion.u1 = u1;
        textureRegion.v1 = v1;
        textureRegion.u2 = u2;
        textureRegion.v2 = v2;
        textureRegion.pixW = visual.pixelWidth();
        textureRegion.pixH = visual.pixelHeight();
        textureRegion.valid = true;

        int textureHandle = visual.textureHandle();
        material.textureHandle = textureHandle;
        material.debugAtlasTag = atlasTag;
        applyFrameToDynamicState(world, state, entityId, material, textureHandle, u1, v1, u2, v2);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.mark(entityId, DirtyBits.MATERIAL);
    }

    private static void applyFrameToDynamicState(World world,
                                                 DynamicEntityRenderState state,
                                                 int entityId,
                                                 RenderMaterialComponent material,
                                                 int textureHandle,
                                                 float u1,
                                                 float v1,
                                                 float u2,
                                                 float v2) {
        if (state == null) return;
        int renderSlot = state.renderSlotForEntity(entityId);
        if (renderSlot == DynamicEntityRenderState.NO_SLOT) return;
        state.textureHandle[renderSlot] = textureHandle;
        state.u1[renderSlot] = u1;
        state.v1[renderSlot] = v1;
        state.u2[renderSlot] = u2;
        state.v2[renderSlot] = v2;

        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                .getSafe(entityId, null);
        int layerIndex = index != null ? index.getLayerIndex() : state.layerIndex[renderSlot];
        int z = index != null ? index.getZIndex() : state.z[renderSlot];
        state.sortKey[renderSlot] = SortKey64.packForBlend(
                material.getShaderIdx(), material.getBlendModeId(), textureHandle,
                layerIndex, z, state.runtimeOrder[renderSlot]);
    }
}
