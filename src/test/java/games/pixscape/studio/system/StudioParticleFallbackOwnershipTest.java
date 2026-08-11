package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioParticleFallbackOwnershipTest {

    @Test
    public void invalidateAllFreesLiveEffectDisposesTemplateOnceAndIsIdempotent() throws Exception {
        StudioParticleFallbackSystem system = system();
        CountingEffect template = new CountingEffect();
        CountingPool pool = new CountingPool(template);
        install(system, "fire.p", template, pool, 1);

        system.invalidateAll();

        assertEquals(1, pool.freeCalls);
        assertEquals(1, template.disposeCalls);
        assertTrue(effects(system).isEmpty());
        assertTrue(pools(system).isEmpty());
        assertTrue(entityPoolKeys(system).isEmpty());

        system.invalidateAll();
        assertEquals(1, pool.freeCalls);
        assertEquals(1, template.disposeCalls);
    }

    @Test
    public void removingEntityFreesItsEffectWithoutDestroyingSharedPool() throws Exception {
        StudioParticleFallbackSystem system = system();
        CountingEffect template = new CountingEffect();
        CountingPool pool = new CountingPool(template);
        install(system, "shared.p", template, pool, 10, 11);

        removeEffect(system, 10);

        assertEquals(1, pool.freeCalls);
        assertEquals(0, template.disposeCalls);
        assertTrue(pools(system).containsKey("shared.p"));
        assertFalse(effects(system).containsKey(10));
        assertTrue(effects(system).containsKey(11));

        system.invalidateAll();
        assertEquals(2, pool.freeCalls);
        assertEquals(1, template.disposeCalls);
    }

    @Test
    public void repeatedFallbackCyclesDisposeEachTemplateExactlyOnce() throws Exception {
        StudioParticleFallbackSystem system = system();
        CountingEffect first = new CountingEffect();
        install(system, "cycle.p", first, new CountingPool(first), 1);

        system.invalidateAll();

        CountingEffect second = new CountingEffect();
        install(system, "cycle.p", second, new CountingPool(second), 2);
        system.invalidateAll();
        system.invalidateAll();

        assertEquals(1, first.disposeCalls);
        assertEquals(1, second.disposeCalls);
        assertTrue(pools(system).isEmpty());
    }

    @Test
    public void sourceRootChangesDisposeOldTemplatesThroughCommonInvalidation() throws Exception {
        StudioParticleFallbackSystem system = system();
        CountingEffect effectsRootTemplate = new CountingEffect();
        install(system, "effects-root.p", effectsRootTemplate, new CountingPool(effectsRootTemplate), 1);

        system.setEffectsRoot(null);

        CountingEffect imagesRootTemplate = new CountingEffect();
        install(system, "images-root.p", imagesRootTemplate, new CountingPool(imagesRootTemplate), 2);
        system.setImagesRoot(null);

        assertEquals(1, effectsRootTemplate.disposeCalls);
        assertEquals(1, imagesRootTemplate.disposeCalls);
        assertTrue(pools(system).isEmpty());
    }

    @Test
    public void worldDisposalDisposesCachedFallbackTemplate() throws Exception {
        StudioParticleFallbackSystem system = system();
        World world = new World(new WorldConfiguration().setSystem(system));
        CountingEffect template = new CountingEffect();
        install(system, "shutdown.p", template, new CountingPool(template), 1);

        world.dispose();

        assertEquals(1, template.disposeCalls);
        assertTrue(pools(system).isEmpty());
    }

    @Test
    public void fallbackTemplateDisposesEachTrackedTextureExactlyOnce() throws Exception {
        CountingFallbackTemplate template = new CountingFallbackTemplate();
        Texture first = allocate(Texture.class);
        Texture second = allocate(Texture.class);
        template.trackOwnedTexture(first);
        template.trackOwnedTexture(first);
        template.trackOwnedTexture(second);

        template.dispose();
        template.dispose();

        assertEquals(2, template.textureDisposeCalls);
    }

    private static StudioParticleFallbackSystem system() {
        return new StudioParticleFallbackSystem(
                new VfxRenderState(),
                null,
                null,
                null,
                null,
                0,
                new ParticleAtlasReadinessCache()
        );
    }

    private static void install(StudioParticleFallbackSystem system,
                                String key,
                                ParticleEffect template,
                                CountingPool pool,
                                int... entityIds) throws Exception {
        StudioParticleFallbackSystem.FallbackPoolEntry entry =
                new StudioParticleFallbackSystem.FallbackPoolEntry(template, pool);
        pools(system).put(key, entry);
        for (int entityId : entityIds) {
            effects(system).put(entityId, pool.obtain());
            entityPoolKeys(system).put(entityId, key);
        }
    }

    private static void removeEffect(StudioParticleFallbackSystem system, int entityId)
            throws Exception {
        Method method = StudioParticleFallbackSystem.class.getDeclaredMethod("removeEffect", int.class);
        method.setAccessible(true);
        method.invoke(system, entityId);
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

    @SuppressWarnings("unchecked")
    private static IntMap<String> entityPoolKeys(StudioParticleFallbackSystem system)
            throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("entityPoolKeys");
        field.setAccessible(true);
        return (IntMap<String>) field.get(system);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class CountingEffect extends ParticleEffect {
        int disposeCalls;

        @Override
        public void dispose() {
            disposeCalls++;
        }
    }

    private static final class CountingPool extends ParticleEffectPool {
        int freeCalls;

        CountingPool(ParticleEffect template) {
            super(template, 1, 16);
        }

        @Override
        public void free(PooledEffect effect) {
            freeCalls++;
            super.free(effect);
        }
    }

    private static final class CountingFallbackTemplate
            extends StudioParticleFallbackSystem.FallbackTemplate {
        int textureDisposeCalls;

        @Override
        void disposeOwnedTexture(Texture texture) {
            textureDisposeCalls++;
        }
    }
}
