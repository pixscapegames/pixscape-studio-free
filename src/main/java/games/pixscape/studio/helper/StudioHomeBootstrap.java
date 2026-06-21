package games.pixscape.studio.helper;

import games.pixscape.studio.configuration.EditorSettings;
public final class StudioHomeBootstrap {

    private StudioHomeBootstrap() {
    }

    public static void ensureExists() {
        // 1) load settings into memory
        EditorSettings.load();

        // 2) force writing the settings file + creating ~/.pixscape-studio
        EditorSettings.save();

        // 3) force creating ~/.pixscape-studio/internal + the white pixel
        InternalAssets.ensureWhitePixelPngExists();
    }
}
