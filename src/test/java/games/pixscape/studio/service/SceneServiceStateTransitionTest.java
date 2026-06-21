package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneServiceStateTransitionTest {

    @Test
    public void loadOpenContext_currentSceneNotPresentInMetadata_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-missing-current-scene-meta");
        FileHandle projectFile = writeProject(dir, "{" +
                "\"projectKind\":\"pixscape-studio-project\"," +
                "\"projectTitle\":\"Test\"," +
                "\"projectFileName\":\"test\"," +
                "\"version\":\"1\"," +
                "\"exportRootPathDir\":\"/tmp/export\"," +
                "\"glProfile\":\"GL30\"," +
                "\"glSamples\":0," +
                "\"currentSceneName\":\"Ghost\"," +
                "\"nextSceneIndex\":2," +
                "\"scenes\":{\"Main\":{\"name\":\"Main\",\"file\":\"scene1.json\"}}" +
                "}");

        Files.createDirectories(dir.resolve("scenes"));
        Files.writeString(dir.resolve("scenes/scene1.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("assets.json"), "{}", StandardCharsets.UTF_8);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                SceneService.loadOpenContextOrThrow(projectFile));

        assertTrue(ex.getMessage().contains("is not declared in scenes map"));
    }

    @Test
    public void rollbackSceneSwitchConfigPointer_withInvalidPrevious_doesNotMutateCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");

        boolean restored = SceneService.rollbackSceneSwitchConfigPointer(cfg, " ");

        assertFalse(restored);
        assertEquals("Main", cfg.getCurrentSceneName());
    }

    private static FileHandle writeProject(Path dir, String json) throws Exception {
        Files.writeString(dir.resolve("project.json"), json, StandardCharsets.UTF_8);
        return new FileHandle(dir.resolve("project.json").toFile());
    }
}
