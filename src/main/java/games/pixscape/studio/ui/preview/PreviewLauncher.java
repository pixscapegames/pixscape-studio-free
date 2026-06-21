package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.PixscapeStudioApplication;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.ui.main.Resolution;

import java.io.IOException;

import static com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation.GL32;
import static games.pixscape.studio.PixscapeStudioApplication.STUDIO_TITLE;

public final class PreviewLauncher {

    private PreviewLauncher() {
    }

    private static Lwjgl3Window previewWindow = null;
    private static Runnable onClosedCallback;
    private static Lwjgl3Window studioWindow;
    private static Resolution resolution;
    private static boolean landscape;

    static void notifyClosed() {
        if (previewWindow != null) {
            previewWindow = null;
            if (onClosedCallback != null) onClosedCallback.run();
        }
    }

    public static boolean isOpen() {
        return previewWindow != null;
    }

    public static void open(
            ProjectConfig cfg,
            Runnable onOpened,
            Runnable onClosed,
            Resolution res,
            boolean land,
            PreviewTarget target
    ) throws IOException {
        if (target == PreviewTarget.HTML) {
            HtmlPreviewLauncher.open(cfg, null, null);
            return;
        }

        HtmlPreviewLauncher.stop();

        Lwjgl3Graphics g = (Lwjgl3Graphics) Gdx.graphics;
        studioWindow = g.getWindow();
        resolution = res;
        landscape = land;

        Lwjgl3Application app = (Lwjgl3Application) Gdx.app;
        PreviewWindow game = new PreviewWindow(new FileHandle(cfg.exportRootPathDir));

        previewWindow = app.newWindow(game, getDefaultConfiguration(cfg));

        previewWindow.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                Runnable cb = onClosed;
                Lwjgl3Window sw = studioWindow;
                previewWindow = null;

                if (sw != null && cb != null) {
                    sw.postRunnable(cb);
                }
                return true;
            }
        });

        if (studioWindow != null && onOpened != null) {
            studioWindow.postRunnable(onOpened);
        }
    }

    public static void setStudioVSync(boolean enabled) {
        if (studioWindow == null) return;
        studioWindow.postRunnable(() -> Gdx.graphics.setVSync(enabled));
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration(ProjectConfig cfg) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(STUDIO_TITLE);
        configuration.setWindowIcon(PixscapeStudioApplication.PIXSCAPE_ICON);
        if (landscape) {
            configuration.setWindowedMode(resolution.witdht(), resolution.height());
        } else {
            configuration.setWindowedMode(resolution.height(), resolution.witdht());
        }

        configuration.setOpenGLEmulation(GL32, 3, 2);
        configuration.useVsync(true);

        configuration.setBackBufferConfig(
                8, 8, 8, 8,
                24, 8,
                cfg.glSamples
        );

        return configuration;
    }
}
