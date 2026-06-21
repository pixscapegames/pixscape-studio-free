package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneServiceRollbackTest {

    @Test
    public void rollbackCreatedSceneMeta_removesFailedSceneAndPreservesCurrent() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("NewScene");

        SceneService.rollbackCreatedSceneMeta(cfg, "NewScene");

        assertEquals("Main", cfg.getCurrentSceneName());
        assertEquals(1, cfg.getSceneNames().size);
        assertNotNull(cfg.getSceneMeta("Main"));
    }

    @Test
    public void rollbackDeletedSceneMeta_restoresMetadataAndCurrentPointer() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        SceneMeta deleted = cfg.getSceneMeta("Main");

        cfg.removeSceneMeta("Main");

        SceneService.rollbackDeletedSceneMeta(cfg, "Main", deleted, "Main");

        assertEquals("Main", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Main"));
        assertEquals(deleted.getFile(), cfg.getSceneMeta("Main").getFile());
    }

    @Test
    public void cleanupFailedNewProjectDir_doesNotDeletePreExistingDirectory() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-existing-project-dir");
        FileHandle projectDir = new FileHandle(dir.toFile());
        projectDir.child("keep.txt").writeString("keep", false, "UTF-8");

        SceneService.cleanupFailedNewProjectDir(projectDir, true);

        assertTrue(projectDir.exists());
        assertTrue(projectDir.child("keep.txt").exists());
    }

    @Test
    public void cleanupFailedNewProjectDir_deletesOnlyDirectoryCreatedByAttempt() throws Exception {
        Path root = Files.createTempDirectory("scene-service-created-project-dir");
        FileHandle projectDir = new FileHandle(root.resolve("created-project").toFile());
        projectDir.mkdirs();
        projectDir.child("partial.txt").writeString("partial", false, "UTF-8");

        SceneService.cleanupFailedNewProjectDir(projectDir, false);

        assertFalse(projectDir.exists());
    }

    @Test
    public void attachRollbackFailure_keepsPrimaryAndAddsSuppressedRollbackError() {
        RuntimeException primary = new RuntimeException("main failure");
        RuntimeException rollback = new RuntimeException("rollback failure");

        RuntimeException merged = SceneService.attachRollbackFailure(primary, rollback);

        assertSame(primary, merged);
        assertEquals(1, merged.getSuppressed().length);
        assertSame(rollback, merged.getSuppressed()[0]);
    }

    @Test
    public void rollbackSceneSwitchConfigPointer_restoresPreviousCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Second");

        boolean restored = SceneService.rollbackSceneSwitchConfigPointer(cfg, "Main");

        assertTrue(restored);
        assertEquals("Main", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void rollbackSceneCreateState_removesGhostSceneFileAndRestoresCurrentScene() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-rollback-create");
        FileHandle projectDir = new FileHandle(dir.toFile());
        projectDir.child("scenes").mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("BrokenScene");
        SceneMeta broken = cfg.getSceneMeta("BrokenScene");
        String brokenFile = broken.getFile();
        projectDir.child("scenes").child(brokenFile).writeString("{\"scene\":\"partial\"}", false, "UTF-8");

        SceneService.rollbackSceneCreateState(cfg, "Main", "BrokenScene", brokenFile, projectDir);

        assertEquals("Main", cfg.getCurrentSceneName());
        assertNull(cfg.getSceneMeta("BrokenScene"));
        assertFalse(projectDir.child("scenes").child(brokenFile).exists());
    }


    @Test
    public void deleteSceneTransition_nonActiveDeleteKeepsCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Main");

        boolean removed = cfg.removeSceneMeta("Second");

        assertTrue(removed);
        assertEquals("Main", cfg.getCurrentSceneName());
        assertNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void deleteSceneTransition_activeDeleteMovesCurrentToRemainingScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Main");

        boolean removed = cfg.removeSceneMeta("Main");

        assertTrue(removed);
        assertEquals("Second", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void resetProjectConfigToEmptyState_replacesSingletonWithSafeEmptyConfig() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Before";
        cfg.createSceneMeta("Main");
        ProjectConfig.setInstance(cfg);

        SceneService.resetProjectConfigToEmptyState();

        ProjectConfig reset = ProjectConfig.getInstance();
        assertEquals(0, reset.getSceneNames().size);
        assertNull(reset.getCurrentSceneName());
    }

}
