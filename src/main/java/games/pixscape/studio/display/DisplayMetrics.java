package games.pixscape.studio.display;

/** Logical window and physical framebuffer dimensions for one desktop window. */
public final class DisplayMetrics {
    private static final float SCALE_EPSILON = 0.0001f;

    private int logicalWidth;
    private int logicalHeight;
    private int framebufferWidth;
    private int framebufferHeight;
    private float scaleX = 1f;
    private float scaleY = 1f;

    public void update(int logicalWidth,
                       int logicalHeight,
                       int framebufferWidth,
                       int framebufferHeight) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;

        if (logicalWidth > 0 && framebufferWidth > 0) {
            scaleX = (float) framebufferWidth / logicalWidth;
        }
        if (logicalHeight > 0 && framebufferHeight > 0) {
            scaleY = (float) framebufferHeight / logicalHeight;
        }
    }

    public int logicalWidth() {
        return logicalWidth;
    }

    public int logicalHeight() {
        return logicalHeight;
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public boolean isHiDpi() {
        return Math.abs(scaleX - 1f) > SCALE_EPSILON
                || Math.abs(scaleY - 1f) > SCALE_EPSILON;
    }
}
