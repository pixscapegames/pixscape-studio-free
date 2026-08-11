package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.io.StudioIO;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class AssetMetaDatabase implements Json.Serializable {

    public static final int CURRENT_VERSION = 3;

    private int version = CURRENT_VERSION;
    private int nextId = 1;
    private final Array<AssetMeta> assets = new Array<>();
    private final IntMap<AssetMeta> byId = new IntMap<>();
    private final ObjectMap<String, AssetMeta> byLogicalPath = new ObjectMap<>();
    private final ObjectMap<String, SourceAssets> bySourceRelPath = new ObjectMap<>();

    private int indexBuildAssetVisits;

    public AssetMetaDatabase() {
    }

    public int size() {
        return assets.size;
    }

    public boolean isEmpty() {
        return assets.size == 0;
    }

    public AssetMeta assetAt(int index) {
        return assets.get(index);
    }

    public int version() {
        return version;
    }

    public int nextId() {
        return nextId;
    }

    /**
     * Allocates an ID in the shared authored asset/tiled-animation ID sequence.
     * The allocation is monotonic and may intentionally leave a gap.
     */
    public int allocateNextId() {
        ensureIdCanBeAllocated();
        return nextId++;
    }

    // ----------------------------------------------------
    // Lookup
    // ----------------------------------------------------

    public AssetMeta findByLogicalPath(String logicalPath) {
        return isBlank(logicalPath) ? null : byLogicalPath.get(logicalPath);
    }

    public AssetMeta findById(int id) {
        return id > 0 ? byId.get(id) : null;
    }

    public int sourceOwnerCount(String sourceRelPath) {
        SourceAssets matches = sourceOwners(sourceRelPath);
        return matches != null ? matches.assets.size : 0;
    }

    public AssetMeta sourceOwnerAt(String sourceRelPath, int ownerIndex) {
        SourceAssets matches = sourceOwners(sourceRelPath);
        int count = matches != null ? matches.assets.size : 0;
        if (ownerIndex < 0 || ownerIndex >= count) {
            throw new IndexOutOfBoundsException(
                    "Source owner index out of range: path='" + sourceRelPath
                            + "', index=" + ownerIndex + ", ownerCount=" + count + ".");
        }
        return matches.assets.get(ownerIndex);
    }

    public AssetMeta findUniqueBySourceRelPath(String sourceRelPath) {
        SourceAssets matches = sourceOwners(sourceRelPath);
        if (matches == null) return null;
        if (matches.assets.size == 1) return matches.assets.first();
        throw ambiguousSource(sourceRelPath, null, matches);
    }

    public AssetMeta findUniqueBySourceRelPath(String sourceRelPath, AssetType type) {
        Objects.requireNonNull(type, "type");
        SourceAssets matches = sourceOwners(sourceRelPath);
        if (matches == null) return null;

        AssetMeta found = null;
        int matchCount = 0;
        for (int i = 0; i < matches.assets.size; i++) {
            AssetMeta candidate = matches.assets.get(i);
            if (candidate.type() != type) continue;
            found = candidate;
            matchCount++;
        }
        if (matchCount == 0) return null;
        if (matchCount == 1) return found;
        throw ambiguousSource(sourceRelPath, type, matches);
    }

    private SourceAssets sourceOwners(String sourceRelPath) {
        return isBlank(sourceRelPath) ? null : bySourceRelPath.get(sourceRelPath);
    }

    // ----------------------------------------------------
    // Removal
    // ----------------------------------------------------

    public boolean removeById(int id) {
        AssetMeta asset = findById(id);
        return asset != null && removeIndexedAsset(asset);
    }

    public boolean removeByLogicalPath(String logicalPath) {
        AssetMeta asset = findByLogicalPath(logicalPath);
        return asset != null && removeIndexedAsset(asset);
    }

    public int removeByLogicalPathPrefix(String prefix) {
        if (isBlank(prefix)) return 0;

        int removed = 0;
        for (int i = assets.size - 1; i >= 0; i--) {
            AssetMeta asset = assets.get(i);
            if (asset.logicalPath().startsWith(prefix)) {
                verifyIndexed(asset);
                assets.removeIndex(i);
                removeFromIndexes(asset);
                removed++;
            }
        }
        return removed;
    }

    private boolean removeIndexedAsset(AssetMeta asset) {
        verifyIndexed(asset);
        int index = assets.indexOf(asset, true);
        if (index < 0) {
            throw new IllegalStateException(
                    "Indexed asset is missing from the source collection: " + describe(asset));
        }
        assets.removeIndex(index);
        removeFromIndexes(asset);
        return true;
    }

    private void removeFromIndexes(AssetMeta asset) {
        if (byId.remove(asset.id()) != asset
                || byLogicalPath.remove(asset.logicalPath()) != asset) {
            throw new IllegalStateException(
                    "Identity indexes are inconsistent for " + describe(asset) + ".");
        }
        if (asset.sourceRelPath() != null) {
            removeSourceIndex(bySourceRelPath, asset);
        }
    }

    // ----------------------------------------------------
    // Registration and identity mutation
    // ----------------------------------------------------

    public AssetMeta registerIfAbsent(AssetType type,
                                      String logicalPath,
                                      String sourceRelPath,
                                      AssetMeta.AssetScope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scope, "scope");
        requireLogicalPath(logicalPath);
        String normalizedSource = normalizeOptionalSourcePath(sourceRelPath);

        AssetMeta existing = byLogicalPath.get(logicalPath);
        if (existing != null) {
            if (existing.type() != type) {
                throw new IllegalStateException(
                        "Asset type collision for logicalPath '" + logicalPath
                                + "': existing=" + describe(existing)
                                + ", requestedType=" + type + ".");
            }
            if (normalizedSource != null) {
                String existingSource = existing.sourceRelPath();
                if (existingSource == null) {
                    updateSourceRelPath(existing.id(), normalizedSource);
                } else if (!existingSource.equals(normalizedSource)) {
                    throw new IllegalStateException(
                            "Asset source collision for logicalPath '" + logicalPath
                                    + "': existingSource='" + existingSource
                                    + "', requestedSource='" + normalizedSource + "'.");
                }
            }
            promoteScopeIfNeeded(existing, scope);
            return existing;
        }

        verifyIdentityAvailable(-1, type, logicalPath, normalizedSource);
        ensureIdCanBeAllocated();

        int id = nextId;
        AssetMeta created = newMeta(id, type, logicalPath, normalizedSource, scope);
        validateAsset(created);

        assets.add(created);
        byId.put(id, created);
        byLogicalPath.put(logicalPath, created);
        if (normalizedSource != null) {
            addSourceIndex(bySourceRelPath, created);
        }
        nextId++;
        return created;
    }

    public boolean updateLogicalPath(int assetId, String newLogicalPath) {
        AssetMeta asset = requireAsset(assetId);
        return updateIdentity(
                assetId,
                newLogicalPath,
                asset.sourceRelPath()
        );
    }

    public boolean updateSourceRelPath(int assetId, String newSourceRelPath) {
        AssetMeta asset = requireAsset(assetId);
        return updateIdentity(
                assetId,
                asset.logicalPath(),
                newSourceRelPath
        );
    }

    public boolean updateIdentity(int assetId,
                                  String newLogicalPath,
                                  String newSourceRelPath) {
        AssetMeta asset = requireAsset(assetId);
        requireLogicalPath(newLogicalPath);
        String normalizedSource = normalizeOptionalSourcePath(newSourceRelPath);

        String oldLogicalPath = asset.logicalPath();
        String oldSourceRelPath = asset.sourceRelPath();
        if (oldLogicalPath.equals(newLogicalPath)
                && Objects.equals(oldSourceRelPath, normalizedSource)) {
            return false;
        }

        verifyIdentityAvailable(assetId, asset.type(), newLogicalPath, normalizedSource);
        verifyIndexed(asset);

        try {
            if (byLogicalPath.remove(oldLogicalPath) != asset) {
                throw new IllegalStateException(
                        "Logical path index is inconsistent for " + describe(asset) + ".");
            }
            if (oldSourceRelPath != null) {
                removeSourceIndex(bySourceRelPath, asset);
            }
            asset.replaceIdentityPaths(newLogicalPath, normalizedSource);
            byLogicalPath.put(newLogicalPath, asset);
            if (normalizedSource != null) {
                addSourceIndex(bySourceRelPath, asset);
            }
        } catch (RuntimeException failure) {
            asset.replaceIdentityPaths(oldLogicalPath, oldSourceRelPath);
            try {
                rebuildIndexesAndValidate();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return true;
    }

    /**
     * Replaces the complete catalog with a deep copy of the validated restored
     * state. No mutable metadata state is shared between the databases.
     */
    public void replaceStateFrom(AssetMetaDatabase restored) {
        Objects.requireNonNull(restored, "restored");
        restored.verifyInternalState();

        Array<AssetMeta> candidateAssets = new Array<>(restored.assets.size);
        for (int i = 0; i < restored.assets.size; i++) {
            candidateAssets.add(copyAsset(restored.assets.get(i)));
        }
        ValidatedIndexes candidateIndexes = buildValidatedIndexes(candidateAssets);
        int candidateNextId = normalizedNextId(restored.nextId, candidateIndexes.maxId);
        int candidateVersion = Math.max(restored.version, 1);

        assets.clear();
        assets.addAll(candidateAssets);
        version = candidateVersion;
        nextId = candidateNextId;
        publishIndexes(candidateIndexes);
    }

    private static AssetMeta copyAsset(AssetMeta source) {
        AssetMeta copy = newMeta(
                source.id(),
                source.type(),
                source.logicalPath(),
                source.sourceRelPath(),
                source.scope
        );
        if (source instanceof AnimationAssetMeta sourceAnimation
                && copy instanceof AnimationAssetMeta copyAnimation) {
            copyAnimation.frameCount = sourceAnimation.frameCount;
            copyAnimation.fps = sourceAnimation.fps;
            copyAnimation.currentClip = sourceAnimation.currentClip;
            copyAnimation.clips = copyClips(sourceAnimation.clips);
        } else if (source instanceof TilesetAssetMeta sourceTileset
                && copy instanceof TilesetAssetMeta copyTileset) {
            copyTileset.imageWidth = sourceTileset.imageWidth;
            copyTileset.imageHeight = sourceTileset.imageHeight;
            copyTileset.tileWidth = sourceTileset.tileWidth;
            copyTileset.tileHeight = sourceTileset.tileHeight;
            copyTileset.columns = sourceTileset.columns;
            copyTileset.rows = sourceTileset.rows;
            copyTileset.spacing = sourceTileset.spacing;
            copyTileset.margin = sourceTileset.margin;
            copyTileset.referenceCellWidth = sourceTileset.referenceCellWidth;
            copyTileset.referenceCellHeight = sourceTileset.referenceCellHeight;
            copyTileset.projection = sourceTileset.projection;
            copyTileset.anchor = sourceTileset.anchor;
            copyTileset.offsetX = sourceTileset.offsetX;
            copyTileset.offsetY = sourceTileset.offsetY;
            copyTileset.renderSize = sourceTileset.renderSize;
        } else if (source instanceof TileAssetMeta sourceTile
                && copy instanceof TileAssetMeta copyTile) {
            copyTile.tilesetId = sourceTile.tilesetId;
            copyTile.sheetIndex = sourceTile.sheetIndex;
            copyTile.cellX = sourceTile.cellX;
            copyTile.cellY = sourceTile.cellY;
        }
        return copy;
    }

    private static ObjectMap<String, AnimationClipMeta> copyClips(
            ObjectMap<String, AnimationClipMeta> source) {
        ObjectMap<String, AnimationClipMeta> copy = new ObjectMap<>();
        if (source == null) return copy;
        for (ObjectMap.Entry<String, AnimationClipMeta> entry : source) {
            AnimationClipMeta sourceClip = entry.value;
            if (sourceClip == null) {
                copy.put(entry.key, null);
                continue;
            }
            copy.put(entry.key, sourceClip.copy());
        }
        return copy;
    }

    private static void promoteScopeIfNeeded(AssetMeta meta,
                                             AssetMeta.AssetScope requestedScope) {
        AssetMeta.AssetScope currentScope =
                meta.scope != null ? meta.scope : AssetMeta.AssetScope.USER;
        if (currentScope != AssetMeta.AssetScope.USER
                && requestedScope == AssetMeta.AssetScope.USER) {
            meta.scope = AssetMeta.AssetScope.USER;
        }
    }

    private AssetMeta requireAsset(int assetId) {
        AssetMeta asset = findById(assetId);
        if (asset == null) {
            throw new IllegalArgumentException("Asset not found: id=" + assetId + ".");
        }
        return asset;
    }

    private void verifyIdentityAvailable(int selfId,
                                         AssetType assetType,
                                         String logicalPath,
                                         String sourceRelPath) {
        AssetMeta logicalCollision = byLogicalPath.get(logicalPath);
        if (logicalCollision != null && logicalCollision.id() != selfId) {
            throw new IllegalStateException(
                    "Duplicate logicalPath '" + logicalPath + "' between asset id="
                            + selfId + " and " + describe(logicalCollision) + ".");
        }
        if (sourceRelPath != null) {
            SourceAssets sourceMatches = bySourceRelPath.get(sourceRelPath);
            AssetMeta sourceCollision =
                    firstIncompatibleSource(sourceMatches, selfId, assetType);
            if (sourceCollision != null) {
                throw new IllegalStateException(
                        "Duplicate sourceRelPath '" + sourceRelPath + "' between asset id="
                                + selfId + " and " + describe(sourceCollision) + ".");
            }
        }
    }

    private void ensureIdCanBeAllocated() {
        if (nextId < 1 || nextId == Integer.MAX_VALUE) {
            throw new IllegalStateException("Asset ID allocation overflow: nextId=" + nextId + ".");
        }
        if (byId.containsKey(nextId)) {
            throw new IllegalStateException(
                    "Asset ID allocator points to an existing ID: " + nextId + ".");
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

    // ----------------------------------------------------
    // Persistence
    // ----------------------------------------------------

    private static final AssetMetaJsonSerializer ASSET_META_SERIALIZER =
            new AssetMetaJsonSerializer();
    private static final Json JSON = buildJson();

    private static Json buildJson() {
        Json json = new Json();
        json.setUsePrototypes(false);
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        json.setSerializer(AssetMeta.class, ASSET_META_SERIALIZER);
        json.setSerializer(SceneMetaRuntime.TiledProjection.class,
                new Json.Serializer<SceneMetaRuntime.TiledProjection>() {
                    @Override
                    public void write(Json json,
                                      SceneMetaRuntime.TiledProjection object,
                                      Class knownType) {
                        json.writeValue(tiledProjectionWireName(object));
                    }

                    @Override
                    public SceneMetaRuntime.TiledProjection read(Json json,
                                                                 JsonValue jsonData,
                                                                 Class type) {
                        return tiledProjectionFromWireName(
                                jsonData != null ? jsonData.asString() : null);
                    }
                });
        json.setSerializer(TilesetAnchor.class, new Json.Serializer<TilesetAnchor>() {
            @Override
            public void write(Json json, TilesetAnchor object, Class knownType) {
                json.writeValue(object != null ? object.wireName() : null);
            }

            @Override
            public TilesetAnchor read(Json json, JsonValue jsonData, Class type) {
                return TilesetAnchor.fromWireName(
                        jsonData != null ? jsonData.asString() : null);
            }
        });
        json.setSerializer(TilesetRenderSize.class,
                new Json.Serializer<TilesetRenderSize>() {
                    @Override
                    public void write(Json json,
                                      TilesetRenderSize object,
                                      Class knownType) {
                        json.writeValue(object != null ? object.wireName() : null);
                    }

                    @Override
                    public TilesetRenderSize read(Json json,
                                                  JsonValue jsonData,
                                                  Class type) {
                        return TilesetRenderSize.fromWireName(
                                jsonData != null ? jsonData.asString() : null);
                    }
                });
        return json;
    }

    public static AssetMetaDatabase load(FileHandle file) {
        if (file == null || !file.exists()) {
            return new AssetMetaDatabase();
        }
        AssetMetaDatabase db = JSON.fromJson(AssetMetaDatabase.class, file);
        return db != null ? db : new AssetMetaDatabase();
    }

    public void save(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("file is null");

        version = CURRENT_VERSION;
        normalizeAssetDefaults();
        verifyInternalState();

        String text = JSON.prettyPrint(this);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        StudioIO.writeAtomic(file, out -> out.write(bytes));
    }

    @Override
    public void write(Json json) {
        json.writeValue("version", version);
        json.writeValue("nextId", nextId);
        json.writeArrayStart("assets");
        for (int i = 0; i < assets.size; i++) {
            ASSET_META_SERIALIZER.write(json, assets.get(i), AssetMeta.class);
        }
        json.writeArrayEnd();
    }

    @Override
    public void read(Json json, JsonValue jsonData) {
        version = Math.max(jsonData.getInt("version", CURRENT_VERSION), 1);
        nextId = jsonData.getInt("nextId", 1);
        assets.clear();

        JsonValue assetsJson = jsonData.get("assets");
        if (assetsJson != null) {
            for (JsonValue child = assetsJson.child; child != null; child = child.next) {
                assets.add(child.isNull()
                        ? null
                        : ASSET_META_SERIALIZER.read(json, child, AssetMeta.class));
            }
        }

        normalizeAssetDefaults();
        rebuildIndexesAndValidate();
        nextId = normalizedNextId(nextId, maxIndexedId());
    }

    private void normalizeAssetDefaults() {
        for (int i = 0; i < assets.size; i++) {
            AssetMeta asset = assets.get(i);
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
            String normalizedSource = normalizeOptionalSourcePath(asset.sourceRelPath());
            if (!Objects.equals(normalizedSource, asset.sourceRelPath())) {
                asset.replaceIdentityPaths(asset.logicalPath(), normalizedSource);
            }
        }
    }

    private void rebuildIndexesAndValidate() {
        ValidatedIndexes indexes = buildValidatedIndexes(assets);
        publishIndexes(indexes);
        indexBuildAssetVisits = assets.size;
    }

    private void publishIndexes(ValidatedIndexes indexes) {
        byId.clear();
        for (IntMap.Entry<AssetMeta> entry : indexes.byId) {
            byId.put(entry.key, entry.value);
        }
        byLogicalPath.clear();
        for (ObjectMap.Entry<String, AssetMeta> entry : indexes.byLogicalPath) {
            byLogicalPath.put(entry.key, entry.value);
        }
        bySourceRelPath.clear();
        for (ObjectMap.Entry<String, SourceAssets> entry : indexes.bySourceRelPath) {
            bySourceRelPath.put(entry.key, entry.value);
        }
        indexBuildAssetVisits = assets.size;
    }

    private static ValidatedIndexes buildValidatedIndexes(Array<AssetMeta> source) {
        ValidatedIndexes indexes = new ValidatedIndexes();
        for (int i = 0; i < source.size; i++) {
            AssetMeta asset = source.get(i);
            validateAsset(asset);

            AssetMeta idCollision = indexes.byId.get(asset.id());
            if (idCollision != null) {
                throw duplicateId(asset.id(), idCollision, asset);
            }
            AssetMeta logicalCollision = indexes.byLogicalPath.get(asset.logicalPath());
            if (logicalCollision != null) {
                throw new IllegalStateException(
                        "Duplicate logicalPath '" + asset.logicalPath() + "' between "
                                + describe(logicalCollision) + " and " + describe(asset) + ".");
            }
            String sourceRelPath = asset.sourceRelPath();
            if (sourceRelPath != null) {
                SourceAssets sourceMatches = indexes.bySourceRelPath.get(sourceRelPath);
                AssetMeta sourceCollision =
                        firstIncompatibleSource(sourceMatches, asset.id(), asset.type());
                if (sourceCollision != null) {
                    throw new IllegalStateException(
                            "Duplicate sourceRelPath '" + sourceRelPath + "' between "
                                    + describe(sourceCollision) + " and " + describe(asset) + ".");
                }
                addSourceIndex(indexes.bySourceRelPath, asset);
            }

            indexes.byId.put(asset.id(), asset);
            indexes.byLogicalPath.put(asset.logicalPath(), asset);
            indexes.maxId = Math.max(indexes.maxId, asset.id());
        }
        return indexes;
    }

    private void verifyInternalState() {
        ValidatedIndexes validated = buildValidatedIndexes(assets);
        if (validated.byId.size != byId.size
                || validated.byLogicalPath.size != byLogicalPath.size
                || validated.bySourceRelPath.size != bySourceRelPath.size) {
            throw new IllegalStateException("Asset metadata indexes do not match the source collection.");
        }
        for (int i = 0; i < assets.size; i++) {
            verifyIndexed(assets.get(i));
        }
        for (ObjectMap.Entry<String, SourceAssets> entry : validated.bySourceRelPath) {
            SourceAssets actual = bySourceRelPath.get(entry.key);
            if (actual == null || actual.assets.size != entry.value.assets.size) {
                throw new IllegalStateException(
                        "Source index bucket does not match the source collection: "
                                + entry.key + ".");
            }
            for (int i = 0; i < actual.assets.size; i++) {
                if (actual.assets.get(i) != entry.value.assets.get(i)) {
                    throw new IllegalStateException(
                            "Source index order does not match the source collection: "
                                    + entry.key + ".");
                }
            }
        }
        normalizedNextId(nextId, validated.maxId);
    }

    private void verifyIndexed(AssetMeta asset) {
        if (byId.get(asset.id()) != asset
                || byLogicalPath.get(asset.logicalPath()) != asset
                || (asset.sourceRelPath() != null
                && !sourceIndexContains(bySourceRelPath, asset))) {
            throw new IllegalStateException(
                    "Asset metadata indexes are inconsistent for " + describe(asset) + ".");
        }
    }

    private int maxIndexedId() {
        int maxId = 0;
        for (IntMap.Entry<AssetMeta> entry : byId) {
            maxId = Math.max(maxId, entry.key);
        }
        return maxId;
    }

    private static int normalizedNextId(int requestedNextId, int maxId) {
        if (maxId == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Asset ID allocation overflow: max asset ID is Integer.MAX_VALUE.");
        }
        int minimum = maxId + 1;
        return Math.max(Math.max(requestedNextId, 1), minimum);
    }

    private static void validateAsset(AssetMeta asset) {
        if (asset == null) {
            throw new IllegalStateException("Asset metadata collection contains a null asset.");
        }
        if (asset.id() <= 0) {
            throw new IllegalStateException(
                    "Asset ID must be > 0: " + describe(asset) + ".");
        }
        if (asset.type() == null) {
            throw new IllegalStateException(
                    "Asset type must not be null: " + describe(asset) + ".");
        }
        if (asset.scope == null) {
            throw new IllegalStateException(
                    "Asset scope must not be null: " + describe(asset) + ".");
        }
        AssetType concreteType = concreteType(asset);
        if (asset.type() != concreteType) {
            throw new IllegalStateException(
                    "Asset concrete type mismatch: class=" + asset.getClass().getSimpleName()
                            + ", declaredType=" + asset.type()
                            + ", expectedType=" + concreteType
                            + ", asset=" + describe(asset) + ".");
        }
        requireLogicalPath(asset.logicalPath());
    }

    private static AssetType concreteType(AssetMeta asset) {
        if (asset instanceof ImageAssetMeta) return AssetType.IMAGE;
        if (asset instanceof AnimationAssetMeta) return AssetType.ANIMATION;
        if (asset instanceof ParticleAssetMeta) return AssetType.PARTICLE;
        if (asset instanceof TilesetAssetMeta) return AssetType.TILESET;
        if (asset instanceof TileAssetMeta) return AssetType.TILE;
        throw new IllegalStateException(
                "Unsupported AssetMeta class: " + asset.getClass().getName() + ".");
    }

    private static IllegalStateException duplicateId(int id,
                                                     AssetMeta first,
                                                     AssetMeta second) {
        return new IllegalStateException(
                "Duplicate asset ID " + id + " between "
                        + describe(first) + " logicalPath='" + first.logicalPath()
                        + "' and " + describe(second) + " logicalPath='"
                        + second.logicalPath() + "'.");
    }

    private static void requireLogicalPath(String logicalPath) {
        if (isBlank(logicalPath)) {
            throw new IllegalArgumentException(
                    "Asset logicalPath must not be null or blank: '" + logicalPath + "'.");
        }
    }

    private static String normalizeOptionalSourcePath(String sourceRelPath) {
        return isBlank(sourceRelPath) ? null : sourceRelPath;
    }

    private static boolean isBlank(String value) {
        if (value == null || value.length() == 0) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }

    private static String describe(AssetMeta asset) {
        if (asset == null) return "null";
        return asset.getClass().getSimpleName()
                + "{id=" + asset.id()
                + ", type=" + asset.type()
                + ", logicalPath='" + asset.logicalPath()
                + "', sourceRelPath='" + asset.sourceRelPath() + "'}";
    }

    private static IllegalStateException ambiguousSource(String sourceRelPath,
                                                         AssetType type,
                                                         SourceAssets matches) {
        int ownerCount = 0;
        for (int i = 0; i < matches.assets.size; i++) {
            if (type == null || matches.assets.get(i).type() == type) {
                ownerCount++;
            }
        }
        StringBuilder message = new StringBuilder("Ambiguous sourceRelPath '")
                .append(sourceRelPath)
                .append("': ");
        if (type != null) {
            message.append("multiple owners of type ").append(type).append("; ");
        }
        message.append("ownerCount=").append(ownerCount).append(", owners=[");
        boolean first = true;
        for (int i = 0; i < matches.assets.size; i++) {
            AssetMeta owner = matches.assets.get(i);
            if (type != null && owner.type() != type) continue;
            if (!first) message.append(", ");
            first = false;
            message.append("{id=").append(owner.id())
                    .append(", type=").append(owner.type())
                    .append(", logicalPath='").append(owner.logicalPath()).append("'}");
        }
        return new IllegalStateException(message.append("].").toString());
    }

    /**
     * Atlas-backed TILE metadata may share the tileset sheet source with other
     * tiles and its TILESET. Other asset types keep a unique non-null source.
     */
    private static AssetMeta firstIncompatibleSource(SourceAssets matches,
                                                     int selfId,
                                                     AssetType requestedType) {
        if (matches == null) return null;
        for (int i = 0; i < matches.assets.size; i++) {
            AssetMeta existing = matches.assets.get(i);
            if (existing.id() == selfId) continue;
            boolean compatibleAtlasOwner =
                    requestedType == AssetType.TILE
                            && (existing.type() == AssetType.TILE
                            || existing.type() == AssetType.TILESET);
            boolean compatibleLateTileset =
                    requestedType == AssetType.TILESET
                            && existing.type() == AssetType.TILE;
            if (!compatibleAtlasOwner && !compatibleLateTileset) {
                return existing;
            }
        }
        return null;
    }

    private static void addSourceIndex(ObjectMap<String, SourceAssets> index,
                                       AssetMeta asset) {
        SourceAssets matches = index.get(asset.sourceRelPath());
        if (matches == null) {
            matches = new SourceAssets();
            index.put(asset.sourceRelPath(), matches);
        }
        int insertIndex = matches.assets.size;
        while (insertIndex > 0
                && matches.assets.get(insertIndex - 1).id() > asset.id()) {
            insertIndex--;
        }
        matches.assets.insert(insertIndex, asset);
    }

    private static void removeSourceIndex(ObjectMap<String, SourceAssets> index,
                                          AssetMeta asset) {
        SourceAssets matches = index.get(asset.sourceRelPath());
        if (matches == null || !matches.assets.removeValue(asset, true)) {
            throw new IllegalStateException(
                    "Source index is inconsistent for " + describe(asset) + ".");
        }
        if (matches.assets.size == 0) {
            index.remove(asset.sourceRelPath());
        }
    }

    private static boolean sourceIndexContains(
            ObjectMap<String, SourceAssets> index,
            AssetMeta asset) {
        SourceAssets matches = index.get(asset.sourceRelPath());
        return matches != null && matches.assets.contains(asset, true);
    }

    int indexBuildAssetVisits() {
        return indexBuildAssetVisits;
    }

    private static String tiledProjectionWireName(
            SceneMetaRuntime.TiledProjection projection) {
        if (projection == SceneMetaRuntime.TiledProjection.ISO) return "isometric";
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) return "orthogonal";
        return null;
    }

    private static SceneMetaRuntime.TiledProjection tiledProjectionFromWireName(
            String raw) {
        if (isBlank(raw)) return null;
        if ("isometric".equalsIgnoreCase(raw) || "ISO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ISO;
        }
        if ("orthogonal".equalsIgnoreCase(raw) || "ORTHO".equalsIgnoreCase(raw)) {
            return SceneMetaRuntime.TiledProjection.ORTHO;
        }
        return null;
    }

    private static final class ValidatedIndexes {
        final IntMap<AssetMeta> byId = new IntMap<>();
        final ObjectMap<String, AssetMeta> byLogicalPath = new ObjectMap<>();
        final ObjectMap<String, SourceAssets> bySourceRelPath = new ObjectMap<>();
        int maxId;
    }

    private static final class SourceAssets {
        final Array<AssetMeta> assets = new Array<>();
    }

    private static final class AssetMetaJsonSerializer
            implements Json.Serializer<AssetMeta> {

        @Override
        public void write(Json json, AssetMeta object, Class knownType) {
            json.writeObjectStart();
            json.writeValue("id", object.id());
            json.writeValue("type",
                    object.type() != null ? object.type().wireName() : null);
            json.writeValue("logicalPath", object.logicalPath());
            json.writeValue("sourceRelPath", object.sourceRelPath());
            json.writeValue("scope",
                    object.scope != null ? object.scope.name() : null);

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
                json.writeValue("anchor",
                        tileset.anchor != null ? tileset.anchor.wireName() : null);
                json.writeValue("offsetX", tileset.offsetX);
                json.writeValue("offsetY", tileset.offsetY);
                json.writeValue("renderSize",
                        tileset.renderSize != null ? tileset.renderSize.wireName() : null);
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
            int id = jsonData.getInt("id", 0);
            String logicalPath = jsonData.getString("logicalPath", null);
            String sourceRelPath = normalizeOptionalSourcePath(
                    jsonData.getString("sourceRelPath", null));
            AssetMeta meta = newMeta(
                    id,
                    assetType,
                    logicalPath,
                    sourceRelPath,
                    readScope(jsonData)
            );

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
                tileset.referenceCellWidth =
                        jsonData.getInt("referenceCellWidth", 0);
                tileset.referenceCellHeight =
                        jsonData.getInt("referenceCellHeight", 0);
                tileset.projection = tiledProjectionFromWireName(
                        jsonData.getString("projection", null));
                tileset.anchor = TilesetAnchor.fromWireName(
                        jsonData.getString("anchor", null));
                tileset.offsetX = jsonData.getInt("offsetX", 0);
                tileset.offsetY = jsonData.getInt("offsetY", 0);
                tileset.renderSize = TilesetRenderSize.fromWireName(
                        jsonData.getString("renderSize", null));
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
            String raw = jsonData.getString(
                    "scope", AssetMeta.AssetScope.USER.name());
            if (isBlank(raw)) {
                return AssetMeta.AssetScope.USER;
            }
            return AssetMeta.AssetScope.valueOf(raw);
        }

        private static void writeAnimationClips(
                Json json,
                ObjectMap<String, AnimationClipMeta> clips) {
            json.writeObjectStart("clips");
            if (clips != null) {
                for (ObjectMap.Entry<String, AnimationClipMeta> entry : clips) {
                    if (entry == null
                            || isBlank(entry.key)
                            || entry.value == null) {
                        continue;
                    }
                    AnimationClipMeta clip = entry.value;
                    json.writeObjectStart(entry.key);
                    json.writeValue("start", clip.start);
                    json.writeValue("end", clip.end);
                    json.writeValue("flipX", clip.flipX);
                    json.writeObjectEnd();
                }
            }
            json.writeObjectEnd();
        }

        private static ObjectMap<String, AnimationClipMeta> readAnimationClips(
                JsonValue clipsJson) {
            ObjectMap<String, AnimationClipMeta> clips = new ObjectMap<>();
            if (clipsJson == null || !clipsJson.isObject()) {
                return clips;
            }
            for (JsonValue child = clipsJson.child;
                 child != null;
                 child = child.next) {
                String name = child.name;
                if (isBlank(name)) continue;

                AnimationClipMeta clip = new AnimationClipMeta(
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
