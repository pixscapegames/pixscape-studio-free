package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

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
    public void everySceneLifecycleBindsOrClearsBothIdentityAuthorities() throws Exception {
        String source = readSceneServiceSource();
        String helper = methodBody(source, "private void bindSceneIdentityAuthorities(");
        assertTrue(helper.contains("getIdentityRegistry().bind(canvas.getEcsWorld(), meta)"));
        assertTrue(helper.contains("getPhysicsService().setPhysicsShapeIdState(meta)"));

        assertOrdered(methodBody(source, "public void newProject("),
                "ProjectConfig.setInstance(cfg);", "bindSceneIdentityAuthorities(meta);",
                "getLayerService().addLayerTop");
        assertOrdered(methodBody(source, "public void createNewScene("),
                "clearWorldAndRenderState();", "bindSceneIdentityAuthorities(meta);",
                "getLayerService().addLayerTop");
        assertTrue(methodBody(source, "void loadScene(").contains("bindSceneIdentityAuthorities(meta);"));
        assertTrue(methodBody(source, "public void unloadProjectToEmptyEditor()")
                .contains("bindSceneIdentityAuthorities(null);"));
    }

    @Test
    public void switchingAndClearingAuthoritiesUsesOnlyTheActiveSceneHighWaters() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        PhysicsService physics = new PhysicsService(world, null);
        SceneMetaRuntime sceneA = new SceneMetaRuntime();
        sceneA.nextEntityStableId = 20;
        sceneA.nextPhysicsShapeId = 20;
        SceneMetaRuntime sceneB = new SceneMetaRuntime();

        identities.bind(world, sceneA);
        physics.setPhysicsShapeIdState(sceneA);
        assertEquals(20, identities.ensureStableId(world.create()));
        assertEquals(20, physics.allocateNewPhysicsShapeId());

        identities.bind(world, sceneB);
        physics.setPhysicsShapeIdState(sceneB);
        assertEquals(1, identities.ensureStableId(world.create()));
        assertEquals(1, physics.allocateNewPhysicsShapeId());
        assertEquals(21, sceneA.nextEntityStableId);
        assertEquals(21, sceneA.nextPhysicsShapeId);

        identities.bind(world, null);
        physics.setPhysicsShapeIdState(null);
        assertThrows(IllegalStateException.class, () -> identities.ensureStableId(world.create()));
        assertThrows(IllegalStateException.class, physics::allocateNewPhysicsShapeId);
        assertEquals(21, sceneA.nextEntityStableId);
        assertEquals(21, sceneA.nextPhysicsShapeId);
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

    private static void assertOrdered(String body, String first, String second, String third) {
        int firstIndex = body.indexOf(first);
        int secondIndex = body.indexOf(second);
        int thirdIndex = body.indexOf(third);
        assertTrue(firstIndex >= 0 && firstIndex < secondIndex && secondIndex < thirdIndex);
    }
}
