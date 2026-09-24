package games.pixscape.studio.scene;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneEditorContextOwnershipContractTest {
    @Test
    public void canvasAttachesOneContextAndKeepsPerContextBindings() throws Exception {
        String canvas = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");

        assertTrue(canvas.contains("private SceneEditorContext sceneEditorContext;"));
        assertTrue(canvas.contains("Map<SceneEditorContext, SceneBinding> sceneBindings"));
        assertTrue(canvas.contains("public void attach(SceneEditorContext context)"));
        assertTrue(canvas.contains("public SceneEditorContext detach()"));
        assertFalse(canvas.contains("private World world;"));
        assertFalse(canvas.contains("new HistoryManager("));
        assertFalse(canvas.contains("new SelectionService("));
        assertTrue(canvas.contains("return requireBoundContext().world();"));
        assertTrue(canvas.contains("return sceneEditorContext.historyManager();"));
        assertTrue(canvas.contains("return sceneEditorContext.selectionService();"));
    }

    @Test
    public void sceneServiceAndPanelsResolveOrRebindTheActiveContext() throws Exception {
        String scenes = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String tree = read("src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java");
        String layers = read("src/main/java/games/pixscape/studio/ui/layer/LayersPanel.java");
        String properties = read("src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java");

        assertTrue(scenes.contains("canvas.getAttachedSceneContext()"));
        assertFalse(scenes.contains("canvas.getEcsWorld()"));
        assertTrue(tree.contains("bindSceneContext(SceneEditorContext context)"));
        assertTrue(layers.contains("bindSceneContext(SceneEditorContext context)"));
        assertTrue(properties.contains("bindSceneContext(SceneEditorContext context)"));
    }

    @Test
    public void sceneDocumentReferencesTheMaterializedContextAndHudHasNoBackButton() throws Exception {
        String document = read("src/main/java/games/pixscape/studio/document/SceneEditorDocument.java");
        String workspace = read("src/main/java/games/pixscape/studio/ui/hud/HudWidgetsPanel.java");

        assertTrue(document.contains("private final SceneEditorContext context;"));
        assertTrue(document.contains("public SceneEditorContext context()"));
        assertFalse(workspace.contains("Back to Scene"));
        assertFalse(workspace.contains("activateMaterializedScene"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
