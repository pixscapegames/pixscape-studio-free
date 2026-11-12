package games.pixscape.studio.io;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * Copies validated atlas geometry without blending, scaling or changing transparent RGB.
 */
public final class AtlasRegionPixels {
    private AtlasRegionPixels() {
    }

    public static void copy(Pixmap page, Pixmap target, int left, int top, int width, int height,
                            boolean rotate, int destinationX, int destinationY) {
        target.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                // TexturePacker stores 90-degree regions counterclockwise.
                int sourceX = left + (rotate ? y : x);
                int sourceY = top + (rotate ? width - x - 1 : y);
                target.drawPixel(destinationX + x, destinationY + y, page.getPixel(sourceX, sourceY));
            }
    }
}
