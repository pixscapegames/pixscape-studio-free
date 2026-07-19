package games.pixscape.studio.configuration;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class RuntimeExportPathsTest {

    @Test
    public void userRootPathKeepsConfiguredParentExportRoot() {
        Path exportRoot = Path.of("build", "tmp", "export-root");

        assertEquals(exportRoot, RuntimeExportPaths.userRootPath(exportRoot));
    }

    @Test
    public void userRootPathAcceptsConfiguredRuntimeProjectDir() {
        Path exportRoot = Path.of("build", "tmp", "export-root");
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);

        assertEquals(exportRoot, RuntimeExportPaths.userRootPath(runtimeRoot));
    }

    @Test
    public void runtimeRootPathUsesCanonicalUserRoot() {
        ProjectConfig cfg = new ProjectConfig();
        Path exportRoot = Path.of("build", "tmp", "export-root");
        cfg.exportRootPathDir = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).toString();

        assertEquals(exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME), RuntimeExportPaths.runtimeRootPath(cfg));
    }
}
