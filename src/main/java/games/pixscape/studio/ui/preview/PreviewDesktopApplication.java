package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import games.pixscape.studio.PixscapeStudioApplication;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation.GL32;
import static games.pixscape.studio.PixscapeStudioApplication.STUDIO_TITLE;

/** Desktop Preview entry point. It intentionally owns a separate JVM and LibGDX event loop. */
public final class PreviewDesktopApplication {
    /** The separate Preview owns its uncapped loop; Studio keeps its own VSync. */
    static final boolean PREVIEW_VSYNC = false;

    private PreviewDesktopApplication() {
    }

    public static void main(String[] args) {
        Arguments options;
        try {
            options = Arguments.parse(args);
        } catch (RuntimeException invalid) {
            PreviewDiagnosticLog.child("ARGUMENTS_FAILED", invalid.toString());
            throw invalid;
        }
        PreviewDiagnosticLog.child("CHILD_BOOT",
                "pid=" + ProcessHandle.current().pid()
                        + " entryPoint=" + PreviewDesktopApplication.class.getName()
                        + " runtime=" + PreviewDiagnosticLog.codeSource(
                        games.pixscape.runtime.render.lighting.LightingRenderer.class)
                        + " " + PreviewDiagnosticLog.javaRuntime()
                        + " requestedVsync=" + PREVIEW_VSYNC + " requestedForegroundFps=0");
        PreviewProcessProtocol.requestExitOnCloseCommand(options.sessionId());
        exitWhenParentDies();
        new Lwjgl3Application(new PreviewWindow(new FileHandle(options.userRoot().toFile()),
                options.sceneName(), options.forwardLighting()), configuration(options));
    }

    static Lwjgl3ApplicationConfiguration configuration(Arguments options) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle(STUDIO_TITLE + " Preview");
        configuration.setWindowIcon(PixscapeStudioApplication.PIXSCAPE_ICON);
        configuration.setHdpiMode(HdpiMode.Logical);
        configuration.setWindowedMode(options.width(), options.height());
        configuration.setOpenGLEmulation(GL32, 3, 2);
        configuration.useVsync(PREVIEW_VSYNC);
        configuration.setForegroundFPS(0);
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 8, options.glSamples());
        return configuration;
    }

    private static void exitWhenParentDies() {
        ProcessHandle parent = ProcessHandle.current().parent().orElse(null);
        if (parent == null) return;
        Thread monitor = new Thread(() -> {
            while (parent.isAlive()) {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            while (com.badlogic.gdx.Gdx.app == null) {
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            com.badlogic.gdx.Gdx.app.postRunnable(com.badlogic.gdx.Gdx.app::exit);
        }, "pixscape-preview-parent-monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    record Arguments(Path userRoot, String sceneName, int width, int height, int glSamples,
                     Boolean forwardLighting, String sessionId) {
        static Arguments parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (int index = 0; index < args.length; index += 2) {
                if (index + 1 >= args.length || !args[index].startsWith("--")) {
                    throw new IllegalArgumentException("Preview arguments must be --name value pairs.");
                }
                values.put(args[index], args[index + 1]);
            }
            Path userRoot = Path.of(required(values, "--user-root")).toAbsolutePath().normalize();
            String scene = required(values, "--scene");
            return new Arguments(userRoot, scene, positive(values, "--width"), positive(values, "--height"),
                    nonNegative(values, "--gl-samples"), values.containsKey("--forward-lighting") ? Boolean.valueOf(required(values, "--forward-lighting")) : null,
                    required(values, "--session"));
        }

        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key + ".");
            return value;
        }

        private static int positive(Map<String, String> values, String key) {
            int value = integer(values, key);
            if (value <= 0) throw new IllegalArgumentException(key + " must be positive.");
            return value;
        }

        private static int nonNegative(Map<String, String> values, String key) {
            int value = integer(values, key);
            if (value < 0) throw new IllegalArgumentException(key + " must not be negative.");
            return value;
        }

        private static int integer(Map<String, String> values, String key) {
            try {
                return Integer.parseInt(required(values, key));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(key + " must be an integer.", invalid);
            }
        }
    }
}
