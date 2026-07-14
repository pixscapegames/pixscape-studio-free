package games.pixscape.studio.ui.config;

import com.badlogic.gdx.graphics.Color;

/** Shared colors for interactive Studio canvas overlays. */
public final class EditorOverlayPalette {
    public static final Color SPATIAL_NEUTRAL_COLOR = new Color(0.25f, 1f, 0.65f, 0.85f);
    public static final Color SPATIAL_TILE_HIGHLIGHT_COLOR = new Color(0.25f, 1f, 0.65f, 0.50f);
    public static final Color WALL_HOVER_COLOR = new Color(1f, 1f, 1f, 1f);
    public static final Color WALL_SELECTED_COLOR = new Color(0.90f, 0.90f, 0.20f, 1f);
    public static final Color VALID_PREVIEW_COLOR = new Color(0.25f, 1f, 0.65f, 1f);
    public static final Color INVALID_PREVIEW_COLOR = new Color(1f, 0.12f, 0.08f, 1f);
    public static final Color HANDLE_COLOR = new Color(1f, 1f, 1f, 1f);

    public static final Color PHYSICS_SELECTED_COLOR = new Color(0.20f, 0.55f, 1f, 1f);
    public static final Color PHYSICS_HOVER_COLOR = new Color(1f, 1f, 1f, 1f);
    public static final Color PHYSICS_FOCUSED_BODY_COLOR = new Color(0.90f, 0.90f, 0.20f, 0.48f);

    public static float spatialTileHighlightPacked() {
        return SPATIAL_TILE_HIGHLIGHT_COLOR.toFloatBits();
    }

    public static Color spatialWallColor(boolean selected,
                                         boolean hovered,
                                         boolean previewActive,
                                         boolean previewValid) {
        if (previewActive) return previewValid ? VALID_PREVIEW_COLOR : INVALID_PREVIEW_COLOR;
        if (selected) return WALL_SELECTED_COLOR;
        if (hovered) return WALL_HOVER_COLOR;
        return SPATIAL_NEUTRAL_COLOR;
    }

    private EditorOverlayPalette() {
    }
}
