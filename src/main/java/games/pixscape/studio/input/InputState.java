package games.pixscape.studio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

public final class InputState extends InputAdapter {

    private int mouseX, mouseY;
    private boolean leftDown;
    private boolean dragging;
    private boolean dragStarted;
    private boolean leftJustPressed;
    private boolean leftJustReleased;

    // --- current state getters ---
    public int getMouseX() {
        return mouseX;
    }

    public int getMouseY() {
        return mouseY;
    }

    public boolean isLeftDown() {
        return leftDown;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isDragStarted() {
        return dragStarted;
    }

    public boolean isCtrl() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    public boolean isShift() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    public boolean isAlt() {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
    }

    public boolean leftJustPressed() {
        if (leftJustPressed) {
            leftJustPressed = false;
            return true;
        }
        return false;
    }

    /**
     * True only once just after the left button is released.
     */
    public boolean leftJustReleased() {
        if (leftJustReleased) {
            leftJustReleased = false; // consommation du flag
            return true;
        }
        return false;
    }

    public boolean delJustPressed() {
        return Gdx.input.isKeyJustPressed(Input.Keys.DEL);
    }

    /** Drops transient pointer state when the shared canvas detaches from a Scene. */
    public void clearAll() {
        leftDown = false;
        dragging = false;
        dragStarted = false;
        leftJustPressed = false;
        leftJustReleased = false;
    }

    // --- input events ---

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer == 0 && button == Input.Buttons.LEFT) {
            mouseX = screenX;
            mouseY = screenY;
            leftDown = true;
            dragging = false;
            dragStarted = false;
            leftJustPressed = true;
            leftJustReleased = false;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer == 0 && button == Input.Buttons.LEFT) {
            mouseX = screenX;
            mouseY = screenY;
            leftDown = false;
            dragging = false;
            dragStarted = false;
            leftJustReleased = true; // <--- armed here, consumed by leftJustReleased()
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (pointer == 0 && leftDown) {
            mouseX = screenX;
            mouseY = screenY;

            boolean wasDragging = dragging;
            dragging = true;
            dragStarted = !wasDragging;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        mouseX = screenX;
        mouseY = screenY;
        return false;
    }
}
