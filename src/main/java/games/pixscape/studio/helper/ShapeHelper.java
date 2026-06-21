package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import space.earlygrey.shapedrawer.ShapeDrawer;

/**
 * Helper ShapeDrawer + drawing utilities.
 * <p>
 * - Provides a shared 1x1 white texture region (lazy).
 * - Provides a convenient ShapeDrawer bound to a Batch.
 * - Provides dashed primitives in world units but stable in screen pixels.
 * <p>
 * IMPORTANT:
 * - Call dispose() on app shutdown to release the GPU texture.
 */
public final class ShapeHelper {

    private ShapeHelper() {
    }

    // -------------------------------------------------------------------------
    // Shared white pixel
    // -------------------------------------------------------------------------

    private static Texture whiteTexture;
    private static TextureRegion whiteRegion;

    public static ShapeDrawer newDrawerFromWhitePixel(Batch batch) {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        Texture tx = new Texture(pm);
        pm.dispose();
        return new ShapeDrawer(batch, new TextureRegion(tx));
    }

    /**
     * Returns a shared 1x1 white region (lazy-created).
     */
    public static TextureRegion whitePixelRegion() {
        if (whiteRegion != null) return whiteRegion;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whiteTexture = new Texture(pm);
        pm.dispose();

        whiteRegion = new TextureRegion(whiteTexture);
        return whiteRegion;
    }

    /**
     * Creates a ShapeDrawer using the shared white pixel.
     * Note: ShapeDrawer is cheap; you can create one per Widget/Screen and just update its batch.
     */
    public static ShapeDrawer newDrawer(Batch batch) {
        return new ShapeDrawer(batch, whitePixelRegion());
    }

    /**
     * Releases the shared white texture (call at shutdown).
     */
    public static void dispose() {
        if (whiteTexture != null) {
            whiteTexture.dispose();
            whiteTexture = null;
            whiteRegion = null;
        }
    }

    // -------------------------------------------------------------------------
    // Pixel-stable thickness helpers
    // -------------------------------------------------------------------------

    public static float worldUnitsPerPixel(OrthographicCamera cam, int screenWidth) {
        return (cam.viewportWidth * cam.zoom) / screenWidth;
    }

    /**
     * Use viewport screenWidth if possible (fallback to Gdx.graphics.getWidth).
     */
    public static int screenWidthFallback() {
        return Gdx.graphics != null ? Gdx.graphics.getWidth() : 1;
    }

    public static void drawDashedLineWorld(
            ShapeDrawer drawer,
            OrthographicCamera cam,
            float thicknessPx,
            float dashPx,
            float gapPx,
            float x1, float y1,
            float x2, float y2,
            int screenWidth
    ) {
        float wpp = worldUnitsPerPixel(cam, screenWidth);
        float thickness = thicknessPx * wpp;
        float dash = dashPx * wpp;
        float gap = gapPx * wpp;

        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len <= 0f) return;

        float ux = dx / len;
        float uy = dy / len;

        float advance = dash + gap;
        float t = 0f;
        while (t < len) {
            float segStart = t;
            float segEnd = Math.min(t + dash, len);

            float sx = x1 + ux * segStart;
            float sy = y1 + uy * segStart;
            float ex = x1 + ux * segEnd;
            float ey = y1 + uy * segEnd;

            drawer.line(sx, sy, ex, ey, thickness);
            t += advance;
        }
    }

    public static void drawDashedRectWorld(
            ShapeDrawer drawer,
            OrthographicCamera cam,
            float thicknessPx,
            float dashPx,
            float gapPx,
            float x0, float y0,
            float x1, float y1,
            int screenWidth
    ) {
        drawDashedLineWorld(drawer, cam, thicknessPx, dashPx, gapPx, x0, y0, x1, y0, screenWidth);
        drawDashedLineWorld(drawer, cam, thicknessPx, dashPx, gapPx, x1, y0, x1, y1, screenWidth);
        drawDashedLineWorld(drawer, cam, thicknessPx, dashPx, gapPx, x1, y1, x0, y1, screenWidth);
        drawDashedLineWorld(drawer, cam, thicknessPx, dashPx, gapPx, x0, y1, x0, y0, screenWidth);
    }

    public static void drawRectWorld(
            ShapeDrawer drawer,
            OrthographicCamera cam,
            float thicknessPx,
            float x0, float y0,
            float x1, float y1,
            int screenWidth
    ) {
        float wpp = worldUnitsPerPixel(cam, screenWidth);
        float thickness = thicknessPx * wpp;

        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float maxY = Math.max(y0, y1);

        drawer.line(minX, minY, maxX, minY, thickness);
        drawer.line(maxX, minY, maxX, maxY, thickness);
        drawer.line(maxX, maxY, minX, maxY, thickness);
        drawer.line(minX, maxY, minX, minY, thickness);
    }

}
