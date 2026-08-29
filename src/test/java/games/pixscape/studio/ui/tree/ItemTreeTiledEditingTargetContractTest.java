package games.pixscape.studio.ui.tree;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemTreeTiledEditingTargetContractTest {

    @Test
    public void onlyTiledMapNodeActivatesTiledEditingContext() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java"),
                StandardCharsets.UTF_8);
        String mapSelection = methodBody(source, "private void handleTiledMapNodeSelection(");
        String entityLayerActivation = methodBody(source, "private void activateLayerForEntity(");

        assertTrue(mapSelection.contains("mapNode.isTiledMapNode()"));
        assertTrue(mapSelection.contains("setTiledMapEditingTarget("));
        assertTrue(mapSelection.contains("mapEntityId, SelectionService.SelectionSource.TREE"));
        assertTrue(entityLayerActivation.contains("selectionService.setActivelayerId(layerEntity, source);"));
        assertEquals(2, countOccurrences(source, "setTiledMapEditingTarget("));
        assertTrue(source.contains("explicitTiledMapEntityId = -1;"));
        assertTrue(source.contains("tree.registerMapNode(mapNode, mapEntityId);"));
        assertTrue(mapSelection.contains("int mapEntityId = mapNode.getEntityId();"));
        assertFalse(source.contains("mapNode.getActor().setUserObject(layerNode.getActor().getUserObject())"));
    }

    @Test
    public void tiledInputGuardUsesTheSelectedMapTargetAuthority() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java"),
                StandardCharsets.UTF_8);
        String guard = methodBody(source, "private boolean isTiledToolInputEnabled()");

        assertTrue(guard.contains("selectionService.isTiledMapEditingTargetActive()"));
        assertTrue(guard.contains("!spatialBlockSelectionService.isEditingActive()"));
    }

    @Test
    public void pickingUsesTheSameSelectedMapTargetAuthority() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8);
        String guard = methodBody(source, "private boolean isTiledModeActive()");

        assertTrue(guard.contains("selectionService.isTiledMapEditingTargetActive()"));
    }

    @Test
    public void hotEditingPathsDoNotResolveMapFromHostLayer() throws Exception {
        String selection = source("src/main/java/games/pixscape/studio/service/SelectionService.java");
        String canvas = source("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String picking = source("src/main/java/games/pixscape/studio/system/PickingSystem.java");
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");
        String properties = source("src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java");

        assertFalse(selection.contains("findTiledMapForHost("));
        assertFalse(canvas.contains("findTiledMapForHost("));
        assertFalse(picking.contains("findTiledMapForHost("));
        assertFalse(gizmo.contains("findTiledMapForHost("));
        assertFalse(properties.contains("findTiledMapForHost("));
    }

    @Test
    public void staleBrushAndRectangleGesturesCannotCommitToReplacementMap() throws Exception {
        String canvas = source("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String touchUp = methodBody(canvas, "public void touchUp(");
        String rectangleUp = methodBody(canvas, "private void handleRectUp()");

        assertTrue(touchUp.contains("tiledMutationController.activeMapEntityId() == mapEntityId"));
        assertTrue(touchUp.contains("tiledMutationController.cancel();"));
        assertTrue(rectangleUp.contains("mapEntityId != startedMapEntityId"));
    }

    @Test
    public void sceneContextResetClearsTransientMapTarget() throws Exception {
        String canvas = source("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String reset = methodBody(canvas, "public void resetEditingContexts()");

        assertTrue(reset.contains("selectionService.clearTiledMapEditingTarget();"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String fragment) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(fragment, from)) >= 0) {
            count++;
            from += fragment.length();
        }
        return count;
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return source.substring(bodyStart + 1, i);
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
