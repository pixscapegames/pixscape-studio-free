package games.pixscape.studio.importer.tmx;

import java.util.Locale;

public enum TmxObjectAlignment {
    UNSPECIFIED(0f, 1f),
    TOP_LEFT(0f, 0f),
    TOP(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    private final float anchorX;
    private final float anchorY;

    TmxObjectAlignment(float anchorX, float anchorY) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    public float anchorX() {
        return anchorX;
    }

    public float anchorY() {
        return anchorY;
    }

    static TmxObjectAlignment fromTiled(String value) {
        if (value == null || value.isBlank() || "unspecified".equalsIgnoreCase(value)) {
            return UNSPECIFIED;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "topleft" -> TOP_LEFT;
            case "top" -> TOP;
            case "topright" -> TOP_RIGHT;
            case "left" -> LEFT;
            case "center" -> CENTER;
            case "right" -> RIGHT;
            case "bottomleft" -> BOTTOM_LEFT;
            case "bottom" -> BOTTOM;
            case "bottomright" -> BOTTOM_RIGHT;
            default -> null;
        };
    }
}
