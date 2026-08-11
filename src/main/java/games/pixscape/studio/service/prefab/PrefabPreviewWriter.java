package games.pixscape.studio.service.prefab;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;

import java.util.Comparator;

public final class PrefabPreviewWriter {
    private static final boolean DEBUG = Boolean.getBoolean("pixscape.debug.prefabPreview");
    private static final int PREVIEW_SIZE = 256;
    private static final float PADDING_PX = 18f;

    private PrefabPreviewWriter() {
    }

    public static void writePrefabPreview(FileHandle outFile, ProjectConfig cfg, EntityGraph graph) {
        if (outFile == null) throw new IllegalArgumentException("outFile is null.");
        if (cfg == null || graph == null || graph.isEmpty()) {
            if (Gdx.app != null) {
                Gdx.app.log("PrefabPreviewWriter", "Graph invalid or empty; using placeholder preview.");
            }
            writePlaceholder(outFile);
            return;
        }

        try {
            AssetMetaDatabase db = AssetMetaDatabase.load(StudioFs.requireAssetsFile(cfg));
            Array<PreviewSprite> sprites = collectSprites(cfg, db, graph);
            if (sprites.size == 0) {
                if (Gdx.app != null) {
                    Gdx.app.log("PrefabPreviewWriter", "No visual sprites resolved; using placeholder preview.");
                }
                writePlaceholder(outFile);
                return;
            }
            renderToFile(outFile, sprites);
        } catch (RuntimeException ex) {
            if (Gdx.app != null) {
                Gdx.app.error("PrefabPreviewWriter", "Failed to render prefab preview, using placeholder.", ex);
            }
            writePlaceholder(outFile);
        }
    }

    public static void writePlaceholder(FileHandle outFile) {
        if (outFile == null) {
            throw new IllegalArgumentException("outFile is null.");
        }
        if (outFile.parent() != null) {
            outFile.parent().mkdirs();
        }
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

    private static Array<PreviewSprite> collectSprites(ProjectConfig cfg, AssetMetaDatabase db, EntityGraph graph) {
        Array<PreviewSprite> out = new Array<>();
        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntityInitializer.PreviewVisualData visual = entry.initializer().toPreviewVisualData();
            if (!visual.hasTransform) {
                debugSkip("missing transform", visual, null);
                continue;
            }
            if (!visual.hasDimensions) {
                debugSkip("missing dimensions", visual, null);
                continue;
            }
            if (visual.width <= 0f || visual.height <= 0f) {
                debugSkip("non-positive dimensions", visual, null);
                continue;
            }
            if (visual.hasPhysicsJoint) {
                debugSkip("physics joint", visual, null);
                continue;
            }
            if (visual.hasPointLight || visual.hasConeLight) {
                debugSkip("light entity", visual, null);
                continue;
            }

            FileHandle image = resolveImageFile(cfg, db, visual);
            if (image == null || !image.exists()) {
                debugSkip("no image resolved", visual, image);
                continue;
            }

            PreviewSprite s = new PreviewSprite();
            s.imageFile = image;
            s.zIndex = visual.hasEntityIndex ? visual.zIndex : 0;
            s.x = visual.x;
            s.y = visual.y;
            s.rotationRad = visual.rotationRad;
            s.scaleX = visual.scaleX;
            s.scaleY = visual.scaleY;
            s.originX = visual.originX;
            s.originY = visual.originY;
            s.width = visual.width;
            s.height = visual.height;
            out.add(s);
        }
        out.sort(Comparator.comparingInt(a -> a.zIndex));
        return out;
    }

