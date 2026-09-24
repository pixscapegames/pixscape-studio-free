package games.pixscape.studio.service.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.viewport.Viewport;
import games.pixscape.studio.helper.ShapeHelper;
import games.pixscape.studio.ui.config.EditorOverlayPalette;

/** Editor-only visual and hit geometry for the selected FREE-node transform. */
final class HudTransformGizmo extends Actor {
    // Match the established World gizmo handle white.
    private static final Color OUTLINE = EditorOverlayPalette.HANDLE_COLOR;
    private static final Color HANDLE = EditorOverlayPalette.HANDLE_COLOR;
    private static final float HANDLE_LOGICAL_PIXELS = 8f;

    private HudTransformGeometry.Bounds bounds;

    HudTransformGizmo() {
        setName("__pixscape-hud-transform-gizmo");
        setTouchable(Touchable.disabled);
        setVisible(false);
    }

    void showFor(HudTransformGeometry.Bounds nextBounds) {
        bounds = nextBounds;
        setVisible(true);
    }

    void clearGizmo() {
        bounds = null;
        setVisible(false);
    }

    HudTransformGeometry.Bounds bounds() { return bounds; }

    HudTransformHandle handleAt(float x, float y) {
        return bounds == null ? null : HudTransformGeometry.handleAt(bounds, x, y, handleSize());
    }

    boolean contains(float x, float y) { return bounds != null && bounds.contains(x, y); }

    @Override public void draw(Batch batch, float parentAlpha) {
        if (bounds == null) return;
        float scale = worldPerLogicalPixel();
        float line = scale;
        float previous = batch.getPackedColor();
        batch.setColor(OUTLINE.r, OUTLINE.g, OUTLINE.b, OUTLINE.a * parentAlpha);
        batch.draw(ShapeHelper.whitePixelRegion(), bounds.x(), bounds.y(), bounds.width(), line);
        batch.draw(ShapeHelper.whitePixelRegion(), bounds.x(), bounds.top() - line, bounds.width(), line);
        batch.draw(ShapeHelper.whitePixelRegion(), bounds.x(), bounds.y(), line, bounds.height());
        batch.draw(ShapeHelper.whitePixelRegion(), bounds.right() - line, bounds.y(), line, bounds.height());
        batch.setColor(HANDLE.r, HANDLE.g, HANDLE.b, HANDLE.a * parentAlpha);
        float size = handleSize();
        for (HudTransformHandle handle : HudTransformHandle.values()) {
            HudTransformGeometry.Bounds handleBounds = HudTransformGeometry.handleBounds(bounds, handle, size);
            batch.draw(ShapeHelper.whitePixelRegion(), handleBounds.x(), handleBounds.y(),
                    handleBounds.width(), handleBounds.height());
        }
        batch.setPackedColor(previous);
    }

    private float handleSize() { return HANDLE_LOGICAL_PIXELS * worldPerLogicalPixel(); }

    private float worldPerLogicalPixel() {
        if (getStage() == null) return 1f;
        Viewport viewport = getStage().getViewport();
        if (viewport.getScreenWidth() <= 0 || viewport.getScreenHeight() <= 0) return 1f;
        return Math.max(viewport.getWorldWidth() / viewport.getScreenWidth(),
                viewport.getWorldHeight() / viewport.getScreenHeight());
    }
}
