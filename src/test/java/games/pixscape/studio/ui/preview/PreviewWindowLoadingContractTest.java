package games.pixscape.studio.ui.preview;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PreviewWindowLoadingContractTest {

    @Test
    public void create_onlyCreatesLoadingPresentation() throws Exception {
        String create = methodBody(source(), "public void create()");
        assertTrue(create.contains("new PreviewLoadingUi()"));
        assertFalse(create.contains("loadProject("));
        assertFalse(create.contains("loadScene("));
        assertFalse(create.contains("beginLoadScene("));
    }

    @Test
    public void normalPreviewInitialization_onlyRunsFromReadyState() throws Exception {
        String startup = methodBody(source(), "private void renderStartup(float dt)");
        int ready = startup.indexOf("case READY:");
        int initialize = startup.indexOf("initializePreview();");
        assertTrue(ready >= 0);
        assertTrue(initialize > ready);
        assertTrue(startup.indexOf("engine.beginLoadScene(") < ready);
        assertTrue(startup.indexOf("sceneLoad.update()") < ready);
    }

    @Test
    public void sceneProgress_usesRemainingRangeAfterProjectMilestone() {
        assertEquals(0.15f, PreviewWindow.sceneProgress(0f), 0.0001f);
        assertEquals(0.575f, PreviewWindow.sceneProgress(0.5f), 0.0001f);
        assertEquals(1f, PreviewWindow.sceneProgress(1f), 0.0001f);
    }

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/preview/PreviewWindow.java"),
                StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(brace + 1, i);
        }
        throw new AssertionError("Method not found: " + signature);
    }
}
