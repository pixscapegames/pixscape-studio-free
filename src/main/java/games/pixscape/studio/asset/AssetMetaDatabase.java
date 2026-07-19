package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.io.StudioIO;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AssetMetaDatabase {

    public static final int CURRENT_VERSION = 3;

    public int version = CURRENT_VERSION;
    public int nextId = 1;
    public Array<AssetMeta> assets = new Array<>();

    public AssetMetaDatabase() {
        // required for Json
    }

    public int allocateNextId() {
        return nextId++;
    }

    // ----------------------------------------------------
    // Lookup
    // ----------------------------------------------------

    public AssetMeta findByLogicalPath(String logicalPath) {
        if (logicalPath == null) return null;
        for (AssetMeta a : assets) {
            if (logicalPath.equals(a.logicalPath)) return a;
        }
        return null;
    }

    public AssetMeta findById(int id) {
        for (AssetMeta a : assets) {
            if (a.id == id) return a;
        }
        return null;
    }

    public AssetMeta findBySourceRelPath(String sourceRelPath) {
        if (sourceRelPath == null) return null;
        for (AssetMeta a : assets) {
            if (sourceRelPath.equals(a.sourceRelPath)) return a;
        }
        return null;
    }

    public int getIdBySourceRelPath(String sourceRelPath) {
        AssetMeta meta = findBySourceRelPath(sourceRelPath);
        return meta != null ? meta.id : -1;
    }

    // ----------------------------------------------------
    // Removal
    // ----------------------------------------------------

    public boolean removeById(int id) {
        for (int i = 0; i < assets.size; i++) {
            AssetMeta a = assets.get(i);
            if (a.id == id) {
                assets.removeIndex(i);
                return true;
            }
        }
        return false;
    }

    public boolean removeByLogicalPath(String logicalPath) {
        if (logicalPath == null) return false;

        for (int i = 0; i < assets.size; i++) {
            AssetMeta a = assets.get(i);
            if (logicalPath.equals(a.logicalPath)) {
                assets.removeIndex(i);
                return true;
            }
        }
        return false;
    }

    public int removeByLogicalPathPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return 0;

        int removed = 0;

        for (int i = assets.size - 1; i >= 0; i--) {
            AssetMeta a = assets.get(i);
            if (a == null || a.logicalPath == null) continue;

            if (a.logicalPath.startsWith(prefix)) {
                assets.removeIndex(i);
                removed++;
            }
        }

        return removed;
    }

    // ----------------------------------------------------
    // Registration
    // ----------------------------------------------------

    public AssetMeta registerIfAbsent(AssetType type,
                                      String logicalPath,
                                      String sourceRelPath,
                                      AssetMeta.AssetScope scope) {

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");

        AssetMeta existing = findByLogicalPath(logicalPath);
        if (existing != null) {
            promoteScopeIfNeeded(existing, scope);
            return existing;
        }

        AssetMeta created = newMeta(nextId++, type, logicalPath, sourceRelPath, scope);
        assets.add(created);
        return created;
    }

    private static void promoteScopeIfNeeded(AssetMeta meta, AssetMeta.AssetScope requestedScope) {
        if (meta == null || requestedScope == null) return;

        AssetMeta.AssetScope currentScope =
                (meta.scope != null) ? meta.scope : AssetMeta.AssetScope.USER;

        if (currentScope != AssetMeta.AssetScope.USER
                && requestedScope == AssetMeta.AssetScope.USER) {
            meta.scope = AssetMeta.AssetScope.USER;
        }
    }

    private static AssetMeta newMeta(int id,
                                     AssetType type,
                                     String logicalPath,
                                     String sourceRelPath,
                                     AssetMeta.AssetScope scope) {
        return switch (type) {
            case IMAGE -> new ImageAssetMeta(id, logicalPath, sourceRelPath, scope);
            case ANIMATION -> new AnimationAssetMeta(id, logicalPath, sourceRelPath, scope);
            case PARTICLE -> new ParticleAssetMeta(id, logicalPath, sourceRelPath, scope);
            case TILESET -> new TilesetAssetMeta(id, logicalPath, sourceRelPath, scope);
            case TILE -> new TileAssetMeta(id, logicalPath, sourceRelPath, scope);
        };
    }

    private static AssetMeta newEmptyMeta(AssetType type) {
        return switch (type) {
            case IMAGE -> new ImageAssetMeta();
            case ANIMATION -> new AnimationAssetMeta();
            case PARTICLE -> new ParticleAssetMeta();
            case TILESET -> new TilesetAssetMeta();
            case TILE -> new TileAssetMeta();
        };
    }

    // ----------------------------------------------------
    // Persistence
    // ----------------------------------------------------

    private static final Json JSON = buildJson();

    private static Json buildJson() {
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        json.setSerializer(AssetMeta.class, new AssetMetaJsonSerializer());
        json.setSerializer(SceneMetaRuntime.TiledProjection.class, new Json.Serializer<SceneMetaRuntime.TiledProjection>() {
            @Override
            public void write(Json json, SceneMetaRuntime.TiledProjection object, Class knownType) {
                json.writeValue(tiledProjectionWireName(object));
            }

            @Override
            public SceneMetaRuntime.TiledProjection read(Json json, JsonValue jsonData, Class type) {
                return tiledProjectionFromWireName(jsonData != null ? jsonData.asString() : null);
            }
        });
        json.setSerializer(TilesetAnchor.class, new Json.Serializer<TilesetAnchor>() {
            @Override
            public void write(Json json, TilesetAnchor object, Class knownType) {
                json.writeValue(object != null ? object.wireName() : null);
            }

            @Override
            public TilesetAnchor read(Json json, JsonValue jsonData, Class type) {
                return TilesetAnchor.fromWireName(jsonData != null ? jsonData.asString() : null);
            }
        });
        json.setSerializer(TilesetRenderSize.class, new Json.Serializer<TilesetRenderSize>() {
            @Override
            public void write(Json json, TilesetRenderSize object, Class knownType) {
                json.writeValue(object != null ? object.wireName() : null);
            }

            @Override
            public TilesetRenderSize read(Json json, JsonValue jsonData, Class type) {
                return TilesetRenderSize.fromWireName(jsonData != null ? jsonData.asString() : null);
            }
        });
        return json;
    }

    public static AssetMetaDatabase load(FileHandle file) {
        if (file == null || !file.exists()) {
            return new AssetMetaDatabase();
        }

        AssetMetaDatabase db = JSON.fromJson(AssetMetaDatabase.class, file);
        if (db == null) {
            db = new AssetMetaDatabase();
        }

        db.normalizeAfterLoad();
        return db;
    }

    public void save(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("file is null");

        version = CURRENT_VERSION;
        normalizeAssetDefaults();

        String text = JSON.prettyPrint(this);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        StudioIO.writeAtomic(file, out -> out.write(bytes));
    }

    private void normalizeAfterLoad() {
        version = Math.max(version, 1);

        if (assets == null) {
            assets = new Array<>();
        }

        int maxId = 0;
        normalizeAssetDefaults();
        for (AssetMeta asset : assets) {
            if (asset != null && asset.id > maxId) {
                maxId = asset.id;
            }
        }

        if (nextId <= maxId) {
            nextId = maxId + 1;
        }
        if (nextId < 1) {
            nextId = 1;
        }
    }

    private void normalizeAssetDefaults() {
        if (assets == null) {
            assets = new Array<>();
        }

        for (AssetMeta asset : assets) {
            if (asset == null) continue;

            if (asset.scope == null) {
                asset.scope = AssetMeta.AssetScope.USER;
            }

            if (asset instanceof AnimationAssetMeta animation && animation.clips == null) {
                animation.clips = new ObjectMap<>();
            }

            if (asset instanceof TilesetAssetMeta tileset) {
                tileset.normalizeProfileDefaults();
            }
        }
    }

    private static String tiledProjectionWireName(SceneMetaRuntime.TiledProjection projection) {
        if (projection == SceneMetaRuntime.TiledProjection.ISO) return "isometric";
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) return "orthogonal";
        return null;
    }

    private static SceneMetaRuntime.TiledProjection tiledProjectionFromWireName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if ("isometric".equalsIgnoreCase(raw) || "ISO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ISO;
        }
        if ("orthogonal".equalsIgnoreCase(raw) || "ORTHO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ORTHO;
        }
        return null;
    }

    // ----------------------------------------------------
    // Json serializer for abstract AssetMeta
    // ----------------------------------------------------

    private static final class AssetMetaJsonSerializer implements Json.Serializer<AssetMeta> {

        @Override
        public void write(Json json, AssetMeta object, Class knownType) {
            json.writeObjectStart();

            json.writeValue("id", object.id);
            json.writeValue("type", object.type != null ? object.type.wireName() : null);
            json.writeValue("logicalPath", object.logicalPath);
            json.writeValue("sourceRelPath", object.sourceRelPath);
            json.writeValue("scope", object.scope != null ? object.scope.name() : null);

            if (object instanceof AnimationAssetMeta animation) {
                json.writeValue("frameCount", animation.frameCount);
                json.writeValue("fps", animation.fps);
                json.writeValue("currentClip", animation.currentClip);
                writeAnimationClips(json, animation.clips);
            } else if (object instanceof TilesetAssetMeta tileset) {
                tileset.normalizeProfileDefaults();
                json.writeValue("imageWidth", tileset.imageWidth);
                json.writeValue("imageHeight", tileset.imageHeight);
                json.writeValue("tileWidth", tileset.tileWidth);
                json.writeValue("tileHeight", tileset.tileHeight);
                json.writeValue("columns", tileset.columns);
                json.writeValue("rows", tileset.rows);
                json.writeValue("spacing", tileset.spacing);
                json.writeValue("margin", tileset.margin);
                json.writeValue("referenceCellWidth", tileset.referenceCellWidth);
                json.writeValue("referenceCellHeight", tileset.referenceCellHeight);
                json.writeValue("projection", tiledProjectionWireName(tileset.projection));
                json.writeValue("anchor", tileset.anchor != null ? tileset.anchor.wireName() : null);
                json.writeValue("offsetX", tileset.offsetX);
                json.writeValue("offsetY", tileset.offsetY);
                json.writeValue("renderSize", tileset.renderSize != null ? tileset.renderSize.wireName() : null);
            } else if (object instanceof TileAssetMeta tile) {
                json.writeValue("tilesetId", tile.tilesetId);
                json.writeValue("sheetIndex", tile.sheetIndex);
                json.writeValue("cellX", tile.cellX);
                json.writeValue("cellY", tile.cellY);
            }

            json.writeObjectEnd();
        }

        @Override
        public AssetMeta read(Json json, JsonValue jsonData, Class type) {
            AssetType assetType = readAssetType(jsonData);
            AssetMeta meta = newEmptyMeta(assetType);

            meta.id = jsonData.getInt("id", 0);
            meta.type = assetType;
            meta.logicalPath = jsonData.getString("logicalPath", null);
            meta.sourceRelPath = jsonData.getString("sourceRelPath", null);
            meta.scope = readScope(jsonData);

            if (meta instanceof AnimationAssetMeta animation) {
                animation.frameCount = jsonData.getInt("frameCount", 0);
                animation.fps = jsonData.getFloat("fps", 0f);
                animation.currentClip = jsonData.getString("currentClip", null);
                animation.clips = readAnimationClips(jsonData.get("clips"));
            } else if (meta instanceof TilesetAssetMeta tileset) {
                tileset.imageWidth = jsonData.getInt("imageWidth", 0);
                tileset.imageHeight = jsonData.getInt("imageHeight", 0);
                tileset.tileWidth = jsonData.getInt("tileWidth", 0);
                tileset.tileHeight = jsonData.getInt("tileHeight", 0);
                tileset.columns = jsonData.getInt("columns", 0);
                tileset.rows = jsonData.getInt("rows", 0);
                tileset.spacing = jsonData.getInt("spacing", 0);
                tileset.margin = jsonData.getInt("margin", 0);
                tileset.referenceCellWidth = jsonData.getInt("referenceCellWidth", 0);
                tileset.referenceCellHeight = jsonData.getInt("referenceCellHeight", 0);
                tileset.projection = tiledProjectionFromWireName(jsonData.getString("projection", null));
                tileset.anchor = TilesetAnchor.fromWireName(jsonData.getString("anchor", null));
                tileset.offsetX = jsonData.getInt("offsetX", 0);
                tileset.offsetY = jsonData.getInt("offsetY", 0);
                tileset.renderSize = TilesetRenderSize.fromWireName(jsonData.getString("renderSize", null));
                tileset.normalizeProfileDefaults();
            } else if (meta instanceof TileAssetMeta tile) {
                tile.tilesetId = jsonData.getInt("tilesetId", -1);
                tile.sheetIndex = jsonData.getInt("sheetIndex", -1);
                tile.cellX = jsonData.getInt("cellX", -1);
                tile.cellY = jsonData.getInt("cellY", -1);
            }

            return meta;
        }

        private static AssetType readAssetType(JsonValue jsonData) {
            String raw = jsonData.getString("type", null);
            AssetType assetType = AssetType.fromWireName(raw);
            if (assetType == null) {
                throw new IllegalArgumentException("Unknown asset type: " + raw);
            }
            return assetType;
        }

        private static AssetMeta.AssetScope readScope(JsonValue jsonData) {
            String raw = jsonData.getString("scope", AssetMeta.AssetScope.USER.name());
            if (raw == null || raw.isBlank()) {
                return AssetMeta.AssetScope.USER;
            }
            return AssetMeta.AssetScope.valueOf(raw);
        }

        private static void writeAnimationClips(Json json, ObjectMap<String, AnimationComponent.Clip> clips) {
            json.writeObjectStart("clips");
            if (clips != null) {
                for (ObjectMap.Entry<String, AnimationComponent.Clip> entry : clips) {
                    if (entry == null || entry.key == null || entry.key.isBlank() || entry.value == null) {
                        continue;
                    }

                    AnimationComponent.Clip clip = entry.value;
                    json.writeObjectStart(entry.key);
                    json.writeValue("start", clip.start);
                    json.writeValue("end", clip.end);
                    json.writeValue("flipX", clip.flipX);
                    json.writeObjectEnd();
                }
            }
            json.writeObjectEnd();
        }

        private static ObjectMap<String, AnimationComponent.Clip> readAnimationClips(JsonValue clipsJson) {
            ObjectMap<String, AnimationComponent.Clip> clips = new ObjectMap<>();
            if (clipsJson == null || !clipsJson.isObject()) {
                return clips;
            }

            for (JsonValue child = clipsJson.child; child != null; child = child.next) {
                String name = child.name;
                if (name == null || name.isBlank()) {
                    continue;
                }

                AnimationComponent.Clip clip = new AnimationComponent.Clip(
                        child.getInt("start", 0),
                        child.getInt("end", 0)
                );
                clip.flipX = child.getBoolean("flipX", false);
                clips.put(name, clip);
            }
            return clips;
        }
    }
}
