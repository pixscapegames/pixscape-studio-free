package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.configuration.SceneRuntimeAvailabilityData;
import games.pixscape.studio.io.StudioFs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RuntimeAvailabilityService {

    public SceneRuntimeAvailabilityData data(SceneMeta scene) {
        if (scene == null) {
            return new SceneRuntimeAvailabilityData();
        }
        if (scene.runtimeAvailability == null) {
            scene.runtimeAvailability = new SceneRuntimeAvailabilityData();
        }
        ensureLists(scene.runtimeAvailability);
        return scene.runtimeAvailability;
    }

    public List<String> listPrefabIds(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).prefabIds);
    }

    public List<Integer> listSpriteAssetIds(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).spriteAssetIds);
    }

    public List<Integer> listAnimationAssetIds(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).animationAssetIds);
    }

    public List<String> listParticleEffectPaths(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).particleEffectPaths);
    }

    public List<Integer> listTiledAnimationIds(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).tiledAnimationIds);
    }

    public List<Integer> listTiledTileAssetIds(SceneMeta scene) {
        return Collections.unmodifiableList(data(scene).tiledTileAssetIds);
    }

    public boolean addPrefab(SceneMeta scene, String prefabId) {
        String normalized = normalizePrefabId(prefabId);
        if (normalized == null) return false;

        ArrayList<String> list = data(scene).prefabIds;
        if (list.contains(normalized)) return false;
        list.add(normalized);
        list.sort(String::compareToIgnoreCase);
        return true;
    }

    public boolean addSprite(SceneMeta scene, int assetId) {
        return addInt(data(scene).spriteAssetIds, assetId);
    }

    public boolean removeSprite(SceneMeta scene, int assetId) {
        return removeInt(data(scene).spriteAssetIds, assetId);
    }

    public boolean addAnimation(SceneMeta scene, int assetId) {
        return addInt(data(scene).animationAssetIds, assetId);
    }

    public boolean removeAnimation(SceneMeta scene, int assetId) {
        return removeInt(data(scene).animationAssetIds, assetId);
    }

    public boolean addParticle(SceneMeta scene, String effectPath) {
        String normalized = normalizeParticleEffectPath(effectPath);
        if (normalized == null) return false;

        ArrayList<String> list = data(scene).particleEffectPaths;
        if (list.contains(normalized)) return false;
        list.add(normalized);
        list.sort(String::compareToIgnoreCase);
        return true;
    }

    public boolean removeParticle(SceneMeta scene, String effectPath) {
        String normalized = normalizeParticleEffectPath(effectPath);
        return normalized != null && data(scene).particleEffectPaths.remove(normalized);
    }

    public boolean removePrefab(SceneMeta scene, String prefabId) {
        String normalized = normalizePrefabId(prefabId);
        return normalized != null && data(scene).prefabIds.remove(normalized);
    }

    public boolean addTiledAnimation(SceneMeta scene, int tileAnimationId) {
        return addInt(data(scene).tiledAnimationIds, tileAnimationId);
    }

    public boolean removeTiledAnimation(SceneMeta scene, int tileAnimationId) {
        return removeInt(data(scene).tiledAnimationIds, tileAnimationId);
    }

    public boolean addTiledTile(SceneMeta scene, int tileAssetId) {
        return addInt(data(scene).tiledTileAssetIds, tileAssetId);
    }

    public boolean removeTiledTile(SceneMeta scene, int tileAssetId) {
        return removeInt(data(scene).tiledTileAssetIds, tileAssetId);
    }

    public boolean removeDeletedAsset(ProjectConfig cfg, AssetMeta meta) {
        if (cfg == null || meta == null || meta.type == null) {
            return false;
        }

        boolean changed = false;
        for (com.badlogic.gdx.utils.ObjectMap.Entry<String, SceneMeta> entry : cfg.getScenesMap()) {
            if (entry == null || entry.value == null) continue;

            changed |= switch (meta.type) {
                case IMAGE -> removeSprite(entry.value, meta.id);
                case ANIMATION -> removeAnimation(entry.value, meta.id);
                case PARTICLE -> removeDeletedParticle(entry.value, meta.sourceRelPath);
                case TILE -> removeTiledTile(entry.value, meta.id);
                case TILESET -> false;
            };
        }
        return changed;
    }

    public boolean removeDeletedTileset(ProjectConfig cfg, AssetMetaDatabase assetDb, int tilesetId) {
        if (cfg == null || assetDb == null || tilesetId <= 0) {
            return false;
        }

        boolean changed = false;
        for (AssetMeta meta : assetDb.assets) {
            if (!(meta instanceof TileAssetMeta tileMeta)) continue;
            if (tileMeta.tilesetId != tilesetId) continue;
            changed |= removeDeletedAsset(cfg, tileMeta);
        }
        return changed;
    }

    private boolean removeDeletedParticle(SceneMeta scene, String sourceRelPath) {
        boolean changed = removeParticle(scene, sourceRelPath);
        changed |= removeParticle(scene, particleRuntimePath(sourceRelPath));
        return changed;
    }

    private static String particleRuntimePath(String sourceRelPath) {
        if (sourceRelPath == null) return null;

        String normalized = sourceRelPath.trim().replace('\\', '/');
        String prefix = StudioFs.DIR_ORIG_EFFECTS + "/";
        if (normalized.startsWith(prefix)) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    public boolean containsPrefab(SceneMeta scene, String prefabId) {
        String normalized = normalizePrefabId(prefabId);
        return normalized != null && data(scene).prefabIds.contains(normalized);
    }

    public boolean containsSprite(SceneMeta scene, int assetId) {
        return data(scene).spriteAssetIds.contains(assetId);
    }

    public boolean containsAnimation(SceneMeta scene, int assetId) {
        return data(scene).animationAssetIds.contains(assetId);
    }

    public boolean containsParticle(SceneMeta scene, String effectPath) {
        String normalized = normalizeParticleEffectPath(effectPath);
        return normalized != null && data(scene).particleEffectPaths.contains(normalized);
    }

    public boolean containsTiledAnimation(SceneMeta scene, int tileAnimationId) {
        return data(scene).tiledAnimationIds.contains(tileAnimationId);
    }

    private static boolean addInt(ArrayList<Integer> list, int value) {
        if (value <= 0 || list == null || list.contains(value)) return false;
        list.add(value);
        list.sort(Integer::compareTo);
        return true;
    }

    private static boolean removeInt(ArrayList<Integer> list, int value) {
        return value > 0 && list != null && list.remove(Integer.valueOf(value));
    }

    private static String normalizePrefabId(String prefabId) {
        if (prefabId == null) return null;
        String normalized = prefabId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeParticleEffectPath(String effectPath) {
        if (effectPath == null) return null;
        String normalized = effectPath.trim().replace('\\', '/');
        return normalized.isEmpty() ? null : normalized;
    }

    private static void ensureLists(SceneRuntimeAvailabilityData data) {
        if (data.spriteAssetIds == null) data.spriteAssetIds = new ArrayList<>();
        if (data.animationAssetIds == null) data.animationAssetIds = new ArrayList<>();
        if (data.particleEffectPaths == null) data.particleEffectPaths = new ArrayList<>();
        if (data.prefabIds == null) data.prefabIds = new ArrayList<>();
        if (data.tiledTileAssetIds == null) data.tiledTileAssetIds = new ArrayList<>();
        if (data.tiledAnimationIds == null) data.tiledAnimationIds = new ArrayList<>();
    }
}
