package games.pixscape.studio.input;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.studio.history.commands.TransformOp;

public final class InputManipulationContext {

    public enum Mode {IDLE, HANDLE_RESIZE, HANDLE_ROTATE}

    public enum Handle {
        NONE,
        N, S, E, W,
        NE, NW, SE, SW,
        ROTATE
    }

    private Mode mode = Mode.IDLE;
    private Handle hovered = Handle.NONE;
    private Handle active = Handle.NONE;

    private final Vector2 dragStartMouse = new Vector2();
    private final Vector2 dragCurrentMouse = new Vector2();

    private float lastMouseX;
    private float lastMouseY;

    // rotation (en RADIANS)
    private float rotateStartAngleRad = 0f; // angle pivot->souris au moment du press
    private float rotateBaseRad = 0f; // entity rotation at drag start

    // scale
    private float scaleXstart = 0f;
    private float scaleYstart = 0f;

    public Mode mode() {
        return mode;
    }

    public Handle hoveredHandle() {
        return hovered;
    }

    public Handle activeHandle() {
        return active;
    }

    public void setHovered(Handle h) {
        this.hovered = (h == null ? Handle.NONE : h);
    }

    public float dragStartMouseX() {
        return dragStartMouse.x;
    }

    public float dragStartMouseY() {
        return dragStartMouse.y;
    }

    // --- SCALE ---

    public void beginResize(Handle h,
                            float mouseWorldX, float mouseWorldY,
                            float sX, float sY) {
        mode = Mode.HANDLE_RESIZE;
        active = (h == null ? Handle.NONE : h);
        dragStartMouse.set(mouseWorldX, mouseWorldY);
        dragCurrentMouse.set(mouseWorldX, mouseWorldY);
        scaleXstart = sX;
        scaleYstart = sY;
    }

    public float scaleXstart() {
        return scaleXstart;
    }

    public float scaleYstart() {
        return scaleYstart;
    }

    // --- ROTATE ---

    /**
     * @param pivotX,pivotY   pivot en monde (centre + origin)
     * @param mouseWorldX/Y   souris en monde au moment du press
     * @param baseRotationRad entity rotation BEFORE drag (radians)
     */
    public void beginRotate(float pivotX, float pivotY,
                            float mouseWorldX, float mouseWorldY,
                            float baseRotationRad) {
        mode = Mode.HANDLE_ROTATE;
        active = Handle.ROTATE;
        dragStartMouse.set(mouseWorldX, mouseWorldY);
        dragCurrentMouse.set(mouseWorldX, mouseWorldY);

        lastMouseX = mouseWorldX;
        lastMouseY = mouseWorldY;

        // pivot -> souris, en radians
        rotateStartAngleRad = (float) Math.atan2(
                mouseWorldY - pivotY,
                mouseWorldX - pivotX
        );
        rotateBaseRad = baseRotationRad;
    }

    public float rotateStartAngleRad() {
        return rotateStartAngleRad;
    }

    public float rotateBaseRad() {
        return rotateBaseRad;
    }

    public float lastMouseX() {
        return lastMouseX;
    }

    public float lastMouseY() {
        return lastMouseY;
    }

    public void setLastMouse(float x, float y) {
        lastMouseX = x;
        lastMouseY = y;
    }

    // --- generic DRAG ---

    public void updateDrag(float mouseWorldX, float mouseWorldY) {
        dragCurrentMouse.set(mouseWorldX, mouseWorldY);
    }

    public boolean isDragging() {
        return mode != Mode.IDLE && active != Handle.NONE;
    }

    public TransformOp currentOp() {
        if (!isDragging()) return null;
        if (mode == Mode.HANDLE_ROTATE) return TransformOp.ROTATE;
        if (mode == Mode.HANDLE_RESIZE) return TransformOp.SCALE;
        return TransformOp.MOVE;
    }

    public void end() {
        mode = Mode.IDLE;
        active = Handle.NONE;
    }
}
