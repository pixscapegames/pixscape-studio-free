package games.pixscape.studio.ui.tree;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
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
        assertTrue(mapSelection.contains("setActivelayerIdForTiledMapContext("));
        assertTrue(entityLayerActivation.contains("selectionService.setActivelayerId(layerEntity, source);"));
        assertEquals(1, countOccurrences(source, "setActivelayerIdForTiledMapContext("));
        assertTrue(source.contains("explicitTiledMapEntityId = -1;"));
        assertTrue(source.contains("tree.registerMapNode(mapNode, mapEntityId);"));
        assertTrue(mapSelection.contains("int mapEntityId = mapNode.getEntityId();"));
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
