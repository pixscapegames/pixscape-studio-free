package games.pixscape.studio.service.tiled;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import games.pixscape.studio.asset.*;

import java.util.Objects;
import java.util.function.IntFunction;

public final class StudioTilesetProfileResolver {

    private IntFunction<AssetMeta> assetMetaLookup;
    private final IntMap<CacheEntry> profileCache = new IntMap<>();

    public StudioTilesetProfileResolver(IntFunction<AssetMeta> assetMetaLookup) {
        setAssetMetaLookup(assetMetaLookup);
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup = assetMetaLookup != null ? assetMetaLookup : id -> null;
        clearCache();
    }

    public RuntimeTilesetProfile resolve(int tileAssetId) {
        if (tileAssetId <= 0) return null;

        AssetMeta tileMetaRaw = assetMetaLookup.apply(tileAssetId);
        if (!(tileMetaRaw instanceof TileAssetMeta tileMeta) || tileMeta.tilesetId <= 0) {
            return null;
        }

        AssetMeta tilesetMetaRaw = assetMetaLookup.apply(tileMeta.tilesetId);
        if (!(tilesetMetaRaw instanceof TilesetAssetMeta tilesetMeta)) {
            return null;
        }

        long fingerprint = fingerprint(tilesetMeta);
        CacheEntry cached = profileCache.get(tilesetMeta.id());
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.profile;
        }

        RuntimeTilesetProfile profile = toRuntimeProfile(tilesetMeta);
        profileCache.put(tilesetMeta.id(), new CacheEntry(fingerprint, profile));
        return profile;
    }

    public void clearCache() {
        profileCache.clear();
    }

    public static RuntimeTilesetProfiles buildRuntimeProfiles(AssetMetaDatabase assetDb) {
        RuntimeTilesetProfiles profiles = RuntimeTilesetProfiles.empty();
        reloadRuntimeProfiles(profiles, assetDb);
        return profiles;
    }

    public static void reloadRuntimeProfiles(RuntimeTilesetProfiles profiles, AssetMetaDatabase assetDb) {
        if (profiles == null) {
            return;
        }
        profiles.clear();
        appendRuntimeProfiles(profiles, assetDb);
    }

    private static void appendRuntimeProfiles(RuntimeTilesetProfiles profiles, AssetMetaDatabase assetDb) {
        if (profiles == null || assetDb == null) {
            return;
        }

        IntMap<IntArray> tileIdsByTilesetId = new IntMap<>();
        for (int i = 0; i < assetDb.size(); i++) {
            AssetMeta meta = assetDb.assetAt(i);
            if (!(meta instanceof TileAssetMeta tile) || tile.id() <= 0 || tile.tilesetId <= 0) {
                continue;
            }

            IntArray tileIds = tileIdsByTilesetId.get(tile.tilesetId);
            if (tileIds == null) {
                tileIds = new IntArray();
                tileIdsByTilesetId.put(tile.tilesetId, tileIds);
            }
            if (!tileIds.contains(tile.id())) {
                tileIds.add(tile.id());
            }
        }

        for (int i = 0; i < assetDb.size(); i++) {
            AssetMeta meta = assetDb.assetAt(i);
            if (!(meta instanceof TilesetAssetMeta tileset) || tileset.id() <= 0) {
                continue;
            }

            IntArray tileIds = tileIdsByTilesetId.get(tileset.id());
            if (tileIds == null || tileIds.size == 0) {
                continue;
            }

            tileIds.sort();
            RuntimeTilesetProfile profile = toRuntimeProfile(tileset);
            profile.tileAssetIds = tileIds.toArray();
            profiles.add(profile);
        }
    }

    public static RuntimeTilesetProfile toRuntimeProfile(TilesetAssetMeta tilesetMeta) {
        if (tilesetMeta == null) return null;

        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = tilesetMeta.id();
        profile.logicalPath = tilesetMeta.logicalPath();
        profile.tileWidth = tilesetMeta.tileWidth;
        profile.tileHeight = tilesetMeta.tileHeight;
        profile.referenceCellWidth = tilesetMeta.referenceCellWidth > 0
                ? tilesetMeta.referenceCellWidth
                : (tilesetMeta.tileWidth > 0 ? tilesetMeta.tileWidth : 32);
        profile.referenceCellHeight = tilesetMeta.referenceCellHeight > 0
                ? tilesetMeta.referenceCellHeight
                : (tilesetMeta.tileHeight > 0 ? tilesetMeta.tileHeight : 32);
        profile.projection = tilesetMeta.projection != null
                ? tilesetMeta.projection
                : SceneMetaRuntime.TiledProjection.ORTHO;
        profile.anchor = toRuntimeAnchor(tilesetMeta.anchor);
        profile.offsetX = tilesetMeta.offsetX;
        profile.offsetY = tilesetMeta.offsetY;
        profile.renderSize = tilesetMeta.renderSize == TilesetRenderSize.NATIVE
                ? RuntimeTilesetRenderSize.NATIVE
                : RuntimeTilesetRenderSize.NATIVE;
        return profile;
    }

    private static RuntimeTilesetAnchor toRuntimeAnchor(TilesetAnchor anchor) {
        if (anchor == null) return RuntimeTilesetAnchor.TOP_CENTER;
        RuntimeTilesetAnchor runtimeAnchor = RuntimeTilesetAnchor.fromWireName(anchor.wireName());
        return runtimeAnchor != null ? runtimeAnchor : RuntimeTilesetAnchor.TOP_CENTER;
    }

    private static long fingerprint(TilesetAssetMeta meta) {
        return Objects.hash(
                meta.id(),
                meta.logicalPath(),
                meta.tileWidth,
                meta.tileHeight,
                meta.referenceCellWidth,
                meta.referenceCellHeight,
                meta.projection,
                meta.anchor,
                meta.offsetX,
                meta.offsetY,
                meta.renderSize
        );
    }

    private static final class CacheEntry {
        final long fingerprint;
        final RuntimeTilesetProfile profile;

        CacheEntry(long fingerprint, RuntimeTilesetProfile profile) {
            this.fingerprint = fingerprint;
            this.profile = profile;
        }
    }
}
