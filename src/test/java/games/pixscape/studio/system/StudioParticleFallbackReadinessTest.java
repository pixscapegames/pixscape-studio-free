package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.ParticleOverridesComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.StringWriter;

import static org.junit.Assert.*;

public class StudioParticleFallbackReadinessTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void fallbackPositionsAtTransformIgnoringOriginAndFollowsChanges() {
        CapturingParticleEffect effect = new CapturingParticleEffect();
        TransformComponent transform = new TransformComponent();
        transform.x = 7f;
        transform.y = 9f;
        transform.originX = 100f;
        transform.originY = 200f;

        StudioParticleFallbackSystem.positionEffect(effect, transform);
        assertEquals(7f, effect.x, 0f);
        assertEquals(9f, effect.y, 0f);

        transform.x = -2f;
        transform.y = 3f;
        StudioParticleFallbackSystem.positionEffect(effect, transform);
        assertEquals(-2f, effect.x, 0f);
        assertEquals(3f, effect.y, 0f);
    }

    @Test
    public void changingEffectsRootInvalidatesReadiness() {
        Fixture fixture = new Fixture(false);

        assertFalse(fixture.resolve());
        fixture.probe.result = true;
        fixture.system.setEffectsRoot(fixture.effectsRoot);
        assertTrue(fixture.resolve());
        assertTrue(fixture.resolve());

        assertEquals(2, fixture.probe.calls);
    }

    @Test
    public void changingImagesRootInvalidatesReadiness() {
        Fixture fixture = new Fixture(false);

        assertFalse(fixture.resolve());
        fixture.probe.result = true;
        fixture.system.setImagesRoot(fixture.effectsRoot);
        assertTrue(fixture.resolve());
        assertTrue(fixture.resolve());

        assertEquals(2, fixture.probe.calls);
    }

    @Test
    public void invalidateAllAllowsStandaloneToAtlasTransition() {
        Fixture fixture = new Fixture(false);

        assertFalse(fixture.resolve());
        fixture.probe.result = true;
        fixture.system.invalidateAll();
        assertTrue(fixture.resolve());
        assertTrue(fixture.resolve());

        assertEquals(2, fixture.probe.calls);
    }

    @Test
    public void worldDisposalClearsReadiness() {
        Fixture fixture = new Fixture(false);
        World world = new World(new WorldConfiguration().setSystem(fixture.system));

        assertFalse(fixture.resolve());
        world.dispose();
        fixture.probe.result = true;
        assertTrue(fixture.resolve());

        assertEquals(2, fixture.probe.calls);
    }

    @Test
    public void atlasPublicationInvalidationReleasesTrackedStandaloneBeforeNextFrame() throws Exception {
        Fixture fixture = new Fixture(false);
        World world = new World(new WorldConfiguration().setSystem(fixture.system));
        try {
            int entityId = world.create();
            ParticleEmitterComponent emitter =
                    world.getMapper(ParticleEmitterComponent.class).create(entityId);
            emitter.atlasTag = "main";
            emitter.effectPath = "fire.p";
            world.getMapper(TransformComponent.class).create(entityId);

            assertFalse(fixture.resolve());
            IntMap<String> trackedPoolKeys = entityPoolKeys(fixture.system);
            trackedPoolKeys.put(entityId, "fire.p");

            fixture.probe.result = true;
            fixture.atlasService.atlas = new TextureAtlas();
            fixture.system.invalidateAll();

            assertFalse(trackedPoolKeys.containsKey(entityId));
            assertEquals(0, fixture.vfxState.activeCount);
            assertEquals(1, fixture.probe.calls);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void atlasReadyButRuntimeUnpreparedKeepsFallbackUntilRuntimeIsPrepared()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("handoff-effects"));
        writeEffect(effectsRoot.child("fire.p"));
        MutableAtlasRuntimeService atlasService = new MutableAtlasRuntimeService();
        com.badlogic.gdx.graphics.OrthographicCamera camera =
                new com.badlogic.gdx.graphics.OrthographicCamera();
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(), camera, 0, atlasService, effectsRoot);
        ParticleAtlasReadinessCacheTest.CountingProbe probe =
                new ParticleAtlasReadinessCacheTest.CountingProbe(true);
        StudioParticleFallbackSystem fallbackSystem = new StudioParticleFallbackSystem(
                new VfxRenderState(), camera, atlasService, effectsRoot, effectsRoot, 0,
                new ParticleAtlasReadinessCache(probe));
        fallbackSystem.setRuntimeParticleSystem(runtimeSystem);
        World world = new World(new WorldConfiguration()
                .setSystem(runtimeSystem)
                .setSystem(fallbackSystem));
        try {
            int entityId = world.create();
            ParticleEmitterComponent emitter =
                    world.getMapper(ParticleEmitterComponent.class).create(entityId);
            emitter.atlasTag = "main";
            emitter.effectPath = "fire.p";
            world.getMapper(TransformComponent.class).create(entityId);

            world.process();

            ParticleEffectPool.PooledEffect standalone = effects(fallbackSystem).get(entityId);
            assertNotNull(standalone);
            assertFalse(runtimeSystem.isPrepared("main", "fire.p"));

            runtimeSystem.prepareRuntimeAvailability("main", new com.badlogic.gdx.utils.Array<>());
            world.process();

            assertTrue(runtimeSystem.isPrepared("main", "fire.p"));
            assertNull(effects(fallbackSystem).get(entityId));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void fallbackExtractionPreservesParticleBlendModes() throws Exception {
        Fixture fixture = new Fixture(false);

        assertExtractedBlend(fixture, false, false, BlendMode.ALPHA);
        assertExtractedBlend(fixture, true, false, BlendMode.ADDITIVE_ALPHA);
        assertExtractedBlend(fixture, false, true, BlendMode.PREMULT_ALPHA);
        assertExtractedBlend(fixture, true, true, BlendMode.PREMULT_ALPHA);
    }

    private static void assertExtractedBlend(Fixture fixture,
                                             boolean additive,
                                             boolean premultipliedAlpha,
                                             BlendMode expected) throws Exception {
        ParticleEmitter emitter = new ParticleEmitter();
        emitter.setAdditive(additive);
        emitter.setPremultipliedAlpha(premultipliedAlpha);
        emitter.setMaxParticleCount(1);
        emitter.particles[0] = new ParticleEmitter.Particle(new Sprite());
        emitter.getActiveArray()[0] = true;

        ParticleEffect effect = new ParticleEffect();
        effect.getEmitters().add(emitter);

        Method collectEffect = StudioParticleFallbackSystem.class.getDeclaredMethod(
                "collectEffect",
                ParticleEffect.class,
                int.class,
                int.class,
                ParticleOverridesComponent.class);
        collectEffect.setAccessible(true);

        fixture.vfxState.clearFrame();
        collectEffect.invoke(fixture.system, effect, 0, 0, null);

        assertEquals(1, fixture.vfxState.activeCount);
        assertEquals(expected.id, fixture.vfxState.blend[0]);
    }

    @SuppressWarnings("unchecked")
    private static IntMap<String> entityPoolKeys(StudioParticleFallbackSystem system)
            throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("entityPoolKeys");
        field.setAccessible(true);
        return (IntMap<String>) field.get(system);
    }

    @SuppressWarnings("unchecked")
    private static IntMap<ParticleEffectPool.PooledEffect> effects(
            StudioParticleFallbackSystem system) throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("effects");
        field.setAccessible(true);
        return (IntMap<ParticleEffectPool.PooledEffect>) field.get(system);
    }

    private static void writeEffect(com.badlogic.gdx.files.FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }

    private static final class Fixture {
        final VfxRenderState vfxState = new VfxRenderState();
        final MutableAtlasRuntimeService atlasService = new MutableAtlasRuntimeService();
        final ParticleAtlasReadinessCacheTest.CountingProbe probe;
        final ParticleAtlasReadinessCache cache;
        final ParticleAtlasReadinessCacheTest.CountingEffectsRoot effectsRoot =
                new ParticleAtlasReadinessCacheTest.CountingEffectsRoot(true);
        final StudioParticleFallbackSystem system;

        Fixture(boolean initialResult) {
            probe = new ParticleAtlasReadinessCacheTest.CountingProbe(initialResult);
            cache = new ParticleAtlasReadinessCache(probe);
            system = new StudioParticleFallbackSystem(
                    vfxState,
                    null,
                    atlasService,
                    effectsRoot,
                    effectsRoot,
                    0,
                    cache
            );
        }

        boolean resolve() {
            return cache.isReady("main", "fire.p", atlasService.atlas, effectsRoot);
        }
    }

    private static final class MutableAtlasRuntimeService extends AtlasRuntimeService {
        TextureAtlas atlas = new TextureAtlas();

        @Override
        public TextureAtlas getAtlas(String tag) {
            return atlas;
        }
    }

    private static final class CapturingParticleEffect extends ParticleEffect {
        float x;
        float y;

        @Override
        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
