package games.pixscape.studio.service.hud;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HudEditorSessionDropCoordinatesTest {
    @Test public void logicalScreenPointerMapsToCanvasLocalCoordinates() {
        ScreenViewport viewport = new ScreenViewport();
        viewport.setScreenBounds(100, 50, 400, 200);

        Vector2 center = HudEditorSession.screenToHud(
                viewport, 600, 300, 450, new Vector2());

        assertEquals(200f, center.x, 0.001f);
        assertEquals(100f, center.y, 0.001f);
    }

    @Test public void movedCanvasOriginMapsPointerWithoutPan() {
        ScreenViewport viewport = new ScreenViewport();
        viewport.setScreenBounds(280, 160, 400, 200);

        Vector2 center = HudEditorSession.screenToHud(viewport, 600, 480, 340, new Vector2());

        assertEquals(200f, center.x, 0.001f);
        assertEquals(100f, center.y, 0.001f);
    }
}
