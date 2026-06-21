package games.pixscape.studio.io;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;

public final class TileAnimationsIO {

    public static final String SUPPORTED_VERSION = "1";

    private static final Json JSON = new Json();

    static {
        JSON.setUsePrototypes(false);
        JSON.setOutputType(JsonWriter.OutputType.json);
        JSON.setIgnoreUnknownFields(true);
    }

    private TileAnimationsIO() {
    }

    public static TileAnimationsMetaDatabase load(FileHandle file) {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        if (!file.exists()) {
            return createEmpty();
        }

        TileAnimationsMetaDatabase db =
                JSON.fromJson(TileAnimationsMetaDatabase.class, StudioIO.readUtf8(file));

        if (db == null) {
            throw new RuntimeException("Invalid tile animations file (null): " + file.path());
        }

        applyDefaults(db);
        validateOrThrow(db, file.path());
        return db;
    }

    public static void save(TileAnimationsMetaDatabase db, FileHandle file) {
        if (db == null) {
            throw new IllegalArgumentException("db is null");
        }
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        applyDefaults(db);
        validateOrThrow(db, file.path());

        String pretty = JSON.prettyPrint(db);
        StudioIO.writeUtf8Atomic(file, pretty);
    }

    public static TileAnimationsMetaDatabase createEmpty() {
        TileAnimationsMetaDatabase db = new TileAnimationsMetaDatabase();
        applyDefaults(db);
        return db;
    }

    public static void applyDefaults(TileAnimationsMetaDatabase db) {
        if (db == null) {
            return;
        }

        if (db.version == null || db.version.isBlank()) {
            db.version = SUPPORTED_VERSION;
        }

        if (db.animations == null) {
            db.animations = new Array<>();
        }

        for (int i = 0; i < db.animations.size; i++) {
            TileAnimationProjectDefData def = db.animations.get(i);
            if (def == null) {
                continue;
            }

            if (def.name == null) {
                def.name = "";
            }

            if (def.frameAssetIds == null) {
                def.frameAssetIds = new int[0];
            }

            if (def.frameDurationsMs == null) {
                def.frameDurationsMs = new int[0];
            }
        }
    }

    public static void validateOrThrow(TileAnimationsMetaDatabase db, String path) {
        if (db == null) {
            throw new RuntimeException("Tile animations database is null: " + path);
        }

        if (db.version == null || db.version.isBlank()) {
            throw new RuntimeException("Missing tile animations version in: " + path);
        }

        if (!SUPPORTED_VERSION.equals(db.version)) {
            throw new RuntimeException(
                    "Unsupported tile animations version '" + db.version + "' in: " + path
            );
        }

        if (db.animations == null) {
            throw new RuntimeException("Tile animations list is null in: " + path);
        }

        for (int i = 0; i < db.animations.size; i++) {
            TileAnimationProjectDefData def = db.animations.get(i);

            if (def == null) {
                throw new RuntimeException("Tile animation entry[" + i + "] is null in: " + path);
            }

            if (def.name == null || def.name.isBlank()) {
                throw new RuntimeException("Tile animation entry[" + i + "] has no name in: " + path);
            }

            if (def.id <= 0) {
                throw new RuntimeException(
                        "Tile animation '" + def.name + "' has invalid id in: " + path
                );
            }

            if (def.frameAssetIds == null || def.frameDurationsMs == null) {
                throw new RuntimeException(
                        "Tile animation '" + def.name + "' has null frames or durations in: " + path
                );
            }

            if (def.frameAssetIds.length != def.frameDurationsMs.length) {
                throw new RuntimeException(
                        "Tile animation '" + def.name + "' has mismatched frame/duration counts in: " + path
                );
            }

            int totalDuration = 0;

            for (int f = 0; f < def.frameAssetIds.length; f++) {
                int frameAssetId = def.frameAssetIds[f];
                int durationMs = def.frameDurationsMs[f];

                if (frameAssetId <= 0) {
                    throw new RuntimeException(
                            "Tile animation '" + def.name + "' has invalid frameAssetIds[" + f + "] in: " + path
                    );
                }

                if (durationMs <= 0) {
                    throw new RuntimeException(
                            "Tile animation '" + def.name + "' has invalid frameDurationsMs[" + f + "] in: " + path
                    );
                }

                totalDuration += durationMs;
                if (totalDuration < 0) {
                    throw new RuntimeException(
                            "Tile animation '" + def.name + "' total duration overflow in: " + path
                    );
                }
            }
        }
    }

    /**
     * Studio may contain draft / empty tiled animations.
     * Runtime export must only keep complete definitions.
     */
    public static boolean isExportable(TileAnimationProjectDefData def) {
        if (def == null) return false;
        if (def.id <= 0) return false;
        if (def.frameAssetIds == null || def.frameDurationsMs == null) return false;
        if (def.frameAssetIds.length == 0) return false;
        if (def.frameAssetIds.length != def.frameDurationsMs.length) return false;

        int totalDuration = 0;

        for (int i = 0; i < def.frameAssetIds.length; i++) {
            if (def.frameAssetIds[i] <= 0) return false;
            if (def.frameDurationsMs[i] <= 0) return false;

            totalDuration += def.frameDurationsMs[i];
            if (totalDuration < 0) return false;
        }

        return true;
    }
}