package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class SceneServiceOpenContextTest {

    @Test
    public void loadOpenContext_validProject_returnsContext() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-context-valid");
        FileHandle projectFile = createProjectLayout(dir, validProjectJson("Main", "scene1.json"), "{}");

        SceneService.OpenProjectContext context = SceneService.loadOpenContextOrThrow(projectFile);

        assertEquals("Main", context.sceneName());
        assertEquals(projectFile.parent().path(), context.projectDir().path());
    }

    @Test(expected = RuntimeException.class)
    public void loadOpenContext_malformedProject_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-context-malformed");
        FileHandle projectFile = writeProject(dir, "{\"projectTitle\":\"broken\"");

        SceneService.loadOpenContextOrThrow(projectFile);
    }

    @Test(expected = RuntimeException.class)
    public void loadOpenContext_missingRequiredField_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-context-missing-field");
        String json = validProjectJson("Main", "scene1.json").replace("\"projectTitle\":\"Test Project\",", "");
        FileHandle projectFile = createProjectLayout(dir, json, "{}");

        SceneService.loadOpenContextOrThrow(projectFile);
    }

    @Test(expected = RuntimeException.class)
    public void loadOpenContext_blankRequiredField_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-context-blank-field");
        String json = validProjectJson("Main", "scene1.json").replace("\"projectTitle\":\"Test Project\"", "\"projectTitle\":\" \"");
        FileHandle projectFile = createProjectLayout(dir, json, "{}");

        SceneService.loadOpenContextOrThrow(projectFile);
    }

    @Test(expected = IllegalStateException.class)
    public void loadOpenContext_missingCurrentSceneFile_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-context-missing-scene");
        FileHandle projectFile = writeProject(dir, validProjectJson("Main", "missing-scene.json"));
        createAssetsMeta(dir);

        SceneService.loadOpenContextOrThrow(projectFile);
    }

    private static FileHandle createProjectLayout(Path dir, String projectJson, String sceneJson) throws Exception {
        FileHandle projectFile = writeProject(dir, projectJson);
        Files.createDirectories(dir.resolve("scenes"));
        Files.writeString(dir.resolve("scenes/scene1.json"), sceneJson, StandardCharsets.UTF_8);
        createAssetsMeta(dir);
        return projectFile;
    }

    private static void createAssetsMeta(Path dir) throws Exception {
        Files.writeString(dir.resolve("assets.json"), "{}", StandardCharsets.UTF_8);
    }

    private static FileHandle writeProject(Path dir, String json) throws Exception {
        Files.writeString(dir.resolve("project.json"), json, StandardCharsets.UTF_8);
        return new FileHandle(dir.resolve("project.json").toFile());
    }

    private static String validProjectJson(String currentSceneName, String currentSceneFile) {
        return "{" +
                "\"projectKind\":\"pixscape-studio-project\"," +
                "\"projectTitle\":\"Test Project\"," +
                "\"projectFileName\":\"test-project\"," +
                "\"version\":\"1\"," +
                "\"exportRootPathDir\":\"/tmp/export\"," +
                "\"glProfile\":\"GL30\"," +
                "\"glSamples\":0," +
                "\"currentSceneName\":\"" + currentSceneName + "\"," +
                "\"nextSceneIndex\":2," +
                "\"scenes\":{" +
                "\"Main\":{" +
                "\"sceneSchemaVersion\":3," +
                "\"name\":\"Main\"," +
                "\"file\":\"" + currentSceneFile + "\"," +
                "\"nextEntityStableId\":1," +
                "\"nextPhysicsShapeId\":1" +
                "}" +
                "}" +
                "}";
    }
}
