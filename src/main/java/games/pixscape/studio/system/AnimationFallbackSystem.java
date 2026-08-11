package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.StudioAnimationPreviewRefresher;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;

import java.util.function.IntFunction;

public final class AnimationFallbackSystem extends IteratingSystem implements ProfiledSystem {

    private final DynamicEntityRenderState state;
    private final StudioAssetVisualResolver visualResolver;
    private IntFunction<AssetMeta> assetMetaLookup;

    private ComponentMapper<AnimationComponent> mAnim;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<AssetRefComponent> mSrc;

    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public AnimationFallbackSystem(DynamicEntityRenderState state,
                                   StudioAssetVisualResolver visualResolver,
                                   IntFunction<AssetMeta> assetMetaLookup) {
        super(Aspect.all(
                AnimationComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class,
                AssetRefComponent.class
        ));
        this.state = state;
        this.visualResolver = visualResolver;
        this.assetMetaLookup = assetMetaLookup;
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        if (assetMetaLookup != null) this.assetMetaLookup = assetMetaLookup;
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

        AssetMeta rawMeta = assetMetaLookup.apply(src.assetId);
        if (!(rawMeta instanceof AnimationAssetMeta animationMeta)) return;
        AnimationClipMeta clip = animationMeta.clips != null
                ? animationMeta.clips.get(a.currentClip)
                : null;
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
        StudioAnimationPreviewRefresher.applyFrame(
                world, e, clip, visual, state, src.atlasTag);
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
