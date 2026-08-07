package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServiceAtlasRepackFlowContractTest {

    @Test
    public void rebuildRenderRuntimeForScene_autoRepackEnabledLoadPathDoesNotPackAtlas() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "private void rebuildRenderRuntimeForScene(");

        assertTrue(methodBody.contains("SceneAtlasLoaderService.loadSceneAtlas(cfg, canonicalTag, projectDir, canvas);"));
        assertFalse(methodBody.contains("SceneAtlasLoaderService.packSceneAtlas("));
    }

    @Test
    public void saveCurrentSceneOnly_autoRepackEnabledStillCallsRepack() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "private void saveCurrentSceneOnly(ProjectConfig cfg)");

        assertTrue(methodBody.contains("if (EditorSettings.get().autoRepackAtlases)"));
        assertTrue(methodBody.contains("repackSceneAtlas(cfg, sceneName, projectDir);"));
    }

    @Test
    public void saveFlows_autoRepackDisabledDoesNotAutoRepackOutsideGuard() throws Exception {
        String source = readSceneServiceSource();
        String saveProjectBody = methodBody(source, "public void saveProjectAndCurrentScene()");
        String saveCurrentBody = methodBody(source, "private void saveCurrentSceneOnly(ProjectConfig cfg)");
        assertTrue(hasSingleRepackInsideAutoRepackGuard(saveCurrentBody, "repackSceneAtlas(cfg, sceneName, projectDir);"));
    }

    @Test
    public void atlasInputChange_doesNotRefreshAssetsOrMutatePublishedAtlasState() throws Exception {
        String source = readSceneServiceSource();
        String callbackBody = methodBody(source, "private void onSceneAtlasInputsChanged(String sceneTag)");

        assertFalse(callbackBody.contains("refreshAssetsPanel();"));
        assertFalse(callbackBody.contains("reloadAtlasAndRebind("));
        assertFalse(callbackBody.contains("RenderRebindHelper"));
        assertFalse(callbackBody.contains("markDirty("));
    }

    @Test
    public void particleAtlasInputChange_schedulesPackWithoutPublishedAtlasInvalidation() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ops/EditorOpsImpl.java"),
                StandardCharsets.UTF_8
        );
        String createBody = methodBody(
                source,
                "public int createParticleEffect(String effectPath, float worldX, float worldY, String metaName)"
        );

        assertTrue(createBody.contains("atlasInputsChangedListener.onSceneAtlasInputsChanged(sceneTag);"));
        assertTrue(createBody.contains("atlasStudioService.requestAsyncPack(sceneTag);"));
        assertFalse(createBody.contains("reloadAtlasAndRebind("));
        assertFalse(createBody.contains("RenderRebindHelper.rebindAfterAtlasChange("));
        assertFalse(createBody.contains("snapshotManager.markDirty("));
    }

    @Test
    public void authoredAssetImport_stillRefreshesAssetsPanel() throws Exception {
        String source = readSceneServiceSource();
        String importBody = methodBody(
                source,
                "public void importAssets(Array<ImportDialog.ImportItem> items)"
        );

        assertTrue(importBody.contains("assetMetaDatabase.save("));
        assertTrue(importBody.contains("refreshAssetsPanel();"));
    }

    @Test
    public void completedGenerationPublication_stillLoadsRebindsAndInvalidatesOnce() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/atlas/AtlasStudioService.java"),
                StandardCharsets.UTF_8
        );
        String applyBody = methodBody(source, "public void applyIfPackReady()");

        assertTrue(applyBody.contains("load(tag, finalAtlasFile);"));
        assertTrue(applyBody.contains("RenderRebindHelper.rebindAfterAtlasChange("));
        assertTrue(applyBody.contains("particleSystem.invalidateAllEffects();"));
        assertTrue(applyBody.contains("canvas.invalidateStudioParticleFallbacks();"));
        assertTrue(occurrences(applyBody, "RenderRebindHelper.rebindAfterAtlasChange(") == 1);
    }

    @Test
    public void failedOrSupersededGeneration_cannotReachPublishedAtlasApply() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/service/atlas/AsyncAtlasRepackCoordinator.java"),
                StandardCharsets.UTF_8
        );
        String launchBody = methodBody(source, "private void launchAsyncPack()");
        String pollBody = methodBody(source, "public synchronized RepackArtifact pollReadyAsyncPack()");
        int catchStart = launchBody.indexOf("catch (Exception ex)");
        int finallyStart = launchBody.indexOf("finally", catchStart);
        String failureBody = launchBody.substring(catchStart, finallyStart);

        assertTrue(launchBody.contains("if (disposed || generation != requestedGeneration)"));
        assertFalse(failureBody.contains("readyArtifact ="));
        assertTrue(pollBody.contains("if (artifact.generation != requestedGeneration)"));
        assertTrue(pollBody.contains("return null;"));
    }

    private static String readSceneServiceSource() throws Exception {
        Path sceneServicePath = Path.of("src/main/java/games/pixscape/studio/service/SceneService.java");
        return Files.readString(sceneServicePath, StandardCharsets.UTF_8);
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

    private static boolean hasSingleRepackInsideAutoRepackGuard(String methodBody, String repackCall) {
        int ifIndex = methodBody.indexOf("if (EditorSettings.get().autoRepackAtlases)");
        if (ifIndex < 0) return false;

        String guardBody = methodBody(methodBody.substring(ifIndex), "if (EditorSettings.get().autoRepackAtlases)");

        int totalRepackCount = occurrences(methodBody, repackCall);
        int guardedRepackCount = occurrences(guardBody, repackCall);
        return totalRepackCount == 1 && guardedRepackCount == 1;
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
