package games.pixscape.studio.service.atlas;

public record AtlasInputSyncResult(
        boolean changed,
        int copiedCount,
        int deletedCount
) {
    public static AtlasInputSyncResult unchanged() {
        return new AtlasInputSyncResult(false, 0, 0);
    }
}