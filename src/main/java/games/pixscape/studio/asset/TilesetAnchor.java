package games.pixscape.studio.asset;

public enum TilesetAnchor {
    BOTTOM_CENTER("bottom-center"),
    BOTTOM_LEFT("bottom-left"),
    CENTER("center"),
    TOP_LEFT("top-left");

    private final String wireName;

    TilesetAnchor(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static TilesetAnchor fromWireName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (TilesetAnchor anchor : values()) {
            if (anchor.wireName.equalsIgnoreCase(raw) || anchor.name().equalsIgnoreCase(raw)) {
                return anchor;
            }
        }
        return null;
    }
}