    private static FileHandle resolveImageFile(ProjectConfig cfg, AssetMetaDatabase db, GenericEntityInitializer.PreviewVisualData visual) {
        if (visual.hasAnimation && visual.hasAssetRef && visual.assetRefAssetId > 0) {
            AssetMeta meta = db.findById(visual.assetRefAssetId);
            if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) return null;
            FileHandle dir = StudioFs.requireStudioProjectDir(cfg).child(meta.sourceRelPath());
            if (!dir.exists() || !dir.isDirectory()) return null;
            FileHandle[] files = dir.list((d, name) -> StudioFs.isImageFile(name));
            if (files == null || files.length == 0) return null;
            java.util.Arrays.sort(files, Comparator.comparing(FileHandle::name));
            return files[0];
        }
        if (!visual.hasAssetRef || visual.assetRefAssetId <= 0) return null;
        AssetMeta meta = db.findById(visual.assetRefAssetId);
        if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) return null;
        return StudioFs.requireStudioProjectDir(cfg).child(meta.sourceRelPath());
    }

    private static void renderToFile(FileHandle outFile, Array<PreviewSprite> sprites) {
        if (outFile.parent() != null) outFile.parent().mkdirs();

        Array<Texture> textures = new Array<>();
        SpriteBatch batch = new SpriteBatch();
        FrameBuffer fbo = new FrameBuffer(Pixmap.Format.RGBA8888, PREVIEW_SIZE, PREVIEW_SIZE, false);
        OrthographicCamera cam = new OrthographicCamera(PREVIEW_SIZE, PREVIEW_SIZE);
        cam.position.set(PREVIEW_SIZE * 0.5f, PREVIEW_SIZE * 0.5f, 0f);
        cam.update();

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

        boolean fboBegun = false;
        boolean batchBegun = false;
        try {
            for (PreviewSprite s : sprites) {
                Texture t = new Texture(s.imageFile);
                textures.add(t);
                s.texture = t;
                float[] b = transformedBounds(s);
                minX = Math.min(minX, b[0]);
                minY = Math.min(minY, b[1]);
                maxX = Math.max(maxX, b[2]);
                maxY = Math.max(maxY, b[3]);
            }
            float worldW = Math.max(1f, maxX - minX);
            float worldH = Math.max(1f, maxY - minY);
            float scale = Math.min((PREVIEW_SIZE - 2f * PADDING_PX) / worldW, (PREVIEW_SIZE - 2f * PADDING_PX) / worldH);
            float centerX = (minX + maxX) * 0.5f;
            float centerY = (minY + maxY) * 0.5f;

            fbo.begin();
            fboBegun = true;
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setProjectionMatrix(cam.combined);
            batch.begin();
            batchBegun = true;
            for (PreviewSprite s : sprites) {
                float drawX = (s.x - centerX) * scale + PREVIEW_SIZE * 0.5f;
                float drawY = (s.y - centerY) * scale + PREVIEW_SIZE * 0.5f;
                batch.draw(s.texture,
                        drawX - s.originX * scale,
                        drawY - s.originY * scale,
                        s.originX * scale,
                        s.originY * scale,
                        s.width * scale,
                        s.height * scale,
                        s.scaleX,
                        s.scaleY,
                        s.rotationRad * MathUtils.radiansToDegrees,
                        0, 0,
                        s.texture.getWidth(), s.texture.getHeight(),
                        false, false);
            }
            batch.end();
            batchBegun = false;

            Pixmap captured = ScreenUtils.getFrameBufferPixmap(0, 0, PREVIEW_SIZE, PREVIEW_SIZE);
            fbo.end();
            fboBegun = false;
            Pixmap flipped = null;
            try {
                flipped = flipY(captured);
                PixmapIO.writePNG(outFile, flipped);
            } finally {
                captured.dispose();
                if (flipped != null) flipped.dispose();
            }
        } finally {
            if (batchBegun) batch.end();
            if (fboBegun) fbo.end();
            for (Texture t : textures) t.dispose();
            batch.dispose();
            fbo.dispose();
        }
    }

    private static Pixmap flipY(Pixmap src) {
        Pixmap out = new Pixmap(src.getWidth(), src.getHeight(), src.getFormat());
        for (int y = 0; y < src.getHeight(); y++) {
            int srcY = src.getHeight() - 1 - y;
            out.drawPixmap(src, 0, srcY, src.getWidth(), 1, 0, y, src.getWidth(), 1);
        }
        return out;
    }

    private static void debugSkip(String reason, GenericEntityInitializer.PreviewVisualData visual, FileHandle attemptedFile) {
        if (!DEBUG || Gdx.app == null) return;
        String attempted = attemptedFile == null ? "<none>" : attemptedFile.path();
        Gdx.app.log("PrefabPreviewWriter", "Skip preview entry: " + reason
                + " assetRefAssetId=" + visual.assetRefAssetId
                + " attemptedPath=" + attempted);
    }

    private static float[] transformedBounds(PreviewSprite s) {
        float[] xs = new float[]{-s.originX, s.width - s.originX, s.width - s.originX, -s.originX};
        float[] ys = new float[]{-s.originY, -s.originY, s.height - s.originY, s.height - s.originY};
        float cos = MathUtils.cos(s.rotationRad);
        float sin = MathUtils.sin(s.rotationRad);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float lx = xs[i] * s.scaleX;
            float ly = ys[i] * s.scaleY;
            float rx = lx * cos - ly * sin;
            float ry = lx * sin + ly * cos;
            float wx = s.x + rx;
            float wy = s.y + ry;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private static void drawNode(Pixmap pixmap, int x, int y, int w, int h) { /* unchanged */
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

    private static final class PreviewSprite {
        FileHandle imageFile;
        Texture texture;
        int zIndex;
        float x, y, rotationRad, scaleX, scaleY, originX, originY, width, height;
    }
}
