package games.pixscape.studio.system;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.particle.ParticleEffect;

final class ParticleAtlasReadinessCache {

    interface ParticleAtlasProbe {
        boolean isReady(FileHandle effectFile, TextureAtlas atlas);
    }

    private enum Readiness {
        READY,
        NOT_READY
    }

    private final ObjectMap<String, AtlasReadinessBucket> byAtlasTag = new ObjectMap<>();
    private final ParticleAtlasProbe probe;

    ParticleAtlasReadinessCache() {
        this(new LoadingParticleAtlasProbe());
    }

    ParticleAtlasReadinessCache(ParticleAtlasProbe probe) {
        if (probe == null) {
            throw new IllegalArgumentException("Particle atlas probe must not be null.");
        }
        this.probe = probe;
    }

    boolean isReady(String atlasTag,
                    String effectPath,
                    TextureAtlas atlas,
                    FileHandle effectsRoot) {
        if (isBlank(atlasTag)
                || isBlank(effectPath)
                || atlas == null
                || effectsRoot == null) {
            return false;
        }

        AtlasReadinessBucket bucket = byAtlasTag.get(atlasTag);
        if (bucket == null || bucket.atlasIdentity != atlas) {
            bucket = new AtlasReadinessBucket(atlas);
            byAtlasTag.put(atlasTag, bucket);
        }

        Readiness cached = bucket.byEffectPath.get(effectPath);
        if (cached != null) {
            return cached == Readiness.READY;
        }

        Readiness resolved = resolve(effectPath, atlas, effectsRoot);
        bucket.byEffectPath.put(effectPath, resolved);
        return resolved == Readiness.READY;
    }

    void clear() {
        byAtlasTag.clear();
    }

    private Readiness resolve(String effectPath,
                              TextureAtlas atlas,
                              FileHandle effectsRoot) {
        try {
            FileHandle effectFile = effectsRoot.child(effectPath);
            if (!effectFile.exists()) {
                return Readiness.NOT_READY;
            }
            return probe.isReady(effectFile, atlas)
                    ? Readiness.READY
                    : Readiness.NOT_READY;
        } catch (RuntimeException failure) {
            return Readiness.NOT_READY;
        }
    }

    private static boolean isBlank(String value) {
        if (value == null || value.length() == 0) {
            return true;
        }
        for (int i = 0, n = value.length(); i < n; i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static final class AtlasReadinessBucket {
        final TextureAtlas atlasIdentity;
        final ObjectMap<String, Readiness> byEffectPath = new ObjectMap<>();

        AtlasReadinessBucket(TextureAtlas atlasIdentity) {
            this.atlasIdentity = atlasIdentity;
        }
    }

    private static final class LoadingParticleAtlasProbe implements ParticleAtlasProbe {
        @Override
        public boolean isReady(FileHandle effectFile, TextureAtlas atlas) {
            ParticleEffect particleEffect = new ParticleEffect();
            try {
                particleEffect.load(effectFile, atlas);
                return true;
            } catch (RuntimeException failure) {
                return false;
            } finally {
                particleEffect.dispose();
            }
        }
    }
}
