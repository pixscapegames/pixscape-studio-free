package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioSceneInputPipelineContractTest {
    @Test
    public void hudTouchFocusRunsBeforeStudioAndStudioBeforeScene() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java");
        int chain = source.indexOf("inputMultiplexer = new InputMultiplexer(");
        int order = source.indexOf("hudTestInputRouter, uiStage, canvas.getGridStage()", chain);
        int state = source.indexOf("canvas.getInputState()", chain);

        assertTrue(chain >= 0 && order > chain);
        assertEquals(-1, state);
        assertFalse(source.contains("StudioCenterInputGate"));
    }

    @Test
    public void sceneDetachCancelsBothGestureStateAndScene2dTouchFocus() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int cancelMethod = source.indexOf("private void cancelPointerInteraction()");
        int nextMethod = source.indexOf("private void createWorld(", cancelMethod);
        String body = source.substring(cancelMethod, nextMethod);

        assertTrue(body.contains("gridStage.cancelTouchFocus();"));
        assertTrue(body.contains("panning = false;"));
        assertTrue(body.contains("inputState.clearAll();"));
    }

    @Test
    public void sceneViewportUsesCenterScreenBoundsWithoutRecentering() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int resize = source.indexOf("public void resize(int x, int y, int width, int height)");
        int dispose = source.indexOf("public void dispose()", resize);
        String body = source.substring(resize, dispose);

        assertTrue(body.contains("viewport.update(width, height, false);"));
        assertTrue(body.contains("viewport.setScreenBounds(x, y, width, height);"));
    }

    @Test
    public void worldSubmissionDoesNotOverwriteTheSceneStageViewport() throws Exception {
        String source = read(
                "src/main/java/games/pixscape/studio/system/StudioRenderSubmitSystem.java");

        assertFalse(source.contains("HdpiUtils.glViewport"));
        assertFalse(source.contains("Gdx.graphics.getWidth(), Gdx.graphics.getHeight()"));
    }

    @Test
    public void emptyDocumentContentFrameDoesNotClaimSceneBackgroundClicks() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/document/EditorDocumentHost.java");
        assertTrue(source.contains("contentFrame.setTouchable(Touchable.childrenOnly);"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
