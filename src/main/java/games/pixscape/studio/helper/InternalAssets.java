package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.StreamUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class InternalAssets {

    private InternalAssets() {
    }

    // Reserved name (unlikely) + stable
    public static final String WHITE_PIXEL_FILE = "__ps_internal_white_px.png";
    public static final String WHITE_PIXEL_REGION = "__pixscape_internal__/__ps_internal_white_px"; // regionName in the atlas (without extension)

    /**
     * ~/.pixscape-studio/internal
     */
    public static Path internalDir() {
        return Paths.get(System.getProperty("user.home"), ".pixscape-studio", "internal");
    }

    /**
     * ~/.pixscape-studio/internal/__ps_internal_white_px.png
     */
    public static Path whitePixelPngPath() {
        return internalDir().resolve(WHITE_PIXEL_FILE);
    }

    /**
     * Creates the white 1x1 RGBA8888 PNG if missing.
     */
    public static void ensureWhitePixelPngExists() {
        Path path = whitePixelPngPath();
        if (Files.exists(path)) return;

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            Gdx.app.error("InternalAssets", "Failed to create internal dir: " + path.getParent(), e);
            return;
        }

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fill();

        // FileHandle via absolute path, puis PixmapIO.writePNG
        FileHandle fh = Gdx.files.absolute(path.toAbsolutePath().toString());
        try {
            // PixmapIO is in com.badlogic.gdx.graphics.PixmapIO
            com.badlogic.gdx.graphics.PixmapIO.writePNG(fh, pm);
        } catch (Exception e) {
            Gdx.app.error("InternalAssets", "Failed to write white pixel png: " + path, e);
        } finally {
            pm.dispose();
        }
    }

    /**
     * Copies the internal white pixel to a destination (overwrite).
     */
    public static void copyWhitePixelTo(FileHandle dst) {
        ensureWhitePixelPngExists();
        FileHandle src = Gdx.files.absolute(whitePixelPngPath().toAbsolutePath().toString());
        if (!src.exists()) return;

        // Ensure parent dir
        dst.parent().mkdirs();

        // Copie binaire
        OutputStream out = null;
        try {
            out = dst.write(false);
            StreamUtils.copyStream(src.read(), out);
        } catch (Exception e) {
            Gdx.app.error("InternalAssets", "Failed to copy white pixel to: " + dst.path(), e);
        } finally {
            StreamUtils.closeQuietly(out);
        }
    }
}
