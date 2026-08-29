package games.pixscape.studio.ui.contextmenu;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StudioContextMenuLightContractTest {

    @Test
    public void addLightUsesOrdinaryLayerAndDeletionRemainsOnGenericEditPath() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/contextmenu/StudioContextMenu.java"),
                StandardCharsets.UTF_8);
        String lights = methodBody(source, "private void showAddLightMenu()");
        String edit = methodBody(source, "private void showEditMenu()");

        assertTrue(lights.contains("layerService.isUniversalLayerEntity(selectionService.getActivelayerId())"));
        assertTrue(lights.contains("ops.createPointLight("));
        assertTrue(lights.contains("ops.createConeLight("));
        assertFalse(lights.contains("deleteEntities("));
        assertFalse(source.contains("Delete light"));
        assertTrue(edit.contains("ops.deleteEntities(selectionService.getSelectionSnapshot())"));
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
