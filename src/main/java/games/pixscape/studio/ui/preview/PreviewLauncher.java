package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.RuntimeExportPaths;
import games.pixscape.studio.ui.main.Resolution;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns the one Desktop Preview child process associated with this Studio instance. */
public final class PreviewLauncher {
    private static final Object LOCK = new Object();
    private static final int MAX_LOG_BYTES = 1_048_576;
    private static Session activeSession;

    private PreviewLauncher() {
    }

    public static boolean isOpen() {
        synchronized (LOCK) {
            return activeSession != null;
        }
    }

    public static void open(
            ProjectConfig cfg,
            Runnable onOpened,
            Runnable onClosed,
            Consumer<String> onFailure,
            Resolution resolution,
            boolean landscape,
            PreviewTarget target
    ) throws IOException {
        if (target == PreviewTarget.HTML) {
            HtmlPreviewLauncher.open(cfg, null, null);
            return;
        }

        FileHandle userRoot = RuntimeExportPaths.userRootFileHandle(cfg);
        if (userRoot == null) {
            throw new IllegalStateException("Desktop preview failed: export root is not configured.");
        }
        if (!userRoot.child(RuntimeExport.RUNTIME_DIR_NAME).child(RuntimeExport.PROJECT_JSON).exists()) {
            throw new IllegalStateException("Desktop preview failed: the runtime export is unavailable.");
        }
        String sceneName = cfg == null ? null : cfg.getCurrentSceneName();
        if (sceneName == null || sceneName.isBlank()) {
            throw new IllegalStateException("Desktop preview failed: no current scene is selected.");
        }
        if (resolution == null) throw new IllegalArgumentException("resolution is null");

        HtmlPreviewLauncher.stop();
        Session session = new Session(UUID.randomUUID().toString(), onOpened, onClosed, onFailure);
        synchronized (LOCK) {
            if (activeSession != null) {
                throw new IllegalStateException("A Desktop Preview is already " + activeSession.lifecycle.state().name().toLowerCase() + ".");
            }
            activeSession = session;
        }

        try {
            boolean forwardLighting = EditorSettings.get().useForwardLighting();
            Resolution windowResolution = landscape ? resolution
                    : new Resolution(resolution.height(), resolution.witdht());
            Gdx.app.log("PreviewLauncher", "Studio pid=" + ProcessHandle.current().pid()
                    + " renderer=" + (forwardLighting ? "ForwardLightingRenderer" : "LightingRenderer")
                    + " runtime=" + games.pixscape.runtime.render.lighting.LightingRenderer.class
                    .getProtectionDomain().getCodeSource().getLocation());
            Path userRootPath = userRoot.file().toPath().toAbsolutePath().normalize();
            session.logFile = PreviewDiagnosticLog.createParentLog(userRootPath, session.sessionId);
            session.childLogFile = PreviewDiagnosticLog.createChildLog(userRootPath, session.sessionId);
            PreviewDiagnosticLog.append(session.logFile, "STUDIO",
                    "pid=" + ProcessHandle.current().pid()
                            + " entryPoint=" + games.pixscape.studio.PixscapeStudioApplication.class.getName()
                            + " runtime=" + PreviewDiagnosticLog.codeSource(
                            games.pixscape.runtime.render.lighting.LightingRenderer.class)
                            + " " + PreviewDiagnosticLog.javaRuntime());
            ProcessBuilder builder = new ProcessBuilder(commandFor(session.sessionId, userRootPath,
                    sceneName, windowResolution, cfg.glSamples, forwardLighting, session.childLogFile));
            builder.directory(workingDirectory().toFile());
            builder.redirectErrorStream(true);
            session.process = builder.start();
            PreviewDiagnosticLog.append(session.logFile, "CHILD_STARTED",
                    "pid=" + session.process.pid()
                            + " entryPoint=" + PreviewDesktopApplication.class.getName()
                            + " childLog=" + session.childLogFile);
            Gdx.app.log("PreviewLauncher", "Started Desktop Preview session=" + session.sessionId
                    + " pid=" + session.process.pid() + " log=" + session.logFile
                    + " childLog=" + session.childLogFile);
            drainOutput(session);
            watchExit(session);
        } catch (IOException | RuntimeException failure) {
            PreviewDiagnosticLog.append(session.logFile, "LAUNCH_FAILED", failure.toString());
            clearIfCurrent(session);
            throw failure;
        }
    }

