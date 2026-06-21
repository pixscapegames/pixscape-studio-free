package games.pixscape.studio.service;

import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

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
}
