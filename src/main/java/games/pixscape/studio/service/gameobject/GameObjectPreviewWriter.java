package games.pixscape.studio.service.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

/** Writes the neutral Stage-5A preview used until hierarchy-aware preview instantiation exists. */
public final class GameObjectPreviewWriter {
    private GameObjectPreviewWriter() { }

    public static void writePlaceholder(FileHandle outFile) {
        if (outFile == null) throw new IllegalArgumentException("outFile is null.");
        if (outFile.parent() != null) outFile.parent().mkdirs();
        Pixmap pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();
            pixmap.setColor(0.12f, 0.13f, 0.16f, 1f);
            pixmap.fillRectangle(10, 10, 108, 108);
            pixmap.setColor(0.72f, 0.74f, 0.82f, 1f);
            pixmap.drawRectangle(10, 10, 108, 108);
            pixmap.drawRectangle(11, 11, 106, 106);
            pixmap.setColor(0.35f, 0.65f, 0.95f, 1f);
            drawLine(pixmap, 42, 42, 82, 42);
            drawLine(pixmap, 64, 42, 64, 82);
            drawLine(pixmap, 42, 82, 82, 82);
            drawNode(pixmap, 28, 28, 28, 28);
            drawNode(pixmap, 72, 28, 28, 28);
            drawNode(pixmap, 50, 72, 28, 28);
            pixmap.setColor(0.95f, 0.85f, 0.35f, 1f);
            pixmap.fillCircle(42, 42, 4);
            pixmap.fillCircle(86, 42, 4);
            pixmap.fillCircle(64, 86, 4);
            PixmapIO.writePNG(outFile, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void drawNode(Pixmap pixmap, int x, int y, int w, int h) {
        pixmap.setColor(0.18f, 0.24f, 0.32f, 1f);
        pixmap.fillRectangle(x, y, w, h);
        pixmap.setColor(0.55f, 0.78f, 1f, 1f);
        pixmap.drawRectangle(x, y, w, h);
        pixmap.setColor(0.34f, 0.58f, 0.82f, 1f);
        pixmap.fillRectangle(x + 6, y + 6, w - 12, 5);
        pixmap.fillRectangle(x + 6, y + 16, w - 16, 5);
    }

    private static void drawLine(Pixmap pixmap, int x1, int y1, int x2, int y2) {
        pixmap.drawLine(x1, y1, x2, y2);
        pixmap.drawLine(x1 + 1, y1, x2 + 1, y2);
    }
}
