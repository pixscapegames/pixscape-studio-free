package games.pixscape.studio.service;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServicePreviewSaveGuardContractTest {

    @Test
    public void sceneAndPreviewSavePredicatesKeepAuthoredAndExportStateSeparate() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/SceneService.java"),
                StandardCharsets.UTF_8
        );

        String markBody = methodBody(source, "public void markCurrentSceneSaveRequired()");
        String leavingBody = methodBody(source, "public boolean requiresSaveBeforeLeavingCurrentScene()");
        String requiresBody = methodBody(source, "public boolean requiresSaveBeforePreview()");

        assertTrue(markBody.contains("sceneContext().markExplicitSaveRequired();"));
        assertTrue(leavingBody.contains("sceneContext().isDirty()"));
        assertFalse(leavingBody.contains("isRuntimeExportMissingOrUnusableForPreview"));
        assertTrue(requiresBody.contains("requiresSaveBeforeLeavingCurrentScene()"));
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
    public void exportedAtlasMissingVisibleCarRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-missing-car-region");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot);
        String tag = cfg.canonicalSceneTag("Main");
        Path studioInput = Path.of(cfg.projectDirectoryPath, "atlases", "input", tag);
        Path packInput = Files.createTempDirectory("preview-car-pack-input");
        Path runtimeAtlases = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("atlases");
        Files.createDirectories(studioInput);
        Files.createDirectories(runtimeAtlases);
        writePng(studioInput.resolve("driver__a1698.png"), 0xff00ff00);
        writePng(studioInput.resolve("car__a1697.png"), 0xff267bd9);
        writePng(packInput.resolve("driver__a1698.png"), 0xff00ff00);
        TexturePacker.process(new TexturePacker.Settings(), packInput.toString(), runtimeAtlases.toString(), tag);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
        writePng(packInput.resolve("car__a1697.png"), 0xff267bd9);
        TexturePacker.process(new TexturePacker.Settings(), packInput.toString(), runtimeAtlases.toString(), tag);
        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void failedSameNamePackRequiresSaveBeforePreviewAfterRestart() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-stale-atlas-pixels");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot);
        String tag = cfg.canonicalSceneTag("Main");
        Path studioInput = Path.of(cfg.projectDirectoryPath, "atlases", "input", tag);
        Path runtimeAtlases = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("atlases");
        Files.createDirectories(studioInput);
        Files.createDirectories(runtimeAtlases);
        writePng(studioInput.resolve("A.png"), 0xffff0000);
        TexturePacker.process(new TexturePacker.Settings(), studioInput.toString(),
                runtimeAtlases.toString(), tag);
        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));

        writePng(studioInput.resolve("A.png"), 0xff267bd9);
        AtlasStudioService atlasService = new AtlasStudioService(null);
        try {
            atlasService.markRepackRequired(new com.badlogic.gdx.files.FileHandle(
                    Path.of(cfg.projectDirectoryPath).toFile()), tag);
        } finally {
            atlasService.disposeAsyncPack();
        }
        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    private static void writePng(Path path, int argb) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) image.setRGB(x, y, argb);
        ImageIO.write(image, "png", path.toFile());
    }

    @Test
    public void changedSavedHudRequiresRuntimeExportBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-stale-saved-hud");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.getCurrentSceneMeta().defaultHudScreenId = "hud/Test3";
        writeUsableRuntimeExport(exportRoot);
        writeHud(cfg, exportRoot, "old HUD", "new HUD");

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void matchingSavedHudDoesNotRequireRuntimeExportBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-current-saved-hud");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.getCurrentSceneMeta().defaultHudScreenId = "hud/Test3";
        writeUsableRuntimeExport(exportRoot);
        writeHud(cfg, exportRoot, "current HUD", "current HUD");

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void configuredRuntimeProjectDirUsesParentExportRootForPreviewValidation() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-configured-runtime-root");
        writeUsableRuntimeExport(exportRoot);

        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.exportRootPathDir = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).toString();

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void configuredRuntimeProjectDirWithMissingProjectRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-configured-runtime-root-missing-project");
        Files.createDirectories(exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME));

        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.exportRootPathDir = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).toString();

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void runtimeExportWithDifferentCurrentSceneRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-stale-current-scene");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot, "Other");

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void tiledRuntimeExportWithoutTilesetProfilesRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-missing-tileset-profiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(42);
        writeUsableRuntimeExport(exportRoot);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void staleTilesetProfilesRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-stale-tileset-profiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(42);
        writeUsableRuntimeExport(exportRoot);
        writeTilesetProfiles(exportRoot, 7);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void currentTilesetProfilesDoesNotRequireSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-current-tileset-profiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(42);
        writeUsableRuntimeExport(exportRoot);
        writeTilesetProfiles(exportRoot, 42);

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void sceneUsedTilesWithEmptyTilesetProfilesRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-empty-tileset-profiles-scene-tiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot, "Main", tiledSceneJson(1451, 1486));
        writeEmptyTilesetProfiles(exportRoot);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void sceneUsedTilesWithIncompleteTilesetProfilesRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-incomplete-tileset-profiles-scene-tiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot, "Main", tiledSceneJson(1451, 1486));
        writeTilesetProfiles(exportRoot, 1451);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void sceneUsedTiledAnimationChecksFrameProfilesInsteadOfAnimationId() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-tiled-animation-frame-profiles");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot, "Main", tiledSceneJson(9001));
        writeTileAnimations(exportRoot, 9001, 1451, 1486);
        writeTilesetProfiles(exportRoot, 1451, 1486);

        assertFalse(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
    }

    @Test
    public void sceneUsedTiledAnimationWithMissingFrameProfileRequiresSaveBeforePreview() throws Exception {
        Path exportRoot = Files.createTempDirectory("preview-tiled-animation-missing-frame-profile");
        ProjectConfig cfg = projectConfig(exportRoot);
        writeUsableRuntimeExport(exportRoot, "Main", tiledSceneJson(9001));
        writeTileAnimations(exportRoot, 9001, 1451, 1486);
        writeTilesetProfiles(exportRoot, 1451);

        assertTrue(SceneService.isRuntimeExportMissingOrUnusableForPreview(cfg));
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
        Files.writeString(runtimeRoot.resolve(RuntimeExport.PROJECT_JSON), runtimeProjectJson("Main"));

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
        writeUsableRuntimeExport(exportRoot, "Main");
    }

    private static void writeUsableRuntimeExport(Path exportRoot, String currentSceneName) throws Exception {
        writeUsableRuntimeExport(exportRoot, currentSceneName, "{\"entities\":{}}");
    }

    private static void writeUsableRuntimeExport(Path exportRoot,
                                                 String currentSceneName,
                                                 String mainSceneJson) throws Exception {
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot.resolve("scenes"));
        Files.writeString(runtimeRoot.resolve(RuntimeExport.PROJECT_JSON), runtimeProjectJson(currentSceneName));
        Files.writeString(runtimeRoot.resolve("scenes").resolve("scene1.json"), mainSceneJson);
        Files.writeString(runtimeRoot.resolve("scenes").resolve("scene2.json"), "{\"entities\":{}}");
    }

    private static void writeHud(ProjectConfig cfg, Path exportRoot,
                                 String exportedDocument, String authoredDocument) throws Exception {
        String asset = "{\"documentId\":\"hud/Test3.json\"}";
        Path studioHud = Path.of(cfg.projectDirectoryPath).resolve("hud");
        Path runtimeHud = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("hud");
        Files.createDirectories(studioHud);
        Files.createDirectories(runtimeHud);
        Files.writeString(studioHud.resolve("Test3.hudscreen"), asset);
        Files.writeString(runtimeHud.resolve("Test3.hudscreen"), asset);
        Files.writeString(studioHud.resolve("Test3.json"), authoredDocument);
        Files.writeString(runtimeHud.resolve("Test3.json"), exportedDocument);
    }

    private static void writeEmptyTilesetProfiles(Path exportRoot) throws Exception {
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot);
        Files.writeString(
                runtimeRoot.resolve("tileset-profiles.json"),
                """
                        {
                          "format": "pixscape.tileset-profiles",
                          "version": 1,
                          "tilesets": []
                        }
                        """,
                StandardCharsets.UTF_8
        );
    }

    private static void writeTilesetProfiles(Path exportRoot, int... tileAssetIds) throws Exception {
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot);

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < tileAssetIds.length; i++) {
            if (i > 0) ids.append(", ");
            ids.append(tileAssetIds[i]);
        }

        Files.writeString(
                runtimeRoot.resolve("tileset-profiles.json"),
                """
                        {
                          "format": "pixscape.tileset-profiles",
                          "version": 1,
                          "tilesets": [
                            {
                              "tilesetId": 1,
                              "tileAssetIds": [%s]
                            }
                          ]
                        }
                        """.formatted(ids),
                StandardCharsets.UTF_8
        );
    }

    private static void writeTileAnimations(Path exportRoot,
                                            int animationId,
                                            int... frameAssetIds) throws Exception {
        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeRoot);

        StringBuilder frameIds = new StringBuilder();
        StringBuilder durations = new StringBuilder();
        for (int i = 0; i < frameAssetIds.length; i++) {
            if (i > 0) {
                frameIds.append(", ");
                durations.append(", ");
            }
            frameIds.append(frameAssetIds[i]);
            durations.append(100);
        }

        Files.writeString(
                runtimeRoot.resolve("tiled-animations.json"),
                """
                        {
                          "version": "1",
                          "animations": [
                            {
                              "name": "anim",
                              "id": %d,
                              "frameAssetIds": [%s],
                              "frameDurationsMs": [%s]
                            }
                          ]
                        }
                        """.formatted(animationId, frameIds, durations),
                StandardCharsets.UTF_8
        );
    }

    private static String runtimeProjectJson(String currentSceneName) {
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
                  "gameObjectsDir": "gameobjects",
                  "currentSceneName": "%s",
                  "scenes": {
                    "Main": {
                      "name": "Main",
                      "file": "scene1.json"
                    },
                    "Other": {
                      "name": "Other",
                      "file": "scene2.json"
                    }
                  }
                }
                """.formatted(currentSceneName);
    }

    private static String tiledSceneJson(int... tileAssetIds) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < tileAssetIds.length; i++) {
            if (i > 0) ids.append(", ");
            ids.append(tileAssetIds[i]);
        }
        return """
                {
                  "entities": {
                    "0": {
                      "components": {
                        "TiledLayerComponent": {
                          "tileAssetIds": {
                            "items": [ %s ],
                            "size": %d
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(ids, tileAssetIds.length);
    }
}
