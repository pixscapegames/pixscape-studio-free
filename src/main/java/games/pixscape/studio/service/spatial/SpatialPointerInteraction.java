package games.pixscape.studio.service.spatial;

/** Screen-space pointer gesture state for Spatial V3 authoring. */
public final class SpatialPointerInteraction {
    public static final float DRAG_THRESHOLD_PX = 5f;

    public enum Target {
        NONE,
        SELECTED_HANDLE,
        SELECTED_FOOTPRINT,
        SELECTED_WALL,
        OTHER_WALL,
        OCCUPIED_TILE
    }

    public enum State {
        IDLE,
        HOVER_TILE,
        PRESSED_WALL,
        PRESSED_HANDLE,
        PRESSED_FOOTPRINT,
        PRESSED_TILE,
        CREATING_WALL_RANGE,
        RESIZING_WALL,
        MOVING_ISOLATED_WALL,
        SLIDING_ATTACHED_WALL
    }

    private State state = State.IDLE;
    private Target target = Target.NONE;
    private float pressScreenX;
    private float pressScreenY;

    public void hover(Target target) {
        if (isPressed() || isDragging()) return;
        this.target = target != null ? target : Target.NONE;
        state = this.target == Target.OCCUPIED_TILE ? State.HOVER_TILE : State.IDLE;
    }

    public void press(Target target, float screenX, float screenY) {
        this.target = target != null ? target : Target.NONE;
        pressScreenX = screenX;
        pressScreenY = screenY;
        switch (this.target) {
            case SELECTED_HANDLE -> state = State.PRESSED_HANDLE;
            case SELECTED_FOOTPRINT -> state = State.PRESSED_FOOTPRINT;
            case SELECTED_WALL, OTHER_WALL -> state = State.PRESSED_WALL;
            case OCCUPIED_TILE -> state = State.PRESSED_TILE;
            default -> state = State.IDLE;
        }
    }

    public boolean crossedDragThreshold(float screenX, float screenY) {
        if (!isPressed()) return false;
        float dx = screenX - pressScreenX;
        float dy = screenY - pressScreenY;
        return dx * dx + dy * dy >= DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX;
    }

    public void beginResize() { state = State.RESIZING_WALL; }
    public void beginMove(boolean attached) {
        state = attached ? State.SLIDING_ATTACHED_WALL : State.MOVING_ISOLATED_WALL;
    }
    public void beginCreation() { state = State.CREATING_WALL_RANGE; }

    public void release() {
        state = State.IDLE;
        target = Target.NONE;
    }

    public State state() { return state; }
    public Target target() { return target; }
    public static boolean clearsWallSelection(Target target) {
        return target == Target.NONE || target == Target.OCCUPIED_TILE;
    }

    public static Target resolveTarget(boolean selectedHandle,
                                       boolean selectedFootprint,
                                       boolean selectedVolume,
                                       boolean otherVolume,
                                       boolean occupiedTile) {
        if (selectedHandle) return Target.SELECTED_HANDLE;
        if (selectedFootprint) return Target.SELECTED_FOOTPRINT;
        if (selectedVolume) return Target.SELECTED_WALL;
        if (otherVolume) return Target.OTHER_WALL;
        return occupiedTile ? Target.OCCUPIED_TILE : Target.NONE;
    }
    public boolean isPressed() {
        return state == State.PRESSED_WALL || state == State.PRESSED_HANDLE
                || state == State.PRESSED_FOOTPRINT || state == State.PRESSED_TILE;
    }
    public boolean isDragging() {
        return state == State.CREATING_WALL_RANGE || state == State.RESIZING_WALL
                || state == State.MOVING_ISOLATED_WALL || state == State.SLIDING_ATTACHED_WALL;
    }
}
