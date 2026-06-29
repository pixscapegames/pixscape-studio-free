package games.pixscape.studio.asset;

public enum TilesetRenderSize {
    NATIVE("native");

    private final String wireName;

    TilesetRenderSize(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static TilesetRenderSize fromWireName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (TilesetRenderSize renderSize : values()) {
            if (renderSize.wireName.equalsIgnoreCase(raw) || renderSize.name().equalsIgnoreCase(raw)) {
                return renderSize;
            }
        }
        return null;
    }
}
