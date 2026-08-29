package games.pixscape.studio.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UniversalLayerTiledUiCleanupTest {
    @Test
    public void sceneCreationPresentsMapDefaultsWithoutSceneTypeChoice() throws Exception {
        String newProject = source("src/main/java/games/pixscape/studio/ui/main/NewProjectWindow.java");
        String newScene = source("src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java");

        assertTrue(newProject.contains("Tiled Map Creation Defaults"));
        assertTrue(newScene.contains("Tiled Map Creation Defaults"));
        assertFalse(newProject.contains("projectionBox.setItems(\"None\""));
        assertFalse(newScene.contains("projectionBox.setItems(\"None\""));
        assertFalse(newProject.contains("tiledEnabled"));
        assertFalse(newScene.contains("tiledEnabled"));
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
    public void scenePropertiesLabelsValuesAsCreationDefaults() throws Exception {
        String properties = source("src/main/java/games/pixscape/studio/ui/property/SceneProperties.java");

        assertTrue(properties.contains("Tiled Map Creation Defaults"));
        assertTrue(properties.contains("Chunk Size:"));
        assertFalse(properties.contains("tiledEnabled"));
    }

    @Test
    public void addMapDialogReadsCreationDefaultsWithoutRetainingSceneState() throws Exception {
        String dialog = source("src/main/java/games/pixscape/studio/ui/layer/AddTiledMapDialog.java");

        assertTrue(dialog.contains("defaults.tiledProjection"));
        assertTrue(dialog.contains("defaults.tileWidth"));
        assertTrue(dialog.contains("defaults.tileHeight"));
        assertTrue(dialog.contains("defaults.chunkSize"));
        assertTrue(dialog.contains("new Request("));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
