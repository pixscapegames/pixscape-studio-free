package games.pixscape.studio.service.asset;

import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TiledAnimationImportSupport {

    private TiledAnimationImportSupport() {
    }

    public static Map<Integer, Integer> importTileAnimations(AssetMetaDatabase assetDb,
                                                             TileAnimationsMetaDatabase tileAnimationsDb,
                                                             String tilesetName,
                                                             List<TsxTilesetDescriptor.TileAnimation> animations,
                                                             Map<Integer, Integer> tileAssetIdsByLocalTileId) {
        Map<Integer, Integer> animationIdsByBaseLocalTileId = new LinkedHashMap<>();
        if (assetDb == null || tileAnimationsDb == null || animations == null || animations.isEmpty()) {
            return animationIdsByBaseLocalTileId;
        }
        if (tileAnimationsDb.animations == null) {
            tileAnimationsDb.animations = new com.badlogic.gdx.utils.Array<>();
        }

        for (TsxTilesetDescriptor.TileAnimation animation : animations) {
            if (animation == null || animation.frames() == null || animation.frames().isEmpty()) {
                continue;
            }

            Integer baseTileAssetId = tileAssetIdsByLocalTileId != null
                    ? tileAssetIdsByLocalTileId.get(animation.baseLocalTileId())
                    : null;
            if (baseTileAssetId == null || baseTileAssetId <= 0) {
                throw new IllegalStateException(
                        "Missing imported tile asset for animated base tile " + animation.baseLocalTileId()
                );
            }

            int[] frameAssetIds = new int[animation.frames().size()];
            int[] frameDurationsMs = new int[animation.frames().size()];
            for (int i = 0; i < animation.frames().size(); i++) {
                TsxTilesetDescriptor.Frame frame = animation.frames().get(i);
                Integer frameAssetId = tileAssetIdsByLocalTileId != null
                        ? tileAssetIdsByLocalTileId.get(frame.localTileId())
                        : null;
                if (frameAssetId == null || frameAssetId <= 0) {
                    throw new IllegalStateException(
                            "Missing imported tile asset for animation frame tile " + frame.localTileId()
                    );
                }
                if (frame.durationMs() <= 0) {
                    throw new IllegalStateException("Tile animation frame duration must be > 0 ms.");
                }
                frameAssetIds[i] = frameAssetId;
                frameDurationsMs[i] = frame.durationMs();
            }

            String desiredName = animationName(tilesetName, animation.baseLocalTileId());
            TileAnimationProjectDefData def = findByName(tileAnimationsDb, desiredName);
            if (def == null) {
                def = new TileAnimationProjectDefData();
                def.id = assetDb.allocateNextId();
                tileAnimationsDb.animations.add(def);
            }
            def.name = uniqueName(tileAnimationsDb, desiredName, def);
            def.frameAssetIds = frameAssetIds;
            def.frameDurationsMs = frameDurationsMs;

            animationIdsByBaseLocalTileId.put(animation.baseLocalTileId(), def.id);
        }

        return animationIdsByBaseLocalTileId;
    }

    private static TileAnimationProjectDefData findByName(TileAnimationsMetaDatabase db, String name) {
        if (db == null || db.animations == null || name == null || name.isBlank()) {
            return null;
        }
        for (TileAnimationProjectDefData def : db.animations) {
            if (def != null && def.name != null && name.equalsIgnoreCase(def.name)) {
                return def;
            }
        }
        return null;
    }

    private static String animationName(String tilesetName, int baseLocalTileId) {
        String base = tilesetName != null && !tilesetName.isBlank()
                ? tilesetName.trim()
                : "tileset";
        return base + "_anim_" + baseLocalTileId;
    }

    private static String uniqueName(TileAnimationsMetaDatabase db,
                                     String desired,
                                     TileAnimationProjectDefData self) {
        String base = desired != null && !desired.isBlank() ? desired.trim() : "tiled_animation";
        String candidate = base;
        int suffix = 2;
        while (containsName(db, candidate, self)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean containsName(TileAnimationsMetaDatabase db,
                                        String name,
                                        TileAnimationProjectDefData self) {
        if (db == null || db.animations == null || name == null) {
            return false;
        }
        for (TileAnimationProjectDefData def : db.animations) {
            if (def == null || def == self || def.name == null) continue;
            if (name.equalsIgnoreCase(def.name)) {
                return true;
            }
        }
        return false;
    }
}
