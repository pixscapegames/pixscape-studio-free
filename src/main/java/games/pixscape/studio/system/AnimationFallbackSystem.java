package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.atlas.AtlasStudioService;

public final class AnimationFallbackSystem extends IteratingSystem implements ProfiledSystem {

    private final RenderStateSOA state;
    private final AtlasStudioService atlasStudioService;

    private ComponentMapper<AnimationComponent> mAnim;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<AssetRefComponent> mSrc;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;

    private DirtyTrackerSystem dirty;

    private final ObjectMap<String, Array<String>> framePathCache = new ObjectMap<>();
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public AnimationFallbackSystem(RenderStateSOA state, AtlasStudioService atlasStudioService) {
        super(Aspect.all(
                AnimationComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class,
                AssetRefComponent.class
        ));
        this.state = state;
        this.atlasStudioService = atlasStudioService;
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
        if (atlasStudioService.isPacked(src.assetId, src.atlasTag)) {
            return;
        }

        AnimationComponent a = mAnim.get(e);
        if (a == null || !a.playing || a.fps <= 0f) return;

        AnimationComponent.Clip clip = a.getClip();
        if (clip == null) return;

        Array<String> framePaths = resolveFramePaths(a.animation);
        if (framePaths == null || framePaths.size == 0) return;

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
        if (frameIndex < 0) frameIndex = 0;
        if (frameIndex >= framePaths.size) frameIndex = framePaths.size - 1;

        TextureRegionComponent tr = mTR.get(e);
        RenderMaterialComponent mat = mMat.get(e);
        boolean bindingValid = tr != null && tr.valid && mat != null && mat.textureHandle != 0;
        if (frameIndex == a.frame && bindingValid) return;

        Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(framePaths.get(frameIndex));
        if (tex == null) return;

        a.frame = frameIndex;
        applyFrame(world, e, clip, tex, state, src.atlasTag);
    }

    public static boolean bindFirstFrameFallback(World world, int e, RenderStateSOA state, String atlasTag) {
        if (world == null || e < 0 || !world.getEntityManager().isActive(e)) return false;

        ComponentMapper<AnimationComponent> mAnim = world.getMapper(AnimationComponent.class);
        AnimationComponent a = mAnim.getSafe(e, null);
        if (a == null) return false;

        AnimationComponent.Clip clip = a.getClip();
        if (clip == null) return false;

        Array<String> framePaths = resolveFramePathsStatic(a.animation);
        if (framePaths == null || framePaths.size == 0) return false;

        int frameIndex = Math.max(0, clip.start);
        if (frameIndex >= framePaths.size) frameIndex = framePaths.size - 1;

        Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(framePaths.get(frameIndex));
        if (tex == null) return false;

        a.frame = frameIndex;
        return applyFrame(world, e, clip, tex, state, atlasTag);
    }

    private Array<String> resolveFramePaths(String animationRelPath) {
        if (animationRelPath == null || animationRelPath.isBlank()) return null;

        Array<String> cached = framePathCache.get(animationRelPath);
        if (cached != null) return cached;

        Array<String> framePaths = resolveFramePathsStatic(animationRelPath);
        if (framePaths != null) {
            framePathCache.put(animationRelPath, framePaths);
        }
        return framePaths;
    }

    private static Array<String> resolveFramePathsStatic(String animationRelPath) {
        if (animationRelPath == null || animationRelPath.isBlank()) return null;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) return null;

        String folderRelPath = StudioFs.DIR_ORIG_ANIMATIONS + "/" + animationRelPath;
        FileHandle animationDir = StudioFs.requireStudioProjectDir(cfg).child(folderRelPath);
        if (!animationDir.exists() || !animationDir.isDirectory()) return null;

        Array<FileHandle> pngFrames = new Array<>();
        for (FileHandle child : animationDir.list()) {
            if (child == null || child.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(child.extension())) continue;
            pngFrames.add(child);
        }
        if (pngFrames.size == 0) return null;

        pngFrames.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        Array<String> framePaths = new Array<>(pngFrames.size);
        for (FileHandle png : pngFrames) {
            framePaths.add(folderRelPath + "/" + png.name());
        }

        return framePaths;
    }

    private static boolean applyFrame(World world,
                                      int e,
                                      AnimationComponent.Clip clip,
                                      Texture tex,
                                      RenderStateSOA state,
                                      String atlasTag) {
        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        TextureRegionComponent tr = mTR.getSafe(e, null);
        RenderMaterialComponent mat = mMat.getSafe(e, null);
        if (tr == null || mat == null) return false;

        float u1 = 0f;
        float v1 = 0f;
        float u2 = 1f;
        float v2 = 1f;

        if (clip != null && clip.flipX) {
            float tmp = u1;
            u1 = u2;
            u2 = tmp;
        }

        tr.u1 = u1;
        tr.v1 = v1;
        tr.u2 = u2;
        tr.v2 = v2;
        tr.pixW = tex.getWidth();
        tr.pixH = tex.getHeight();
        tr.valid = true;

        int textureHandle = TextureRegistry.handleOf(tex);
        mat.textureHandle = textureHandle;
        mat.debugAtlasTag = atlasTag;

        applyFrameToRenderState(world, state, e, mat, textureHandle, u1, v1, u2, v2);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.mark(e, DirtyBits.MATERIAL);
        }
        return true;
    }

    private static void applyFrameToRenderState(World world,
                                                RenderStateSOA state,
                                                int e,
                                                RenderMaterialComponent mat,
                                                int textureHandle,
                                                float u1,
                                                float v1,
                                                float u2,
                                                float v2) {
        if (state == null || e < 0 || e >= state.textureHandle.length) return;

        state.touch(e);
        state.textureHandle[e] = textureHandle;
        state.u1[e] = u1;
        state.v1[e] = v1;
        state.u2[e] = u2;
        state.v2[e] = v2;

        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);
        EntityIndexComponent index = mEntityIndex != null ? mEntityIndex.getSafe(e, null) : null;

        int layerIndex = index != null ? index.getLayerIndex() : state.layerIndex[e];
        int z = index != null ? index.getZIndex() : state.z[e];
        int runtimeOrder = state.runtimeOrder[e];

        state.sortKey[e] = SortKey64.packForBlend(
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
