package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StudioParticleFallbackLoopingTest {

    @Test
    public void initialRealizationOverridesSourceLoopingForEveryEmitter() throws Exception {
        Fixture fixture = new Fixture();
        try {
            int loopingEntity = fixture.createEntity(true);
            int nonLoopingEntity = fixture.createEntity(false);

            fixture.world.process();

            assertAllContinuous(fixture.effect(loopingEntity), true);
            assertAllContinuous(fixture.effect(nonLoopingEntity), false);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void restartReappliesAuthoredLoopingAndConsumesRequest() throws Exception {
        Fixture fixture = new Fixture();
        try {
            int entityId = fixture.createEntity(false);
            fixture.world.process();

            ParticleEffectPool.PooledEffect effect = fixture.effect(entityId);
            StudioParticleFallbackSystem.applyLooping(effect, true);

            ParticleEmitterComponent component = fixture.emitter(entityId);
            component.looping = false;
            component.restartRequested = true;
            fixture.world.process();

            assertAllContinuous(effect, false);
            assertFalse(component.restartRequested);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void playReappliesAuthoredLoopingAndConsumesRequest() throws Exception {
        Fixture fixture = new Fixture();
        try {
            int entityId = fixture.createEntity(false);
            fixture.world.process();

            ParticleEffectPool.PooledEffect effect = fixture.effect(entityId);
            StudioParticleFallbackSystem.applyLooping(effect, false);

            ParticleEmitterComponent component = fixture.emitter(entityId);
            component.looping = true;
            component.playRequested = true;
            fixture.world.process();

            assertAllContinuous(effect, true);
            assertFalse(component.playRequested);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void liveLoopingChangeWaitsForRuntimeMatchingPlaybackRequest() throws Exception {
        Fixture fixture = new Fixture();
        try {
            int entityId = fixture.createEntity(false);
            fixture.world.process();

            ParticleEffectPool.PooledEffect effect = fixture.effect(entityId);
            fixture.emitter(entityId).looping = true;
            fixture.world.process();

            assertAllContinuous(effect, false);
        } finally {
            fixture.dispose();
        }
    }

    private static void assertAllContinuous(ParticleEffect effect, boolean expected) {
        assertNotNull(effect);
        assertTrue(effect.getEmitters().size >= 2);
        for (int i = 0, n = effect.getEmitters().size; i < n; i++) {
            ParticleEmitter emitter = effect.getEmitters().get(i);
            if (expected) {
                assertTrue(emitter.isContinuous());
            } else {
                assertFalse(emitter.isContinuous());
            }
        }
    }

    private static ParticleEffect newTemplate() {
        ParticleEffect effect = new ParticleEffect();

        ParticleEmitter nonContinuous = new ParticleEmitter();
        nonContinuous.setContinuous(false);
        effect.getEmitters().add(nonContinuous);

        ParticleEmitter continuous = new ParticleEmitter();
        continuous.setContinuous(true);
        effect.getEmitters().add(continuous);

        return effect;
    }

    @SuppressWarnings("unchecked")
    private static ObjectMap<String, StudioParticleFallbackSystem.FallbackPoolEntry> pools(
            StudioParticleFallbackSystem system) throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("pools");
        field.setAccessible(true);
        return (ObjectMap<String, StudioParticleFallbackSystem.FallbackPoolEntry>) field.get(system);
    }

    @SuppressWarnings("unchecked")
    private static IntMap<ParticleEffectPool.PooledEffect> effects(
            StudioParticleFallbackSystem system) throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("effects");
        field.setAccessible(true);
        return (IntMap<ParticleEffectPool.PooledEffect>) field.get(system);
    }

    private static final class Fixture {
        private static final String EFFECT_PATH = "looping.p";

        final StudioParticleFallbackSystem system;
        final World world;

        Fixture() throws Exception {
            ParticleAtlasReadinessCacheTest.CountingEffectsRoot root =
                    new ParticleAtlasReadinessCacheTest.CountingEffectsRoot(true);
            system = new StudioParticleFallbackSystem(
                    new VfxRenderState(),
                    null,
                    null,
                    root,
                    root,
                    0,
                    new ParticleAtlasReadinessCache()
            );
            ParticleEffect template = newTemplate();
            pools(system).put(
                    EFFECT_PATH,
                    new StudioParticleFallbackSystem.FallbackPoolEntry(
                            template,
                            new ParticleEffectPool(template, 1, 16)
                    )
            );
            world = new World(new WorldConfiguration().setSystem(system));
        }

        int createEntity(boolean looping) {
            int entityId = world.create();
            ParticleEmitterComponent component = emitter(entityId);
            component.effectPath = EFFECT_PATH;
            component.looping = looping;
            component.autoStart = false;
            component.paused = true;
            world.getMapper(TransformComponent.class).create(entityId);
            return entityId;
        }

        ParticleEmitterComponent emitter(int entityId) {
            return world.getMapper(ParticleEmitterComponent.class).create(entityId);
        }

        ParticleEffectPool.PooledEffect effect(int entityId) throws Exception {
            return effects(system).get(entityId);
        }

        void dispose() {
            world.dispose();
        }
    }
}
