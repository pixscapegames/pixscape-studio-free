package games.pixscape.studio.system;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParticleAtlasReadinessCacheTest {

    @Test
    public void positiveResultIsProbedOnceAcrossRepeatedLookups() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        for (int i = 0; i < 5_000; i++) {
            assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));
        }

        assertEquals(1, probe.calls);
        assertEquals(1, effectsRoot.childCalls);
        assertEquals(1, effectsRoot.effectFile.existsCalls);
    }

    @Test
    public void negativeResultIsProbedOnceAcrossRepeatedLookups() {
        CountingProbe probe = new CountingProbe(false);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        for (int i = 0; i < 5_000; i++) {
            assertFalse(cache.isReady("main", "fire.p", atlas, effectsRoot));
        }

        assertEquals(1, probe.calls);
        assertEquals(1, effectsRoot.childCalls);
        assertEquals(1, effectsRoot.effectFile.existsCalls);
    }

    @Test
    public void probeExceptionIsRememberedAsNotReady() {
        CountingProbe probe = new CountingProbe(false);
        probe.failure = new RuntimeException("broken effect");
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        assertFalse(cache.isReady("main", "fire.p", atlas, effectsRoot));
        assertFalse(cache.isReady("main", "fire.p", atlas, effectsRoot));

        assertEquals(1, probe.calls);
    }

    @Test
    public void distinctEffectPathsAreProbedIndependently() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));
        assertTrue(cache.isReady("main", "smoke.p", atlas, effectsRoot));
        assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));

        assertEquals(2, probe.calls);
        assertEquals(2, effectsRoot.childCalls);
    }

    @Test
    public void distinctAtlasTagsAreProbedIndependently() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));
        assertTrue(cache.isReady("secondary", "fire.p", atlas, effectsRoot));

        assertEquals(2, probe.calls);
    }

    @Test
    public void replacingAtlasInstanceUnderSameTagReprobes() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlasA = new TextureAtlas();
        TextureAtlas atlasB = new TextureAtlas();

        assertTrue(cache.isReady("main", "fire.p", atlasA, effectsRoot));
        probe.result = false;
        assertFalse(cache.isReady("main", "fire.p", atlasB, effectsRoot));
        assertFalse(cache.isReady("main", "fire.p", atlasB, effectsRoot));

        assertEquals(2, probe.calls);
    }

    @Test
    public void clearAllowsOneNewProbe() {
        CountingProbe probe = new CountingProbe(false);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        assertFalse(cache.isReady("main", "fire.p", atlas, effectsRoot));
        cache.clear();
        probe.result = true;
        assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));
        assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));

        assertEquals(2, probe.calls);
    }

    @Test
    public void absentEffectFileIsRememberedWithoutProbe() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(false);
        TextureAtlas atlas = new TextureAtlas();

        for (int i = 0; i < 5_000; i++) {
            assertFalse(cache.isReady("main", "missing.p", atlas, effectsRoot));
        }

        assertEquals(0, probe.calls);
        assertEquals(1, effectsRoot.childCalls);
        assertEquals(1, effectsRoot.effectFile.existsCalls);
    }

    @Test
    public void invalidInputsNeverProbeOrAccessFilesystem() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        assertFalse(cache.isReady(null, "fire.p", atlas, effectsRoot));
        assertFalse(cache.isReady("", "fire.p", atlas, effectsRoot));
        assertFalse(cache.isReady(" ", "fire.p", atlas, effectsRoot));
        assertFalse(cache.isReady("main", null, atlas, effectsRoot));
        assertFalse(cache.isReady("main", "", atlas, effectsRoot));
        assertFalse(cache.isReady("main", " ", atlas, effectsRoot));
        assertFalse(cache.isReady("main", "fire.p", null, effectsRoot));
        assertFalse(cache.isReady("main", "fire.p", atlas, null));

        assertEquals(0, probe.calls);
        assertEquals(0, effectsRoot.childCalls);
    }

    @Test
    public void sharedEffectAcrossEmittersAndFramesUsesOneProbe() {
        CountingProbe probe = new CountingProbe(true);
        ParticleAtlasReadinessCache cache = new ParticleAtlasReadinessCache(probe);
        CountingEffectsRoot effectsRoot = new CountingEffectsRoot(true);
        TextureAtlas atlas = new TextureAtlas();

        for (int frame = 0; frame < 10_000; frame++) {
            for (int emitter = 0; emitter < 100; emitter++) {
                assertTrue(cache.isReady("main", "fire.p", atlas, effectsRoot));
            }
        }

        assertEquals(1, probe.calls);
        assertEquals(1, effectsRoot.childCalls);
    }

    static final class CountingProbe implements ParticleAtlasReadinessCache.ParticleAtlasProbe {
        boolean result;
        RuntimeException failure;
        int calls;

        CountingProbe(boolean result) {
            this.result = result;
        }

        @Override
        public boolean isReady(FileHandle effectFile, TextureAtlas atlas) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    static final class CountingEffectsRoot extends FileHandle {
        final CountingEffectFile effectFile;
        int childCalls;

        CountingEffectsRoot(boolean effectExists) {
            super("effects");
            effectFile = new CountingEffectFile(effectExists);
        }

        @Override
        public FileHandle child(String name) {
            childCalls++;
            return effectFile;
        }

        @Override
        public boolean exists() {
            return true;
        }
    }

    static final class CountingEffectFile extends FileHandle {
        final boolean exists;
        int existsCalls;

        CountingEffectFile(boolean exists) {
            super("effects/effect.p");
            this.exists = exists;
        }

        @Override
        public boolean exists() {
            existsCalls++;
            return exists;
        }
    }
}
