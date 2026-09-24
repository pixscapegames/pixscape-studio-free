package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.CoordSpaces;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioViewportInputAgreementTest {
    private Graphics previousGraphics;
    private GL20 previousGl;

    @Before public void installLogicalGraphics() {
        GdxNativesLoader.load();
        previousGraphics = Gdx.graphics;
        previousGl = Gdx.gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(), new Class[]{Graphics.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> 1920;
                    case "getHeight", "getBackBufferHeight" -> 1080;
                    case "getDeltaTime" -> 1f / 60f;
                    case "getFramesPerSecond" -> 60;
                    default -> primitiveDefault(method.getReturnType());
                });
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(), new Class[]{GL20.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        Gdx.gl20 = Gdx.gl;
    }

    @After public void restoreGraphics() {
        Gdx.graphics = previousGraphics;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl;
    }

    @Test
    public void centerScreenBoundsRemainTheSingleSceneRenderAndInputCoordinateSpace() {
        ScreenViewport viewport = new ScreenViewport();
        viewport.setScreenBounds(280, 180, 1298, 780);
        viewport.setWorldSize(1298f, 780f);
        viewport.getCamera().position.set(100f, 200f, 0f);
        viewport.getCamera().viewportWidth = 1298f;
        viewport.getCamera().viewportHeight = 780f;
        viewport.getCamera().update();

        assertRoundTrip(viewport, -369f, -190f); // bottom-left of the center viewport
        assertRoundTrip(viewport, 280f, 200f);  // authored camera center remains intact
        assertRoundTrip(viewport, 929f, 590f);  // top-right of the center viewport
    }

    @Test
    public void hudViewportExcludesTheVisibleRulerCellsFromTheCenterStack() {
        Rectangle center = new Rectangle(280f, 180f, 1298f, 780f);

        Rectangle canvas = StudioApplicationAdapter.hudCanvasBounds(center, true, new Rectangle());

        assertEquals(320f, canvas.x, 0.001f);
        assertEquals(180f, canvas.y, 0.001f);
        assertEquals(1258f, canvas.width, 0.001f);
        assertEquals(755f, canvas.height, 0.001f);
        assertEquals(center, StudioApplicationAdapter.hudCanvasBounds(
                center, false, new Rectangle()));
    }

    @Test
    public void stageBoundsRejectChromeAndKeepTouchFocusForDragRelease() {
        ScreenViewport viewport = new ScreenViewport();
        Stage stage = new Stage(viewport, batchProxy());
        try {
            viewport.update(1298, 780, false);
            viewport.setScreenBounds(280, 180, 1298, 780);
            Actor surface = new Actor();
            surface.setBounds(0f, 0f, 1298f, 780f);
            AtomicInteger drags = new AtomicInteger();
            AtomicInteger releases = new AtomicInteger();
            InputState inputState = new InputState();
            surface.addListener(new InputListener() {
                @Override public boolean touchDown(
                        InputEvent event, float x, float y, int pointer, int button) {
                    inputState.touchDown(300, 500, pointer, button);
                    return true;
                }

                @Override public void touchDragged(
                        InputEvent event, float x, float y, int pointer) {
                    inputState.touchDragged(1700, 50, pointer);
                    drags.incrementAndGet();
                }

                @Override public void touchUp(
                        InputEvent event, float x, float y, int pointer, int button) {
                    inputState.touchUp(1700, 50, pointer, button);
                    releases.incrementAndGet();
                }
            });
            stage.addActor(surface);

            assertFalse(stage.touchDown(100, 500, 0, 0)); // Items/chrome, outside center viewport.
            assertFalse(inputState.leftJustPressed());
            assertTrue(stage.touchDown(300, 500, 0, 0));
            assertTrue(inputState.leftJustPressed());
            assertTrue(stage.touchDragged(1700, 50, 0)); // Continues outside through touch focus.
            assertTrue(stage.touchUp(1700, 50, 0, 0));
            assertEquals(1, drags.get());
            assertEquals(1, releases.get());
            assertTrue(inputState.leftJustReleased());
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void coordSpacesRoundTripHonorsNonZeroSmallerSceneViewport() {
        ScreenViewport viewport = viewport(280, 180, 1298, 780, 100f, 200f, 1f);
        CoordSpaces spaces = new CoordSpaces(
                (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera(),
                viewport,
                null,
                null);
        Vector2 screen = spaces.worldToScreen(325f, -40f, new Vector2());
        screen.y = 1080f - screen.y;

        Vector2 restored = spaces.screenToWorld(screen.x, screen.y, new Vector2());

        assertEquals(325f, restored.x, 0.001f);
        assertEquals(-40f, restored.y, 0.001f);
    }

    @Test
    public void pixelToWorldScaleUsesSceneViewportWidth() {
        ScreenViewport viewport = viewport(280, 180, 800, 600, 0f, 0f, 1.5f);
        com.badlogic.gdx.graphics.OrthographicCamera camera =
                (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera();
        StudioDrawContext context = new StudioDrawContext(null, null, camera, viewport);
        CoordSpaces spaces = new CoordSpaces(camera, viewport, null, null);

        assertEquals(1.5f, context.wpp(), 0.001f);
        assertEquals(15f, context.pxToWorld(10f), 0.001f);
        assertEquals(1.5f, spaces.worldUnitsPerPixel(), 0.001f);
    }

    @Test
    public void logicalCoordinateAgreementIsStableAcrossHidpiRatios() {
        for (int framebufferWidth : new int[]{1920, 2880, 3840}) {
            Gdx.graphics = graphics(1920, 1080, framebufferWidth, framebufferWidth * 1080 / 1920);
            ScreenViewport viewport = viewport(280, 180, 1298, 780, 100f, 200f, 1f);
            CoordSpaces spaces = new CoordSpaces(
                    (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera(),
                    viewport,
                    null,
                    null);
            Vector2 screen = spaces.worldToScreen(25f, 75f, new Vector2());
            screen.y = 1080f - screen.y;
            Vector2 restored = spaces.screenToWorld(screen.x, screen.y, new Vector2());
            assertEquals(25f, restored.x, 0.001f);
            assertEquals(75f, restored.y, 0.001f);
        }
    }

    @Test
    public void dndBoundaryUsesTheLogicalSceneViewportRatherThanTheFullWindow() {
        ScreenViewport viewport = viewport(280, 180, 1298, 780, 0f, 0f, 1f);

        assertTrue(WorldCanvas.isInsideSceneViewport(viewport, 1080, 280, 120));
        assertTrue(WorldCanvas.isInsideSceneViewport(viewport, 1080, 1577, 899));
        assertFalse(WorldCanvas.isInsideSceneViewport(viewport, 1080, 279, 500));
        assertFalse(WorldCanvas.isInsideSceneViewport(viewport, 1080, 1578, 500));
        assertFalse(WorldCanvas.isInsideSceneViewport(viewport, 1080, 500, 119));
        assertFalse(WorldCanvas.isInsideSceneViewport(viewport, 1080, 500, 900));
    }

    @Test
    public void drawContextRejectsACameraThatDoesNotOwnTheViewport() {
        ScreenViewport viewport = viewport(280, 180, 800, 600, 0f, 0f, 1f);
        com.badlogic.gdx.graphics.OrthographicCamera viewportCamera =
                (com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera();

        StudioDrawContext context = new StudioDrawContext(
                null, null, viewportCamera, viewport);
        assertEquals(viewportCamera, context.cam);
        assertEquals(viewport, context.viewport);

        try {
            new StudioDrawContext(
                    null,
                    null,
                    new com.badlogic.gdx.graphics.OrthographicCamera(),
                    viewport);
            org.junit.Assert.fail("A mismatched camera/viewport pair must be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("camera"));
        }
    }

    private static ScreenViewport viewport(int x,
                                           int y,
                                           int width,
                                           int height,
                                           float cameraX,
                                           float cameraY,
                                           float zoom) {
        ScreenViewport viewport = new ScreenViewport();
        viewport.update(width, height, false);
        viewport.setScreenBounds(x, y, width, height);
        viewport.getCamera().position.set(cameraX, cameraY, 0f);
        ((com.badlogic.gdx.graphics.OrthographicCamera) viewport.getCamera()).zoom = zoom;
        viewport.getCamera().update();
        return viewport;
    }

    private static void assertRoundTrip(ScreenViewport viewport, float x, float y) {
        Vector2 point = new Vector2(x, y);
        viewport.project(point);
        point.y = 1080f - point.y; // Stage input uses top-left window coordinates.
        viewport.unproject(point);
        assertEquals(x, point.x, 0.001f);
        assertEquals(y, point.y, 0.001f);
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

    private static Batch batchProxy() {
        return (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private static Graphics graphics(int logicalWidth,
                                     int logicalHeight,
                                     int framebufferWidth,
                                     int framebufferHeight) {
        return (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(), new Class[]{Graphics.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth" -> logicalWidth;
                    case "getHeight" -> logicalHeight;
                    case "getBackBufferWidth" -> framebufferWidth;
                    case "getBackBufferHeight" -> framebufferHeight;
                    case "getDeltaTime" -> 1f / 60f;
                    case "getFramesPerSecond" -> 60;
                    default -> primitiveDefault(method.getReturnType());
                });
    }
}
