package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.helper.RuntimeShaderResources;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.ProjectFileCleanupService;
import games.pixscape.studio.service.asset.StudioAnimationAssets;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RuntimeExport {
    public static final String RUNTIME_PROJECT_KIND = "pixscape-runtime-project";

    public static final String RUNTIME_DIR_NAME = "pixscape-project";
    public static final String PROJECT_JSON = "project.json";
    private static final String ANIMATIONS_JSON = "animations.json";
    private static final String TILESET_PROFILES_JSON = "tileset-profiles.json";
    private static final String TILESET_PROFILES_FORMAT = "pixscape.tileset-profiles";

    // Components to exclude from the runtime (noms "courts" Artemis)
    private static final ObjectSet<String> RUNTIME_EXCLUDED_COMPONENTS = new ObjectSet<>();

    static {
        RUNTIME_EXCLUDED_COMPONENTS.add("EntityMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("LayerMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("CameraMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsRuntimeBodyComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsRuntimeJointComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsCompiledFixturesComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("SpatialPhysicsFootprintComponent");
    }

    private static final Json JSON = new Json();

    static {
        JSON.setUsePrototypes(false);
        JSON.setOutputType(JsonWriter.OutputType.json);
        JSON.setIgnoreUnknownFields(true);
    }

    private RuntimeExport() {
    }

    /**
     * Export runtime to: <userProjectDir>/<RUNTIME_DIR_NAME>/
     *
     * @param studioCfg        Studio config (source of truth for scenes/options)
     * @param studioProjectDir Studio project directory selected by the user
     * @param userProjectDir   chosen export parent directory
     */
    public static RuntimeConfig exportRuntime(ProjectConfig studioCfg,
                                              FileHandle studioProjectDir,
                                              FileHandle userProjectDir) {
        if (studioCfg == null) throw new GdxRuntimeException("studioCfg is null");
        if (studioProjectDir == null) throw new GdxRuntimeException("studioProjectDir is null");
        if (userProjectDir == null) throw new GdxRuntimeException("userProjectDir is null");

        if (studioCfg.projectFileName == null || studioCfg.projectFileName.isBlank()) {
            throw new GdxRuntimeException("studioCfg.projectFileName is blank");
        }

        // 0) runtime root dir = <userProjectDir>/pixscape-project
        FileHandle runtimeDir = userProjectDir.child(RUNTIME_DIR_NAME);

        if (runtimeDir.exists()) {
            runtimeDir.deleteDirectory();
        }

        runtimeDir.mkdirs();

        // 1) RuntimeConfig
        RuntimeConfig out = new RuntimeConfig();
        out.version = (studioCfg.version != null && !studioCfg.version.isBlank())
                ? studioCfg.version
                : RuntimeConfig.DEFAULT_VERSION;

        out.projectFileName = studioCfg.projectFileName;

        // Optional/informational: it can be filled here once during export
        out.runtimeRootDir = runtimeDir.path();

        out.scenesDir = RuntimeFs.DIR_SCENES;
        out.atlasesDir = RuntimeFs.DIR_ATLASES;
        out.effectsDir = RuntimeFs.DIR_EFFECTS;
        out.animationsDir = RuntimeFs.DIR_ANIMATIONS;
        out.shadersDir = RuntimeFs.DIR_SHADERS;
        out.audioDir = RuntimeFs.DIR_AUDIO;
        out.gameObjectsDir = RuntimeFs.DIR_GAME_OBJECTS;
        out.glSamples = studioCfg.glSamples;

        // 2) Studio scenes -> runtime (deterministic order)
        ObjectMap<String, SceneMeta> studioScenes = studioCfg.getScenesMap();
        if (studioScenes == null || studioScenes.size == 0) {
            throw new GdxRuntimeException("No scenes to export (studio config has empty scenes map).");
        }

        Array<String> sceneNames = new Array<>();
        for (ObjectMap.Entry<String, SceneMeta> e : studioScenes) {
            if (e.key != null) {
                sceneNames.add(e.key);
            }
        }
        sceneNames.sort(String::compareTo);

        for (String sceneName : sceneNames) {
            if (sceneName == null || sceneName.isBlank()) continue;

            SceneMeta studioMeta = studioScenes.get(sceneName);
            if (studioMeta == null) continue;

            SceneAmbientLighting.applyDefaultsAndDerive(studioMeta);

            String file = RuntimeFs.filenameOnly(studioMeta.file);
            if (file == null || file.isBlank()) {
                throw new GdxRuntimeException("Scene '" + sceneName + "' has no file; cannot export.");
            }

            SceneMetaRuntime runtimeMeta = new SceneMetaRuntime(studioMeta);
            runtimeMeta.name = (studioMeta.name != null && !studioMeta.name.isBlank())
                    ? studioMeta.name
                    : sceneName;
            runtimeMeta.file = file;

            if (runtimeMeta.pixelsPerMeter <= 0f) {
                runtimeMeta.pixelsPerMeter = 100f;
            }

            out.scenes.put(sceneName, runtimeMeta);
        }

        // 3) safe current scene
        out.currentSceneName = studioCfg.getCurrentSceneName();
        if (out.currentSceneName == null || !out.scenes.containsKey(out.currentSceneName)) {
            out.currentSceneName = out.firstSceneNameSorted();
        }

        // 4) Validation AVANT export physique
        out.applyDefaultsAndValidate(runtimeDir.child(PROJECT_JSON).path());

        // 5) Export runtime scenes
        FileHandle studioScenesDir = studioProjectDir.child(StudioFs.DIR_SCENES);
        FileHandle runtimeScenesDir = runtimeDir.child(out.scenesDir);
        runtimeScenesDir.mkdirs();

        for (ObjectMap.Entry<String, SceneMetaRuntime> e : out.scenes) {
            SceneMetaRuntime sm = e.value;
            if (sm == null) continue;

            FileHandle in = studioScenesDir.child(sm.file);
            if (!in.exists()) {
                throw new GdxRuntimeException("Missing studio scene file: " + in.path());
            }

            FileHandle outScene = runtimeScenesDir.child(sm.file);
            sanitizeArtemisSceneJson(in, outScene, RUNTIME_EXCLUDED_COMPONENTS);
        }

        // 6) Copier les ressources runtime
        // Do not export Studio animation source frames.
        // Runtime sprite animations are atlas-backed.
        copyAtlasesWithoutInput(
                studioProjectDir.child(StudioFs.DIR_ATLASES),
                runtimeDir.child(out.atlasesDir)
        );
        copyDirIfExists(
                studioProjectDir.child(StudioFs.DIR_ORIG_EFFECTS),
                runtimeDir.child(out.effectsDir)
        );
        copyRuntimeShaderResources(runtimeDir.child(out.shadersDir));
        copyDirIfExists(
                studioProjectDir.child(StudioFs.DIR_ORIG_SHADERS),
                runtimeDir.child(out.shadersDir)
        );
        copyDirIfExists(
                studioProjectDir.child(StudioFs.DIR_ORIG_AUDIO),
                runtimeDir.child(out.audioDir)
        );
        copyGameObjectFiles(
                studioProjectDir.child(StudioFs.DIR_GAME_OBJECTS),
                runtimeDir.child(out.gameObjectsDir)
        );

        // 6b) Export tiled animations registry (runtime-ready only)
        exportTileAnimations(
                studioProjectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON),
                runtimeDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        exportAnimations(
                studioProjectDir.child(StudioFs.FILE_ASSETS_JSON),
                runtimeDir.child(ANIMATIONS_JSON)
        );
        exportTilesetProfiles(
                studioProjectDir.child(StudioFs.FILE_ASSETS_JSON),
                runtimeDir.child(TILESET_PROFILES_JSON),
                studioCfg,
                studioProjectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON),
                runtimeScenesDir,
                out
        );

        // 7) Final project.json write
        saveProject(out, runtimeDir, studioCfg);

        return out;
    }

    private static void copyRuntimeShaderResources(FileHandle shadersDir) {
        if (shadersDir == null) {
            return;
        }

        try {
            RuntimeShaderResources.copyTo(shadersDir.file().toPath());
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to export Pixscape runtime shaders: " + shadersDir.path(), e);
        }
    }

    private static void copyGameObjectFiles(FileHandle sourceDir, FileHandle targetDir) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }

        targetDir.mkdirs();

        for (FileHandle file : sourceDir.list()) {
            if (file == null || file.isDirectory()) {
                continue;
            }
            if (ProjectFileCleanupService.shouldSkipRuntimeExportFile(file)) {
                continue;
            }

            if (file.name().endsWith(GameObjectAsset.EXTENSION)) {
                file.copyTo(targetDir.child(file.name()));
            }
        }
    }

    private static void exportTileAnimations(FileHandle studioFile, FileHandle runtimeFile) {
        if (runtimeFile == null) {
            return;
        }

        TileAnimationsMetaDatabase studioDb;

        if (studioFile != null && studioFile.exists()) {
            studioDb = TileAnimationsIO.load(studioFile);
        } else {
            studioDb = TileAnimationsIO.createEmpty();
        }

        TileAnimationsMetaDatabase runtimeDb = TileAnimationsIO.createEmpty();
        runtimeDb.version = studioDb.version;

        for (TileAnimationProjectDefData def : studioDb.animations) {
            if (!TileAnimationsIO.isExportable(def)) {
                continue;
            }

            TileAnimationProjectDefData exported = new TileAnimationProjectDefData();
            exported.id = def.id;
            exported.name = def.name;
            exported.frameAssetIds = def.frameAssetIds != null
                    ? java.util.Arrays.copyOf(def.frameAssetIds, def.frameAssetIds.length)
                    : new int[0];
            exported.frameDurationsMs = def.frameDurationsMs != null
                    ? java.util.Arrays.copyOf(def.frameDurationsMs, def.frameDurationsMs.length)
                    : new int[0];

            runtimeDb.animations.add(exported);
        }

        TileAnimationsIO.save(runtimeDb, runtimeFile);
    }

    private static void exportAnimations(FileHandle assetsFile, FileHandle runtimeFile) {
        if (runtimeFile == null) {
            return;
        }

        AssetMetaDatabase assetDb = AssetMetaDatabase.load(assetsFile);
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        JsonValue animations = new JsonValue(JsonValue.ValueType.array);

        for (int i = 0; i < assetDb.size(); i++) {
            AssetMeta meta = assetDb.assetAt(i);
                if (!(meta instanceof AnimationAssetMeta animation)) {
                    continue;
                }
                if (animation.id() <= 0) {
                    continue;
                }

                animations.addChild(animationJson(animation));
        }

        root.addChild("animations", animations);
        runtimeFile.parent().mkdirs();
        String pretty = root.prettyPrint(JsonWriter.OutputType.json, 120);
        StudioIO.writeAtomic(runtimeFile, out -> out.write(pretty.getBytes(StandardCharsets.UTF_8)));
    }

    private static void exportTilesetProfiles(FileHandle assetsFile,
                                              FileHandle runtimeFile,
                                              ProjectConfig studioCfg,
                                              FileHandle tileAnimationsFile,
                                              FileHandle runtimeScenesDir,
                                              RuntimeConfig runtimeCfg) {
        if (runtimeFile == null) {
            return;
        }

        AssetMetaDatabase assetDb = AssetMetaDatabase.load(assetsFile);
        TileAnimationsMetaDatabase tileAnimationsDb = tileAnimationsFile != null && tileAnimationsFile.exists()
                ? TileAnimationsIO.load(tileAnimationsFile)
                : TileAnimationsIO.createEmpty();
        IntSet runtimeTileAssetIds = collectRuntimeTileAssetIds(
                studioCfg,
                runtimeScenesDir,
                runtimeCfg,
                tileAnimationsDb
        );
        IntMap<IntArray> tileIdsByTilesetId = collectRuntimeTileIdsByTileset(assetDb, runtimeTileAssetIds);

        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("format", new JsonValue(TILESET_PROFILES_FORMAT));
        root.addChild("version", new JsonValue(1));

        JsonValue tilesets = new JsonValue(JsonValue.ValueType.array);
        if (tileIdsByTilesetId.size > 0) {
            Array<TilesetAssetMeta> exportableTilesets = new Array<>();
            for (int i = 0; i < assetDb.size(); i++) {
                AssetMeta meta = assetDb.assetAt(i);
                if (meta instanceof TilesetAssetMeta tileset
                        && tileset.id() > 0
                        && tileIdsByTilesetId.containsKey(tileset.id())) {
                    exportableTilesets.add(tileset);
                }
            }
            exportableTilesets.sort(RuntimeExport::compareTilesets);

            for (TilesetAssetMeta tileset : exportableTilesets) {
                IntArray tileIds = tileIdsByTilesetId.get(tileset.id());
                if (tileIds == null || tileIds.size == 0) {
                    continue;
                }
                tileIds.sort();
                tilesets.addChild(tilesetProfileJson(tileset, tileIds));
            }
        }
        root.addChild("tilesets", tilesets);

        runtimeFile.parent().mkdirs();
        String pretty = root.prettyPrint(JsonWriter.OutputType.json, 120);
        StudioIO.writeAtomic(runtimeFile, out -> out.write(pretty.getBytes(StandardCharsets.UTF_8)));
    }

    private static IntSet collectRuntimeTileAssetIds(ProjectConfig studioCfg,
                                                     FileHandle runtimeScenesDir,
                                                     RuntimeConfig runtimeCfg,
                                                     TileAnimationsMetaDatabase tileAnimationsDb) {
        IntSet out = new IntSet();
        collectRuntimeAvailabilityTileAssetIds(studioCfg, out, tileAnimationsDb);
        collectRuntimeSceneTileAssetIds(runtimeScenesDir, runtimeCfg, out, tileAnimationsDb);
        return out;
    }

    private static void collectRuntimeAvailabilityTileAssetIds(ProjectConfig studioCfg,
                                                               IntSet out,
                                                               TileAnimationsMetaDatabase tileAnimationsDb) {
        if (studioCfg == null) {
            return;
        }

        ObjectMap<String, SceneMeta> scenes = studioCfg.getScenesMap();
        if (scenes == null || scenes.size == 0) {
            return;
        }

        for (ObjectMap.Entry<String, SceneMeta> entry : scenes) {
            SceneMeta scene = entry != null ? entry.value : null;
            SceneRuntimeAvailabilityData availability = scene != null ? scene.runtimeAvailability : null;
            if (availability == null || availability.tiledTileAssetIds == null) {
                continue;
            }
            for (Integer assetId : availability.tiledTileAssetIds) {
                if (assetId != null && assetId > 0) {
                    out.add(assetId);
                }
            }
            if (availability.tiledAnimationIds != null) {
                for (Integer animationId : availability.tiledAnimationIds) {
                    addTiledAnimationFrameAssetIds(tileAnimationsDb, animationId, out);
                }
            }
        }
    }

    private static void collectRuntimeSceneTileAssetIds(FileHandle runtimeScenesDir,
                                                       RuntimeConfig runtimeCfg,
                                                       IntSet out,
                                                       TileAnimationsMetaDatabase tileAnimationsDb) {
        if (runtimeScenesDir == null || runtimeCfg == null || runtimeCfg.scenes == null || runtimeCfg.scenes.size == 0) {
            return;
        }

        for (ObjectMap.Entry<String, SceneMetaRuntime> entry : runtimeCfg.scenes) {
            SceneMetaRuntime scene = entry != null ? entry.value : null;
            String file = scene != null ? RuntimeFs.filenameOnly(scene.file) : null;
            if (file == null || file.isBlank()) {
                continue;
            }

            FileHandle sceneFile = runtimeScenesDir.child(file);
            if (!sceneFile.exists()) {
                throw new GdxRuntimeException("Runtime export missing exported scene file while collecting tiled profiles: "
                        + sceneFile.path());
            }

            collectTiledLayerTileAssetIds(new JsonReader().parse(sceneFile), out, tileAnimationsDb);
        }
    }

    private static void collectTiledLayerTileAssetIds(JsonValue root,
                                                      IntSet out,
                                                      TileAnimationsMetaDatabase tileAnimationsDb) {
        JsonValue entities = root != null ? root.get("entities") : null;
        if (entities == null || !entities.isObject()) {
            return;
        }

        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            JsonValue components = entity.get("components");
            if (components == null || !components.isObject()) {
                continue;
            }

            JsonValue tiled = components.get("TiledLayerComponent");
            if (tiled == null || !tiled.isObject()) {
                continue;
            }

            collectIntBagValues(tiled.get("tileAssetIds"), out, tileAnimationsDb);
            JsonValue data = tiled.get("data");
            if (data != null && data.isObject()) {
                collectIntBagValues(data.get("tileAssetIds"), out, tileAnimationsDb);
            }
        }
    }

    private static void collectIntBagValues(JsonValue bag,
                                            IntSet out,
                                            TileAnimationsMetaDatabase tileAnimationsDb) {
        JsonValue items = bag != null ? bag.get("items") : null;
        if (items == null || !items.isArray()) {
            return;
        }

        int size = bag.getInt("size", items.size);
        int index = 0;
        for (JsonValue item = items.child; item != null && index < size; item = item.next, index++) {
            int assetId = item.asInt();
            if (assetId > 0) {
                if (!addTiledAnimationFrameAssetIds(tileAnimationsDb, assetId, out)) {
                    out.add(assetId);
                }
            }
        }
    }

    private static boolean addTiledAnimationFrameAssetIds(TileAnimationsMetaDatabase db,
                                                          Integer tileAnimationId,
                                                          IntSet out) {
        if (db == null || db.animations == null || tileAnimationId == null || tileAnimationId <= 0 || out == null) {
            return false;
        }
        for (TileAnimationProjectDefData def : db.animations) {
            if (def == null || def.id != tileAnimationId) continue;
            if (def.frameAssetIds != null) {
                for (int frameAssetId : def.frameAssetIds) {
                    if (frameAssetId > 0) {
                        out.add(frameAssetId);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static IntMap<IntArray> collectRuntimeTileIdsByTileset(AssetMetaDatabase assetDb,
                                                                   IntSet runtimeTileAssetIds) {
        IntMap<IntArray> out = new IntMap<>();
        if (assetDb == null || runtimeTileAssetIds == null || runtimeTileAssetIds.size == 0) {
            return out;
        }

        for (IntSet.IntSetIterator it = runtimeTileAssetIds.iterator(); it.hasNext; ) {
            int tileAssetId = it.next();
            AssetMeta tileMeta = assetDb.findById(tileAssetId);
            if (!(tileMeta instanceof TileAssetMeta tile)) {
                throw new GdxRuntimeException("Runtime export requires a tile asset profile, but asset "
                        + tileAssetId + " is not a tile asset.");
            }
            if (tile.tilesetId <= 0) {
                throw new GdxRuntimeException("Runtime export requires a tileset profile for tile asset "
                        + tileAssetId + ", but the tile has no tileset id.");
            }
            AssetMeta tilesetMeta = assetDb.findById(tile.tilesetId);
            if (!(tilesetMeta instanceof TilesetAssetMeta)) {
                throw new GdxRuntimeException("Runtime export requires a tileset profile for tile asset "
                        + tileAssetId + ", but tileset " + tile.tilesetId + " is missing.");
            }

            IntArray tileIds = out.get(tile.tilesetId);
            if (tileIds == null) {
                tileIds = new IntArray();
                out.put(tile.tilesetId, tileIds);
            }
            if (!tileIds.contains(tile.id())) {
                tileIds.add(tile.id());
            }
        }

        return out;
    }

    private static JsonValue tilesetProfileJson(TilesetAssetMeta tileset, IntArray tileIds) {
        tileset.normalizeProfileDefaults();

        JsonValue node = new JsonValue(JsonValue.ValueType.object);
        node.addChild("tilesetId", new JsonValue(tileset.id()));
        node.addChild("logicalPath", new JsonValue(tileset.logicalPath()));
        node.addChild("tileWidth", new JsonValue(tileset.tileWidth));
        node.addChild("tileHeight", new JsonValue(tileset.tileHeight));
        node.addChild("referenceCellWidth", new JsonValue(tileset.referenceCellWidth));
        node.addChild("referenceCellHeight", new JsonValue(tileset.referenceCellHeight));
        node.addChild("projection", new JsonValue(tiledProjectionWireName(tileset.projection)));
        node.addChild("anchor", new JsonValue(tileset.anchor != null ? tileset.anchor.wireName() : null));
        node.addChild("offsetX", new JsonValue(tileset.offsetX));
        node.addChild("offsetY", new JsonValue(tileset.offsetY));
        node.addChild("renderSize", new JsonValue(tileset.renderSize != null ? tileset.renderSize.wireName() : null));

        JsonValue tileAssetIds = new JsonValue(JsonValue.ValueType.array);
        for (int i = 0; i < tileIds.size; i++) {
            tileAssetIds.addChild(new JsonValue(tileIds.get(i)));
        }
        node.addChild("tileAssetIds", tileAssetIds);
        return node;
    }

    private static int compareTilesets(TilesetAssetMeta left, TilesetAssetMeta right) {
        String leftPath = left != null && left.logicalPath() != null ? left.logicalPath() : "";
        String rightPath = right != null && right.logicalPath() != null ? right.logicalPath() : "";
        int byPath = leftPath.compareTo(rightPath);
        if (byPath != 0) {
            return byPath;
        }
        int leftId = left != null ? left.id() : 0;
        int rightId = right != null ? right.id() : 0;
        return Integer.compare(leftId, rightId);
    }

    private static String tiledProjectionWireName(TiledProjection projection) {
        if (projection == TiledProjection.ISO) return "isometric";
        return "orthogonal";
    }

    private static JsonValue animationJson(AnimationAssetMeta animation) {
        AnimationDefData definition = StudioAnimationAssets.toRuntimeData(animation);
        new AnimationDef(definition);
        JsonValue node = new JsonValue(JsonValue.ValueType.object);
        node.addChild("assetId", new JsonValue(definition.assetId));
        node.addChild("name", new JsonValue(definition.name));
        node.addChild("fps", new JsonValue(definition.fps));
        node.addChild("currentClip", new JsonValue(definition.currentClip));
        node.addChild("frameCount", new JsonValue(definition.frameCount));

        JsonValue clips = new JsonValue(JsonValue.ValueType.array);
        for (AnimationClipDefData clip : definition.clips) {
            clips.addChild(animationClipJson(clip));
        }
        node.addChild("clips", clips);
        return node;
    }

    private static JsonValue animationClipJson(AnimationClipDefData clip) {
        JsonValue node = new JsonValue(JsonValue.ValueType.object);
        node.addChild("name", new JsonValue(clip.name));
        node.addChild("start", new JsonValue(clip.start));
        node.addChild("end", new JsonValue(clip.end));
        node.addChild("flipX", new JsonValue(clip.flipX));
        return node;
    }

    public static void saveProject(RuntimeConfig cfg, FileHandle projectDir) {
        saveProject(cfg, projectDir, null);
    }

    private static void saveProject(RuntimeConfig cfg, FileHandle projectDir, ProjectConfig studioCfg) {
        if (cfg == null) throw new GdxRuntimeException("cfg is null");
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");

        projectDir.mkdirs();
        FileHandle file = projectDir.child(PROJECT_JSON);

        // Do not rewrite runtimeRootDir here.
        cfg.applyDefaultsAndValidate(file.path());

        final byte[] bytes;
        try {
            String pretty = JSON.prettyPrint(cfg);
            JsonValue root = new JsonReader().parse(pretty);
            root.remove("projectKind");
            root.addChild("projectKind", new JsonValue(RUNTIME_PROJECT_KIND));
            injectRuntimeAvailability(root, studioCfg);
            pretty = root.prettyPrint(JsonWriter.OutputType.json, 1);
            bytes = pretty.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new GdxRuntimeException("Failed to serialize runtime project json: " + file.path(), e);
        }

        StudioIO.writeAtomic(file, out -> out.write(bytes));
    }

    private static void injectRuntimeAvailability(JsonValue runtimeProjectRoot, ProjectConfig studioCfg) {
        if (runtimeProjectRoot == null || studioCfg == null) return;

        JsonValue scenes = runtimeProjectRoot.get("scenes");
        if (scenes == null || !scenes.isObject()) return;

        for (JsonValue sceneValue = scenes.child; sceneValue != null; sceneValue = sceneValue.next) {
            String sceneName = sceneValue.name;
            SceneMeta studioMeta = studioCfg.getSceneMeta(sceneName);
            SceneRuntimeAvailabilityData data = studioMeta != null ? studioMeta.runtimeAvailability : null;
            if (data == null) {
                data = new SceneRuntimeAvailabilityData();
            }

            sceneValue.remove("runtimeAvailability");
            sceneValue.addChild("runtimeAvailability", runtimeAvailabilityJson(data));
        }
    }

    private static JsonValue runtimeAvailabilityJson(SceneRuntimeAvailabilityData data) {
        JsonValue root = new JsonValue(JsonValue.ValueType.object);

        JsonValue sprites = new JsonValue(JsonValue.ValueType.array);
        if (data.spriteAssetIds != null) {
            for (Integer assetId : data.spriteAssetIds) {
                if (assetId != null && assetId > 0) {
                    sprites.addChild(new JsonValue(assetId));
                }
            }
        }
        root.addChild("sprites", sprites);

        JsonValue animations = new JsonValue(JsonValue.ValueType.array);
        if (data.animationAssetIds != null) {
            for (Integer assetId : data.animationAssetIds) {
                if (assetId != null && assetId > 0) {
                    animations.addChild(new JsonValue(assetId));
                }
            }
        }
        root.addChild("animations", animations);

        JsonValue particles = new JsonValue(JsonValue.ValueType.array);
        if (data.particleEffectPaths != null) {
            for (String effectPath : data.particleEffectPaths) {
                if (effectPath != null && !effectPath.isBlank()) {
                    particles.addChild(new JsonValue(effectPath));
                }
            }
        }
        root.addChild("particles", particles);

        JsonValue gameObjects = new JsonValue(JsonValue.ValueType.array);
        if (data.gameObjectIds != null) {
            for (String gameObjectId : data.gameObjectIds) {
                if (gameObjectId != null && !gameObjectId.isBlank()) {
                    gameObjects.addChild(new JsonValue(gameObjectId));
                }
            }
        }
        root.addChild("gameObjects", gameObjects);

        JsonValue tiledTiles = new JsonValue(JsonValue.ValueType.array);
        if (data.tiledTileAssetIds != null) {
            for (Integer assetId : data.tiledTileAssetIds) {
                if (assetId != null && assetId > 0) {
                    tiledTiles.addChild(new JsonValue(assetId));
                }
            }
        }
        root.addChild("tiledTiles", tiledTiles);

        JsonValue tiledAnimations = new JsonValue(JsonValue.ValueType.array);
        if (data.tiledAnimationIds != null) {
            for (Integer animationId : data.tiledAnimationIds) {
                if (animationId != null && animationId > 0) {
                    tiledAnimations.addChild(new JsonValue(animationId));
                }
            }
        }
        root.addChild("tiledAnimations", tiledAnimations);

        return root;
    }

    // ---------------------------------------------------------------------
    // Artemis JSON sanitizing
    // ---------------------------------------------------------------------

    private static void sanitizeArtemisSceneJson(FileHandle inFile,
                                                 FileHandle outFile,
                                                 ObjectSet<String> studioOnlyComponents) {
        JsonValue root;
        try {
            root = new JsonReader().parse(inFile);
        } catch (Exception e) {
            throw new GdxRuntimeException("Failed to parse scene json: " + inFile.path(), e);
        }

        if (root == null) {
            throw new GdxRuntimeException("Invalid scene json (null): " + inFile.path());
        }

        removeStudioOnlyArtemisComponents(root, studioOnlyComponents);

        outFile.parent().mkdirs();
        try {
            String pretty = root.prettyPrint(JsonWriter.OutputType.json, 120);
            StudioIO.writeAtomic(outFile, out -> {
                byte[] bytes = pretty.getBytes(StandardCharsets.UTF_8);
                out.write(bytes);
            });
        } catch (Exception e) {
            throw new GdxRuntimeException("Failed to write runtime scene json: " + outFile.path(), e);
        }
    }

    private static void removeStudioOnlyArtemisComponents(JsonValue root,
                                                          ObjectSet<String> studioOnlyComponents) {
        if (root == null || studioOnlyComponents == null) return;

        // componentIdentifiers: object { "fqcn":"ShortName", ... }
        JsonValue compIds = root.get("componentIdentifiers");
        if (compIds != null && compIds.isObject()) {
            for (JsonValue entry = compIds.child; entry != null; ) {
                JsonValue next = entry.next;

                String fqcn = entry.name;
                String shortName = entry.asString();

                boolean remove = shortName != null && studioOnlyComponents.contains(shortName);
                if (!remove && fqcn != null && fqcn.contains(".studio.")) {
                    remove = true;
                }

                if (remove) {
                    compIds.remove(fqcn);
                }
                entry = next;
            }
        }

        // archetypes: object { "1":[ "CompA","CompB"... ], ... }
        JsonValue archetypes = root.get("archetypes");
        if (archetypes != null && archetypes.isObject()) {
            for (JsonValue entry = archetypes.child; entry != null; entry = entry.next) {
                if (entry.isArray()) {
                    removeFromStringArray(entry, studioOnlyComponents);
                }
            }
        }

        JsonValue entities = root.get("entities");
        if (entities != null && entities.isObject()) {
            for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
                JsonValue comps = ent.get("components");
                if (comps == null || !comps.isObject()) continue;

                for (String compName : studioOnlyComponents) {
                    if (compName != null && comps.has(compName)) {
                        comps.remove(compName);
                    }
                }
            }
        }
    }

    private static void copyAtlasesWithoutInput(FileHandle srcAtlasesDir, FileHandle dstAtlasesDir) {
        if (srcAtlasesDir == null || !srcAtlasesDir.exists()) return;
        if (dstAtlasesDir == null) return;

        dstAtlasesDir.mkdirs();

        FileHandle[] list = srcAtlasesDir.list();
        if (list == null) return;

        for (FileHandle f : list) {
            if (f == null) continue;

            if (f.isDirectory()) {
                if (ProjectFileCleanupService.shouldSkipRuntimeExportDirectory(f)) {
                    continue;
                }

                copyDirIfExists(f, dstAtlasesDir.child(f.name()));
                continue;
            }

            if (ProjectFileCleanupService.shouldSkipRuntimeExportFile(f)) {
                continue;
            }

            f.copyTo(dstAtlasesDir.child(f.name()));
        }
    }

    private static void removeFromStringArray(JsonValue array, ObjectSet<String> toRemove) {
        if (array == null || !array.isArray() || toRemove == null) return;

        for (JsonValue item = array.child; item != null; ) {
            JsonValue next = item.next;
            String compName = item.asString();
            if (compName != null && toRemove.contains(compName)) {
                int idx = indexOfChild(array, item);
                if (idx >= 0) {
                    array.remove(idx);
                }
            }
            item = next;
        }
    }

    private static int indexOfChild(JsonValue array, JsonValue target) {
        int i = 0;
        for (JsonValue it = array.child; it != null; it = it.next, i++) {
            if (it == target) return i;
        }
        return -1;
    }

    static void copyDirIfExists(FileHandle srcDir, FileHandle dstDir) {
        if (srcDir == null || !srcDir.exists()) return;
        if (dstDir == null) return;

        if (ProjectFileCleanupService.shouldSkipRuntimeExportDirectory(srcDir)) {
            return;
        }

        dstDir.mkdirs();

        FileHandle[] list = srcDir.list();
        if (list == null) return;

        for (FileHandle f : list) {
            if (f == null) continue;

            if (f.isDirectory()) {
                if (ProjectFileCleanupService.shouldSkipRuntimeExportDirectory(f)) {
                    continue;
                }

                copyDirIfExists(f, dstDir.child(f.name()));
                continue;
            }

            if (ProjectFileCleanupService.shouldSkipRuntimeExportFile(f)) {
                continue;
            }

            f.copyTo(dstDir.child(f.name()));
        }
    }

}
