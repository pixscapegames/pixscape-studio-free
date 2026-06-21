package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class SceneServiceSaveProgressContractTest {

    @Test
    public void saveProjectAndCurrentSceneWithProgress_declaresExpectedStepMessages() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");

        assertTrue(methodBody.contains("\"Preparing save...\""));
        assertTrue(methodBody.contains("\"Saving project file...\""));
        assertTrue(methodBody.contains("\"Repacking atlas...\""));
        assertTrue(methodBody.contains("\"Rebuilding tiled sparse data...\""));
        assertTrue(methodBody.contains("\"Saving scene...\""));
        assertTrue(methodBody.contains("\"Saving tiled animations...\""));
        assertTrue(methodBody.contains("\"Exporting runtime...\""));
        assertTrue(methodBody.contains("\"Finalizing...\""));
    }

    @Test
    public void saveProjectAndCurrentSceneWithProgress_usesRunnerAndSceneGuard() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");

        assertTrue(methodBody.contains("SaveProgressRunner runner = new SaveProgressRunner(uiStage);"));
        assertTrue(methodBody.contains("if (!plan.hasSceneToSave())"));
        assertTrue(methodBody.contains("runner.run(steps, onSuccess, onError);"));
    }

    private static String readSceneServiceSource() throws Exception {
        Path sceneServicePath = Path.of("src/main/java/games/pixscape/studio/service/SceneService.java");
        return Files.readString(sceneServicePath, StandardCharsets.UTF_8);
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
