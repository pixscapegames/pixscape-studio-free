package games.pixscape.studio.service.hud;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HudEditorSessionDropCoordinatesTest {
    @Test public void logicalScreenPointerMapsThroughHudViewportWorldSize() {
        FitViewport viewport = new FitViewport(1920f, 1080f);
        viewport.setScreenBounds(100, 50, 400, 200);
        viewport.getCamera().position.set(960f, 540f, 0f);

        Vector2 center = HudEditorSession.screenToHud(
                viewport, 600, 300, 450, new Vector2());

        assertEquals(960f, center.x, 0.001f);
        assertEquals(540f, center.y, 0.001f);
    }

    @Test public void translatedCameraAndVisibleExtentControlProjectionAndPanLimits() {
        FitViewport viewport = new FitViewport(400f, 200f);
        viewport.setScreenBounds(100, 50, 400, 200);
        viewport.getCamera().position.set(600f, 300f, 0f);

        Vector2 center = HudEditorSession.screenToHud(viewport, 600, 300, 450, new Vector2());

        assertEquals(600f, center.x, 0.001f);
        assertEquals(300f, center.y, 0.001f);
        assertEquals(200f, HudEditorSession.clampCameraAxis(0f, 1920f, 400f), 0.001f);
        assertEquals(1720f, HudEditorSession.clampCameraAxis(2000f, 1920f, 400f), 0.001f);
        assertEquals(960f, HudEditorSession.clampCameraAxis(400f, 1920f, 2000f), 0.001f);
    }
}
