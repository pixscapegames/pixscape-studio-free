package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.helper.RuntimeShaderResources;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.ProjectFileCleanupService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class RuntimeExport {
    public static final String RUNTIME_PROJECT_KIND = "pixscape-runtime-project";

    public static final String RUNTIME_DIR_NAME = "pixscape-project";
    public static final String PROJECT_JSON = "project.json";
    private static final String ANIMATIONS_JSON = "animations.json";

    // Components to exclude from the runtime (noms "courts" Artemis)
    private static final ObjectSet<String> RUNTIME_EXCLUDED_COMPONENTS = new ObjectSet<>();

    static {
        RUNTIME_EXCLUDED_COMPONENTS.add("EntityMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("LayerMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("CameraMetaComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsRuntimeBodyComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsRuntimeJointComponent");
        RUNTIME_EXCLUDED_COMPONENTS.add("PhysicsAuthoringComponent");
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
        out.prefabsDir = RuntimeFs.DIR_PREFABS;
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

            String file = RuntimeFs.filenameOnly(studioMeta.file);
            if (file == null || file.isBlank()) {
                throw new GdxRuntimeException("Scene '" + sceneName + "' has no file; cannot export.");
            }

            SceneMetaRuntime runtimeMeta = new SceneMetaRuntime(studioMeta);
            runtimeMeta.mainCameraOffscreen = studioMeta.mainCameraOffscreen;
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
        copyPrefabFiles(
                studioProjectDir.child(StudioFs.DIR_PREFABS),
                runtimeDir.child(out.prefabsDir)
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

    private static void copyPrefabFiles(FileHandle sourceDir, FileHandle targetDir) {
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

            boolean isStudioPrefab = file.name().endsWith(StudioFs.EXT_PREFAB);
            boolean isRuntimeFragment = file.name().endsWith(".pixfragment.json");

            if (!isStudioPrefab && !isRuntimeFragment) {
                continue;
            }
            if (isRuntimeFragment) {
                sanitizeRuntimePrefabFragment(file, targetDir.child(file.name()));
            } else {
                file.copyTo(targetDir.child(file.name()));
            }
        }
    }

    private static void sanitizeRuntimePrefabFragment(FileHandle inFile, FileHandle outFile) {
        JsonValue root = new JsonReader().parse(inFile);
        removeStudioOnlyArtemisComponents(root, RUNTIME_EXCLUDED_COMPONENTS);

        JsonValue entities = root.get("entities");

        if (entities != null && entities.isObject()) {
            for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
                JsonValue comps = ent.get("components");
                if (comps == null) continue;

                JsonValue id = comps.get("PixscapeIdentityComponent");
                if (id != null && id.isObject()) {
                    id.remove("stableId");
                }
            }
        }

        outFile.parent().mkdirs();
        String pretty = root.prettyPrint(JsonWriter.OutputType.json, 120);
        StudioIO.writeAtomic(outFile, out -> out.write(pretty.getBytes(StandardCharsets.UTF_8)));
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

        if (assetDb.assets != null) {
            for (AssetMeta meta : assetDb.assets) {
                if (!(meta instanceof AnimationAssetMeta animation)) {
                    continue;
                }
                if (animation.id <= 0) {
                    continue;
                }

                animations.addChild(animationJson(animation));
            }
        }

        root.addChild("animations", animations);
        runtimeFile.parent().mkdirs();
        String pretty = root.prettyPrint(JsonWriter.OutputType.json, 120);
        StudioIO.writeAtomic(runtimeFile, out -> out.write(pretty.getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonValue animationJson(AnimationAssetMeta animation) {
        JsonValue node = new JsonValue(JsonValue.ValueType.object);
        int frameCount = normalizedFrameCount(animation);

        node.addChild("assetId", new JsonValue(animation.id));
        node.addChild("name", new JsonValue(animationRuntimeName(animation)));
        node.addChild("fps", new JsonValue(animation.fps > 0f ? animation.fps : 12f));
        node.addChild("currentClip", new JsonValue(normalizedCurrentClip(animation)));
        node.addChild("frameCount", new JsonValue(frameCount));

        JsonValue clips = new JsonValue(JsonValue.ValueType.array);
        if (animation.clips != null && animation.clips.size > 0) {
            Array<String> names = new Array<>();
            for (ObjectMap.Entry<String, games.pixscape.runtime.component.AnimationComponent.Clip> entry : animation.clips) {
                if (entry != null && entry.key != null && !entry.key.isBlank() && entry.value != null) {
                    names.add(entry.key);
                }
            }
            names.sort(String::compareTo);

            for (String clipName : names) {
                games.pixscape.runtime.component.AnimationComponent.Clip clip = animation.clips.get(clipName);
                if (clip == null) continue;
                clips.addChild(animationClipJson(clipName, clip, frameCount));
            }
        }

        if (clips.size == 0) {
            games.pixscape.runtime.component.AnimationComponent.Clip fallback =
                    new games.pixscape.runtime.component.AnimationComponent.Clip(0, Math.max(0, frameCount - 1));
            clips.addChild(animationClipJson("default", fallback, frameCount));
        }

        node.addChild("clips", clips);
        return node;
    }

    private static JsonValue animationClipJson(String name,
                                               games.pixscape.runtime.component.AnimationComponent.Clip clip,
                                               int frameCount) {
        int start = Math.max(0, clip.start);
        int end = Math.max(start, clip.end);
        if (frameCount > 0) {
            end = Math.min(end, frameCount - 1);
        }

        JsonValue node = new JsonValue(JsonValue.ValueType.object);
        node.addChild("name", new JsonValue(name));
        node.addChild("start", new JsonValue(start));
        node.addChild("end", new JsonValue(end));
        node.addChild("flipX", new JsonValue(clip.flipX));
        return node;
    }

    private static int normalizedFrameCount(AnimationAssetMeta animation) {
        int frameCount = Math.max(0, animation.frameCount);
        if (frameCount > 0) {
            return frameCount;
        }
        if (animation.clips != null) {
            for (ObjectMap.Entry<String, games.pixscape.runtime.component.AnimationComponent.Clip> entry : animation.clips) {
                if (entry != null && entry.value != null) {
                    frameCount = Math.max(frameCount, entry.value.end + 1);
                }
            }
        }
        return Math.max(1, frameCount);
    }

    private static String normalizedCurrentClip(AnimationAssetMeta animation) {
        if (animation.currentClip != null && !animation.currentClip.isBlank()) {
            return animation.currentClip;
        }
        if (animation.clips != null && animation.clips.size > 0) {
            Array<String> names = new Array<>();
            for (ObjectMap.Entry<String, games.pixscape.runtime.component.AnimationComponent.Clip> entry : animation.clips) {
                if (entry != null && entry.key != null && !entry.key.isBlank() && entry.value != null) {
                    names.add(entry.key);
                }
            }
            if (names.size > 0) {
                names.sort(String::compareTo);
                return names.first();
            }
        }
        return "default";
    }

    private static String animationRuntimeName(AnimationAssetMeta animation) {
        String logical = animation.logicalPath != null ? animation.logicalPath : "";
        String name = RuntimeFs.baseName(logical);
        return name != null && !name.isBlank() ? name : "animation_" + animation.id;
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

        JsonValue prefabs = new JsonValue(JsonValue.ValueType.array);
        if (data.prefabIds != null) {
            for (String prefabId : data.prefabIds) {
                if (prefabId != null && !prefabId.isBlank()) {
                    prefabs.addChild(new JsonValue(prefabId));
                }
            }
        }
        root.addChild("prefabs", prefabs);

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
