package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;

public final class HandleHelper {
    private HandleHelper() {
    }

    public static float worldUnitsPerPixel(OrthographicCamera cam) {
        return (cam.viewportWidth * cam.zoom) / Math.max(1, Gdx.graphics.getWidth());
    }

    public static float pxToWorld(OrthographicCamera cam, float px) {
        return px * worldUnitsPerPixel(cam);
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
