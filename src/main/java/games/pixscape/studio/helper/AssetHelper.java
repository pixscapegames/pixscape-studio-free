package games.pixscape.studio.helper;

import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

public final class AssetHelper {

    private AssetHelper() {
    }

    // =========================
    // Logical paths (meta layer)
    // =========================

    public static String logicalImage(String fileName) {
        return StudioFs.logicalImage(fileName);
    }

    public static String logicalAnimation(String animationName) {
        return StudioFs.logicalAnimation(animationName);
    }

    public static String logicalEffect(String fileName) {
        return StudioFs.logicalEffect(fileName);
    }

    // =========================
    // Studio source paths
    // =========================

    public static String sourceImage(ProjectConfig cfg, String fileName) {
        return StudioFs.sourceImage(fileName);
    }

    public static String sourceAnimationDir(ProjectConfig cfg, String animationName) {
        return StudioFs.sourceAnimationDir(animationName);
    }

    public static String sourceEffect(ProjectConfig cfg, String fileName) {
        return StudioFs.sourceEffect(fileName);
    }

    // =========================
    // Atlas keys (runtime layer)
    // =========================

    public static String atlasRegionFromStandalone(String textureRelPath) {
        return removeExtension(textureRelPath);
    }

    public static String atlasRegionFromAnimation(String animationName) {
        return animationName;
    }

    // =========================
    // Utilities
    // =========================

    public static String removeExtension(String path) {
        return StudioFs.removeExtension(path);
    }

    public static String normalize(String path) {
        return StudioFs.normalizePath(path);
    }

    public static String buildRegionName(String baseName, int assetId) {
        return baseName + "__a" + assetId;
    }

    public static String extractBaseName(String relPath) {
        if (relPath == null || relPath.isBlank()) return "";

        // 1) keep only the file
        int slash = relPath.lastIndexOf('/');
        String name = (slash >= 0) ? relPath.substring(slash + 1) : relPath;

        // 2) retirer extension
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        // 3) remove __a<ID> suffix if present
        int marker = name.lastIndexOf("__a");
        if (marker > 0) {
            name = name.substring(0, marker);
        }

        return name;
    }


    public static int extractAssetIdFromRegionName(String regionName) {
        if (regionName == null) return -1;

        int marker = regionName.lastIndexOf("__a");
        if (marker < 0) return -1;

        try {
            return Integer.parseInt(regionName.substring(marker + 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
