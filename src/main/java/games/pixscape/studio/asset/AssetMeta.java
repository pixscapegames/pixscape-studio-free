package games.pixscape.studio.asset;

public abstract class AssetMeta {

    public enum AssetScope {
        USER,       // visible and removable
        INTERNAL,   // hidden in the UI
        GENERATED   // generated automatically
    }

    public int id;
    public AssetType type;
    public String logicalPath;
    public String sourceRelPath;
    public AssetScope scope;

    public AssetMeta() {
        // required for Json
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

    public boolean isUserVisible() {
        return scope == AssetScope.USER;
    }
}