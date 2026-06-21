package games.pixscape.studio.ops;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class EditorOpsStandaloneSpritePackGuardContractTest {

    @Test
    public void createStandaloneSprite_requestsPackOnlyWhenNotQueuedOrRunning_andInputChangedOrNotPacked() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ops/EditorOpsImpl.java"),
                StandardCharsets.UTF_8
        );

        String body = methodBody(source, "public int createStandaloneSprite(String relativePath, float worldX, float worldY, String metaName)");

        assertTrue(body.contains("boolean inputChanged = sceneService.ensureImageInAtlasInput(sceneTag, fullRelPath);"));
        assertTrue(body.contains("boolean alreadyPacked = assetId >= 0 && atlasStudioService.isPacked(assetId, sceneTag);"));
        assertTrue(body.contains("boolean packAlreadyQueuedOrRunning = atlasStudioService.hasAsyncPackQueuedOrRunningFor(sceneTag);"));
        assertTrue(body.contains("if (!packAlreadyQueuedOrRunning && (inputChanged || !alreadyPacked))"));
        assertTrue(body.contains("atlasStudioService.requestAsyncPack(sceneTag);"));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
