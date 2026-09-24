package games.pixscape.studio.ui.main;

import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldCanvasMultiSceneContractTest {
    @Test
    public void sharedCanvasHasExplicitAttachmentAndOnlyProcessesAttachedContext() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        assertTrue(source.contains("public void attach(SceneEditorContext context)"));
        assertTrue(source.contains("public SceneEditorContext detach()"));
        assertTrue(source.contains("if (getAttachedSceneContext() == null) return;"));
        assertTrue(source.contains("activeContext.recordProcessedFrame();"));
        assertTrue(source.contains("return sceneEditorContext != null ? sceneEditorContext.sceneIdentity() : null;"));
        assertFalse(source.contains("return cfg.canonicalSceneTagCurrent();"));
    }

    @Test
    public void documentActivationOwnsSceneHudAttachmentAndPanelRebinding() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java");
        assertTrue(source.contains("canvas.detach();"));
        assertTrue(source.contains("canvas.attach(currentContext);"));
        assertTrue(source.contains("itemTreePanel.bindSceneContext(context)"));
        assertTrue(source.contains("layersPanel.bindSceneContext(context)"));
        assertTrue(source.contains("propertiesPanel.bindSceneContext(context)"));
        assertTrue(source.contains("toolBar.bindSceneContext(context)"));
        assertTrue(source.contains("toolBar.suspendSceneContext()"));
    }

    @Test
    public void activeSceneRebindsAllTiledMutationSurfaces() throws Exception {
        String canvas = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String contextMenu = read("src/main/java/games/pixscape/studio/ui/contextmenu/StudioContextMenu.java");
        String assets = read("src/main/java/games/pixscape/studio/ui/asset/AssetsPanel.java");
        assertTrue(canvas.contains("contextMenu.bindSceneContext(this)"));
        assertTrue(contextMenu.contains("public void bindSceneContext(WorldCanvas canvas)"));
        assertTrue(assets.contains("getAttachedSceneContext() == null"));
        assertTrue(assets.contains("app.getCanvas().getTiledPaintService()"));
        assertFalse(assets.contains("private final TiledPaintService tiledPaintService"));
    }

    @Test
    public void effectiveModeProjectionUpdatesOnlyAttachedSceneSubmode() throws Exception {
        String canvas = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        assertTrue(canvas.contains("setActiveSceneSubmodeSink"));
        assertTrue(canvas.contains("SceneEditorContext activeContext = getAttachedSceneContext()"));
        assertTrue(canvas.contains("activeContext.rememberSceneSubmode(mode)"));
    }

    @Test
    public void lowLevelSceneContextWorkCannotReplaceDocumentModeAuthority() {
        StudioEditingModeService modes = new StudioEditingModeService();
        HudScreenEditorDocument hud = new HudScreenEditorDocument("hud/main", "HUD");
        StudioApplicationAdapter.projectDocumentEditingMode(modes, hud, 1);

        SceneEditorContext temporary = new SceneEditorContext("scene-b", modes);
        temporary.rememberSceneSubmode(StudioEditingMode.PHYSICS);
        temporary.captureView(10f, 20f, 2f);

        assertTrue(modes.hasActiveHudDocument());
        assertEquals(StudioEditingMode.HUD, modes.getCurrentMode());

        SceneEditorContext sceneA = new SceneEditorContext("scene-a", modes);
        sceneA.rememberSceneSubmode(StudioEditingMode.TILED);
        SceneEditorDocument sceneADocument = new SceneEditorDocument("scene-a", "A", sceneA);
        StudioApplicationAdapter.projectDocumentEditingMode(modes, sceneADocument, 1);

        temporary.rememberSceneSubmode(StudioEditingMode.SPATIAL);
        temporary.captureView(-5f, 4f, .5f);
        assertTrue(modes.hasActiveSceneDocument());
        assertEquals(StudioEditingMode.TILED, modes.getCurrentMode());
    }

    @Test
    public void worldCanvasAttachContainsNoDocumentModeProjection() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int start = source.indexOf("public void attach(SceneEditorContext context)");
        int end = source.indexOf("public SceneEditorContext detach()", start);
        String attach = source.substring(start, end);

        assertFalse(attach.contains("activateSceneDocument"));
        assertFalse(attach.contains("activateHudDocument"));
        assertFalse(attach.contains("deactivateDocument"));
    }

    @Test
    public void internalContextsDoNotBindProjectSceneGpuSnapshots() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int start = source.indexOf("public void attach(SceneEditorContext context)");
        int end = source.indexOf("public SceneEditorContext detach()", start);
        String attach = source.substring(start, end);
        assertTrue(attach.contains("gpuSnapshotManager != null && context.sceneIdentity() != null"));
    }

    @Test
    public void temporaryWorldsUseOnlyTheirOwnPhysicsShapeIdAuthority() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        assertTrue(source.contains("new PhysicsService(world(), box2dWorldService, sceneMeta)"));
    }

    @Test
    public void focusedCameraIsCapturedByTheActiveIsolatedContext() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        assertTrue(source.contains("public void focusCameraAt(float worldX, float worldY)"));
        assertTrue(source.contains("captureView(sceneEditorContext)"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
