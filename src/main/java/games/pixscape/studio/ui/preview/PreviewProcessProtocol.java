package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;

import java.util.Objects;

/** Minimal line protocol between the Studio process and its one Desktop Preview child. */
final class PreviewProcessProtocol {
    static final String PREFIX = "PIXSCAPE_PREVIEW";
    static final String SESSION_PROPERTY = "pixscape.preview.session";

    private PreviewProcessProtocol() {
    }

    static void ready() {
        emit("READY", "");
    }

    static void loading() {
        emit("LOADING", "");
    }

    static void failed(Throwable failure) {
        String message = failure == null ? "Preview startup failed." : failure.getMessage();
        emit("ERROR", message == null || message.isBlank() ? "Preview startup failed." : message);
    }

    static boolean isReadyLine(String line, String sessionId) {
        return eventMatches(line, "READY", sessionId);
    }

    static boolean isLoadingLine(String line, String sessionId) {
        return eventMatches(line, "LOADING", sessionId);
    }

    static String errorMessage(String line, String sessionId) {
        String prefix = PREFIX + " ERROR " + sessionId + " ";
        return line != null && line.startsWith(prefix) ? line.substring(prefix.length()) : null;
    }

    static boolean isCloseCommand(String line, String sessionId) {
        return Objects.equals("CLOSE " + sessionId, line);
    }

    private static boolean eventMatches(String line, String event, String sessionId) {
        return Objects.equals(PREFIX + " " + event + " " + sessionId, line);
    }

    private static void emit(String event, String message) {
        String sessionId = System.getProperty(SESSION_PROPERTY, "");
        if (sessionId.isBlank()) return;
        String line = PREFIX + " " + event + " " + sessionId
                + (message == null || message.isBlank() ? "" : " " + singleLine(message));
        PreviewDiagnosticLog.child("PROTOCOL_" + event, line);
        System.out.println(line);
        System.out.flush();
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    static void requestExitOnCloseCommand(String sessionId) {
        Thread control = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isCloseCommand(line, sessionId)) {
                        requestApplicationExit();
                        return;
                    }
                }
            } catch (java.io.IOException ignored) {
                // Parent death is also monitored independently by the Preview entry point.
            }
        }, "pixscape-preview-control");
        control.setDaemon(true);
        control.start();
    }

    private static void requestApplicationExit() {
        while (Gdx.app == null) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Gdx.app.postRunnable(Gdx.app::exit);
    }
}
