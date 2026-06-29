package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.helper.ShapeHelper;
import games.pixscape.studio.service.asset.TilesetSliceLayout;

import java.io.File;

final class TilesetTilePreviewActor extends Actor {

    private static final String TAG = "TilesetProfilePreview";
    private static final float PREVIEW_SIZE = 160f;
    private static final Color BACKGROUND = new Color(0.08f, 0.08f, 0.09f, 1f);
    private static final Color BORDER = new Color(0.42f, 0.42f, 0.46f, 1f);
    private static final Color EMPTY = new Color(0.18f, 0.18f, 0.20f, 1f);
    private static final Color CELL_OUTLINE = new Color(0.92f, 0.86f, 0.55f, 1f);
    private static final Color ANCHOR_MARKER = new Color(0.45f, 0.82f, 1f, 1f);
    private static final float PREVIEW_PADDING = 10f;
    private static final float LINE_THICKNESS = 1.5f;

    private FileHandle sourceFile;
    private Texture sourceTexture;
    private TextureRegion tileRegion;
    private TilesetProfilePreviewPlacement.Placement placement;
    private String statusText = "";
    private String lastLoggedStatus = "";
    private int lastTileWidth;
    private int lastTileHeight;
    private int lastSpacing;
    private int lastMargin;
    private int lastTileIndex;

    TilesetTilePreviewActor(FileHandle sourceFile) {
        setSize(PREVIEW_SIZE, PREVIEW_SIZE);
        loadSourceTexture(sourceFile);
    }

    void setSourceFile(FileHandle sourceFile) {
        if (sameSourceFile(sourceFile)) return;
        loadSourceTexture(sourceFile);
    }

    void updatePreview(int tileWidth,
                       int tileHeight,
                       int spacing,
                       int margin,
                       int tileIndex,
                       int referenceCellWidth,
                       int referenceCellHeight,
                       SceneMetaRuntime.TiledProjection projection,
                       TilesetAnchor anchor,
                       int offsetX,
                       int offsetY) {
        lastTileWidth = tileWidth;
        lastTileHeight = tileHeight;
        lastSpacing = spacing;
        lastMargin = margin;
        lastTileIndex = tileIndex;

        if (sourceTexture == null) {
            if (statusText == null || statusText.isBlank()) {
                setStatus("Preview unavailable: image not loaded", null);
            }
            return;
        }

        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(
                sourceTexture.getWidth(),
                sourceTexture.getHeight(),
                tileWidth,
                tileHeight,
                spacing,
                margin
        );
        if (!layout.valid()) {
            clearTileRegion();
            setStatus("Preview unavailable: invalid slicing: " + layout.invalidReason(), null);
            return;
        }

        TilesetSliceLayout.SourceRect rect = layout.clampedSourceRect(tileIndex);
        if (!rect.valid()) {
            clearTileRegion();
            setStatus("Preview unavailable: " + rect.invalidReason(), null);
            return;
        }

        try {
            if (tileRegion == null) {
                tileRegion = new TextureRegion(sourceTexture);
            }
            tileRegion.setRegion(rect.x(), rect.y(), rect.width(), rect.height());
            placement = TilesetProfilePreviewPlacement.calculate(
                    rect.width(),
                    rect.height(),
                    referenceCellWidth,
                    referenceCellHeight,
                    projection,
                    anchor,
                    offsetX,
                    offsetY
            );
            setStatus("");
        } catch (RuntimeException ex) {
            clearTileRegion();
            setStatus("Preview unavailable: preview region failed: " + shortMessage(ex), ex);
        }
    }

    void clearPreview(String statusText) {
        clearTileRegion();
        setStatus(statusText != null ? statusText : "");
    }

    String statusText() {
        return statusText;
    }

    boolean hasImage() {
        return sourceTexture != null && tileRegion != null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (batch == null) return;
        float oldColor = batch.getPackedColor();
        try {
            drawPreview(batch, parentAlpha);
        } catch (RuntimeException ex) {
            clearTileRegion();
            setStatus("Preview unavailable: draw failed: " + shortMessage(ex), ex);
        } finally {
            batch.setPackedColor(oldColor);
        }
    }

