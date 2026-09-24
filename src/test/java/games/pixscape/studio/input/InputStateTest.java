package games.pixscape.studio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputStateTest {
    private Input previousInput;

    @Before
    public void installGloballyPressedInput() {
        previousInput = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(
                Input.class.getClassLoader(),
                new Class[]{Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isButtonJustPressed")) return true;
                    return primitiveDefault(method.getReturnType());
                });
    }

    @After
    public void restoreInput() {
        Gdx.input = previousInput;
    }

    @Test
    public void globalChromePressCannotArmScenePointerState() {
        InputState state = new InputState();

        assertFalse(state.leftJustPressed());
        assertFalse(state.isLeftDown());
        assertFalse(state.leftJustReleased());
    }

    @Test
    public void sceneOwnedPressDragAndReleaseAreTrackedExactlyOnce() {
        InputState state = new InputState();

        state.touchDown(320, 240, 0, Input.Buttons.LEFT);
        assertTrue(state.leftJustPressed());
        assertFalse(state.leftJustPressed());
        assertTrue(state.isLeftDown());
        assertEquals(320, state.getMouseX());
        assertEquals(240, state.getMouseY());

        state.touchDragged(350, 260, 0);
        assertTrue(state.isDragging());
        assertTrue(state.isDragStarted());

        state.touchUp(350, 260, 0, Input.Buttons.LEFT);
        assertFalse(state.isLeftDown());
        assertTrue(state.leftJustReleased());
        assertFalse(state.leftJustReleased());
    }

    @Test
    public void documentDetachClearsEveryTransientPointerFlag() {
        InputState state = new InputState();
        state.touchDown(10, 20, 0, Input.Buttons.LEFT);
        state.touchDragged(20, 30, 0);

        state.clearAll();

        assertFalse(state.isLeftDown());
        assertFalse(state.isDragging());
        assertFalse(state.isDragStarted());
        assertFalse(state.leftJustPressed());
        assertFalse(state.leftJustReleased());
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
}
