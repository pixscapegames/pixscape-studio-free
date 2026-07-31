package games.pixscape.studio.asset;

/**
 * User-facing identity derived from authoritative asset metadata.
 */
public record AssetDisplayInfo(String displayName, int assetId, String sourcePath) {

    public static AssetDisplayInfo from(AssetMeta meta) {
        if (meta == null) {
            throw new IllegalArgumentException("Asset metadata must not be null.");
        }

        String logicalPath = meta.logicalPath();
        if (logicalPath == null || logicalPath.isBlank()) {
            throw new IllegalArgumentException("Asset logical path must not be blank.");
        }

        int separator = logicalPath.lastIndexOf('/');
        String displayName = logicalPath.substring(separator + 1);
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("Asset logical path must end with a name.");
        }

        return new AssetDisplayInfo(displayName, meta.id(), meta.sourceRelPath());
    }

    public String tooltipText() {
        return "Asset name: " + displayName
                + "\nAsset ID: " + assetId
                + "\nSource: " + sourcePath;
    }

    public static String defaultEntityName(String requestedName,
                                           AssetMeta meta,
                                           String fallback) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }
        if (meta != null) {
            return from(meta).displayName();
        }
        return fallback;
    }
}