    private void drawPreview(Batch batch, float parentAlpha) {
        batch.setColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a * parentAlpha);
        batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), getHeight());

        if (hasImage()) {
            TilesetProfilePreviewPlacement.Placement activePlacement = placement != null
                    ? placement
                    : TilesetProfilePreviewPlacement.calculate(
                    tileRegion.getRegionWidth(),
                    tileRegion.getRegionHeight(),
                    tileRegion.getRegionWidth(),
                    tileRegion.getRegionHeight(),
                    SceneMetaRuntime.TiledProjection.ORTHO,
                    TilesetAnchor.BOTTOM_CENTER,
                    0,
                    0
            );
            TilesetProfilePreviewPlacement.Bounds union = activePlacement.unionBounds();
            float availableW = Math.max(1f, getWidth() - PREVIEW_PADDING * 2f);
            float availableH = Math.max(1f, getHeight() - PREVIEW_PADDING * 2f);
            float scale = Math.min(availableW / Math.max(1f, union.width()), availableH / Math.max(1f, union.height()));

            TilesetProfilePreviewPlacement.Bounds tileBounds = activePlacement.tileBounds();
            float drawW = tileBounds.width() * scale;
            float drawH = tileBounds.height() * scale;
            float drawX = viewX(tileBounds.left(), union, scale);
            float drawY = viewY(tileBounds.bottom(), union, scale);
            batch.setColor(1f, 1f, 1f, parentAlpha);
            batch.draw(tileRegion, drawX, drawY, drawW, drawH);
            drawCellOverlay(batch, parentAlpha, activePlacement, union, scale);
        } else {
            batch.setColor(EMPTY.r, EMPTY.g, EMPTY.b, EMPTY.a * parentAlpha);
            batch.draw(ShapeHelper.whitePixelRegion(), getX() + 1f, getY() + 1f, getWidth() - 2f, getHeight() - 2f);
        }

        batch.setColor(BORDER.r, BORDER.g, BORDER.b, BORDER.a * parentAlpha);
        batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), getWidth(), 1f);
        batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY() + getHeight() - 1f, getWidth(), 1f);
        batch.draw(ShapeHelper.whitePixelRegion(), getX(), getY(), 1f, getHeight());
        batch.draw(ShapeHelper.whitePixelRegion(), getX() + getWidth() - 1f, getY(), 1f, getHeight());
    }

    private void drawCellOverlay(Batch batch,
                                 float parentAlpha,
                                 TilesetProfilePreviewPlacement.Placement activePlacement,
                                 TilesetProfilePreviewPlacement.Bounds union,
                                 float scale) {
        TilesetProfilePreviewPlacement.Point[] points = activePlacement.cellOutline();
        if (points != null && points.length > 1) {
            batch.setColor(CELL_OUTLINE.r, CELL_OUTLINE.g, CELL_OUTLINE.b, CELL_OUTLINE.a * parentAlpha);
            for (int i = 0; i < points.length; i++) {
                TilesetProfilePreviewPlacement.Point from = points[i];
                TilesetProfilePreviewPlacement.Point to = points[(i + 1) % points.length];
                drawLine(
                        batch,
                        viewX(from.x(), union, scale),
                        viewY(from.y(), union, scale),
                        viewX(to.x(), union, scale),
                        viewY(to.y(), union, scale),
                        LINE_THICKNESS
                );
            }
        }

        TilesetProfilePreviewPlacement.Point anchor = activePlacement.anchorPoint();
        float anchorX = viewX(anchor.x(), union, scale);
        float anchorY = viewY(anchor.y(), union, scale);
        float marker = 4f;
        batch.setColor(ANCHOR_MARKER.r, ANCHOR_MARKER.g, ANCHOR_MARKER.b, ANCHOR_MARKER.a * parentAlpha);
        batch.draw(ShapeHelper.whitePixelRegion(), anchorX - marker, anchorY - 0.5f, marker * 2f, 1f);
        batch.draw(ShapeHelper.whitePixelRegion(), anchorX - 0.5f, anchorY - marker, 1f, marker * 2f);
    }

    private float viewX(float worldX, TilesetProfilePreviewPlacement.Bounds union, float scale) {
        float contentW = union.width() * scale;
        return getX() + (getWidth() - contentW) * 0.5f + (worldX - union.left()) * scale;
    }

    private float viewY(float worldY, TilesetProfilePreviewPlacement.Bounds union, float scale) {
        float contentH = union.height() * scale;
        return getY() + (getHeight() - contentH) * 0.5f + (worldY - union.bottom()) * scale;
    }

    private void drawLine(Batch batch, float x1, float y1, float x2, float y2, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0f) return;

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.draw(
                ShapeHelper.whitePixelRegion(),
                x1,
                y1 - thickness * 0.5f,
                0f,
                thickness * 0.5f,
                length,
                thickness,
                1f,
                1f,
                angle
        );
    }

    @Override
    public boolean remove() {
        dispose();
        return super.remove();
    }

    @Override
    protected void setStage(com.badlogic.gdx.scenes.scene2d.Stage stage) {
        boolean leavingRealStage = getStage() != null && stage == null;
        super.setStage(stage);
        if (leavingRealStage) {
            dispose();
        }
    }

    private void loadSourceTexture(FileHandle requestedFile) {
        clearSourceTexture();
        clearTileRegion();

        FileHandle resolved;
        try {
            resolved = resolveSourceFile(requestedFile);
        } catch (RuntimeException ex) {
            setStatus("Preview unavailable: failed to resolve file: " + shortMessage(ex), ex);
            return;
        }

        sourceFile = resolved;
        if (resolved == null) {
            setStatus("Preview unavailable: no source file");
            return;
        }

        try {
            if (!resolved.exists()) {
                setStatus("Preview unavailable: file not found");
                logStatus(null);
                return;
            }
            if (resolved.isDirectory()) {
                setStatus("Preview unavailable: source is a directory");
                logStatus(null);
                return;
            }

            sourceTexture = new Texture(resolved);
            sourceTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            setStatus("");
        } catch (RuntimeException ex) {
            clearSourceTexture();
            setStatus("Preview unavailable: texture load failed: " + shortMessage(ex), ex);
        }
    }

    private FileHandle resolveSourceFile(FileHandle sourceFile) {
        if (sourceFile == null) return null;
        File file;
        try {
            file = sourceFile.file();
        } catch (RuntimeException ex) {
            return sourceFile;
        }
        if (file != null && Gdx.files != null) {
            return Gdx.files.absolute(file.getAbsolutePath());
        }
        return sourceFile;
    }

    private boolean sameSourceFile(FileHandle nextSourceFile) {
        if (sourceFile == null || nextSourceFile == null) {
            return sourceFile == nextSourceFile;
        }
        return sourceFile.path().equals(nextSourceFile.path());
    }

    private void dispose() {
        clearTileRegion();
        clearSourceTexture();
    }

    private void clearTileRegion() {
        tileRegion = null;
        placement = null;
    }

    private void clearSourceTexture() {
        if (sourceTexture != null) {
            sourceTexture.dispose();
            sourceTexture = null;
        }
    }

    private void setStatus(String statusText) {
        this.statusText = statusText != null ? statusText : "";
    }

    private void setStatus(String statusText, Throwable throwable) {
        setStatus(statusText);
        logStatus(throwable);
    }

    private void logStatus(Throwable throwable) {
        if (Gdx.app == null || statusText == null || statusText.isBlank() || statusText.equals(lastLoggedStatus)) {
            return;
        }

        lastLoggedStatus = statusText;
        String message = statusText + " [" + sourceDiagnostics() + "]";
        if (throwable != null) {
            Gdx.app.error(TAG, message, throwable);
        } else {
            Gdx.app.error(TAG, message);
        }
    }

    private String sourceDiagnostics() {
        String path = sourceFile != null ? sourceFile.path() : "<none>";
        String absolutePath = "<unknown>";
        boolean exists = false;
        boolean directory = false;
        boolean readable = false;
        try {
            File file = sourceFile != null ? sourceFile.file() : null;
            if (file != null) {
                absolutePath = file.getAbsolutePath();
                readable = file.canRead();
            }
            exists = sourceFile != null && sourceFile.exists();
            directory = sourceFile != null && sourceFile.isDirectory();
        } catch (RuntimeException ignored) {
            // Best-effort diagnostics only.
        }

        String imageSize = sourceTexture != null
                ? sourceTexture.getWidth() + "x" + sourceTexture.getHeight()
                : "not-loaded";
        return "path=" + path
                + ", absolutePath=" + absolutePath
                + ", exists=" + exists
                + ", readable=" + readable
                + ", directory=" + directory
                + ", imageSize=" + imageSize
                + ", tile=" + lastTileWidth + "x" + lastTileHeight
                + ", spacing=" + lastSpacing
                + ", margin=" + lastMargin
                + ", tileIndex=" + lastTileIndex;
    }

    private static String shortMessage(Throwable throwable) {
        if (throwable == null) return "unknown";
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
