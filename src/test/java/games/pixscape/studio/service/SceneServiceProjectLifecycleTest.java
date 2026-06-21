package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneServiceProjectLifecycleTest {

    @Test
    public void projectLayoutInitialization_createsRequiredDirsAssetsRegistryAndProjectFile() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-project-layout");
        FileHandle projectDir = new FileHandle(dir.toFile());

        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_IMAGES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_TILES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_EFFECTS).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_SHADERS).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_AUDIO).mkdirs();
        projectDir.child(StudioFs.DIR_ATLASES).mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Lifecycle";
        cfg.projectFileName = "lifecycle";
        cfg.exportRootPathDir = projectDir.path();
        cfg.createSceneMeta("MainScene");

        FileHandle projectFile = projectDir.child("lifecycle.json");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        assetsFile.writeString("{}", false, "UTF-8");

        assertTrue(projectDir.child(StudioFs.DIR_SCENES).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_IMAGES).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_TILES).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_EFFECTS).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_SHADERS).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ORIG_AUDIO).exists());
        assertTrue(projectDir.child(StudioFs.DIR_ATLASES).exists());
        assertTrue(projectFile.exists());
        assertTrue(assetsFile.exists());
    }

    @Test
    public void projectSaveValidation_invalidExportRootDoesNotOverwriteExistingProjectFile() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-project-save-validation");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());
        projectFile.writeString("{\"safe\":\"before\"}", false, "UTF-8");
        String baseline = projectFile.readString("UTF-8");

        ProjectConfig invalidCfg = new ProjectConfig();
        invalidCfg.projectTitle = "Invalid";
        invalidCfg.projectFileName = "invalid";
        invalidCfg.exportRootPathDir = " ";
        invalidCfg.createSceneMeta("Main");

        try {
            SceneService.requireValidExportRootOrThrow(invalidCfg, "saveProjectAndCurrentScene");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertEquals(baseline, projectFile.readString("UTF-8"));
    }

    @Test
    public void tryOpenProjectFailureContract_loadOpenContextFailureLeavesSafeResettableState() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-open-failure-contract");
        FileHandle projectFile = new FileHandle(dir.resolve("broken.json").toFile());
        projectFile.writeString("{\"projectTitle\":\"broken\"", false, "UTF-8");

        ProjectConfig polluted = new ProjectConfig();
        polluted.createSceneMeta("Polluted");
        ProjectConfig.setInstance(polluted);

        try {
            SceneService.loadOpenContextOrThrow(projectFile);
        } catch (RuntimeException expected) {
            SceneService.resetProjectConfigToEmptyState();
        }

        ProjectConfig reset = ProjectConfig.getInstance();
        assertNotNull(reset);
        assertEquals(0, reset.getSceneNames().size);
        assertFalse(projectFile.parent().child(StudioFs.FILE_ASSETS_JSON).exists());
    }

    @Test
    public void openingStudioProjectFromStudioDirSucceeds() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-open-valid");
        FileHandle projectDir = new FileHandle(dir.toFile());
        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);
        scenesDir.mkdirs();
        scenesDir.child("scene1.json").writeString("{}", false, "UTF-8");
        projectDir.child(StudioFs.FILE_ASSETS_JSON).writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Valid";
        cfg.projectFileName = "valid";
        cfg.exportRootPathDir = Files.createTempDirectory("scene-service-export-valid").toString();
        cfg.createSceneMeta("Main");
        FileHandle projectFile = projectDir.child("valid.json");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        SceneService.OpenProjectContext context = SceneService.loadOpenContextOrThrow(projectFile);
        assertNotNull(context);
    }

    @Test
    public void openingRuntimeExportProjectJsonIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-runtime-reject");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());
        projectFile.writeString("{\"projectKind\":\"pixscape-runtime-project\"}", false, "UTF-8");
        try {
            SceneService.loadOpenContextOrThrow(projectFile);
            fail("Expected runtime export project json to be rejected.");
        } catch (IllegalStateException ex) {
            assertEquals("This is an exported Pixscape runtime project, not a Studio project.", ex.getMessage());
        }
    }

    @Test
    public void openingProjectInsideExportDirIsAllowed() throws Exception {
        Path exportRoot = Files.createTempDirectory("scene-service-export-root");
        Path projectPath = exportRoot.resolve("pixscape-project").resolve("studio");
        Files.createDirectories(projectPath.resolve(StudioFs.DIR_SCENES));
        FileHandle projectDir = new FileHandle(projectPath.toFile());
        projectDir.child(StudioFs.FILE_ASSETS_JSON).writeString("{}", false, "UTF-8");
        projectDir.child(StudioFs.DIR_SCENES).child("scene1.json").writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Invalid";
        cfg.projectFileName = "invalid";
        cfg.exportRootPathDir = exportRoot.toString();
        cfg.createSceneMeta("Main");
        FileHandle projectFile = projectDir.child("invalid.json");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        SceneService.OpenProjectContext context = SceneService.loadOpenContextOrThrow(projectFile);

        assertNotNull(context);
    }

    @Test
    public void exportDirInsideStudioProjectDirIsAllowed() throws Exception {
        Path projectPath = Files.createTempDirectory("scene-service-project-root");
        Files.createDirectories(projectPath.resolve(StudioFs.DIR_SCENES));
        FileHandle projectDir = new FileHandle(projectPath.toFile());
        projectDir.child(StudioFs.FILE_ASSETS_JSON).writeString("{}", false, "UTF-8");
        projectDir.child(StudioFs.DIR_SCENES).child("scene1.json").writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Invalid2";
        cfg.projectFileName = "invalid2";
        cfg.exportRootPathDir = projectPath.resolve("pixscape-project").toString();
        cfg.createSceneMeta("Main");
        FileHandle projectFile = projectDir.child("invalid2.json");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        SceneService.OpenProjectContext context = SceneService.loadOpenContextOrThrow(projectFile);

        assertNotNull(context);
    }

    @Test
    public void studioDirAndExportDirSameIsAllowed() throws Exception {
        Path sameRoot = Files.createTempDirectory("scene-service-same-root");
        Files.createDirectories(sameRoot.resolve(StudioFs.DIR_SCENES));
        FileHandle projectDir = new FileHandle(sameRoot.toFile());
        projectDir.child(StudioFs.FILE_ASSETS_JSON).writeString("{}", false, "UTF-8");
        projectDir.child(StudioFs.DIR_SCENES).child("scene1.json").writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Invalid3";
        cfg.projectFileName = "invalid3";
        cfg.exportRootPathDir = sameRoot.toString();
        cfg.createSceneMeta("Main");
        FileHandle projectFile = projectDir.child("invalid3.json");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        SceneService.OpenProjectContext context = SceneService.loadOpenContextOrThrow(projectFile);

        assertNotNull(context);
    }
}
