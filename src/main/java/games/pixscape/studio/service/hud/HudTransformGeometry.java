package games.pixscape.studio.service.hud;

/** Pure overlay-local geometry for a conventional move/resize transform gizmo. */
public final class HudTransformGeometry {
    public static final float MINIMUM_SIZE = 1f;

    public record Bounds(float x, float y, float width, float height) {
        public Bounds {
            if (width < 0f || height < 0f) throw new IllegalArgumentException("Bounds must not be negative.");
        }
        public float right() { return x + width; }
        public float top() { return y + height; }
        public boolean contains(float pointX, float pointY) {
            return pointX >= x && pointX <= right() && pointY >= y && pointY <= top();
        }
    }

    private HudTransformGeometry() {}

    public static Bounds move(Bounds initial, float deltaX, float deltaY) {
        return new Bounds(initial.x + deltaX, initial.y + deltaY, initial.width, initial.height);
    }

    /** Keeps a freely authored element fully inside the HUD reference rectangle. */
    public static Bounds clampToSurface(Bounds bounds, float surfaceWidth, float surfaceHeight) {
        float width = Math.min(bounds.width, surfaceWidth);
        float height = Math.min(bounds.height, surfaceHeight);
        float x = Math.max(0f, Math.min(bounds.x, surfaceWidth - width));
        float y = Math.max(0f, Math.min(bounds.y, surfaceHeight - height));
        return new Bounds(x, y, width, height);
    }

    /** Resizes in local coordinates while deliberately keeping the opposite edge fixed. */
    public static Bounds resize(Bounds initial, HudTransformHandle handle,
                                float deltaX, float deltaY) {
        if (handle == null) throw new IllegalArgumentException("Transform handle is required.");
        float left = initial.x;
        float right = initial.right();
        float bottom = initial.y;
        float top = initial.top();
        if (handle.movesWest()) left = Math.min(left + deltaX, right - MINIMUM_SIZE);
        if (handle.movesEast()) right = Math.max(right + deltaX, left + MINIMUM_SIZE);
        if (handle.movesSouth()) bottom = Math.min(bottom + deltaY, top - MINIMUM_SIZE);
        if (handle.movesNorth()) top = Math.max(top + deltaY, bottom + MINIMUM_SIZE);
        return new Bounds(left, bottom, right - left, top - bottom);
    }

    public static Bounds handleBounds(Bounds bounds, HudTransformHandle handle, float size) {
        if (size <= 0f) throw new IllegalArgumentException("Handle size must be positive.");
        float centerX = handle.movesWest() ? bounds.x
                : handle.movesEast() ? bounds.right() : bounds.x + bounds.width / 2f;
        float centerY = handle.movesSouth() ? bounds.y
                : handle.movesNorth() ? bounds.top() : bounds.y + bounds.height / 2f;
        float half = size / 2f;
        return new Bounds(centerX - half, centerY - half, size, size);
    }

    /** Corners deliberately win where their squares overlap an edge handle. */
    public static HudTransformHandle handleAt(Bounds bounds, float pointX, float pointY,
                                              float handleSize) {
        for (HudTransformHandle handle : HudTransformHandle.values()) {
            if (handleBounds(bounds, handle, handleSize).contains(pointX, pointY)) return handle;
        }
        return null;
    }
}
