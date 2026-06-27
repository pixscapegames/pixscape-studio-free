package games.pixscape.studio.service;

import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServicePreviewSaveGuardContractTest {

    @Test
    public void previewSaveGuard_methodsMaintainPersistentDirtyGuardContract() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );

        String markBody = methodBody(source, "public void markPreviewSaveRequired()");
        String requiresBody = methodBody(source, "public boolean requiresSaveBeforePreview()");

        assertTrue(markBody.contains("previewSaveRequired = true;"));
        assertTrue(requiresBody.contains("historyManager.isDirty()"));
        assertTrue(requiresBody.contains("previewSaveRequired"));
        assertTrue(requiresBody.contains("isRuntimeExportMissingOrUnusableForPreview(ProjectConfig.getInstance())"));
    }

    @Test
    public void missingRuntimeExportRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-missing-runtime-export");
        ProjectConfig cfg = projectConfig(exportRoot);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void presentRuntimeExportDoesNotRequireSaveByItself() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-present-runtime-export");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot);

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void copiedStudioProjectWithMissingRuntimeExportRequiresSaveWithoutCrash() throws Exception {
        Path studioProjectDir = Files.createTempDirectory("preview-copied-studio-project");
        Path exportRoot = Files.createTempDirectory("preview-copied-export-root");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.projectDirectoryPath = studioProjectDir.toString();
        Files.createDirectories(studioProjectDir.resolve("scenes"));

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void incompleteRuntimeExportProjectRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-incomplete-runtime-export");
        ProjectConfig cfg = projectConfig(exportRoot);
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot);
        Files.writeString(runtimeRoot.resolve(RuntimeExport.PROJECT_JSON), runtimeProjectJson("scene1.json"));

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void blankExportRootDoesNotHideExistingPreviewValidation() {
        ProjectConfig cfg = projectConfig(Path.of("unused"));
        cfg.exportRootPathDir = "";

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }

    private static ProjectConfig projectConfig(Path exportRoot) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Preview Export";
        cfg.projectFileName = "preview-export";
        cfg.projectDirectoryPath = exportRoot.resolve("studio").toString();
        cfg.exportRootPathDir = exportRoot.toString();
        cfg.createSceneMeta("Main");
        return cfg;
    }

    private static void writeUsableRuntimeExport(Path exportRoot) throws Exception {
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot.resolve("scenes"));
        Files.writeString(runtimeRoot.resolve(RuntimeExport.PROJECT_JSON), runtimeProjectJson("scene1.json"));
        Files.writeString(runtimeRoot.resolve("scenes").resolve("scene1.json"), "{\"entities\":{}}");
    }

    private static String runtimeProjectJson(String sceneFile) {
        return """
                {
                  "projectKind": "pixscape-runtime-project",
                  "projectFileName": "preview-export",
                  "version": "1",
                  "runtimeRootDir": "runtime",
                  "scenesDir": "scenes",
                  "atlasesDir": "atlases",
                  "effectsDir": "effects",
                  "animationsDir": "animations",
                  "shadersDir": "shaders",
                  "audioDir": "audio",
                  "prefabsDir": "prefabs",
                  "currentSceneName": "Main",
                  "scenes": {
                    "Main": {
                      "name": "Main",
                      "file": "%s"
                    }
                  }
                }
                """.formatted(sceneFile);
    }
}
