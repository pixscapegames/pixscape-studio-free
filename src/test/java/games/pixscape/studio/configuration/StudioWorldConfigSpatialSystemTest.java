package games.pixscape.studio.configuration;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioWorldConfigSpatialSystemTest {


    @Test
    public void studioViewportUsesRuntimeWorldFactoryAndStudioSubmitSystem() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String submitSource = read("src/main/java/games/pixscape/studio/system/StudioRenderSubmitSystem.java");
        String particleFallbackSource = read("src/main/java/games/pixscape/studio/system/StudioParticleFallbackSystem.java");

        assertTrue(source.contains("WorldConfigFactory.buildWorld("));
        assertTrue(source.contains("SceneMeta sceneMeta = cfg != null ? cfg.getCurrentSceneMeta() : null;"));
        assertTrue(source.contains("dynamicEntityState = new DynamicEntityRenderState();"));
        assertTrue(source.contains("frameQueue = new FrameRenderQueue();"));
        assertTrue(source.contains("vfxState = new VfxRenderState();"));
        assertTrue(source.contains("tiledState = new TiledMapRenderState();"));
        assertTrue(source.contains("new RenderContext(dynamicEntityState, layerState, drawList, frameQueue, vfxState, tiledState, metricsBatch, caps);"));
        assertTrue(source.contains("camera,\n                        dynamicEntityState,\n                        layerState,"));
        assertTrue(source.contains("drawList,\n                        frameQueue,\n                        vfxState,\n                        tiledState,\n                        stats,"));
        assertTrue(source.contains("sceneMeta,"));
        assertTrue(source.contains("new StudioRenderSubmitSystem("));
        assertTrue(source.contains("layerState,\n                                frameQueue,\n                                camera,"));
        assertTrue(submitSource.contains("private final FrameRenderQueue frameQueue;"));
        assertTrue(submitSource.contains("int size = frameQueue.size;"));
        assertTrue(particleFallbackSource.contains("private final VfxRenderState vfxState;"));
        assertTrue(particleFallbackSource.contains("vfxState.addParticleQuad("));
        assertFalse(particleFallbackSource.contains("private final " + legacyRenderStateName() + " state;"));
    }

    @Test
    public void studioViewportPassesTilesetProfilesToRuntimeTiledSync() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");

        assertTrue(source.contains("studioTilesetProfiles = StudioTilesetProfileResolver.buildRuntimeProfiles(assetMetaDatabaseForFallback);"));
        assertTrue(source.contains("tileAnimationRegistry,\n                        studioTilesetProfiles,\n                        systemProfiler,"));
        assertTrue(source.contains("public void refreshTilesetProfileRegistry(AssetMetaDatabase assetMetaDatabase)"));
    }

    @Test
    public void assetImportsRefreshStudioTilesetProfileRegistryFromLiveDatabase() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String refreshAfterSave = "assetMetaDatabase.save(ctx.projectDir.child(StudioFs.FILE_ASSETS_JSON));\n"
                + "        canvas.refreshTilesetProfileRegistry(assetMetaDatabase);\n"
                + "        refreshAssetsPanel();";

        assertTrue(countOccurrences(source, refreshAfterSave) >= 2);
    }

    @Test
    public void sceneReloadRefreshesStudioTilesetProfileRegistryBeforeAtlasRebind() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String method = methodBody(source, "private void rebuildRenderRuntimeForScene(");

        int refresh = method.indexOf("refreshStudioTilesetProfileRegistry(projectDir);");
        int loadAtlas = method.indexOf("SceneAtlasLoaderService.loadSceneAtlas(");
        int rebind = method.indexOf("rebindTiles();");

        assertTrue(refresh >= 0);
        assertTrue(refresh < loadAtlas);
        assertTrue(loadAtlas < rebind);
    }

    @Test
    public void spatialBlockModeSuppressesTiledPreviewGhost() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int methodStart = source.indexOf("private void updateTiledPreview()");
        int methodEnd = source.indexOf("private boolean isTiledToolInputEnabled()", methodStart);

        assertTrue(methodStart >= 0);
        assertTrue(methodEnd > methodStart);

        String method = source.substring(methodStart, methodEnd);
        int guard = method.indexOf("if (!isTiledToolInputEnabled())");
        int guardClear = method.indexOf("tiledPreviewService.clear();", guard);
        int guardReturn = method.indexOf("return;", guardClear);
        int tintedPreview = method.indexOf("tiledPreviewService.showTintedCoverage(");
        int ghostPreview = method.indexOf("tiledPreviewService.show(");

        assertTrue(guard >= 0);
        assertTrue(guard < guardClear);
        assertTrue(guardClear < guardReturn);
        assertTrue(guard < tintedPreview);
        assertTrue(guard < ghostPreview);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String legacyRenderStateName() {
        return "RenderState" + "SOA";
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = source.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
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
