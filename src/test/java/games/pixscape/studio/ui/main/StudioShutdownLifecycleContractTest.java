package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class StudioShutdownLifecycleContractTest {
    @Test
    public void shutdownStopsActivationAndUsesSilentDocumentTeardownBeforeServiceDisposal()
            throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java"),
                StandardCharsets.UTF_8);
        String dispose = methodBody(source, "public void dispose()");
        String activation = methodBody(source,
                "private void applyDocumentActivation(OpenEditorDocument previous, OpenEditorDocument current)");
        String modeProjection = methodBody(source,
                "static void projectDocumentEditingMode(StudioEditingModeService modeService");

        assertTrue(dispose.indexOf("disposing = true")
                < dispose.indexOf("editorDocumentManager.clearForTeardown()"));
        assertTrue(dispose.indexOf("editorDocumentManager.clearForTeardown()")
                < dispose.indexOf("hudEditorSession.dispose()"));
        assertTrue(activation.contains("if (disposing) return"));
        assertTrue(activation.contains("if (hudEditorSession != null) hudEditorSession.suspend()"));
        assertTrue(activation.contains("current == null && hudEditorSession != null"));
        assertTrue(activation.contains("projectDocumentEditingMode("));
        assertTrue(modeProjection.contains("deactivateDocument("));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(bodyStart + 1, i);
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
