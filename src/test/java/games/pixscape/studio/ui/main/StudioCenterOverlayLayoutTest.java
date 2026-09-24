package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.studio.ui.hud.HudCanvasInputHost;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class StudioCenterOverlayLayoutTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void hudLayersFillCenterWithoutForcingDockPanelsOffscreen() {
        Actor inputHost = new Actor();
        inputHost.setTouchable(Touchable.enabled);
        VisTable statusOverlay = new VisTable();
        statusOverlay.setTouchable(Touchable.disabled);
        statusOverlay.add().width(520f).pad(24f);

        Stack center = new Stack();
        center.add(StudioApplicationAdapter.centerOverlayLayer(inputHost));
        center.add(StudioApplicationAdapter.centerOverlayLayer(statusOverlay));

        VisTable root = new VisTable();
        Actor leftDock = new Actor();
        Actor rightDock = new Actor();
        root.add(leftDock).width(280f).growY();
        root.add(center).grow();
        root.add(rightDock).width(342f).growY();

        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.addActor(root);
            // A non-Layout transparent input Actor reports its current size as its default maximum.
            // Exercise both growth transitions: large -> small alone would not expose the retained maximum.
            assertHostFillsCenterAfterResize(root, center, inputHost, statusOverlay, rightDock,
                    stage, 800f, 600f);
            assertHostFillsCenterAfterResize(root, center, inputHost, statusOverlay, rightDock,
                    stage, 1920f, 1000f);
            assertHostFillsCenterAfterResize(root, center, inputHost, statusOverlay, rightDock,
                    stage, 720f, 540f);
            assertHostFillsCenterAfterResize(root, center, inputHost, statusOverlay, rightDock,
                    stage, 1920f, 1000f);
        } finally {
            stage.dispose();
        }
    }

    @Test public void hudDropObstructionAllowsOnlyTheCanvasInputSurface() {
        HudCanvasInputHost inputHost = new HudCanvasInputHost(
                new games.pixscape.studio.service.hud.HudEditorSession());

        assertTrue(StudioApplicationAdapter.isHudDropUiHitAllowed(null, inputHost));
        assertTrue(StudioApplicationAdapter.isHudDropUiHitAllowed(inputHost, inputHost));
        assertFalse(StudioApplicationAdapter.isHudDropUiHitAllowed(new Actor(), inputHost));
    }

    private static void assertHostFillsCenterAfterResize(VisTable root, Stack center, Actor inputHost,
                                                           VisTable statusOverlay, Actor rightDock,
                                                           Stage stage, float width, float height) {
        root.setSize(width, height);
        validate(root);

        assertEquals(center.getWidth(), inputHost.getWidth(), 0.01f);
        assertEquals(center.getHeight(), inputHost.getHeight(), 0.01f);
        assertEquals(center.getWidth(), statusOverlay.getWidth(), 0.01f);
        assertTrue(rightDock.getX() + rightDock.getWidth() <= root.getWidth() + 0.01f);

        Vector2 centerLower = center.localToStageCoordinates(new Vector2(0f, 0f));
        Vector2 centerUpper = center.localToStageCoordinates(new Vector2(center.getWidth(), center.getHeight()));
        Vector2 hostLower = inputHost.localToStageCoordinates(new Vector2(0f, 0f));
        Vector2 hostUpper = inputHost.localToStageCoordinates(
                new Vector2(inputHost.getWidth(), inputHost.getHeight()));
        assertEquals(centerLower.x, hostLower.x, 0.01f);
        assertEquals(centerLower.y, hostLower.y, 0.01f);
        assertEquals(centerUpper.x, hostUpper.x, 0.01f);
        assertEquals(centerUpper.y, hostUpper.y, 0.01f);
        assertSame(inputHost, stage.hit(hostLower.x + 0.1f,
                hostLower.y + inputHost.getHeight() * 0.5f, true));
        assertSame(inputHost, stage.hit(hostUpper.x - 0.1f,
                hostLower.y + inputHost.getHeight() * 0.5f, true));
    }

    private static void validate(VisTable table) {
        table.invalidateHierarchy();
        table.validate();
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }
}