    /** Requests normal child shutdown and only targets the Preview process if it does not exit. */
    public static void close() {
        Session session;
        synchronized (LOCK) {
            session = activeSession;
            if (session == null || !session.lifecycle.closing()) return;
        }
        try (BufferedWriter control = new BufferedWriter(new OutputStreamWriter(
                session.process.getOutputStream(), StandardCharsets.UTF_8))) {
            control.append("CLOSE ").append(session.sessionId).append('\n').flush();
        } catch (IOException ignored) {
            // The exit watcher reports the child outcome and restores the Studio button state.
        }
        Thread watchdog = new Thread(() -> stopAfterGracePeriod(session), "pixscape-preview-stop-" + session.process.pid());
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static List<String> commandFor(String sessionId, Path userRoot, String sceneName, Resolution resolution,
                                   int glSamples, boolean forwardLighting, Path childLogFile) {
        ArrayList<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-D" + PreviewProcessProtocol.SESSION_PROPERTY + "=" + sessionId);
        command.add("-D" + PreviewDiagnosticLog.CHILD_LOG_PROPERTY + "="
                + childLogFile.toAbsolutePath().normalize());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(PreviewDesktopApplication.class.getName());
        command.add("--user-root");
        command.add(userRoot.toAbsolutePath().normalize().toString());
        command.add("--scene");
        command.add(sceneName);
        command.add("--width");
        command.add(Integer.toString(resolution.witdht()));
        command.add("--height");
        command.add(Integer.toString(resolution.height()));
        command.add("--gl-samples");
        command.add(Integer.toString(glSamples));
        command.add("--forward-lighting");
        command.add(Boolean.toString(forwardLighting));
        command.add("--session");
        command.add(sessionId);
        return List.copyOf(command);
    }

    static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private static Path workingDirectory() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static void drainOutput(Session session) {
        Thread drainer = new Thread(() -> {
            int written = 0;
            try (BufferedReader input = new BufferedReader(new java.io.InputStreamReader(
                    session.process.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter log = Files.newBufferedWriter(session.logFile, StandardCharsets.UTF_8,
                         StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                String line;
                while ((line = input.readLine()) != null) {
                    if (written < MAX_LOG_BYTES) {
                        String entry = line + System.lineSeparator();
                        int remaining = MAX_LOG_BYTES - written;
                        if (entry.length() <= remaining) {
                            log.write(entry);
                            written += entry.length();
                        } else {
                            log.write(entry, 0, remaining);
                            written = MAX_LOG_BYTES;
                        }
                        log.flush();
                    }
                    if (PreviewProcessProtocol.isLoadingLine(line, session.sessionId)) markLoading(session);
                    else if (PreviewProcessProtocol.isReadyLine(line, session.sessionId)) markReady(session);
                    else {
                        String error = PreviewProcessProtocol.errorMessage(line, session.sessionId);
                        if (error != null) markFailure(session, error);
                    }
                }
            } catch (IOException failure) {
                markFailure(session, "Preview log stream failed: " + failure.getMessage());
            }
        }, "pixscape-preview-log-" + session.process.pid());
        drainer.setDaemon(true);
        drainer.start();
    }

    private static void watchExit(Session session) {
        Thread watcher = new Thread(() -> {
            try {
                finish(session, session.process.waitFor());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "pixscape-preview-exit-" + session.process.pid());
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void stopAfterGracePeriod(Session session) {
        try {
            if (!session.process.waitFor(3, TimeUnit.SECONDS)) {
                session.process.destroy();
                if (!session.process.waitFor(2, TimeUnit.SECONDS)) session.process.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void markLoading(Session session) {
        synchronized (LOCK) {
            if (activeSession == session) session.lifecycle.loading();
        }
    }

    private static void markReady(Session session) {
        Runnable callback = null;
        synchronized (LOCK) {
            if (activeSession != session || session.readyNotified || !session.lifecycle.ready()) return;
            session.readyNotified = true;
            callback = session.onOpened;
        }
        PreviewDiagnosticLog.append(session.logFile, "READY",
                "pid=" + session.process.pid() + " childLog=" + session.childLogFile);
        dispatchOnlyWhileCurrent(session, callback);
    }

    private static void markFailure(Session session, String message) {
        Consumer<String> callback = null;
        synchronized (LOCK) {
            if (activeSession != session || session.failureNotified || !session.lifecycle.failed()) return;
            session.failureNotified = true;
            callback = session.onFailure;
        }
        String withLog = message + "\n\nPreview log: " + session.logFile;
        Consumer<String> finalCallback = callback;
        dispatchUnlessSuperseded(session, () -> {
            Gdx.app.error("PreviewLauncher", message);
            if (finalCallback != null) finalCallback.accept(withLog);
        });
    }

    private static void finish(Session session, int exitCode) {
        Runnable callback = null;
        String failure = null;
        synchronized (LOCK) {
            if (activeSession != session) return;
            if (!session.readyNotified && !session.failureNotified
                    && session.lifecycle.state() != PreviewSessionLifecycle.State.CLOSING) {
                failure = "Preview ended before it became ready (exit code " + exitCode + ").";
            }
            activeSession = null;
            callback = session.onClosed;
        }
        PreviewDiagnosticLog.append(session.logFile, "CHILD_EXIT", "code=" + exitCode);
        if (failure != null) markFailureAfterExit(session, failure);
        dispatchUnlessSuperseded(session, callback);
    }

    private static void markFailureAfterExit(Session session, String message) {
        Consumer<String> callback = session.onFailure;
        String withLog = message + "\n\nPreview log: " + session.logFile;
        dispatchUnlessSuperseded(session, () -> {
            Gdx.app.error("PreviewLauncher", message);
            if (callback != null) callback.accept(withLog);
        });
    }

    private static void clearIfCurrent(Session session) {
        synchronized (LOCK) {
            if (activeSession == session) activeSession = null;
        }
    }

    private static void dispatch(Runnable action) {
        if (action == null) return;
        if (Gdx.app == null) action.run();
        else Gdx.app.postRunnable(action);
    }

    private static void dispatchOnlyWhileCurrent(Session session, Runnable action) {
        dispatch(() -> {
            synchronized (LOCK) {
                if (activeSession != session) return;
            }
            action.run();
        });
    }

    private static void dispatchUnlessSuperseded(Session session, Runnable action) {
        dispatch(() -> {
            synchronized (LOCK) {
                if (activeSession != null && activeSession != session) return;
            }
            action.run();
        });
    }

    private static final class Session {
        final String sessionId;
        final Runnable onOpened;
        final Runnable onClosed;
        final Consumer<String> onFailure;
        volatile Process process;
        volatile Path logFile;
        volatile Path childLogFile;
        final PreviewSessionLifecycle lifecycle = new PreviewSessionLifecycle();
        boolean readyNotified;
        boolean failureNotified;

        Session(String sessionId, Runnable onOpened, Runnable onClosed, Consumer<String> onFailure) {
            this.sessionId = sessionId;
            this.onOpened = onOpened;
            this.onClosed = onClosed;
            this.onFailure = onFailure;
        }
    }
}
