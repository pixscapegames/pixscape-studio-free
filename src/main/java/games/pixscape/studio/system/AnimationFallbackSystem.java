package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.systems.IteratingSystem;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;

public final class AnimationFallbackSystem extends IteratingSystem implements ProfiledSystem {

    private final DynamicEntityRenderState state;
    private final StudioAssetVisualResolver visualResolver;

    private ComponentMapper<AnimationComponent> mAnim;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<AssetRefComponent> mSrc;

    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public AnimationFallbackSystem(DynamicEntityRenderState state,
                                   StudioAssetVisualResolver visualResolver) {
        super(Aspect.all(
                AnimationComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class,
                AssetRefComponent.class
        ));
        this.state = state;
        this.visualResolver = visualResolver;
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.ANIMATION_FALLBACK);
        }
    }

    @Override
    protected void process(int e) {
        AssetRefComponent src = mSrc.get(e);
        StudioAssetVisual firstVisual =
                visualResolver.resolveFirst(src.assetId, src.atlasTag);
        if (firstVisual != null
                && firstVisual.source() == StudioAssetVisual.Source.ATLAS) {
            return;
        }

        AnimationComponent a = mAnim.get(e);
        if (a == null || !a.playing || a.fps <= 0f) return;

        AnimationComponent.Clip clip = a.getClip();
        if (clip == null) return;

        int start = Math.max(0, clip.start);
        int end = Math.max(0, clip.end);
        int dir = (end >= start) ? 1 : -1;
        int count = Math.abs(end - start) + 1;
        if (count <= 0) return;

        a.stateTime += world.getDelta();

        float frameDur = 1f / a.fps;
        int local = (int) (a.stateTime / frameDur);
        local = a.loop ? (local % count) : Math.min(local, count - 1);

        int frameIndex = start + local * dir;

        TextureRegionComponent tr = mTR.get(e);
        RenderMaterialComponent mat = mMat.get(e);
        boolean bindingValid = tr != null && tr.valid && mat != null && mat.textureHandle != 0;

        StudioAssetVisual visual =
                visualResolver.resolveFrame(src.assetId, src.atlasTag, frameIndex);
        if (visual == null
                || visual.source() != StudioAssetVisual.Source.STANDALONE) {
            return;
        }
        if (visual.frameIndex() == a.frame && bindingValid) return;

        a.frame = visual.frameIndex();
        applyFrame(world, e, clip, visual, state, src.atlasTag);
    }

    private static void applyFrame(World world,
                                   int e,
                                   AnimationComponent.Clip clip,
                                   StudioAssetVisual visual,
                                   DynamicEntityRenderState state,
                                   String atlasTag) {
        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        TextureRegionComponent tr = mTR.getSafe(e, null);
        RenderMaterialComponent mat = mMat.getSafe(e, null);
        if (tr == null || mat == null) return;

        float u1 = visual.u1();
        float v1 = visual.v1();
        float u2 = visual.u2();
        float v2 = visual.v2();

        if (clip != null && clip.flipX) {
            float tmp = u1;
            u1 = u2;
            u2 = tmp;
        }

        tr.u1 = u1;
        tr.v1 = v1;
        tr.u2 = u2;
        tr.v2 = v2;
        tr.pixW = visual.pixelWidth();
        tr.pixH = visual.pixelHeight();
        tr.valid = true;

        int textureHandle = visual.textureHandle();
        mat.textureHandle = textureHandle;
        mat.debugAtlasTag = atlasTag;

        applyFrameToDynamicState(world, state, e, mat, textureHandle, u1, v1, u2, v2);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.mark(e, DirtyBits.MATERIAL);
        }
    }

    private static void applyFrameToDynamicState(World world,
                                                 DynamicEntityRenderState state,
                                                 int e,
                                                 RenderMaterialComponent mat,
                                                 int textureHandle,
                                                 float u1,
                                                 float v1,
                                                 float u2,
                                                 float v2) {
        if (state == null || e < 0) return;

        int renderSlot = state.renderSlotForEntity(e);
        if (renderSlot == DynamicEntityRenderState.NO_SLOT) return;

        state.textureHandle[renderSlot] = textureHandle;
        state.u1[renderSlot] = u1;
        state.v1[renderSlot] = v1;
        state.u2[renderSlot] = u2;
        state.v2[renderSlot] = v2;

        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);
        EntityIndexComponent index = mEntityIndex != null ? mEntityIndex.getSafe(e, null) : null;

        int layerIndex = index != null ? index.getLayerIndex() : state.layerIndex[renderSlot];
        int z = index != null ? index.getZIndex() : state.z[renderSlot];
        int runtimeOrder = state.runtimeOrder[renderSlot];

        state.sortKey[renderSlot] = SortKey64.packForBlend(
                mat.getShaderIdx(),
                mat.getBlendModeId(),
                textureHandle,
                layerIndex,
                z,
            runtimeOrder
        );
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.ANIMATION_FALLBACK, profileStartNs);
            profiling = false;
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
