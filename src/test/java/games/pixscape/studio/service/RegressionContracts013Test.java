package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegressionContracts013Test {

    @Test
    public void saveProjectAndCurrentScene_usesPreparedPlan_andRunsPersistenceAtlasExportFlow() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String body = methodBody(source, "public void saveProjectAndCurrentScene()");

        assertOrdered(body,
                "prepareSaveExecutionPlan()",
                "saveProjectFile(plan.cfg())",
                "maybeRepackAtlas(plan)",
                "rebuildSparseFromDense()",
                "saveScene(",
                "saveTileAnimations(plan)",
                "exportRuntimeBestEffort(",
                "finishSaveWithScene("
        );
    }

    @Test
    public void sceneAtlasInput_sync_cleansUnusedInput_andKeepsWhitePixel() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String body = methodBody(source, "public AtlasInputSyncResult syncSceneAtlasInput(");

        assertTrue(body.contains("inputDir.mkdirs();"));
        assertTrue(body.contains("ensureInternalWhitePixel(inputDir);"));
        assertTrue(body.contains("toRequiredInputFileNames(required)"));
        assertTrue(body.contains("cleanupUnusedInputFiles(inputDir, requiredInputFileNames)"));
    }

    @Test
    public void atlasOperationsLogSummariesInsteadOfEveryInputOrAlias() throws Exception {
        String inputSource = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String loaderSource = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasLoaderService.java");

        assertTrue(inputSource.contains("\"Atlas input synced: scene=\""));
        assertFalse(inputSource.contains("\"Copied atlas input: \""));
        assertFalse(inputSource.contains("\"Deleted unused atlas input file: \""));
        assertFalse(inputSource.contains("\"Copied animation frame to atlas input: \""));
        assertTrue(loaderSource.contains("settings.silent = true;"));
        assertTrue(loaderSource.contains("\"Scene atlas packed: scene=\""));
    }

    @Test
    public void sceneAtlasInput_cleanup_onlyRemovesUnreferencedAssets_notPrefabSourceOwnership() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String collectBody = methodBody(source, "private Set<String> collectRequiredAtlasInputPathsForCurrentScene(");

        assertOrdered(collectBody,
                "Aspect.all(AssetRefComponent.class)",
                "collectUsedTiledRenderableAssetIds(world, tileAnimationsDb)",
                "addParticleImageSourcePaths(cfg, world, required)"
        );
    }

    @Test
    public void sceneAtlasInput_cleanup_isIdempotent_andFileNameBased() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String methodBody = methodBody(source, "private static Set<String> toRequiredInputFileNames(");
        String cleanupBody = methodBody(source, "private static int cleanupUnusedInputFiles(");

        assertTrue(methodBody.contains("fileNameFromPath(relPath)"));
        assertTrue(cleanupBody.contains("if (!requiredInputFileNames.contains(child.name()))"));
    }

    @Test
    public void sceneAtlasInput_cleanup_keepsAnimationAndTiledAssetsByLiveSceneReferences() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String tiledBody = methodBody(source, "public IntSet collectUsedTiledRenderableAssetIds(");

        assertTrue(tiledBody.contains("findTileAnimationProjectDef(tileAnimationsDb, logicalId)"));
        assertTrue(tiledBody.contains("used.add(frameAssetId)"));
        assertTrue(tiledBody.contains("used.add(logicalId)"));
    }

    @Test
    public void sceneAtlasInput_cleanup_ignoresPhysicsOnlyData() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/SceneAtlasInputService.java");
        String collectBody = methodBody(source, "private Set<String> collectRequiredAtlasInputPathsForCurrentScene(");

        assertFalse(collectBody.contains("Physics"));
        assertFalse(collectBody.contains("Joint"));
    }

    @Test
    public void maybeRepackAtlas_syncsInput_deletesOldPages_packsAndReloads() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String body = methodBody(source, "private void maybeRepackAtlas(SaveExecutionPlan plan)");

        assertOrdered(body,
                "syncSceneAtlasInputForSave(plan)",
                "ProjectFileCleanupService.deleteSceneAtlasFiles",
                "SceneAtlasLoaderService.packSceneAtlas",
                "SceneAtlasLoaderService.loadSceneAtlas"
        );
    }

    @Test
    public void runtimeExport_rebuildsAndDoesNotExportStudioAnimationSources() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/configuration/RuntimeExport.java");
        String body = methodBody(source, "public static RuntimeConfig exportRuntime(");

        assertTrue(body.contains("runtimeDir.exists()"));
        assertTrue(body.contains("runtimeDir.deleteDirectory()"));
        assertTrue(body.contains("copyAtlasesWithoutInput("));
        assertTrue(body.contains("StudioFs.DIR_ORIG_EFFECTS"));
        assertTrue(body.contains("StudioFs.DIR_ORIG_SHADERS"));
        assertTrue(body.contains("StudioFs.DIR_ORIG_AUDIO"));

        assertFalse("Runtime export must not copy Studio animation source frames.",
                body.contains("StudioFs.DIR_ORIG_ANIMATIONS"));
    }

    @Test
    public void asyncCoordinator_latestWins_andObsoleteOrInterruptedNeverApply() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/atlas/AsyncAtlasRepackCoordinator.java");

        String requestBody = methodBody(source, "public synchronized long requestAsyncPack(");
        assertTrue(requestBody.contains("requestedGeneration++"));
        assertTrue(requestBody.contains("cancelRunning(\"newer request\")"));

        String launchBody = methodBody(source, "private void launchAsyncPack()");
        assertTrue(launchBody.contains("if (Thread.currentThread().isInterrupted())"));
        assertTrue(launchBody.contains("if (disposed || generation != requestedGeneration)"));
    }

    private static void assertOrdered(String text, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = text.indexOf(marker);
            assertTrue("Missing marker: " + marker, current >= 0);
            assertTrue("Marker order invalid for: " + marker, current > previous);
            previous = current;
        }
    }

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of(rel), StandardCharsets.UTF_8);
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
}
