package games.pixscape.studio.asset;

public abstract class AssetMeta {

    public enum AssetScope {
        USER,       // visible and removable
        INTERNAL,   // hidden in the UI
        GENERATED   // generated automatically
    }

    private final int id;
    private final AssetType type;
    private String logicalPath;
    private String sourceRelPath;
    public AssetScope scope;

    protected AssetMeta(AssetType type) {
        this(0, type, null, null, AssetScope.USER);
    }

    protected AssetMeta(int id,
                        AssetType type,
                        String logicalPath,
                        String sourceRelPath,
                        AssetScope scope) {
        this.id = id;
        this.type = type;
        this.logicalPath = logicalPath;
        this.sourceRelPath = sourceRelPath;
        this.scope = scope;
    }

    public int id() {
        return id;
    }

    public AssetType type() {
        return type;
    }

    public String logicalPath() {
        return logicalPath;
    }

    public String sourceRelPath() {
        return sourceRelPath;
    }

    void replaceIdentityPaths(String logicalPath, String sourceRelPath) {
        this.logicalPath = logicalPath;
        this.sourceRelPath = sourceRelPath;
    }

    public boolean isUserVisible() {
        return scope == AssetScope.USER;
    }
}
