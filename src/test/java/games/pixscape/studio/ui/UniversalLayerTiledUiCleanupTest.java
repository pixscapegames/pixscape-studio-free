package games.pixscape.studio.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UniversalLayerTiledUiCleanupTest {
    @Test
    public void sceneAndProjectCreationExposeNoTiledMapDefaults() throws Exception {
        String newProject = source("src/main/java/games/pixscape/studio/ui/main/NewProjectWindow.java");
        String newScene = source("src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java");

        assertFalse(newProject.contains("Tiled Map Creation Defaults"));
        assertFalse(newScene.contains("Tiled Map Creation Defaults"));
        assertFalse(newProject.contains("projectionBox"));
        assertFalse(newProject.contains("tfTileWidth"));
        assertFalse(newProject.contains("tfTileHeight"));
        assertFalse(newScene.contains("NewSceneWindow"));
    }

    @Test
    public void addTiledMapIsExposedForEveryRealLayerWithoutSceneGate() throws Exception {
        String tree = source("src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java");
        String contextMenu = source("src/main/java/games/pixscape/studio/ui/contextmenu/StudioContextMenu.java");

        assertTrue(tree.contains("if (!layerService.isLayerEntity(layerEntityId)) return false;"));
        assertTrue(tree.contains("addMenu.addItem(addMap);"));
        assertTrue(contextMenu.contains("if (!layerService.isLayerEntity(layerEntityId)) return;"));
        assertFalse(tree.contains("tiledEnabled"));
        assertFalse(contextMenu.contains("tiledEnabled"));
    }

    @Test
    public void scenePropertiesExposeNoTiledMapDefaults() throws Exception {
        String properties = source("src/main/java/games/pixscape/studio/ui/property/SceneProperties.java");

        assertFalse(properties.contains("Tiled Map Creation Defaults"));
        assertFalse(properties.contains("tiledProjection"));
        assertFalse(properties.contains("tileWidth"));
        assertFalse(properties.contains("tileHeight"));
        assertFalse(properties.contains("chunkSize"));
    }

    @Test
    public void addMapDialogOwnsEstablishedDefaultsWithoutReadingSceneMeta() throws Exception {
        String dialog = source("src/main/java/games/pixscape/studio/ui/layer/AddTiledMapDialog.java");

        assertTrue(dialog.contains("DEFAULT_PROJECTION = TiledProjection.ORTHO"));
        assertTrue(dialog.contains("DEFAULT_TILE_WIDTH = 32"));
        assertTrue(dialog.contains("DEFAULT_TILE_HEIGHT = 32"));
        assertTrue(dialog.contains("DEFAULT_MAP_WIDTH = 256"));
        assertTrue(dialog.contains("DEFAULT_MAP_HEIGHT = 256"));
        assertTrue(dialog.contains("DEFAULT_CHUNK_SIZE = 16"));
        assertFalse(dialog.contains("ProjectConfig"));
        assertFalse(dialog.contains("SceneMeta"));
        assertTrue(dialog.contains("new Request("));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
