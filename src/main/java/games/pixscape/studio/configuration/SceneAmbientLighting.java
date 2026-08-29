package games.pixscape.studio.configuration;

/** Keeps authored ambient controls authoritative over exported runtime multipliers. */
public final class SceneAmbientLighting {
    public static final float DEFAULT_RED = 0.20f;
    public static final float DEFAULT_GREEN = 0.20f;
    public static final float DEFAULT_BLUE = 0.35f;

    private SceneAmbientLighting() {
    }

    /** Migrates legacy multiplier-only data, applies authored defaults, then derives runtime data. */
    public static void applyDefaultsAndDerive(SceneMeta meta) {
        if (meta == null) return;

        meta.ambientMulR = validLegacyMultiplier(meta.ambientMulR);
        meta.ambientMulG = validLegacyMultiplier(meta.ambientMulG);
        meta.ambientMulB = validLegacyMultiplier(meta.ambientMulB);

        if (!isFinite(meta.ambientIntensity)) {
            boolean defaultMultipliers = approximatelyOne(meta.ambientMulR)
                    && approximatelyOne(meta.ambientMulG)
                    && approximatelyOne(meta.ambientMulB);
            if (defaultMultipliers) {
                meta.ambientIntensity = 0f;
                meta.ambientColorR = DEFAULT_RED;
                meta.ambientColorG = DEFAULT_GREEN;
                meta.ambientColorB = DEFAULT_BLUE;
            } else {
                meta.ambientIntensity = 1f;
                meta.ambientColorR = clamp01(meta.ambientMulR);
                meta.ambientColorG = clamp01(meta.ambientMulG);
                meta.ambientColorB = clamp01(meta.ambientMulB);
            }
        }

        meta.ambientIntensity = clamp01(fallback(meta.ambientIntensity, 0f));
        meta.ambientColorR = clamp01(fallback(meta.ambientColorR, DEFAULT_RED));
        meta.ambientColorG = clamp01(fallback(meta.ambientColorG, DEFAULT_GREEN));
        meta.ambientColorB = clamp01(fallback(meta.ambientColorB, DEFAULT_BLUE));
        deriveRuntimeMultipliers(meta);
    }

    /** Derives runtime multipliers from normalized authored color and intensity. */
    public static void deriveRuntimeMultipliers(SceneMeta meta) {
        if (meta == null) return;

        float intensity = clamp01(fallback(meta.ambientIntensity, 0f));
        float red = clamp01(fallback(meta.ambientColorR, DEFAULT_RED));
        float green = clamp01(fallback(meta.ambientColorG, DEFAULT_GREEN));
        float blue = clamp01(fallback(meta.ambientColorB, DEFAULT_BLUE));

        meta.ambientIntensity = intensity;
        meta.ambientColorR = red;
        meta.ambientColorG = green;
        meta.ambientColorB = blue;
        meta.ambientMulR = 1f + (red - 1f) * intensity;
        meta.ambientMulG = 1f + (green - 1f) * intensity;
        meta.ambientMulB = 1f + (blue - 1f) * intensity;
    }

    private static float validLegacyMultiplier(float value) {
        return isFinite(value) && value >= 0f ? value : 1f;
    }

    private static float fallback(float value, float fallback) {
        return isFinite(value) ? value : fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean approximatelyOne(float value) {
        return Math.abs(value - 1f) < 0.0001f;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
