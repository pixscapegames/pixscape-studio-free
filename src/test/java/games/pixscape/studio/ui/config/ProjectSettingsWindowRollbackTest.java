package games.pixscape.studio.ui.config;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.ProjectRenameService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProjectSettingsWindowRollbackTest {

    @Test
    public void projectSettingsSnapshot_restore_revertsPartialApplyState() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.exportRootPathDir = "/tmp/original-export";
        cfg.projectDirectoryPath = "/tmp/original-project";
        cfg.projectTitle = "Original";
        cfg.projectFileName = "original-project";
        cfg.glSamples = 2;
        EditorSettings.get().msaaSamples = 2;

        ProjectSettingsWindow.ProjectSettingsSnapshot snapshot =
                ProjectSettingsWindow.ProjectSettingsSnapshot.capture(cfg, EditorSettings.get().msaaSamples);

        cfg.exportRootPathDir = "/tmp/changed-export";
        cfg.projectDirectoryPath = "/tmp/changed-project";
        cfg.projectTitle = "Changed";
        cfg.projectFileName = "changed-project";
        cfg.glSamples = 8;
        EditorSettings.get().msaaSamples = 8;

        snapshot.restore(cfg);

        assertEquals("/tmp/original-export", cfg.exportRootPathDir);
        assertEquals("/tmp/original-project", cfg.projectDirectoryPath);
        assertEquals("Original", cfg.projectTitle);
        assertEquals("original-project", cfg.projectFileName);
        assertEquals(2, cfg.glSamples);
        assertEquals(2, EditorSettings.get().msaaSamples);
    }

    @Test
    public void rollbackProjectFileRename_restoresFilesystemAndConfigWhenSaveFailsAfterRename() throws Exception {
        Path root = Files.createTempDirectory("project-settings-rename-rollback");
        FileHandle projectDir = new FileHandle(root.resolve("chosen-project-dir").toFile());
        projectDir.mkdirs();
        projectDir.child("old-project.json").writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectFileName = "old-project";
        cfg.projectDirectoryPath = projectDir.path();

        ProjectRenameService.renameProjectFile(projectDir, cfg, "new-project");
        RuntimeException primaryFailure = new RuntimeException("save failed");
        ProjectSettingsWindow.rollbackProjectFileRename(projectDir, cfg, "old-project", primaryFailure);

        assertEquals("old-project", cfg.projectFileName);
        assertTrue(root.resolve("chosen-project-dir").toFile().exists());
        assertTrue(root.resolve("chosen-project-dir/old-project.json").toFile().exists());
    }

    @Test
    public void rollbackProjectDirectoryMove_restoresFilesystemAndConfigWhenSaveFailsAfterMove() throws Exception {
        Path root = Files.createTempDirectory("project-settings-directory-rollback");
        FileHandle oldDir = new FileHandle(root.resolve("old-dir").toFile());
        FileHandle newDir = new FileHandle(root.resolve("new-dir").toFile());
        oldDir.mkdirs();
        oldDir.child("project.json").writeString("{}", false, "UTF-8");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectDirectoryPath = newDir.path();

        ProjectRenameService.moveProjectDirectory(oldDir, newDir);
        RuntimeException primaryFailure = new RuntimeException("save failed");
        ProjectSettingsWindow.rollbackProjectDirectoryMove(newDir, oldDir, cfg, oldDir.path(), primaryFailure);

        assertEquals(oldDir.path(), cfg.projectDirectoryPath);
        assertTrue(root.resolve("old-dir/project.json").toFile().exists());
    }
}
