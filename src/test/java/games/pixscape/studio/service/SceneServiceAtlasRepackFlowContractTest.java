package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServiceAtlasRepackFlowContractTest {

    @Test
    public void rebuildRenderRuntimeForScene_autoRepackEnabledLoadPathDoesNotPackAtlas() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "private void rebuildRenderRuntimeForScene(");

        assertTrue(methodBody.contains("SceneAtlasLoaderService.loadSceneAtlas(cfg, canonicalTag, projectDir, canvas);"));
        assertFalse(methodBody.contains("SceneAtlasLoaderService.packSceneAtlas("));
    }

    @Test
    public void saveCurrentSceneOnly_autoRepackEnabledStillCallsRepack() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "private void saveCurrentSceneOnly(ProjectConfig cfg)");

        assertTrue(methodBody.contains("if (EditorSettings.get().autoRepackAtlases)"));
        assertTrue(methodBody.contains("repackSceneAtlas(cfg, sceneName, projectDir);"));
    }

    @Test
    public void saveFlows_autoRepackDisabledDoesNotAutoRepackOutsideGuard() throws Exception {
        String source = readSceneServiceSource();
        String saveProjectBody = methodBody(source, "public void saveProjectAndCurrentScene()");
        String saveCurrentBody = methodBody(source, "private void saveCurrentSceneOnly(ProjectConfig cfg)");
        assertTrue(hasSingleRepackInsideAutoRepackGuard(saveCurrentBody, "repackSceneAtlas(cfg, sceneName, projectDir);"));
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

    private static boolean hasSingleRepackInsideAutoRepackGuard(String methodBody, String repackCall) {
        int ifIndex = methodBody.indexOf("if (EditorSettings.get().autoRepackAtlases)");
        if (ifIndex < 0) return false;

        String guardBody = methodBody(methodBody.substring(ifIndex), "if (EditorSettings.get().autoRepackAtlases)");

        int totalRepackCount = occurrences(methodBody, repackCall);
        int guardedRepackCount = occurrences(guardBody, repackCall);
        return totalRepackCount == 1 && guardedRepackCount == 1;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
