package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.io.StudioFs;

/** Scene output shares only its scene-specific HUD directory. */
public final class SceneHudEnvironmentPaths {
    public static final String HUD_DIRECTORY = "hud";
    public static final String ATLAS_FILE = "hud.atlas";

    private SceneHudEnvironmentPaths() {}

    public static String sceneTag(String tag) {
        if (tag == null || !tag.matches("[A-Za-z0-9][A-Za-z0-9_-]*")
                || tag.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))
            throw new IllegalArgumentException("Unsafe Scene HUD tag: " + tag);
        return tag;
    }

    public static FileHandle liveDirectory(FileHandle project, String tag) {
        return project.child(StudioFs.DIR_ATLASES).child(HUD_DIRECTORY).child(sceneTag(tag));
    }

    public static String atlasId(String tag) {
        return StudioFs.DIR_ATLASES + "/" + HUD_DIRECTORY
                + "/" + sceneTag(tag) + "/" + ATLAS_FILE;
    }
}
