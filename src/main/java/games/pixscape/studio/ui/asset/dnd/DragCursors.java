package games.pixscape.studio.ui.asset.dnd;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;

public final class DragCursors {
    private DragCursors() {
    }

    public static Cursor makeGhostCursor(Pixmap src, int hotspotX, int hotspotY) {
        if (src == null) return null;

        int target = chooseCursorSize(src.getWidth(), src.getHeight()); // 16/32/64

        // Canvas target transparent
        Pixmap out = new Pixmap(target, target, Pixmap.Format.RGBA8888);
        out.setColor(0, 0, 0, 0);
        out.fill();

        // Fit proportionnel
        float scale = Math.min((float) target / src.getWidth(), (float) target / src.getHeight());
        int w = Math.max(1, Math.round(src.getWidth() * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        int x = (target - w) / 2;
        int y = (target - h) / 2;

        Pixmap.Blending old = out.getBlending();
        out.setBlending(Pixmap.Blending.None);
        out.drawPixmap(src, 0, 0, src.getWidth(), src.getHeight(), x, y, w, h);
        reduceAlpha(out, 0.5f);
        out.setBlending(old);

        int hx = Math.max(0, Math.min(x + Math.round(hotspotX * scale), target - 1));
        int hy = Math.max(0, Math.min(y + Math.round(hotspotY * scale), target - 1));

        Cursor c = Gdx.graphics.newCursor(out, hx, hy);
        out.dispose();

        return c;
    }

    public static Cursor makeForbiddenCursor() {
        Pixmap pixmap = makeForbiddenPixmap();
        if (pixmap == null) return null;

        Cursor cursor = Gdx.graphics.newCursor(pixmap, 6, 6);
        pixmap.dispose();
        return cursor;
    }

    private static int chooseCursorSize(int w, int h) {
        int max = Math.max(w, h);
        if (max <= 16) return 16;
        if (max <= 32) return 32;
        return 64;
    }

    private static Pixmap makeForbiddenPixmap() {
        try {
            Drawable drawable = VisUI.getSkin().getDrawable("forbidden");
            if (drawable instanceof TextureRegionDrawable textureRegionDrawable) {
                Pixmap pixmap = copyRegion(textureRegionDrawable.getRegion());
                if (pixmap != null) {
                    return pixmap;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to a generated cursor if the skin is not ready.
        }

        return makeFallbackForbiddenPixmap();
    }

    private static Pixmap copyRegion(TextureRegion region) {
        if (region == null || region.getTexture() == null) {
            return null;
        }

        TextureData data = region.getTexture().getTextureData();
        if (data == null) {
            return null;
        }

        if (!data.isPrepared()) {
            data.prepare();
        }

        Pixmap source = data.consumePixmap();
        Pixmap out = new Pixmap(region.getRegionWidth(), region.getRegionHeight(), Pixmap.Format.RGBA8888);
        out.setColor(0f, 0f, 0f, 0f);
        out.fill();
        out.drawPixmap(
                source,
                region.getRegionX(),
                region.getRegionY(),
                region.getRegionWidth(),
                region.getRegionHeight(),
                0,
                0,
                region.getRegionWidth(),
                region.getRegionHeight()
        );

        if (data.disposePixmap()) {
            source.dispose();
        }

        return out;
    }

    private static Pixmap makeFallbackForbiddenPixmap() {
        Pixmap pm = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pm.setColor(0f, 0f, 0f, 0f);
        pm.fill();
        pm.setColor(0.9f, 0.05f, 0.08f, 1f);
        pm.drawCircle(16, 16, 13);
        pm.drawCircle(16, 16, 12);
        for (int i = 0; i < 4; i++) {
            pm.drawLine(7 + i, 23, 23 + i, 7);
        }
        return pm;
    }

    private static void reduceAlpha(Pixmap pm, float factor) {
        int w = pm.getWidth();
        int h = pm.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = pm.getPixel(x, y);

                int r = (rgba >>> 24) & 0xff;
                int g = (rgba >>> 16) & 0xff;
                int b = (rgba >>> 8) & 0xff;
                int a = rgba & 0xff;

                a = Math.round(a * factor);

                pm.drawPixel(x, y,
                        (r << 24) |
                                (g << 16) |
                                (b << 8) |
                                a);
            }
        }
    }
}
