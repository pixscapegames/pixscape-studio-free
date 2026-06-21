package games.pixscape.studio.configuration;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class StudioWorldConfigSpatialSystemTest {


    @Test
    public void studioViewportUsesRuntimeWorldFactoryAndStudioSubmitSystem() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");

        assertTrue(source.contains("WorldConfigFactory.buildWorld("));
        assertTrue(source.contains("SceneMeta sceneMeta = cfg != null ? cfg.getCurrentSceneMeta() : null;"));
        assertTrue(source.contains("sceneMeta,"));
        assertTrue(source.contains("new StudioRenderSubmitSystem("));
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
}
