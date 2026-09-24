package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class PreviewInputAdapterTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void nativeHudKeyboardFocusCancelsHeldCameraCommandsUntilANewKeyDown() {
        Input previousInput = Gdx.input;
        Set<Integer> physicallyPressed = new HashSet<>();
        Gdx.input = input(physicallyPressed);
        Stage hudStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            TextField field = new TextField("", VisUI.getSkin().get(
                    "default", TextField.TextFieldStyle.class));
            hudStage.addActor(field);
            PreviewWindow.PreviewInputAdapter preview =
                    new PreviewWindow.PreviewInputAdapter();
            InputMultiplexer routing = new InputMultiplexer(hudStage, preview);

            physicallyPressed.add(Input.Keys.LEFT);
            assertTrue(routing.keyDown(Input.Keys.LEFT));
            assertTrue(preview.isPressed(Input.Keys.LEFT));

            hudStage.setKeyboardFocus(field);
            assertSame(field, hudStage.getKeyboardFocus());
            preview.synchronizeHudKeyboardFocus(hudStage.getKeyboardFocus() != null);
            assertFalse(preview.isPressed(Input.Keys.LEFT));

            hudStage.setKeyboardFocus(null);
            preview.synchronizeHudKeyboardFocus(hudStage.getKeyboardFocus() != null);
            assertFalse(preview.isPressed(Input.Keys.LEFT));

            hudStage.setKeyboardFocus(field);
            physicallyPressed.remove(Input.Keys.LEFT);
            assertTrue(routing.keyUp(Input.Keys.LEFT));
            hudStage.setKeyboardFocus(null);
            physicallyPressed.add(Input.Keys.LEFT);
            assertTrue(routing.keyDown(Input.Keys.LEFT));
            assertTrue(preview.isPressed(Input.Keys.LEFT));

            preview.keyUp(Input.Keys.LEFT);
            hudStage.setKeyboardFocus(field);
            physicallyPressed.add(Input.Keys.RIGHT);
            assertTrue(routing.keyDown(Input.Keys.RIGHT));
            preview.synchronizeHudKeyboardFocus(hudStage.getKeyboardFocus() != null);
            hudStage.setKeyboardFocus(null);
            assertFalse(preview.isPressed(Input.Keys.RIGHT));
        } finally {
            hudStage.dispose();
            Gdx.input = previousInput;
        }
    }

    @Test
    public void nativeSliderOwnsPreviewPointerUntilReleaseOutsideAndDisabledFallsThrough() {
        Input previousInput = Gdx.input;
        Gdx.input = input(new HashSet<>());
        Stage hudStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            hudStage.getViewport().update(300, 200, true);
            BaseDrawable background = new BaseDrawable();
            background.setMinHeight(6f);
            BaseDrawable knob = new BaseDrawable();
            knob.setMinWidth(16f);
            knob.setMinHeight(16f);
            Slider slider = new Slider(0f, 100f, 1f, false,
                    new Slider.SliderStyle(background, knob));
            slider.setBounds(20f, 80f, 200f, 30f);
            final int[] changes = {0};
            slider.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) { changes[0]++; }
            });
            hudStage.addActor(slider);
            final int[] lowerPointerCalls = {0};
            InputAdapter lower = new InputAdapter() {
                @Override public boolean touchDown(int x, int y, int pointer, int button) {
                    lowerPointerCalls[0]++;
                    return true;
                }
                @Override public boolean touchDragged(int x, int y, int pointer) {
                    lowerPointerCalls[0]++;
                    return true;
                }
                @Override public boolean touchUp(int x, int y, int pointer, int button) {
                    lowerPointerCalls[0]++;
                    return true;
                }
            };
            InputMultiplexer routing = new InputMultiplexer(hudStage, lower);
            Vector2 inside = hudStage.stageToScreenCoordinates(
                    slider.localToStageCoordinates(new Vector2(20f, 15f)));

            assertTrue(routing.touchDown(Math.round(inside.x), Math.round(inside.y),
                    0, Input.Buttons.LEFT));
            assertTrue(slider.isDragging());
            assertTrue(routing.touchDragged(290, 10, 0));
            assertTrue(routing.touchUp(290, 10, 0, Input.Buttons.LEFT));
            assertFalse(slider.isDragging());
            assertEquals(0, lowerPointerCalls[0]);
            assertTrue(changes[0] > 0);

            slider.setDisabled(true);
            assertTrue(routing.touchDown(Math.round(inside.x), Math.round(inside.y),
                    0, Input.Buttons.LEFT));
            assertFalse(slider.isDragging());
            assertEquals(1, lowerPointerCalls[0]);
        } finally {
            hudStage.dispose();
            Gdx.input = previousInput;
        }
    }

    private static Input input(Set<Integer> physicallyPressed) {
        return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> {
                    if ("isKeyPressed".equals(method.getName())) {
                        return physicallyPressed.contains((Integer) args[0]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
