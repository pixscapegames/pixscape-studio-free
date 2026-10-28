package games.pixscape.studio.ui.preview;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PreviewDiagnosticLogTest {
    @Test
    public void createsIndependentDurableParentAndChildLogs() throws Exception {
        Path root = Files.createTempDirectory("pixscape-preview-log-test");
        Path parent = PreviewDiagnosticLog.createParentLog(root, "session-a");
        Path child = PreviewDiagnosticLog.createChildLog(root, "session-a");

        PreviewDiagnosticLog.append(parent, "CHILD_STARTED", "pid=42");
        PreviewDiagnosticLog.append(child, "PROTOCOL_READY", "PIXSCAPE_PREVIEW READY session-a");

        assertTrue(parent.toString().contains(".pixscape"));
        assertTrue(Files.readString(parent).contains("CHILD_STARTED pid=42"));
        assertTrue(Files.readString(child).contains("PROTOCOL_READY"));
    }
}
