package games.pixscape.studio.configuration;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.helper.InternalAssets;
import games.pixscape.studio.io.StudioFs;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ProjectDirectoryAndWhitePixelContractTest {
    private static HeadlessApplication app;

    @BeforeClass
    public static void startGdx() {
        if (Gdx.app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }
    }

    @AfterClass
    public static void stopGdx() {
        if (app != null) {
            app.exit();
            app = null;
        }
    }

    @Test
    public void studioFs_resolvesProjectDirFromProjectDirectoryPath_notLegacyStudioProjectsRoot() throws Exception {
        Path customDir = Files.createTempDirectory("pixscape-custom-project-dir");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Custom";
        cfg.projectFileName = "custom";
        cfg.projectDirectoryPath = customDir.toString();
        cfg.exportRootPathDir = Files.createTempDirectory("pixscape-custom-export-dir").toString();
        cfg.createSceneMeta("Main");

        FileHandle resolved = StudioFs.requireStudioProjectDir(cfg);

        assertEquals(new FileHandle(customDir.toFile()).path(), resolved.path());
        assertFalse(resolved.path().replace('\\', '/').contains("/.pixscape-studio/projects/"));
    }

    @Test
    public void loadProject_setsProjectDirectoryToActualFileParentForMovedProjects() throws Exception {
        Path customDir = Files.createTempDirectory("pixscape-open-custom-project-dir");
        Files.createDirectories(customDir.resolve(StudioFs.DIR_SCENES));

        String json = "{" +
                "\"projectKind\":\"pixscape-studio-project\"," +
                "\"projectTitle\":\"Moved Project\"," +
                "\"projectFileName\":\"moved-project\"," +
                "\"projectDirectoryPath\":\"C:/stale/location\"," +
                "\"version\":\"1\"," +
                "\"exportRootPathDir\":\"" + jsonPath(Files.createTempDirectory("pixscape-open-custom-export")) + "\"," +
                "\"glSamples\":0," +
                "\"currentSceneName\":\"Main\"," +
                "\"nextSceneIndex\":2," +
                "\"scenes\":{\"Main\":{\"name\":\"Main\",\"file\":\"scene1.json\"}}" +
                "}";
        Path projectFile = customDir.resolve("moved-project.json");
        Files.writeString(projectFile, json, StandardCharsets.UTF_8);

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(new FileHandle(projectFile.toFile()));

        assertEquals(new FileHandle(customDir.toFile()).path(), loaded.projectDirectoryPath);
    }

    @Test
    public void whitePixelSourceRemainsInStudioInternalDirectory() {
        String internalDir = InternalAssets.internalDir().toString().replace('\\', '/');
        String whitePixelPath = InternalAssets.whitePixelPngPath().toString().replace('\\', '/');

        assertTrue(internalDir.endsWith("/.pixscape-studio/internal"));
        assertTrue(whitePixelPath.endsWith("/.pixscape-studio/internal/" + InternalAssets.WHITE_PIXEL_FILE));
        assertFalse(whitePixelPath.contains("/Pixscape Projects/"));
        assertFalse(whitePixelPath.contains("/pixscape-project/"));
    }

    private static String jsonPath(Path path) {
        return path.toString().replace("\\", "\\\\");
    }
}
