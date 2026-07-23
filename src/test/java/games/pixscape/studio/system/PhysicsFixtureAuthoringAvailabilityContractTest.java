package games.pixscape.studio.system;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhysicsFixtureAuthoringAvailabilityContractTest {

    @Test
    public void gizmoFixtureOverlay_usesEcsAuthoringData_withoutBox2dAvailabilityGate() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/system/GizmoSystem.java");

        String hasFixtureOverlayWork = methodBody(source, "private boolean hasFixtureOverlayWork()");
        assertTrue(hasFixtureOverlayWork.contains("isFixtureOverlayVisible() && physicsService != null"));
        assertFalse(hasFixtureOverlayWork.contains("physicsService.isAvailable()"));

        String isDrawableFixtureBody = methodBody(source, "private boolean isDrawableFixtureBody(int bodyEid)");
        assertTrue(isDrawableFixtureBody.contains("fixtures.hasShapes()"));
        assertFalse(isDrawableFixtureBody.contains("physicsService.isAvailable()"));
    }

    @Test
    public void pickingFixtureAuthoring_usesEcsAuthoringData_withoutBox2dAvailabilityGate() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/system/PickingSystem.java");

        String isFixturePickingEnabled = methodBody(source, "private boolean isFixturePickingEnabled()");
        assertTrue(isFixturePickingEnabled.contains("physicsService != null"));
        assertTrue(isFixturePickingEnabled.contains("isExplicitPhysicsEditMode()"));
        assertFalse(isFixturePickingEnabled.contains("physicsService.isAvailable()"));

        String isFixtureBodyPickCandidate = methodBody(source, "private boolean isFixtureBodyPickCandidate(int bodyEid)");
        assertTrue(isFixtureBodyPickCandidate.contains("hasAuthoringFixtures(bodyEid)"));
        assertFalse(isFixtureBodyPickCandidate.contains("physicsService.isAvailable()"));
    }

    @Test
    public void fixturePickingService_usesEcsFixtures_withoutBox2dAvailabilityGate() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/physics/PhysicsFixturePickingService.java");

        String pick = methodBody(source, "public PickResult pick(");
        assertTrue(pick.contains("!physicsService.hasPhysics(bodyEntityId)"));
        assertTrue(pick.contains("compiler.compile(source)"));
        assertFalse(pick.contains("physicsService.isAvailable()"));
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
