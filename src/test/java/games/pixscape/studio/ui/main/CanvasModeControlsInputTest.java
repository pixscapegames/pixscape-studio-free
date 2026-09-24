package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.hud.HudCanvasInputHost;
import games.pixscape.studio.ui.hud.HudCanvasStatusOverlay;
import games.pixscape.studio.ui.hud.HudTestInputRouter;
import games.pixscape.studio.ui.widget.CanvasModeControls;
import games.pixscape.studio.ui.widget.CanvasModeIndicator;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class CanvasModeControlsInputTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void layoutKeepsIndicatorNaturalAndRemovesHudActionOutsideHud() throws Exception {
        EventFlow.i().flush();
        try (DockingTestFixture fixture = DockingTestFixture.create()) {
            StudioEditingModeService modes = modeService(fixture);
            CanvasModeControls controls = StudioApplicationAdapter.installCanvasModeControls(
                    fixture.manager.getCenterStack(), modes);
            try {
                CanvasModeIndicator indicator = controls.indicator();
                modes.activateSceneDocument(StudioEditingMode.NORMAL, 1);
                EventFlow.i().flush();
                controls.validate();

                assertEquals(Touchable.disabled, indicator.getTouchable());
                assertNull(controls.hudTestToggle().getParent());
                assertEquals(1, controls.getChildren().size);
                assertEquals(indicator.getPrefWidth(), controls.getPrefWidth(), 0.01f);

                modes.bindHudTestModeController(new FakeHudTestController());
                modes.activateHudDocument(2);
                EventFlow.i().flush();
                controls.validate();

                assertEquals("MODE: HUD", indicator.getDisplayedText());
                assertSame(controls, indicator.getParent());
                assertSame(controls, controls.hudTestToggle().getParent());
                assertEquals(2, controls.getChildren().size);
                assertTrue(controls.getPrefWidth() > indicator.getPrefWidth());
            } finally {
                controls.dispose();
            }
        }
    }

    @Test public void productionCenterLayersRouteStageClickToStableButtonBothWays()
            throws Exception {
        EventFlow.i().flush();
        try (DockingTestFixture fixture = DockingTestFixture.create()) {
            StudioEditingModeService modes = modeService(fixture);
            FakeHudTestController controller = new FakeHudTestController();
            modes.bindHudTestModeController(controller);
            modes.activateHudDocument(1);
            EventFlow.i().flush();

            HudEditorSession session = new HudEditorSession();
            HudCanvasInputHost inputHost = new HudCanvasInputHost(session);
            inputHost.activateHudInput();
            Stack center = fixture.manager.getCenterStack();
            center.add(StudioApplicationAdapter.centerOverlayLayer(inputHost));
            center.add(StudioApplicationAdapter.centerOverlayLayer(
                    new HudCanvasStatusOverlay(session)));
            CanvasModeControls controls = StudioApplicationAdapter.installCanvasModeControls(
                    center, modes);
            try {
                fixture.studioStage.getViewport().update(1200, 800, true);
                fixture.shell.setFillParent(true);
                fixture.studioStage.addActor(fixture.shell);
                fixture.studioStage.act(0f);
                fixture.shell.validate();

                Actor toggle = controls.hudTestToggle();
                assertSame(controls, toggle.getParent());
                assertNotSame(controls.indicator(), toggle.getParent());
                assertTrue(toggle.getWidth() > 0f);
                assertTrue(toggle.getHeight() > 0f);
                assertEquals(Touchable.enabled, toggle.getTouchable());
                assertFalse(controls.hudTestToggle().isDisabled());

                Table modeOverlay = (Table) controls.getParent();
                assertEquals(Touchable.childrenOnly, modeOverlay.getTouchable());
                assertSame(modeOverlay, center.getChildren().peek());

                Vector2 stagePoint = toggle.localToStageCoordinates(
                        new Vector2(toggle.getWidth() * 0.5f, toggle.getHeight() * 0.5f));
                Actor hit = fixture.studioStage.hit(stagePoint.x, stagePoint.y, true);
                assertTrue("Expected the mode toggle or one of its children, got " + hit
                                + " at " + stagePoint + " chain=" + describeChain(toggle),
                        isDescendantOrSelf(hit, toggle));
                assertTrue(inputHost.getTouchable() == Touchable.enabled);
                Vector2 emptyCanvasPoint = center.localToStageCoordinates(
                        new Vector2(center.getWidth() * 0.5f, center.getHeight() * 0.5f));
                assertSame("Empty mode-overlay space must pass through to the HUD canvas",
                        inputHost, fixture.studioStage.hit(
                                emptyCanvasPoint.x, emptyCanvasPoint.y, true));

                Vector2 screenPoint = fixture.studioStage.stageToScreenCoordinates(
                        new Vector2(stagePoint));
                InputMultiplexer processors = new InputMultiplexer(
                        new HudTestInputRouter(session, fixture.studioStage), fixture.studioStage);

                assertTrue(processors.touchDown(Math.round(screenPoint.x), Math.round(screenPoint.y),
                        0, Input.Buttons.LEFT));
                assertSame(toggle, controls.hudTestToggle());
                assertTrue(processors.touchUp(Math.round(screenPoint.x), Math.round(screenPoint.y),
                        0, Input.Buttons.LEFT));
                assertTrue(modes.isHudTestMode());
                assertEquals("Switch to EDIT mode", controls.hudTestToggle().getText().toString());

                assertTrue(processors.touchDown(Math.round(screenPoint.x), Math.round(screenPoint.y),
                        0, Input.Buttons.LEFT));
                assertSame(toggle, controls.hudTestToggle());
                assertTrue(processors.touchUp(Math.round(screenPoint.x), Math.round(screenPoint.y),
                        0, Input.Buttons.LEFT));
                assertFalse(modes.isHudTestMode());
                assertEquals("Switch to TEST mode", controls.hudTestToggle().getText().toString());
                assertEquals(1, controller.entries);
                assertEquals(1, controller.exits);
            } finally {
                controls.dispose();
                session.dispose();
            }
        }
    }

    @Test public void rulerOverlayRebuildReusesTheSameButtonAndRestoresItsHitPriority()
            throws Exception {
        EventFlow.i().flush();
        try (DockingTestFixture fixture = DockingTestFixture.create()) {
            StudioEditingModeService modes = modeService(fixture);
            modes.bindHudTestModeController(new FakeHudTestController());
            modes.activateHudDocument(1);
            EventFlow.i().flush();
            CanvasModeControls controls = StudioApplicationAdapter.installCanvasModeControls(
                    fixture.manager.getCenterStack(), modes);
            try {
                Actor button = controls.hudTestToggle();
                fixture.manager.setRulersVisible(false);
                assertNotSame(controls, controls.indicator().getParent());

                StudioApplicationAdapter.restoreCanvasModeControls(controls);

                assertSame(button, controls.hudTestToggle());
                assertSame(controls, controls.indicator().getParent());
                assertSame(controls.getParent(),
                        fixture.manager.getCenterStack().getChildren().peek());
            } finally {
                controls.dispose();
            }
        }
    }

    @Test public void worldEditorReceivesNoInputInTestAndResumesInEdit() throws Exception {
        try (DockingTestFixture fixture = DockingTestFixture.create()) {
            fixture.studioStage.getViewport().update(16, 16, true);
            fixture.floatingStage.getViewport().update(16, 16, true);
            Actor editor = new Actor();
            editor.setBounds(0f, 0f, 16f, 16f);
            int[] editorDowns = {0};
            editor.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int button) {
                    editorDowns[0]++;
                    return true;
                }
            });
            fixture.floatingStage.addActor(editor);
            InputMultiplexer inputs = new InputMultiplexer(
                    new HudTestInputRouter(new HudEditorSession(), fixture.studioStage),
                    fixture.studioStage, fixture.floatingStage);
            Vector2 screen = fixture.floatingStage.stageToScreenCoordinates(new Vector2(8f, 8f));
            int screenX = Math.round(screen.x);
            int screenY = Math.round(screen.y);

            StudioApplicationAdapter.setWorldInputEnabled(inputs, fixture.floatingStage, false);
            assertFalse(inputs.touchDown(screenX, screenY, 0, Input.Buttons.LEFT));
            assertEquals(0, editorDowns[0]);

            StudioApplicationAdapter.setWorldInputEnabled(inputs, fixture.floatingStage, true);
            assertTrue(inputs.touchDown(screenX, screenY, 0, Input.Buttons.LEFT));
            assertEquals(1, editorDowns[0]);
        }
    }

    private static StudioEditingModeService modeService(DockingTestFixture fixture)
            throws ReflectiveOperationException {
        CanvasModeIndicator indicator = DockingTestFixture.field(
                fixture.manager, "modeIndicator", CanvasModeIndicator.class);
        return DockingTestFixture.field(
                indicator, "modeService", StudioEditingModeService.class);
    }

    private static boolean isDescendantOrSelf(Actor actor, Actor ancestor) {
        for (Actor current = actor; current != null; current = current.getParent()) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private static String describeChain(Actor actor) {
        StringBuilder out = new StringBuilder();
        for (Actor current = actor; current != null; current = current.getParent()) {
            if (!out.isEmpty()) out.append(" <- ");
            out.append(current.getClass().getSimpleName())
                    .append('[').append(current.getX()).append(',').append(current.getY())
                    .append(' ').append(current.getWidth()).append('x').append(current.getHeight())
                    .append(" touch=").append(current.getTouchable())
                    .append(" visible=").append(current.isVisible()).append(']');
        }
        return out.toString();
    }

    private static final class FakeHudTestController
            implements StudioEditingModeService.HudTestModeController {
        private boolean active;
        private int entries;
        private int exits;
        @Override public boolean canEnter() { return true; }
        @Override public boolean enter() { active = true; entries++; return true; }
        @Override public void exit() { active = false; exits++; }
        @Override public boolean isActive() { return active; }
    }
}
