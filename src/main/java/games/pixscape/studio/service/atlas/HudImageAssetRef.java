package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.helper.AssetHelper;
import games.pixscape.studio.ui.asset.AssetNode;

/** Stable bridge between a project Image identity and its generated-atlas region. */
public record HudImageAssetRef(int assetId, String resourceName, String sourceRelPath) {
    public static HudImageAssetRef fromSelected(AssetNode node, AssetMetaDatabase database,
                                                FileHandle projectDir) {
        if (node == null || node.kind != AssetNode.Kind.IMAGE
                || node.root != AssetNode.Root.IMAGES || node.assetId <= 0) return null;
        return fromAssetId(node.assetId, database, projectDir);
    }

    /** Resolves the stable project Image identity carried by non-selection UI such as DnD. */
    public static HudImageAssetRef fromAssetId(int assetId, AssetMetaDatabase database,
                                               FileHandle projectDir) {
        if (assetId <= 0) return null;
        AssetMeta meta = database != null ? database.findById(assetId) : null;
        if (meta == null || meta.type() != AssetType.IMAGE) return null;
        HudImageAssetRef ref = fromMeta(meta);
        FileHandle source = projectDir != null ? projectDir.child(ref.sourceRelPath()) : null;
        return source != null && source.exists() && !source.isDirectory() ? ref : null;
    }

    public static HudImageAssetRef resolve(String resourceName, AssetMetaDatabase database) {
        int assetId = AssetHelper.extractAssetIdFromRegionName(resourceName);
        AssetMeta meta = database != null ? database.findById(assetId) : null;
        if (meta == null || meta.type() != AssetType.IMAGE) {
            throw new IllegalArgumentException(
                    "HUD image region does not resolve to a project Image asset: " + resourceName + ".");
        }
        HudImageAssetRef ref = fromMeta(meta);
        if (!ref.resourceName().equals(resourceName)) {
            throw new IllegalArgumentException("HUD image region '" + resourceName
                    + "' does not match Image asset " + assetId + " source identity.");
        }
        return ref;
    }

    private static HudImageAssetRef fromMeta(AssetMeta meta) {
        String sourcePath = meta.sourceRelPath();
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("Project Image asset " + meta.id()
                    + " has no source path.");
        }
        String normalized = sourcePath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = fileName.lastIndexOf('.');
        String resourceName = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (AssetHelper.extractAssetIdFromRegionName(resourceName) != meta.id()) {
            throw new IllegalArgumentException("Project Image asset " + meta.id()
                    + " does not use the stable Scene atlas source naming convention.");
        }
        return new HudImageAssetRef(meta.id(), resourceName, normalized);
    }
}
