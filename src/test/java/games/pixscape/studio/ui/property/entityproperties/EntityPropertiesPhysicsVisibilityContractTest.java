package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class EntityPropertiesPhysicsVisibilityContractTest {

    @Test
    public void entityPhysicsSectionReadsCurrentScenePhysicsStateWhenBound() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/property/entityproperties/EntityProperties.java");

        String constructor = methodBody(source, "public EntityProperties(EntityPropertiesContext ctx)");
        assertTrue(constructor.contains("syncScenePhysicsEnabled();"));

        String setEntityId = methodBody(source, "public void setEntityId(int entityId)");
        assertTrue(setEntityId.contains("syncScenePhysicsEnabled();"));

        String sync = methodBody(source, "private void syncScenePhysicsEnabled()");
        assertTrue(sync.contains("ProjectConfig.getInstance()"));
        assertTrue(sync.contains("cfg != null ? cfg.getCurrentSceneMeta() : null"));
        assertTrue(sync.contains("scenePhysicsEnabled = meta != null && meta.physicsEnabled;"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
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
