package games.pixscape.studio.helper;

public final class HandleHelper {
    private HandleHelper() {
    }

    public static boolean insideSquare(float px, float py, float cx, float cy, float halfWidthorld) {
        return px >= cx - halfWidthorld && px <= cx + halfWidthorld
                && py >= cy - halfWidthorld && py <= cy + halfWidthorld;
    }

    public static boolean insideCircle(float px, float py, float cx, float cy, float rWorld) {
        float dx = px - cx, dy = py - cy;
        return dx * dx + dy * dy <= rWorld * rWorld;
    }

    public static float dst2(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        return dx * dx + dy * dy;
    }
}
