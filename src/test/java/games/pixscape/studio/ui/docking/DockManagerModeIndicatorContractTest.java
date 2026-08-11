package games.pixscape.studio.ui.docking;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockManagerModeIndicatorContractTest {
    @Test
    public void stableIndicatorOverlaysTheCanvasAndTracksRulerGeometry() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/docking/DockManager.java");

        assertTrue(source.contains("private final Stack centerStack = new Stack();"));
        assertTrue(source.contains("private final CanvasModeIndicator modeIndicator;"));
        assertTrue(source.contains("centerStack.add(rulersAndCanvasPlaceholder);"));
        assertTrue(source.contains("centerStack.add(modeOverlay);"));
        assertTrue(source.contains("modeOverlay.setTouchable(Touchable.disabled);"));
        assertTrue(source.contains("(rulersVisible ? RulerActor.TOP_HEIGHT : 0f) + 5f"));
        assertTrue(source.contains("modeOverlay.add().width(RulerActor.LEFT_WIDTH);"));
        assertFalse(methodBody(source, "private void rebuildLayout()").contains("new CanvasModeIndicator"));
    }

    @Test
    public void hiddenRulersDoNotKeepReservedCells() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/docking/DockManager.java");
        String rebuild = methodBody(source, "private void rebuildCenterOverlay()");

        assertTrue(rebuild.contains("rulersAndCanvasPlaceholder.clearChildren();"));
        assertTrue(rebuild.contains("if (rulersVisible)"));
        assertTrue(rebuild.contains("rulersAndCanvasPlaceholder.add().expand().grow();"));
    }

    @Test
    public void realContextAuthoritiesFeedAndResetTheStudioModeService() throws Exception {
        String selection = read("src/main/java/games/pixscape/studio/service/SelectionService.java");
        String physics = read("src/main/java/games/pixscape/studio/service/physics/PhysicsSelectionService.java");
        String spatial = read("src/main/java/games/pixscape/studio/service/spatial/SpatialBlockSelectionService.java");
        String scenes = read("src/main/java/games/pixscape/studio/service/SceneService.java");

        assertTrue(selection.contains("setModeActive(StudioEditingMode.TILED, isTiled"));
        assertTrue(physics.contains("setModeActive(StudioEditingMode.PHYSICS, bodyEid >= 0"));
        assertTrue(spatial.contains("setModeActive(StudioEditingMode.SPATIAL, active"));
        assertTrue(scenes.contains("canvas.resetEditingContexts();"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Method not found: " + signature);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(brace + 1, i);
        }
        throw new AssertionError("Method end not found: " + signature);
    }
}
