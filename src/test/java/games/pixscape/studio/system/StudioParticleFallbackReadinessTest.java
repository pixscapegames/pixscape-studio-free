package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class StudioParticleFallbackReadinessTest {

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
    public void atlasPublicationReleasesTrackedStandaloneBeforeNextFrame() throws Exception {
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
            world.process();

            assertFalse(trackedPoolKeys.containsKey(entityId));
            assertEquals(0, fixture.vfxState.activeCount);
            assertEquals(2, fixture.probe.calls);
        } finally {
            world.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    private static IntMap<String> entityPoolKeys(StudioParticleFallbackSystem system)
            throws Exception {
        Field field = StudioParticleFallbackSystem.class.getDeclaredField("entityPoolKeys");
        field.setAccessible(true);
        return (IntMap<String>) field.get(system);
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
}
