package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.service.atlas.AtlasStudioService;

public final class StudioParticleFallbackSystem extends BaseSystem implements ProfiledSystem {

    private static final String TAG = "StudioParticleFallbackSystem";

    private final RenderStateSOA state;
    private final OrthographicCamera camera;
    private final AtlasStudioService atlasStudioService;
    private final int defaultShaderIdx;
    private int frameFallbackCursor;

    private FileHandle effectsRoot;
    private FileHandle imagesRoot;

    private int vfxStartIndex = -1;
    private int vfxEndIndex = -1;

    private ComponentMapper<ParticleEmitterComponent> mEmitter;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<VisibilityComponent> mVis;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<ParticleOverridesComponent> mOverrides;

    private EntitySubscription subscription;

    private final IntMap<ParticleEffectPool.PooledEffect> effects = new IntMap<>();
    private final ObjectMap<String, ParticleEffectPool> pools = new ObjectMap<>();
    private final IntMap<String> entityPoolKeys = new IntMap<>();

    private final IntArray fallbackSlots = new IntArray();
    private final IntSet loggedFailures = new IntSet();

    private Texture lastTex;
    private int lastTexHandle;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public StudioParticleFallbackSystem(RenderStateSOA state,
                                        OrthographicCamera camera,
                                        AtlasStudioService atlasStudioService,
                                        FileHandle effectsRoot,
                                        FileHandle imagesRoot,
                                        int defaultShaderIdx) {
        this.state = state;
        this.camera = camera;
        this.atlasStudioService = atlasStudioService;
        this.effectsRoot = effectsRoot;
        this.imagesRoot = imagesRoot;
        this.defaultShaderIdx = defaultShaderIdx;
    }

    public void setVfxRange(int vfxStartIndex, int vfxEndIndex) {
        this.vfxStartIndex = vfxStartIndex;
        this.vfxEndIndex = vfxEndIndex;
    }

    public void setEffectsRoot(FileHandle effectsRoot) {
        this.effectsRoot = effectsRoot;
        invalidateAll();
    }

    public void setImagesRoot(FileHandle imagesRoot) {
        this.imagesRoot = imagesRoot;
        invalidateAll();
    }

    public void invalidateAll() {
        for (IntMap.Entries<ParticleEffectPool.PooledEffect> it = effects.entries(); it.hasNext(); ) {
            ParticleEffectPool.PooledEffect fx = it.next().value;
            if (fx != null) fx.free();
        }

        effects.clear();
        pools.clear();
        entityPoolKeys.clear();
        loggedFailures.clear();

        clearFallbackSlots();

        lastTex = null;
        lastTexHandle = 0;
    }

    @Override
    protected void initialize() {
        subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(ParticleEmitterComponent.class, TransformComponent.class)
        );

        subscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    removeEffect(data[i]);
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.STUDIO_PARTICLE_FALLBACK);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.STUDIO_PARTICLE_FALLBACK, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        clearFallbackSlots();
        frameFallbackCursor = vfxStartIndex;

        if (vfxStartIndex < 0 || vfxEndIndex <= vfxStartIndex) return;
        if (effectsRoot == null || imagesRoot == null) return;
        if (!effectsRoot.exists() || !imagesRoot.exists()) return;

        IntBag bag = subscription.getEntities();
        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];

            if (mVis != null && mVis.has(e) && !mVis.get(e).isVisible()) {
                removeEffect(e);
                continue;
            }

            ParticleEmitterComponent comp = mEmitter.getSafe(e, null);
            TransformComponent t = mTransform.getSafe(e, null);
            if (comp == null || t == null) {
                removeEffect(e);
                continue;
            }

            if (isReadyInAtlas(e, comp)) {
                removeEffect(e);
                continue;
            }

            ParticleEffectPool.PooledEffect fx = effects.get(e);
            String poolKey = poolKey(comp);

            if (fx == null || !poolKey.equals(entityPoolKeys.get(e))) {
                removeEffect(e);

                fx = createStandaloneEffect(e, comp);
                if (fx == null) continue;

                effects.put(e, fx);
                entityPoolKeys.put(e, poolKey);

                if (comp.autoStart) fx.start();
            }

            if (comp.localSpace) {
                fx.setPosition(t.x + t.originX, t.y + t.originY);
            }

            if (comp.restartRequested) {
                fx.reset(true, true);
                comp.restartRequested = false;
            }

            if (comp.playRequested) {
                fx.start();
                comp.playRequested = false;
            }

            if (comp.paused) continue;

            fx.update(world.getDelta());

            if (!isEffectVisible(fx)) continue;

            ParticleOverridesComponent ov =
                    mOverrides != null ? mOverrides.getSafe(e, null) : null;

            if (ov != null && !ov.enabled) continue;

            collectEffect(
                    fx,
                    resolveLayerIndex(e),
                    resolveZIndex(e),
                    ov
            );
        }
    }

    private boolean isReadyInAtlas(int entityId, ParticleEmitterComponent emitter) {
        if (atlasStudioService == null) return false;
        if (emitter.atlasTag == null || emitter.atlasTag.isBlank()) return false;
        if (emitter.effectPath == null || emitter.effectPath.isBlank()) return false;
        if (effectsRoot == null) return false;

        TextureAtlas atlas = atlasStudioService.getAtlas(emitter.atlasTag);
        if (atlas == null) return false;

        FileHandle effectFile = effectsRoot.child(emitter.effectPath);
        if (!effectFile.exists()) return false;

        ParticleEffect probe = new ParticleEffect();
        try {
            probe.load(effectFile, atlas);
            return true;
        } catch (RuntimeException ex) {
            return false;
        } finally {
            probe.dispose();
        }
    }

    private ParticleEffectPool.PooledEffect createStandaloneEffect(int entityId,
                                                                   ParticleEmitterComponent emitter) {
        if (emitter.effectPath == null || emitter.effectPath.isBlank()) return null;

        FileHandle effectFile = effectsRoot.child(emitter.effectPath);
        if (!effectFile.exists()) {
            logFailureOnce(entityId, "Effect file not found: " + effectFile.path(), null);
            return null;
        }

        String key = poolKey(emitter);
        ParticleEffectPool pool = pools.get(key);

        if (pool == null) {
            ParticleEffect template = new ParticleEffect();

            try {
                template.load(effectFile, imagesRoot);
                template.setEmittersCleanUpBlendFunction(false);
            } catch (RuntimeException ex) {
                template.dispose();
                logFailureOnce(entityId,
                        "Failed standalone particle fallback: "
                                + effectFile.path()
                                + " imagesRoot=" + imagesRoot.path(),
                        ex);
                return null;
            }

            pool = new ParticleEffectPool(template, 1, 16);
            pools.put(key, pool);
        }

        ParticleEffectPool.PooledEffect fx = pool.obtain();
        fx.setEmittersCleanUpBlendFunction(false);
        return fx;
    }

    private void collectEffect(ParticleEffect fx,
                               int layerIndex,
                               int zIndex,
                               ParticleOverridesComponent ov) {
        var emitters = fx.getEmitters();

        for (int ei = 0, en = emitters.size; ei < en; ei++) {
            ParticleEmitter emitter = emitters.get(ei);

            int blendId = emitter.isAdditive()
                    ? BlendMode.ADDITIVE_ALPHA.id
                    : BlendMode.ALPHA.id;

            ParticleEmitter.Particle[] particles = emitter.particles;
            boolean[] active = emitter.getActiveArray();
            if (particles == null || active == null) continue;

            int cap = emitter.getCapacity();
            for (int pi = 0; pi < cap; pi++) {
                if (!active[pi]) continue;

                ParticleEmitter.Particle p = particles[pi];
                if (p == null) continue;

                int slot = nextFreeVfxSlot();
                if (slot < 0) return;

                pushParticle(p, slot, blendId, layerIndex, zIndex, ov);
            }
        }
    }

    private int nextFreeVfxSlot() {
        for (int i = vfxStartIndex; i < vfxEndIndex; i++) {
            if (!state.enabled[i]) {
                fallbackSlots.add(i);
                return i;
            }
        }
        return -1;
    }

    private void pushParticle(Sprite sprite,
                              int slot,
                              int blendId,
                              int layerIndex,
                              int zIndex,
                              ParticleOverridesComponent ov) {
        state.touch(slot);

        state.kind[slot] = RenderStateSOA.KIND_SPRITE;
        state.enabled[slot] = true;
        state.visible[slot] = true;
        state.entityId[slot] = -1;

        float[] v = sprite.getVertices();

        float x1 = v[Batch.X1], y1 = v[Batch.Y1], u1 = v[Batch.U1], vv1 = v[Batch.V1];
        float x2 = v[Batch.X2], y2 = v[Batch.Y2], u2 = v[Batch.U2], vv2 = v[Batch.V2];
        float x3 = v[Batch.X3], y3 = v[Batch.Y3], u3 = v[Batch.U3], vv3 = v[Batch.V3];
        float x4 = v[Batch.X4], y4 = v[Batch.Y4], u4 = v[Batch.U4], vv4 = v[Batch.V4];

        float sizeMul = ov != null ? ov.sizeMul : 1f;
        float alphaMul = ov != null ? ov.alphaMul : 1f;
        int tintRgba = ov != null ? ov.tintRgba : -1;

        if (sizeMul != 1f) {
            float cx = (x1 + x2 + x3 + x4) * 0.25f;
            float cy = (y1 + y2 + y3 + y4) * 0.25f;

            state.x1[slot] = cx + (x1 - cx) * sizeMul;
            state.y1[slot] = cy + (y1 - cy) * sizeMul;
            state.x2[slot] = cx + (x2 - cx) * sizeMul;
            state.y2[slot] = cy + (y2 - cy) * sizeMul;
            state.x3[slot] = cx + (x3 - cx) * sizeMul;
            state.y3[slot] = cy + (y3 - cy) * sizeMul;
            state.x4[slot] = cx + (x4 - cx) * sizeMul;
            state.y4[slot] = cy + (y4 - cy) * sizeMul;
        } else {
            state.x1[slot] = x1;
            state.y1[slot] = y1;
            state.x2[slot] = x2;
            state.y2[slot] = y2;
            state.x3[slot] = x3;
            state.y3[slot] = y3;
            state.x4[slot] = x4;
            state.y4[slot] = y4;
        }

        state.u1[slot] = Math.min(Math.min(u1, u2), Math.min(u3, u4));
        state.u2[slot] = Math.max(Math.max(u1, u2), Math.max(u3, u4));
        state.v1[slot] = Math.min(Math.min(vv1, vv2), Math.min(vv3, vv4));
        state.v2[slot] = Math.max(Math.max(vv1, vv2), Math.max(vv3, vv4));

        Color col = sprite.getColor();
        float r = col.r;
        float g = col.g;
        float b = col.b;
        float a = col.a;

        if (tintRgba != -1) {
            r *= ((tintRgba >>> 24) & 0xff) / 255f;
            g *= ((tintRgba >>> 16) & 0xff) / 255f;
            b *= ((tintRgba >>> 8) & 0xff) / 255f;
            a *= (tintRgba & 0xff) / 255f;
        }

        a *= alphaMul;

        r = clamp01(r);
        g = clamp01(g);
        b = clamp01(b);
        a = clamp01(a);

        state.colorPacked[slot] = Color.toFloatBits(r, g, b, a);
        state.a[slot] = a;

        Texture tex = sprite.getTexture();
        int texHandle;

        if (tex == lastTex) {
            texHandle = lastTexHandle;
        } else {
            texHandle = TextureRegistry.handleOf(tex);
            lastTex = tex;
            lastTexHandle = texHandle;
        }

        state.textureHandle[slot] = texHandle;
        state.shader[slot] = defaultShaderIdx;
        state.blend[slot] = blendId;
        state.layerIndex[slot] = layerIndex;
        state.z[slot] = zIndex;

        int tie = (slot - vfxStartIndex) & SortKey64.MAX_TIE;
        state.runtimeOrder[slot] = tie;
        state.paramsId[slot] = 0;
        state.customParamsId[slot] = 0;

        state.sortKey[slot] = SortKey64.packForBlend(
                state.shader[slot],
                state.blend[slot],
                texHandle,
                layerIndex,
                zIndex,
                tie
        );
    }

    private boolean isEffectVisible(ParticleEffect fx) {
        BoundingBox box = fx.getBoundingBox();
        if (!box.isValid()) return true;

        float halfW = camera.viewportWidth * 0.5f * camera.zoom;
        float halfH = camera.viewportHeight * 0.5f * camera.zoom;

        float minX = camera.position.x - halfW;
        float maxX = camera.position.x + halfW;
        float minY = camera.position.y - halfH;
        float maxY = camera.position.y + halfH;

        return box.max.x >= minX
                && box.min.x <= maxX
                && box.max.y >= minY
                && box.min.y <= maxY;
    }

    private void clearFallbackSlots() {
        for (int i = 0; i < fallbackSlots.size; i++) {
            state.disable(fallbackSlots.get(i));
        }
        fallbackSlots.clear();
    }

    private void removeEffect(int entityId) {
        ParticleEffectPool.PooledEffect fx = effects.remove(entityId);
        if (fx != null) fx.free();

        entityPoolKeys.remove(entityId);
        loggedFailures.remove(entityId);
    }

    private int resolveLayerIndex(int entityId) {
        EntityIndexComponent index =
                mEntityIndex != null ? mEntityIndex.getSafe(entityId, null) : null;
        return index != null ? index.getLayerIndex() : 0;
    }

    private int resolveZIndex(int entityId) {
        EntityIndexComponent index =
                mEntityIndex != null ? mEntityIndex.getSafe(entityId, null) : null;
        return index != null ? index.getZIndex() : 0;
    }

    private void logFailureOnce(int entityId, String message, Throwable ex) {
        if (loggedFailures.contains(entityId)) return;

        if (ex != null) {
            Gdx.app.error(TAG, message, ex);
        } else {
            Gdx.app.error(TAG, message);
        }

        loggedFailures.add(entityId);
    }

    private static String poolKey(ParticleEmitterComponent emitter) {
        return emitter.effectPath != null ? emitter.effectPath : "";
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
