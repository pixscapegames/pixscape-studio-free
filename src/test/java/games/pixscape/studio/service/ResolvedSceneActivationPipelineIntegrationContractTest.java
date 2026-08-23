package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ResolvedSceneActivationPipelineIntegrationContractTest {

    @Test
    public void coldOpenAndSceneSwitchShareTheSameResolvedActivationPipeline() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String openBody = methodBody(source, "private void openProjectStrict(");
        String switchBody = methodBody(source, "public void changeSceneNow(");
        String loadBody = methodBody(source, "void loadScene(");

        assertTrue(openBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertTrue(switchBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertTrue(loadBody.contains("sceneActivationPipeline.activate("));
    }

    @Test
    public void sceneSwitchNowPersistsSelectionAndLoadsWithoutSavingOrDirtyChecks() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String switchBody = methodBody(source, "public void changeSceneNow(");

        assertTrue(switchBody.contains("cfg.setCurrentSceneByName(sceneName);"));
        assertTrue(switchBody.contains("saveProjectFile(cfg);"));
        assertTrue(switchBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertFalse(switchBody.contains("saveCurrentSceneOnly("));
        assertFalse(switchBody.contains("historyManager.isDirty()"));
        assertFalse(switchBody.contains("requiresSaveBeforeLeavingCurrentScene()"));
    }

    @Test
    public void loadScenePreservesDestructiveResolutionActivationAndPublicationOrder() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String body = methodBody(source, "void loadScene(");

        assertOrdered(body,
                "clearWorldAndRenderState();",
                "SceneMeta meta = cfg.getSceneMeta(sceneName);",
                "String canonicalTag = cfg.canonicalSceneTagFor(meta);",
                "FileHandle sceneFile = scenesDir.child(meta.getFile());",
                "sceneActivationPipeline.activate(",
                "EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));"
        );
    }

    @Test
    public void tmxActivationUsesOnlyTheNormalLoadSceneClear() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String activationBody = methodBody(source, "private void activateImportedTmxScene(");
        String loadBody = methodBody(source, "void loadScene(");

        assertTrue(activationBody.contains("loadScene(cfg, result.sceneName(), projectDir);"));
        assertFalse(activationBody.contains("clearWorldAndRenderState();"));
        assertEquals(1, countOccurrences(loadBody, "clearWorldAndRenderState();"));
    }

    @Test
    public void tmxActivationCentersCameraAfterImportedSceneLoads() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String activationBody = methodBody(source, "private void activateImportedTmxScene(");

        assertOrdered(
                activationBody,
                "loadScene(cfg, result.sceneName(), projectDir);",
                "canvas.centerCamera();",
                "assertCurrentSceneMetadataIntegrity("
        );
    }

    @Test
    public void tiledAnimationStateReloadsBeforeTmxActivationAndRollbackRecovery() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String reloadBody = methodBody(source, "private void reloadTileAnimationsFromProject(");
        String activationBody = methodBody(source, "private void activateImportedTmxScene(");
        String restoreBody = methodBody(source, "private void restorePreviousSceneAfterTmxActivationFailure(");
        String openBody = methodBody(source, "private void openProjectStrict(");

        assertOrdered(reloadBody,
                "TileAnimationsIO.load(",
                "reloadTileAnimationRegistryFromProjectData();"
        );
        assertOrdered(activationBody,
                "reloadTileAnimationsFromProject(projectDir);",
                "loadScene(cfg, result.sceneName(), projectDir);"
        );
        assertOrdered(restoreBody,
                "cfg.setCurrentSceneByName(previousSceneName);",
                "reloadTileAnimationsFromProject(projectDir);",
                "loadScene(cfg, previousSceneName, projectDir);"
        );
        assertTrue(openBody.contains("reloadTileAnimationsFromProject(projectDir);"));
        assertFalse(openBody.contains("TileAnimationsIO.load("));
    }

    @Test
    public void activationCompilesLinkedPhysicsAfterTiledAndSpatialResolution()
            throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/"
                        + "ResolvedSceneActivationPipeline.java"),
                StandardCharsets.UTF_8
        );
        String body = methodBody(source, "void activate(");

        assertOrdered(body,
                "sceneLoader.load(",
                "world.process();",
                "SceneLoader.forceFullRenderDirty(world);",
                "resolveTiledLayersForActivation(",
                "validateAndCompileSpatialBlocksForActivation(",
                "PhysicsService.rebuildPreparedBodyCaches(",
                "target.meta().pixelsPerMeter",
                "renderRuntimeRebuilder.rebuild("
        );
    }

    private static void assertOrdered(String source, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int current = source.indexOf(fragment);
            assertTrue("Missing fragment: " + fragment, current >= 0);
            assertTrue("Out-of-order fragment: " + fragment, current > previous);
            previous = current;
        }
    }

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(fragment, from)) >= 0) {
            count++;
            from += fragment.length();
        }
        return count;
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
                if (depth == 0) return source.substring(bodyStart + 1, i);
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
