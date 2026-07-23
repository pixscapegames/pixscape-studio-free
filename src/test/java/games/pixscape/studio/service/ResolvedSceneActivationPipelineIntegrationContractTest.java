package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ResolvedSceneActivationPipelineIntegrationContractTest {

    @Test
    public void coldOpenAndSceneSwitchShareTheSameResolvedActivationPipeline() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String openBody = methodBody(source, "private void openProjectStrict(");
        String switchBody = methodBody(source, "public void changeScene(");
        String loadBody = methodBody(source, "void loadScene(");

        assertTrue(openBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertTrue(switchBody.contains("loadScene(cfg, sceneName, projectDir);"));
        assertTrue(loadBody.contains("sceneActivationPipeline.activate("));
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
    public void studioWorldAcceptsNoCurrentSceneAndActivationBindsBeforeLoading() throws Exception {
        String canvasSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java"),
                StandardCharsets.UTF_8
        );
        String createWorldBody = methodBody(canvasSource, "private void createWorld(");
        assertTrue(createWorldBody.contains(
                "SceneMeta sceneMeta = cfg != null ? cfg.getCurrentSceneMeta() : null;"));
        assertTrue(createWorldBody.contains("WorldConfigFactory.buildWorld("));
        assertTrue(createWorldBody.contains("sceneMeta,"));

        String activationSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/ResolvedSceneActivationPipeline.java"),
                StandardCharsets.UTF_8
        );
        String activationBody = methodBody(activationSource, "void activate(");
        assertOrdered(activationBody,
                "FixtureIdAllocatorSystem fixtureIds = world.getSystem(FixtureIdAllocatorSystem.class);",
                "fixtureIds.bind(target.meta());",
                "sceneLoader.load(world, target.sceneFile(), false, target.meta());"
        );

        String sceneServiceSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );
        String clearBody = methodBody(sceneServiceSource, "private void clearWorldAndRenderState(");
        assertOrdered(clearBody,
                "fixtureIds.unbind();",
                "world.delete(data[i]);",
                "world.process();"
        );
        String newProjectBody = methodBody(sceneServiceSource, "public void newProject(");
        assertOrdered(newProjectBody,
                "ProjectConfig.setInstance(cfg);",
                "bindFixtureAllocator(meta);",
                "getLayerService().addLayerTop("
        );
        String newSceneBody = methodBody(sceneServiceSource, "public void createNewScene(");
        assertOrdered(newSceneBody,
                "clearWorldAndRenderState();",
                "bindFixtureAllocator(meta);",
                "getLayerService().addLayerTop("
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
