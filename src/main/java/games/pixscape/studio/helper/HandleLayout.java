package games.pixscape.studio.helper;

public final class HandleLayout {
    private HandleLayout() {
    }

    // --- Corners
    public static float swX(float[] c) {
        return c[0];
    }

    public static float swY(float[] c) {
        return c[1];
    }

    public static float seX(float[] c) {
        return c[2];
    }

    public static float seY(float[] c) {
        return c[3];
    }

    public static float neX(float[] c) {
        return c[4];
    }

    public static float neY(float[] c) {
        return c[5];
    }

    public static float nwX(float[] c) {
        return c[6];
    }

    public static float nwY(float[] c) {
        return c[7];
    }

    // --- Edge midpoints
    public static float midSX(float[] c) {
        return (c[0] + c[2]) * 0.5f;
    }

    public static float midSY(float[] c) {
        return (c[1] + c[3]) * 0.5f;
    }

    public static float midEX(float[] c) {
        return (c[2] + c[4]) * 0.5f;
    }

    public static float midEY(float[] c) {
        return (c[3] + c[5]) * 0.5f;
    }

    public static float midNX(float[] c) {
        return (c[4] + c[6]) * 0.5f;
    }

    public static float midNY(float[] c) {
        return (c[5] + c[7]) * 0.5f;
    }

    public static float midWX(float[] c) {
        return (c[6] + c[0]) * 0.5f;
    }

    public static float midWY(float[] c) {
        return (c[7] + c[1]) * 0.5f;
    }

    // --- Center (diag midpoint)
    public static float centerX(float[] c) {
        return (c[0] + c[4]) * 0.5f;
    }

    public static float centerY(float[] c) {
        return (c[1] + c[5]) * 0.5f;
    }

    // --- Rotate handle position (top normal)
    public static void rotateHandle(float[] c, float offsetWorld, float[] out2 /* [rx,ry] */) {
        float mxTop = (c[4] + c[6]) * 0.5f;
        float myTop = (c[5] + c[7]) * 0.5f;

        float cx = (c[0] + c[4]) * 0.5f;
        float cy = (c[1] + c[5]) * 0.5f;

        float nx = mxTop - cx;
        float ny = myTop - cy;

        float nlen = (float) Math.hypot(nx, ny);
        if (nlen > 0f) {
            nx /= nlen;
            ny /= nlen;
        } else {
            nx = 0f;
            ny = 1f;
        }

        out2[0] = mxTop + nx * offsetWorld;
        out2[1] = myTop + ny * offsetWorld;
    }
}
