package games.pixscape.studio.service;

import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class SceneServiceCreateNewSceneContractTest {

    @Test
    public void createNewScene_usesLoadScenePipelineAsSingleSourceOfTruth() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void createNewScene(");

        assertTrue(methodBody.contains("saveCurrentSceneOnly(cfg);"));
        assertTrue(methodBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertTrue(methodBody.contains("assertCurrentSceneMetadataIntegrity(cfg, sceneName, \"createNewScene\");"));
    }

    @Test
    public void assertCurrentSceneMetadataIntegrity_rejectsIncoherentCurrentScenePointers() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Main");

        SceneService.rollbackCreatedSceneMeta(cfg, "Main");

        try {
            SceneService.assertCurrentSceneMetadataIntegrity(cfg, "Main", "test");
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("current scene"));
        }
    }

    private static String readSceneServiceSource() throws Exception {
        return Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
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
