package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DockSplitPaneTest {
    @Parameterized.Parameters(name = "vertical={0}")
    public static List<Object[]> orientations() {
        return List.of(new Object[]{false}, new Object[]{true});
    }

    @Parameterized.Parameter
    public boolean vertical;
    private Stage stage;
    private DockSplitPane split;
    private WidgetGroup first, second;
    private int handleDraws;

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
        Gdx.graphics = new MockGraphics() {
            @Override
            public int getWidth() {
                return 1000;
            }

            @Override
            public int getHeight() {
                return 1000;
            }
        };
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Before
    public void setup() {
        // Public interface double only; no inspection of VisUI's private state.
        Matrix4 matrix = new Matrix4();
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTransformMatrix", "getProjectionMatrix" -> matrix;
                    case "isDrawing", "isBlendingEnabled" -> true;
                    default -> null;
                });
        stage = new Stage(new ScreenViewport(), batch);
        first = content();
        second = content();
        BaseDrawable handle = new BaseDrawable() {
            @Override
            public void draw(Batch b, float x, float y, float w, float h) {
                assertTrue(w >= 0 && h >= 0);
                handleDraws++;
            }
        };
        handle.setMinWidth(10);
        handle.setMinHeight(10);
        split = new DockSplitPane(first, second, vertical, new VisSplitPane.VisSplitPaneStyle(handle, handle));
        split.setBounds(200, 200, 400, 400);
        stage.addActor(split);
        split.validate();
    }

    @After
    public void dispose() {
        stage.dispose();
    }

    @Test
    public void buttonTextAndScrollReachContentWithoutChangingSplit() {
        VisTextButton button = new VisTextButton("Button");
        button.setBounds(10, 10, 120, 30);
        first.addActor(button);
        Vector2 p = screen(button, 20, 15);
        stage.touchDown((int) p.x, (int) p.y, 0, 0);
        stage.touchUp((int) p.x, (int) p.y, 0, 0);
        assertTrue(button.isChecked());
        VisTextField field = new VisTextField("");
        field.setBounds(10, 50, 120, 30);
        first.addActor(field);
        p = screen(field, 20, 15);
        stage.touchDown((int) p.x, (int) p.y, 0, 0);
        stage.touchUp((int) p.x, (int) p.y, 0, 0);
        assertSame(field, stage.getKeyboardFocus());
        stage.keyTyped('x');
        assertEquals("x", field.getText());
        float[] scroll = {0};
        field.addListener(new InputListener() {
            @Override
            public boolean scrolled(InputEvent e, float x, float y, float ax, float ay) {
                scroll[0] += ay;
                return true;
            }
        });
        stage.setScrollFocus(field);
        assertTrue(stage.scrolled(0, 2));
        assertEquals(2, scroll[0], 0);
        assertEquals(.5f, split.getSplit(), 0);
    }

    @Test
    public void emptyContentHitsActorBehindSplit() {
        Actor behind = new Actor();
        behind.setBounds(0, 0, 1000, 1000);
        stage.getRoot().addActorAt(0, behind);
        Vector2 p = first.localToStageCoordinates(new Vector2(20, 20));
        assertSame(behind, stage.hit(p.x, p.y, true));
        assertNull(split.hit(-1, 20, true));
        assertNull(split.hit(split.getWidth(), 20, true));
    }

    @Test
    public void overflowingDescendantCannotStealAnotherPaneOrHandle() {
        Actor overflow = new Actor();
        overflow.setBounds(-1000, -1000, 3000, 3000);
        second.addActor(overflow);
        Vector2 p = first.localToParentCoordinates(new Vector2(20, 20));
        assertNull(split.hit(p.x, p.y, true));
        p = handlePoint();
        assertSame(split, split.hit(p.x, p.y, true));
        p = second.localToParentCoordinates(new Vector2(20, 20));
        assertSame(overflow, split.hit(p.x, p.y, true));
        assertNull(split.hit(-10, 10, true));
    }

    @Test
    public void nestedSplitCannotHitOutsideItsClippedBranch() {
        DockSplitPane nested = new DockSplitPane(content(), content(), !vertical, split.getStyle());
        nested.setBounds(-100, -100, 600, 600);
        second.addActor(nested);
        Vector2 p = first.localToParentCoordinates(new Vector2(20, 20));
        assertNull(split.hit(p.x, p.y, true));
        p = handlePoint();
        assertSame(split, split.hit(p.x, p.y, true));
    }

    @Test
    public void nativeDragReleasesOutsideAndCanStartAgain() {
        Vector2 start = downOnHandle();
        stage.touchDragged((int) start.x + 35, (int) start.y + 35, 0);
        assertTrue(split.getSplit() > .5f);
        stage.touchUp(990, 990, 0, 0);
        assertFalse(split.cancelResize());
        float released = split.getSplit();
        assertFalse(stage.touchDragged(10, 10, 0));
        assertEquals(released, split.getSplit(), 0);
        assertFreshDrag();
    }

    @Test
    public void cancellationKeepsLastRatioAndAllowsNewDrag() {
        assertFalse(split.cancelResize());
        Vector2 start = downOnHandle();
        stage.touchDragged((int) start.x + 30, (int) start.y + 30, 0);
        float ratio = split.getSplit();
        assertTrue(split.cancelResize());
        assertFalse("An already cancelled gesture must not consume another Escape", split.cancelResize());
        assertFalse(stage.touchDragged(10, 10, 0));
        assertEquals(ratio, split.getSplit(), 0);
        assertFreshDrag();
    }

    @Test
    public void contentDragCrossingHandleNeverStartsResize() {
        Actor actor = new Actor();
        actor.setBounds(10, 10, 80, 60);
        first.addActor(actor);
        int[] calls = {0};
        actor.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                return true;
            }

            @Override
            public void touchDragged(InputEvent e, float x, float y, int p) {
                calls[0]++;
            }
        });
        Vector2 start = screen(actor, 20, 20);
        assertTrue(stage.touchDown((int) start.x, (int) start.y, 0, 0));
        assertFalse("Content focus is not a separator gesture", split.cancelResize());
        Vector2 end = screen(split, handlePoint().x, handlePoint().y);
        stage.touchDragged((int) end.x, (int) end.y, 0);
        stage.touchUp((int) end.x, (int) end.y, 0, 0);
        assertEquals(1, calls[0]);
        assertEquals(.5f, split.getSplit(), 0);
    }

    @Test
    public void resizingDuringDragCancelsBeforeNextInputWithoutRequiringLayout() {
        downOnHandle();
        split.setSize(300, 300);
        assertFalse(stage.touchDragged(990, 990, 0));
        assertFalse(split.cancelResize());
        assertEquals(.5f, split.getSplit(), 0);
        assertFreshDrag();
    }

    @Test
    public void insufficientSpaceIsSafeAndRecoversRepeatedly() {
        for (float length : new float[]{0, 1, 9, 10, 10.001f, 11, 20}) {
            split.setSize(400, 400);
            split.setSplitAmount(.37f);
            downOnHandle();
            split.setSize(vertical ? 400 : length, vertical ? length : 400);
            // Touch focus must be gone even before a layout or a draw can execute.
            assertFalse(stage.touchDragged(990, 990, 0));
            split.validate();
            assertFiniteBounds();
            if (length <= 10) {
                int before = handleDraws;
                split.draw(stage.getBatch(), 1);
                assertEquals(before, handleDraws);
                assertNull(split.hit(0, 0, true));
                assertEquals(0, first.getWidth(), 0);
                assertEquals(0, second.getHeight(), 0);
            }
            assertEquals(.37f, split.getSplit(), 0);
            split.setSize(400, 400);
            assertFreshDrag();
        }
    }

    @Test
    public void zeroCrossAxisAlsoCancelsAndRecovers() {
        downOnHandle();
        split.setSize(vertical ? 0 : 400, vertical ? 400 : 0);
        split.validate();
        assertFalse(stage.touchDragged(990, 990, 0));
        assertNull(split.hit(0, 0, true));
        assertFiniteBounds();
        split.setSize(400, 400);
        assertFreshDrag();
    }

    @Test
    public void tinyPositiveSpaceStillUsesNativeDragAndFiniteGeometry() {
        split.setSize(vertical ? 400 : 10.25f, vertical ? 10.25f : 400);
        downOnHandle();
        stage.touchDragged(990, 990, 0);
        stage.touchUp(990, 990, 0, 0);
        split.validate();
        assertFiniteBounds();
        assertTrue(split.getSplit() >= 0 && split.getSplit() <= 1);
    }

    @Test
    public void visibilityAndTouchableModesAreRespected() {
        Vector2 p = handlePoint();
        split.setVisible(false);
        assertNull(split.hit(p.x, p.y, false));
        split.setVisible(true);
        split.setTouchable(Touchable.childrenOnly);
        assertNull(split.hit(p.x, p.y, true));
        assertSame(split, split.hit(p.x, p.y, false));
        split.setTouchable(Touchable.disabled);
        assertNull(split.hit(p.x, p.y, true));
    }

    private static WidgetGroup content() {
        WidgetGroup content = new WidgetGroup();
        content.setTouchable(Touchable.childrenOnly);
        return content;
    }

    private Vector2 handlePoint() {
        split.validate();
        return vertical ? new Vector2(split.getWidth() / 2, split.getSecondWidgetBounds().height + 5)
                : new Vector2(split.getFirstWidgetBounds().width + 5, split.getHeight() / 2);
    }

    private Vector2 screen(Actor actor, float x, float y) {
        return stage.stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(x, y)));
    }

    private Vector2 downOnHandle() {
        Vector2 local = handlePoint();
        Vector2 p = screen(split, local.x, local.y);
        assertTrue(stage.touchDown(Math.round(p.x), Math.round(p.y), 0, 0));
        return p;
    }

    private void assertFreshDrag() {
        split.setSplitAmount(.5f);
        Vector2 p = downOnHandle();
        stage.touchDragged(Math.round(p.x) + 20, Math.round(p.y) + 20, 0);
        stage.touchUp(Math.round(p.x) + 20, Math.round(p.y) + 20, 0, 0);
        assertTrue(split.getSplit() > .5f);
        assertFiniteBounds();
    }

    private void assertFiniteBounds() {
        assertTrue(Float.isFinite(split.getSplit()));
        for (Actor child : split.getChildren()) {
            assertTrue(Float.isFinite(child.getX()) && Float.isFinite(child.getY()));
            assertTrue(Float.isFinite(child.getWidth()) && child.getWidth() >= 0);
            assertTrue(Float.isFinite(child.getHeight()) && child.getHeight() >= 0);
        }
    }
}
