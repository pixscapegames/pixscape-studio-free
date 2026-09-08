package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolBarContractTest {

    @Test
    public void toolbarUsesAuthoritativeTiledEditingTargetAndEditorModeEvents() throws Exception {
        String source = readToolBarSource();
        String constructor = methodBody(source, "public ToolBar(StudioApplicationAdapter app)");
        String update = methodBody(source, "private void updateEditingContextState()");

        assertTrue(constructor.contains("EventFlow.i().subscribe(EventFlow.EditorModeChanged.class"));
        assertTrue(update.contains("selectionService.isTiledMapEditingTargetActive()"));
        assertTrue(update.contains("setAlignmentButtonsDisabled(tiledMapTarget)"));
        assertFalse(source.contains("LayerService"));
    }

    private static String readToolBarSource() throws Exception {
        return Files.readString(Path.of("src/main/java/games/pixscape/studio/ui/main/ToolBar.java"), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
