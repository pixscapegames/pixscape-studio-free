package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.configuration.SceneMeta;

import java.util.List;

/**
 * Interprets Scene metadata as the ordered logical roots for Scene HUD preparation.
 *
 * <p>V1 has one direct presentation root. Later Scene-scoped runtime-availability roots
 * will join this collection without changing its downstream consumers.</p>
 */
public final class SceneHudRoots {
    private SceneHudRoots() {
    }

    /**
     * Returns the direct Scene HUD root first when present, otherwise an empty immutable list.
     * Invalid non-blank identifiers are rejected by the canonical Runtime identifier policy.
     */
    public static List<String> collect(SceneMeta sceneMeta) {
        if (sceneMeta == null) {
            return List.of();
        }

        String defaultHudScreenId = HudScreenAssetId.normalizeOptional(sceneMeta.defaultHudScreenId);
        return defaultHudScreenId == null ? List.of() : List.of(defaultHudScreenId);
    }
}
