package games.pixscape.studio.asset;

public enum AssetType {
    IMAGE("image"),
    ANIMATION("animation"),
    PARTICLE("particle"),
    TILESET("tileset"),
    TILE("tile");

    private final String wireName;

    AssetType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static AssetType fromWireName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (AssetType type : values()) {
            if (type.wireName.equalsIgnoreCase(raw) || type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
