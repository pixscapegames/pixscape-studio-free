package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldCanvasPanTest {
    private Graphics previousGraphics;
    private GL20 previousGl;

    @Before
    public void installLogicalWindow() {
        GdxNativesLoader.load();
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        Gdx.graphics = graphics(1600, 900, 1600, 900);
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        Gdx.gl20 = Gdx.gl;
    }

    @After
    public void restoreGraphics() {
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl;
    }

    @Test
    public void oneHundredPixelDragTranslatesCameraByOneHundredWorldUnits() {
        Fixture fixture = fixture(1000, 600, 1f);

        drag(fixture, 500, 400, 600, 400);

        assertEquals(-100f, fixture.camera.position.x, 0.001f);
        assertEquals(0f, fixture.camera.position.y, 0.001f);
    }

    @Test
    public void tenSamplesAndOneSampleProduceTheSameFinalCameraPosition() {
        Fixture once = fixture(1000, 600, 1f);
        Fixture sampled = fixture(1000, 600, 1f);

        drag(once, 500, 400, 600, 400);
        for (int x = 500; x < 600; x += 10) {
            drag(sampled, x, 400, x + 10, 400);
        }

        assertEquals(once.camera.position.x, sampled.camera.position.x, 0.001f);
        assertEquals(once.camera.position.y, sampled.camera.position.y, 0.001f);
    }

    @Test
    public void cameraMovementDoesNotFeedBackIntoTheNextPointerDelta() {
        Fixture fixture = fixture(1000, 600, 1f);

        drag(fixture, 500, 400, 550, 400);
        assertEquals(-50f, fixture.camera.position.x, 0.001f);
        drag(fixture, 550, 400, 600, 400);

        assertEquals(-100f, fixture.camera.position.x, 0.001f);
    }

    @Test
    public void zoomedCameraConvertsScreenDeltaThroughTheViewport() {
        Fixture fixture = fixture(1000, 600, 2f);

        drag(fixture, 500, 400, 600, 400);

        assertEquals(-200f, fixture.camera.position.x, 0.001f);
    }

    @Test
    public void resizedCenterViewportRetainsStablePan() {
        Fixture fixture = fixture(1000, 600, 1f);
        fixture.viewport.update(700, 500, false);
        fixture.viewport.setScreenBounds(300, 200, 700, 500);

        drag(fixture, 500, 400, 600, 400);

        assertEquals(-100f, fixture.camera.position.x, 0.001f);
    }

    @Test
    public void scrollInsideSceneViewportChangesZoom() {
        Fixture fixture = fixture(1000, 600, 1f);

        boolean handled = WorldCanvas.scrollSceneViewport(
                fixture.viewport,
                fixture.camera,
                900,
                700,
                450,
                1f,
                new Vector2(),
                new Vector2());

        assertTrue(handled);
        assertEquals(1.1f, fixture.camera.zoom, 0.001f);
    }

    @Test
    public void scrollOutsideSceneViewportDoesNotChangeZoomOrCamera() {
        Fixture fixture = fixture(1000, 600, 1f);
        fixture.camera.position.set(25f, -40f, 0f);
        fixture.camera.update();

        boolean handled = WorldCanvas.scrollSceneViewport(
                fixture.viewport,
                fixture.camera,
                900,
                100,
                450,
                1f,
                new Vector2(),
                new Vector2());

        assertFalse(handled);
        assertEquals(1f, fixture.camera.zoom, 0.001f);
        assertEquals(25f, fixture.camera.position.x, 0.001f);
        assertEquals(-40f, fixture.camera.position.y, 0.001f);
    }

    private static Fixture fixture(int width, int height, float zoom) {
        OrthographicCamera camera = new OrthographicCamera();
        ScreenViewport viewport = new ScreenViewport(camera);
        viewport.update(width, height, false);
        viewport.setScreenBounds(200, 150, width, height);
        camera.position.set(0f, 0f, 0f);
        camera.zoom = zoom;
        camera.update();
        return new Fixture(camera, viewport);
    }

    private static void drag(Fixture fixture,
                             float previousX,
                             float previousY,
                             float currentX,
                             float currentY) {
        WorldCanvas.panCameraBetweenScreenPoints(
                fixture.viewport,
                fixture.camera,
                new Vector2(previousX, previousY),
                new Vector2(currentX, currentY),
                new Vector2(),
                new Vector2());
    }

    private static Graphics graphics(int logicalWidth,
                                     int logicalHeight,
                                     int framebufferWidth,
                                     int framebufferHeight) {
        return (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> logicalWidth;
                    case "getHeight" -> logicalHeight;
                    case "getBackBufferWidth" -> framebufferWidth;
                    case "getBackBufferHeight" -> framebufferHeight;
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private record Fixture(OrthographicCamera camera, ScreenViewport viewport) {
    }
}
