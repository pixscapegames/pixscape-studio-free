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
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.service.atlas.AtlasStudioService;

public final class StudioParticleFallbackSystem extends BaseSystem implements ProfiledSystem {

    private static final String TAG = "StudioParticleFallbackSystem";

    private final VfxRenderState vfxState;
    private final OrthographicCamera camera;
    private final AtlasStudioService atlasStudioService;
    private final int defaultShaderIdx;

    private FileHandle effectsRoot;
    private FileHandle imagesRoot;

    private ComponentMapper<ParticleEmitterComponent> mEmitter;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<VisibilityComponent> mVis;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<ParticleOverridesComponent> mOverrides;

    private EntitySubscription subscription;

    private final IntMap<ParticleEffectPool.PooledEffect> effects = new IntMap<>();
    private final ObjectMap<String, ParticleEffectPool> pools = new ObjectMap<>();
    private final IntMap<String> entityPoolKeys = new IntMap<>();

    private final IntSet loggedFailures = new IntSet();

    private Texture lastTex;
    private int lastTexHandle;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public StudioParticleFallbackSystem(VfxRenderState vfxState,
                                        OrthographicCamera camera,
                                        AtlasStudioService atlasStudioService,
                                        FileHandle effectsRoot,
                                        FileHandle imagesRoot,
                                        int defaultShaderIdx) {
        this.vfxState = vfxState;
        this.camera = camera;
        this.atlasStudioService = atlasStudioService;
        this.effectsRoot = effectsRoot;
        this.imagesRoot = imagesRoot;
        this.defaultShaderIdx = defaultShaderIdx;
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
        if (vfxState == null) return;
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

                pushParticle(p, blendId, layerIndex, zIndex, ov);
            }
        }
    }

    private void pushParticle(Sprite sprite,
                              int blendId,
                              int layerIndex,
                              int zIndex,
                              ParticleOverridesComponent ov) {
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

            x1 = cx + (x1 - cx) * sizeMul;
            y1 = cy + (y1 - cy) * sizeMul;
            x2 = cx + (x2 - cx) * sizeMul;
            y2 = cy + (y2 - cy) * sizeMul;
            x3 = cx + (x3 - cx) * sizeMul;
            y3 = cy + (y3 - cy) * sizeMul;
            x4 = cx + (x4 - cx) * sizeMul;
            y4 = cy + (y4 - cy) * sizeMul;
        }

        float uMin = Math.min(Math.min(u1, u2), Math.min(u3, u4));
        float uMax = Math.max(Math.max(u1, u2), Math.max(u3, u4));
        float vMin = Math.min(Math.min(vv1, vv2), Math.min(vv3, vv4));
        float vMax = Math.max(Math.max(vv1, vv2), Math.max(vv3, vv4));

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

        float colorPacked = Color.toFloatBits(r, g, b, a);

        Texture tex = sprite.getTexture();
        int texHandle;

        if (tex == lastTex) {
            texHandle = lastTexHandle;
        } else {
            texHandle = TextureRegistry.handleOf(tex);
            lastTex = tex;
            lastTexHandle = texHandle;
        }

        int tie = vfxState.activeCount & SortKey64.MAX_TIE;

        long sortKey = SortKey64.packForBlend(
                defaultShaderIdx,
                blendId,
                texHandle,
                layerIndex,
                zIndex,
                tie
        );

        vfxState.addParticleQuad(
                texHandle,
                defaultShaderIdx,
                blendId,
                layerIndex,
                zIndex,
                0,
                0,
                sortKey,
                x1,
                y1,
                x2,
                y2,
                x3,
                y3,
                x4,
                y4,
                uMin,
                vMin,
                uMax,
                vMax,
                colorPacked,
                RenderRepeatFlags.NONE,
                -1
        );
    }

    private boolean isEffectVisible(ParticleEffect fx) {
        BoundingBox box = fx.getBoundingBox();
        if (!box.isValid()) return true;

        float halfWidth = camera.viewportWidth * 0.5f * camera.zoom;
        float halfHeight = camera.viewportHeight * 0.5f * camera.zoom;

        float minX = camera.position.x - halfWidth;
        float maxX = camera.position.x + halfWidth;
        float minY = camera.position.y - halfHeight;
        float maxY = camera.position.y + halfHeight;

        return box.max.x >= minX
                && box.min.x <= maxX
                && box.max.y >= minY
                && box.min.y <= maxY;
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
