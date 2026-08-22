package games.pixscape.studio.importer.tmx;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmxSceneImportBulkSparseContractTest {

    @Test
    public void populateTilesUsesNewLayerBuilderInsteadOfRandomUpdateInsertion() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/importer/tmx/TmxSceneImportService.java"),
                StandardCharsets.UTF_8
        );
        String method = methodBody(source, "private void populateTiles(");

        assertTrue(method.contains("TiledSparseStorageHelper.beginNewLayerStorage("));
        assertTrue(method.contains("sparseStorage.append("));
        assertFalse(method.contains("TiledSparseStorageHelper.setTile("));
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
