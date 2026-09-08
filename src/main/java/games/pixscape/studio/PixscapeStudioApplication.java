package games.pixscape.studio;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.ui.crash.CrashDialog;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import static com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation.GL32;

public class PixscapeStudioApplication {
    public static final int EDITOR_WIDTH = 1200;
    public static final int EDITOR_HEIGHT = 800;
    public static String STUDIO_TITLE = "Pixscape 2D Game Studio";
    public static String PIXSCAPE_ICON = "icons/pixscape_icon.png";

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            CrashDialog.show("Unhandled exception in thread: " + thread.getName(), throwable);
        });

        try {
            EditorSettings.load();
            createApplication();
        } catch (Throwable t) {
            CrashDialog.show("Pixscape Studio crashed", t);
        }
    }

    private static void createApplication() {
        new Lwjgl3Application(new StudioApplicationAdapter(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(STUDIO_TITLE);
        configuration.setWindowIcon(PIXSCAPE_ICON);
        configuration.setWindowedMode(EDITOR_WIDTH, EDITOR_HEIGHT);
        configuration.setHdpiMode(HdpiMode.Logical);
        configuration.setOpenGLEmulation(GL32, 3, 2);
        configuration.useVsync(true);

        configuration.setBackBufferConfig(
                8, 8, 8, 8,
                24, 8,
                EditorSettings.get().msaaSamples
        );

        return configuration;
    }
}
