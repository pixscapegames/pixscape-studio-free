package games.pixscape.studio.ui.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Small, append-only evidence log for one Preview session.
 *
 * <p>The child writes its own file rather than relying on Studio's pipe drainer: when either
 * process is terminated, facts already reached by the child remain available for diagnosis.</p>
 */
final class PreviewDiagnosticLog {
    static final String CHILD_LOG_PROPERTY = "pixscape.preview.diagnosticLog";
    private static final String DIRECTORY = ".pixscape/preview-diagnostics";

    private PreviewDiagnosticLog() {
    }

    static Path createParentLog(Path userRoot, String sessionId) throws IOException {
        return create(userRoot, sessionId, "parent");
    }

    static Path createChildLog(Path userRoot, String sessionId) throws IOException {
        return create(userRoot, sessionId, "child");
    }

    static void append(Path file, String event, String detail) {
        if (file == null) return;
        String line = Instant.now() + " " + event
                + (detail == null || detail.isBlank() ? "" : " " + singleLine(detail))
                + System.lineSeparator();
        try {
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
            // Diagnostics must never make an otherwise usable Preview fail.
        }
    }

    static void child(String event, String detail) {
        String value = System.getProperty(CHILD_LOG_PROPERTY);
        if (value == null || value.isBlank()) return;
        append(Path.of(value), event, detail);
    }

    static String codeSource(Class<?> type) {
        if (type == null || type.getProtectionDomain() == null
                || type.getProtectionDomain().getCodeSource() == null) return "unavailable";
        return type.getProtectionDomain().getCodeSource().getLocation().toString();
    }

    static String javaRuntime() {
        return "javaHome=" + System.getProperty("java.home", "unknown")
                + " javaVersion=" + System.getProperty("java.version", "unknown")
                + " javaVendor=" + System.getProperty("java.vendor", "unknown");
    }

    private static Path create(Path userRoot, String sessionId, String role) throws IOException {
        if (userRoot == null) throw new IllegalArgumentException("userRoot is null");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is blank");
        Path directory = userRoot.resolve(DIRECTORY).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path file = directory.resolve("preview-" + sessionId + "-" + role + ".log");
        Files.createFile(file);
        return file;
    }

    private static String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }
}
