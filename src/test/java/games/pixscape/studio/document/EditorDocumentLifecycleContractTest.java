package games.pixscape.studio.document;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Focused wiring checks for multi-Scene document activation. */
public class EditorDocumentLifecycleContractTest {
    @Test
    public void sceneLoadingPublishesACompleteCandidateWithoutReplacingOpenScenes() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        assertTrue(source.contains("canvas.createSceneContext(canonicalTag, meta)"));
        assertTrue(source.contains("openScene(canonicalTag, sceneName, candidate)"));
        assertTrue(source.contains("if (open instanceof games.pixscape.studio.document.SceneEditorDocument)"));
        assertTrue(source.contains("canvas.releaseSceneContext(candidate)"));
    }

    @Test
    public void hudAssetsOpenThroughDocumentManagerAndSceneTabReplacesBackButton() throws Exception {
        String assets = read("src/main/java/games/pixscape/studio/ui/asset/AssetsPanel.java");
        assertTrue(assets.contains("app.openHudScreen(node.path)"));
        assertTrue(assets.contains("app.openGameObject(node.path)"));
        assertFalse(assets.contains("app.getHudEditorSession().open("));

        String hierarchy = read("src/main/java/games/pixscape/studio/ui/hud/HudHierarchyPanel.java");
        String widgets = read("src/main/java/games/pixscape/studio/ui/hud/HudWidgetsPanel.java");
        assertFalse(hierarchy.contains("Back to Scene"));
        assertFalse(widgets.contains("documentManager::activateMaterializedScene"));
        assertFalse(widgets.contains("session::exit"));
    }

    @Test
    public void gameObjectAssetsUseAnIsolatedDocumentWorldAndAssetOnlyPersistence() throws Exception {
        String app = read("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java");
        assertTrue(app.contains("canvas.createSceneContext(null, new SceneMeta())"));
        assertTrue(app.contains("requiresSpatialLayer(asset)"));
        assertTrue(app.contains("new ToggleSpatialActorLayerCommand("));
        assertTrue(app.contains("candidate.world().process();"));
        assertTrue(app.contains("assetFile, assetId, layerIndex, 0f, 0f"));
        assertTrue(app.contains("canvas.focusCameraAt(0f, 0f);"));
        assertTrue(app.contains("RenderRebindHelper.rebindEntitiesAfterAtlasChange("));
        assertTrue(app.contains("editorDocumentManager.openGameObject"));
        assertTrue(app.contains("captureForGameObject(root)"));
        assertTrue(app.contains("saveGameObject(document.assetFile(), graph)"));
        assertTrue(app.contains("GameObjectEditorDocument"));

        String manager = read("src/main/java/games/pixscape/studio/document/EditorDocumentManager.java");
        assertTrue(manager.contains("DirtyGameObjectCloseHandler"));
        assertTrue(manager.contains("gameObject.context().setDirtyStateListener"));
    }

    @Test
    public void activeDocumentProjectsRendererPanelsAndCompatibilityMode() throws Exception {
        String app = read("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java");
        assertTrue(app.contains("editorDocumentManager.activeDocument()"));
        assertTrue(app.contains("activeDocument instanceof HudScreenEditorDocument"));
        assertTrue(app.contains("activeDocument instanceof SceneEditorDocument"));
        assertTrue(app.contains("deactivateDocument"));
        assertTrue(app.contains("activateHudDocument"));
        assertTrue(app.contains("canvas.attach(currentContext)"));
        assertTrue(app.contains("canvas.detach()"));

        String tree = read("src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java");
        String properties = read("src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java");
        assertTrue(tree.contains("app.getEditorDocumentManager().addListener"));
        assertTrue(properties.contains("app.getEditorDocumentManager().addListener"));
        assertFalse(tree.contains("event.mode() == StudioEditingMode.HUD"));
        assertFalse(properties.contains("event.mode() == StudioEditingMode.HUD"));
    }

    @Test
    public void hudPreviewControllerDoesNotOwnGlobalDocumentMode() throws Exception {
        String session = read("src/main/java/games/pixscape/studio/service/hud/HudEditorSession.java");
        assertFalse(session.contains("StudioEditingModeService"));
        assertFalse(session.contains("setMode(StudioEditingMode.HUD"));
        assertTrue(session.contains("public void suspend()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
