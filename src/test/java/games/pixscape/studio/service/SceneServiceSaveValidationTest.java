package games.pixscape.studio.service;

import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.RuntimeExportPaths;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class SceneServiceSaveValidationTest {

    @Test(expected = IllegalStateException.class)
    public void requireValidExportRootOrThrow_blankExportRoot_throws() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.exportRootPathDir = "   ";

        SceneService.requireValidExportRootOrThrow(cfg, "saveProjectAndCurrentScene");
    }

    @Test
    public void requireValidExportRootOrThrow_nonBlankExportRoot_doesNotThrow() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.exportRootPathDir = "/tmp/export";

        SceneService.requireValidExportRootOrThrow(cfg, "saveProjectAndCurrentScene");
    }

    @Test
    public void configuredRuntimeProjectDirNormalizesToExportParent() {
        Path exportRoot = Path.of("build", "tmp", "preview-export");
        ProjectConfig cfg = new ProjectConfig();
        cfg.exportRootPathDir = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).toString();

        SceneService.requireValidExportRootOrThrow(cfg, "saveProjectAndCurrentScene");

        assertEquals(exportRoot, RuntimeExportPaths.userRootPath(cfg));
    }
}
