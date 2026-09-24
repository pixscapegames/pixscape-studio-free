package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.service.runtimeavailability.SceneHudRoots;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SceneHudRootsPersistenceTest {

    @Test
    public void saveLoadPreservesAbsentDefaultHud() throws Exception {
        FileHandle projectFile = newProjectFile("scene-hud-roots-absent");
        ProjectConfig cfg = newProjectConfig();

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertNull(reloaded.getCurrentSceneMeta().defaultHudScreenId);
        assertTrue(SceneHudRoots.collect(reloaded.getCurrentSceneMeta()).isEmpty());
    }

    @Test
    public void saveLoadPreservesDirectHudAndItsCollectorResult() throws Exception {
        FileHandle projectFile = newProjectFile("scene-hud-roots-present");
        ProjectConfig cfg = newProjectConfig();
        cfg.getCurrentSceneMeta().defaultHudScreenId = "hud/status";
        List<String> before = SceneHudRoots.collect(cfg.getCurrentSceneMeta());

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertEquals("hud/status", reloaded.getCurrentSceneMeta().defaultHudScreenId);
        assertEquals(before, SceneHudRoots.collect(reloaded.getCurrentSceneMeta()));
    }

    @Test
    public void existingSceneJsonWithoutDefaultHudRemainsCompatible() throws Exception {
        Path directory = Files.createTempDirectory("scene-hud-roots-legacy");
        FileHandle projectFile = new FileHandle(directory.resolve("project.json").toFile());
        projectFile.writeString("""
                {
                  "projectKind": "pixscape-studio-project",
                  "projectTitle": "Legacy",
                  "projectFileName": "legacy",
                  "version": "1",
                  "exportRootPathDir": "build/export",
                  "glSamples": 0,
                  "currentSceneName": "Main",
                  "nextSceneIndex": 2,
                  "scenes": {
                    "Main": {
                      "sceneSchemaVersion": 3,
                      "name": "Main",
                      "file": "scene1.json",
                      "nextEntityStableId": 1,
                      "nextPhysicsShapeId": 1
                    }
                  }
                }
                """, false, "UTF-8");

        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertNull(reloaded.getCurrentSceneMeta().defaultHudScreenId);
        assertTrue(SceneHudRoots.collect(reloaded.getCurrentSceneMeta()).isEmpty());
    }

    private static FileHandle newProjectFile(String prefix) throws Exception {
        return new FileHandle(Files.createTempDirectory(prefix).resolve("project.json").toFile());
    }

    private static ProjectConfig newProjectConfig() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Scene HUD Roots";
        cfg.projectFileName = "scene-hud-roots";
        cfg.exportRootPathDir = "build/export";
        cfg.createSceneMeta("Main");
        return cfg;
    }
}
