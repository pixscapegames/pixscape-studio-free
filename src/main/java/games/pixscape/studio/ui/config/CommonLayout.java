package games.pixscape.studio.ui.config;

import com.badlogic.gdx.graphics.Color;

public final class CommonLayout {
    /** Authored creation size for new structural HUD widgets placed freely. */
    public static final float DEFAULT_FREE_WIDTH = 200f;
    public static final float DEFAULT_FREE_HEIGHT = 120f;

    public static final float HUD_WIDGET_ICON_SIZE = 32f;
    public static final float HUD_WIDGET_BUTTON_PADDING = 6f;
    public static final float HUD_WIDGET_BUTTON_SIZE =
            HUD_WIDGET_ICON_SIZE + HUD_WIDGET_BUTTON_PADDING * 2f;
    public static final float HUD_WIDGET_GRID_GAP = 6f;
    public static final float DOCUMENT_TAB_GAP = 10f;

    public static final float LABEL_WIDTH = 86f;
    public static final float FIELD_WIDTH = 100f;
    public static final Color BUTTON_COLOR = Color.TEAL;

    public static final float PAD_LEFT_SUBMENU = 30f;
    public static final float PROPERTY_SECTION_TITLE_BOTTOM_PAD = 20f;

    public static final Color PHYSICS_BASE_COLOR = new Color(0.35f, 0.9f, 1f, 1f);
    public static final Color PHYSICS_JOINT_COLOR = new Color(0.55f, 0.85f, 1f, 1f);
    public static final Color PHYSICS_SENSOR_COLOR = new Color(0.95f, 0.45f, 0.95f, 0.95f);
    public static final Color PHYSICS_JOINT_OVERLAY_COLOR = new Color(0.55f, 0.85f, 1f, 0.90f);
    private CommonLayout() {
    }
}
