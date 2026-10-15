package games.pixscape.studio.service.asset;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudFontReferences;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.io.StudioFs;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class AssetUsageScanner {

    private final World world;
    private final ProjectConfig projectConfig;
    private final AssetMetaDatabase assetMetaDatabase;
    private final List<HudScreenEditorDocument> openHudDocuments;

    private final ComponentMapper<AssetRefComponent> mAssetRef;
    private final ComponentMapper<AnimationComponent> mAnimation;
    private final ComponentMapper<ParticleEmitterComponent> mParticle;
    private final ComponentMapper<TiledLayerComponent> mTiled;

    public AssetUsageScanner(World world,
                             ProjectConfig projectConfig,
                             AssetMetaDatabase assetMetaDatabase) {
        this(world, projectConfig, assetMetaDatabase, List.of());
    }

    public AssetUsageScanner(World world, ProjectConfig projectConfig,
                             AssetMetaDatabase assetMetaDatabase,
                             List<HudScreenEditorDocument> openHudDocuments) {
        this.world = world;
        this.projectConfig = Objects.requireNonNull(projectConfig, "projectConfig");
        this.assetMetaDatabase = Objects.requireNonNull(assetMetaDatabase, "assetMetaDatabase");
        this.openHudDocuments = List.copyOf(openHudDocuments != null ? openHudDocuments : List.of());

        this.mAssetRef = world != null ? world.getMapper(AssetRefComponent.class) : null;
        this.mAnimation = world != null ? world.getMapper(AnimationComponent.class) : null;
        this.mParticle = world != null ? world.getMapper(ParticleEmitterComponent.class) : null;
        this.mTiled = world != null ? world.getMapper(TiledLayerComponent.class) : null;
    }

    public AssetUsageReport scanAsset(int assetId) {
        if (assetId <= 0) {
            return AssetUsageReport.empty();
        }

        AssetMeta assetMeta = assetMetaDatabase.findById(assetId);
        if (assetMeta == null) {
            return AssetUsageReport.empty();
        }

        UsageAccumulator acc = new UsageAccumulator();

        if (assetMeta.type() == games.pixscape.studio.asset.AssetType.FONT) {
            scanBitmapFontHudDocuments(assetId, acc);
            return acc.toReport();
        }
        if (assetMeta.type() == games.pixscape.studio.asset.AssetType.SKIN) {
            scanSkinHudDocuments(assetMeta.sourceRelPath(), acc);
            return acc.toReport();
        }

        if (world == null) {
            throw new IllegalStateException("A live Scene is required to inspect this Asset type.");
        }

        scanCurrentLoadedScene(assetMeta, acc);
        scanOtherProjectSceneFiles(assetMeta, acc);

        return acc.toReport();
    }

    /** Cold, project-wide deletion proof. Tileset membership is deliberately not a reference. */
    public java.util.Map<Integer, String> tileRetentionReasons(java.util.Set<Integer> candidates,
                                                              List<World> openWorlds) {
        java.util.Map<Integer, String> retained = new java.util.LinkedHashMap<>();
        if (candidates.isEmpty()) return retained;
        FileHandle root = resolveProjectDir();
        java.util.Set<String> scanned = new java.util.HashSet<>();
        for (String sceneName : projectConfig.getSceneNames()) {
            SceneMeta meta = projectConfig.getSceneMeta(sceneName);
            if (meta == null || meta.getFile() == null) {
                retainUnknown(candidates, retained, "Scene metadata is incomplete: " + sceneName);
                continue;
            }
            scanRetentionFile(root.child(StudioFs.DIR_SCENES).child(meta.getFile()), candidates, retained, scanned);
            scanRetentionJson(new com.badlogic.gdx.utils.Json().toJson(meta), candidates, retained,
                    "Scene availability: " + sceneName);
        }
        scanRetentionDirectory(root.child(StudioFs.DIR_GAME_OBJECTS), candidates, retained, scanned);
        scanRetentionDirectory(root.child("prefabs"), candidates, retained, scanned);
        // Other authored JSON resources (animations, HUD documents, properties and dependency declarations).
        scanAuthoredJson(root, root, candidates, retained, scanned);
        JsonValue catalog = new JsonReader().parse(assetMetaDatabase.toSnapshotJson());
        JsonValue assets = catalog.get("assets");
        for (JsonValue asset = assets.child; asset != null; asset = asset.next) {
            String type = asset.getString("type", "");
            if (!"tile".equalsIgnoreCase(type) && !"tileset".equalsIgnoreCase(type))
                collectRetentionReferences(asset, false, candidates, retained, "Asset dependency");
        }
        for (World open : openWorlds) {
            try {
                var manager = open.getSystem(com.artemis.managers.WorldSerializationManager.class);
                if (manager == null) throw new IllegalStateException("No World serializer");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                manager.save(bytes, new com.artemis.io.SaveFileFormat(open.getAspectSubscriptionManager()
                        .get(Aspect.all()).getEntities()));
                scanRetentionJson(bytes.toString(java.nio.charset.StandardCharsets.UTF_8), candidates,
                        retained, "Open unsaved document");
            } catch (RuntimeException failure) {
                retainUnknown(candidates, retained, "Cannot inspect an open document: " + failure.getMessage());
            }
        }
        for (HudScreenEditorDocument open : openHudDocuments) {
            try {
                scanRetentionJson(new com.badlogic.gdx.utils.Json().toJson(open.document()), candidates,
                        retained, "Open HUD document: " + open.screenId());
            } catch (RuntimeException failure) {
                retainUnknown(candidates, retained, "Cannot inspect an open HUD document");
            }
        }
        return retained;
    }

    private void scanAuthoredJson(FileHandle root, FileHandle directory, java.util.Set<Integer> ids,
                                  java.util.Map<Integer, String> retained, java.util.Set<String> scanned) {
        for (FileHandle file : retentionFiles(directory, ids, retained)) {
            if (file.isDirectory()) {
                String name = file.name();
                if (!name.equals("orig") && !name.equals("atlases") && !name.startsWith(".")
                        && !name.equals("runtime") && !name.equals("build")
                        && !name.equals(games.pixscape.studio.configuration.RuntimeExport.RUNTIME_DIR_NAME))
                    scanAuthoredJson(root, file, ids, retained, scanned);
            } else if (file.extension().equalsIgnoreCase("json")
                    && !file.equals(root.child(StudioFs.FILE_ASSETS_JSON))) {
                scanRetentionFile(file, ids, retained, scanned);
            }
        }
    }

    private void scanRetentionDirectory(FileHandle directory, java.util.Set<Integer> ids,
                                        java.util.Map<Integer, String> retained, java.util.Set<String> scanned) {
        if (!directory.exists()) return;
        for (FileHandle file : retentionFiles(directory, ids, retained)) {
            if (games.pixscape.studio.service.ProjectFileCleanupService.shouldSkipRuntimeExportFile(file)) continue;
            if (StudioFs.isImageFile(file.name())) continue;
            if (file.isDirectory()) scanRetentionDirectory(file, ids, retained, scanned);
            else scanRetentionFile(file, ids, retained, scanned);
        }
    }

    private FileHandle[] retentionFiles(FileHandle directory, java.util.Set<Integer> ids,
                                         java.util.Map<Integer, String> retained) {
        if (java.nio.file.Files.isSymbolicLink(directory.file().toPath())) {
            retainUnknown(ids, retained, "Cannot inspect linked authored directory: " + directory.name());
            return new FileHandle[0];
        }
        java.util.List<FileHandle> files = new java.util.ArrayList<>();
        try (var entries = java.nio.file.Files.newDirectoryStream(directory.file().toPath())) {
            for (var path : entries) files.add(new FileHandle(path.toFile()));
        } catch (java.io.IOException | RuntimeException failure) {
            retainUnknown(ids, retained, "Cannot inspect authored directory: " + directory.name());
        }
        return files.toArray(FileHandle[]::new);
    }

    private void scanRetentionFile(FileHandle file, java.util.Set<Integer> ids,
                                   java.util.Map<Integer, String> retained, java.util.Set<String> scanned) {
        if (!scanned.add(file.file().toPath().toAbsolutePath().normalize().toString())) return;
        if (!file.exists() || file.isDirectory()) {
            retainUnknown(ids, retained, "Missing authored resource: " + file.name());
            return;
        }
        try { scanRetentionJson(file.readString("UTF-8"), ids, retained, file.name()); }
        catch (RuntimeException failure) { retainUnknown(ids, retained, "Cannot read authored resource: " + file.name()); }
    }

    private void scanRetentionJson(String content, java.util.Set<Integer> ids,
                                          java.util.Map<Integer, String> retained, String label) {
        try {
            JsonValue root = new JsonReader().parse(content);
            if (!root.isObject() && !root.isArray()) throw new IllegalArgumentException("Invalid authored JSON root");
            collectRetentionReferences(root, false, ids, retained, label);
            verifyGameObjectReferences(root, false, ids, retained, label);
        } catch (RuntimeException failure) {
            retainUnknown(ids, retained, "Cannot inspect " + label + ": " + failure.getMessage());
        }
    }

    private void verifyGameObjectReferences(JsonValue node, boolean gameObjects, java.util.Set<Integer> ids,
                                            java.util.Map<Integer, String> retained, String label) {
        String name = node.name == null ? "" : node.name.toLowerCase(java.util.Locale.ROOT);
        gameObjects = name.equals("gameobjectids") || name.equals("runtimegameobjectids")
                || (gameObjects && node.name == null);
        if (node.isString() && (gameObjects || name.equals("gameobjectid")
                || (name.equals("assetid") && (node.asString().endsWith(".gameobject")
                    || node.asString().startsWith("gameobjects/"))))) {
            String value = node.asString();
            if (!value.isBlank()) {
                try {
                    String id = games.pixscape.runtime.gameobject.GameObjectAssetId.normalize(value);
                    if (!resolveProjectDir().child(id).exists())
                        retainUnknown(ids, retained, "Missing Game Object dependency in " + label + ": " + id);
                } catch (RuntimeException failure) {
                    retainUnknown(ids, retained, "Cannot resolve Game Object dependency in " + label);
                }
            }
        }
        for (JsonValue child = node.child; child != null; child = child.next)
            verifyGameObjectReferences(child, gameObjects, ids, retained, label);
    }

    private static void retainUnknown(java.util.Set<Integer> ids, java.util.Map<Integer, String> retained,
                                       String reason) {
        for (int id : ids) retained.putIfAbsent(id, reason);
    }

    private static void collectRetentionReferences(JsonValue node, boolean reference,
                                                    java.util.Set<Integer> ids,
                                                    java.util.Map<Integer, String> retained, String label) {
        collectRetentionReferences(node, reference, false, ids, retained, label);
    }

    private static void collectRetentionReferences(JsonValue node, boolean reference, boolean uncertain,
                                                    java.util.Set<Integer> ids,
                                                    java.util.Map<Integer, String> retained, String label) {
        String name = node.name == null ? "" : node.name.toLowerCase(java.util.Locale.ROOT);
        reference |= name.contains("asset") || name.endsWith("tileid") || name.endsWith("tileids");
        uncertain |= name.equals("properties") || name.equals("customproperties");
        if (reference && node.isObject() && node.has("items")) {
            JsonValue items = node.get("items");
            int size = node.getInt("size", items.size);
            if (!items.isArray() || size < 0 || size > items.size) {
                retainUnknown(ids, retained, "Invalid asset reference bag in " + label); return;
            }
            int index = 0;
            for (JsonValue item = items.child; item != null && index < size; item = item.next, index++)
                collectRetentionReferences(item, true, uncertain, ids, retained, label);
            return;
        }
        if (node.isNumber() && (reference || uncertain) && ids.contains(node.asInt()))
            retained.putIfAbsent(node.asInt(), reference ? "Referenced by " + label
                    : "Cannot exclude a tile reference in authored properties: " + label);
        for (JsonValue child = node.child; child != null; child = child.next)
            collectRetentionReferences(child, reference, uncertain, ids, retained, label);
    }

    public AssetUsageReport scanTileset(int tilesetId) {
        if (tilesetId <= 0) {
            return AssetUsageReport.empty();
        }

        IntSet tileAssetIds = collectTileAssetIdsForTileset(tilesetId);
        if (tileAssetIds.size == 0) {
            return AssetUsageReport.empty();
        }

        UsageAccumulator acc = new UsageAccumulator();

        scanCurrentLoadedSceneTiles(tileAssetIds, acc);
        scanOtherProjectSceneFilesTiles(tileAssetIds, acc);

        return acc.toReport();
    }

    private void scanCurrentLoadedScene(AssetMeta assetMeta, UsageAccumulator acc) {
        String currentSceneName = projectConfig.getCurrentSceneName();
        if (currentSceneName == null || currentSceneName.isBlank()) {
            return;
        }

        boolean found = switch (assetMeta.type()) {
            case IMAGE -> scanCurrentWorldByAssetId(assetMeta.id(), acc, currentSceneName);
            case ANIMATION -> scanCurrentWorldAnimationAssetId(assetMeta.id(), acc, currentSceneName);
            case TILE -> scanCurrentWorldTile(assetMeta.id(), acc, currentSceneName);
            case PARTICLE -> scanCurrentWorldParticle(assetMeta, acc, currentSceneName);
            case TILESET -> false;
            case FONT -> false;
            case SKIN -> false;
        };

        if (found) {
            acc.referencedInCurrentLoadedScene = true;
        }
    }

    private void scanCurrentLoadedSceneTiles(IntSet tileAssetIds, UsageAccumulator acc) {
        String currentSceneName = projectConfig.getCurrentSceneName();
        if (currentSceneName == null || currentSceneName.isBlank()) {
            return;
        }

        boolean found = scanCurrentWorldTiles(tileAssetIds, acc, currentSceneName);
        if (found) {
            acc.referencedInCurrentLoadedScene = true;
        }
    }

    private void scanOtherProjectSceneFiles(AssetMeta assetMeta, UsageAccumulator acc) {
        FileHandle scenesDir = resolveScenesDir();
        if (scenesDir == null || !scenesDir.exists()) {
            return;
        }

        String currentSceneName = projectConfig.getCurrentSceneName();

        for (String sceneName : projectConfig.getSceneNames()) {
            if (sceneName == null || sceneName.isBlank()) continue;
            if (sceneName.equals(currentSceneName)) continue;

            SceneMeta sceneMeta = projectConfig.getSceneMeta(sceneName);
            if (sceneMeta == null || sceneMeta.getFile() == null || sceneMeta.getFile().isBlank()) {
                continue;
            }

            FileHandle sceneFile = scenesDir.child(sceneMeta.getFile());
            if (!sceneFile.exists() || sceneFile.isDirectory()) {
                continue;
            }

            switch (assetMeta.type()) {
                case IMAGE -> scanSceneFileByAssetId(sceneFile, sceneName, assetMeta.id(), acc);
                case ANIMATION -> scanSceneFileAnimationAssetId(sceneFile, sceneName, assetMeta.id(), acc);
                case TILE -> scanSceneFileTile(sceneFile, sceneName, assetMeta.id(), acc);
                case PARTICLE -> scanSceneFileParticle(sceneFile, sceneName, assetMeta, acc);
                case TILESET -> {
                    // no-op
                }
                case FONT -> {
                    // Font usage is scanned across all HUD documents above.
                }
                case SKIN -> {
                    // Skin usage is scanned across all HUD assets above.
                }
            }
        }
    }

    private void scanSkinHudDocuments(String skinId, UsageAccumulator acc) {
        if (skinId == null || skinId.isBlank()) return;
        FileHandle projectDir = resolveProjectDir();
        java.util.HashSet<String> openIds = new java.util.HashSet<>();
        for (HudScreenEditorDocument open : openHudDocuments) {
            openIds.add(open.screenId());
            if (sameProjectPath(projectDir, skinId, open.asset().skinId)) {
                acc.occurrenceCount++;
                acc.addDocument(open.screenId());
            }
        }
        FileHandle hudDir = projectDir
                .child(games.pixscape.runtime.hud.HudScreenAssetId.DIRECTORY);
        if (!hudDir.exists()) return;
        for (FileHandle assetFile : hudDir.list("." + HudScreenAsset.EXTENSION.substring(1))) {
            String documentLabel = assetFile.nameWithoutExtension();
            if (openIds.contains(documentLabel)) continue;
            try {
                JsonValue assetJson = new JsonReader().parse(assetFile);
                if (sameProjectPath(projectDir, skinId, assetJson.getString("skinId", null))) {
                    acc.occurrenceCount++;
                    acc.addDocument(documentLabel);
                }
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Cannot verify Skin usage in HUD asset '"
                        + documentLabel + "'.", failure);
            }
        }
    }

    private static boolean sameProjectPath(FileHandle projectDir, String first, String second) {
        if (first == null || first.isBlank() || second == null || second.isBlank()) return false;
        return resolveProjectPath(projectDir, first).equals(resolveProjectPath(projectDir, second));
    }

    private static Path resolveProjectPath(FileHandle projectDir, String reference) {
        Path root = projectDir.file().toPath().toAbsolutePath().normalize();
        try {
            String systemPath = reference.trim()
                    .replace('\\', File.separatorChar)
                    .replace('/', File.separatorChar);
            Path relative = Path.of(systemPath);
            if (relative.isAbsolute() || relative.getRoot() != null) {
                throw new IllegalStateException("Skin reference must be project-relative: " + reference);
            }
            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root)) {
                throw new IllegalStateException("Skin reference escapes the Studio project directory: "
                        + reference);
            }
            return resolved;
        } catch (InvalidPathException failure) {
            throw new IllegalStateException("Skin reference is not a valid project path: "
                    + reference, failure);
        }
    }

    private void scanBitmapFontHudDocuments(int assetId, UsageAccumulator acc) {
        java.util.HashSet<String> openIds = new java.util.HashSet<>();
        for (HudScreenEditorDocument open : openHudDocuments) {
            openIds.add(open.screenId());
            scanBitmapFontDocument(assetId, open.screenId(), open.document(), acc);
        }
        FileHandle projectDir = resolveProjectDir();
        FileHandle hudDir = projectDir.child(games.pixscape.runtime.hud.HudScreenAssetId.DIRECTORY);
        if (!hudDir.exists()) return;
        for (FileHandle assetFile : hudDir.list("." + HudScreenAsset.EXTENSION.substring(1))) {
            String documentLabel = assetFile.nameWithoutExtension();
            try {
                JsonValue assetJson = new JsonReader().parse(assetFile);
                String documentId = assetJson.getString("documentId", null);
                if (documentId == null || documentId.isBlank()) {
                    throw new IllegalStateException("HUD asset has no documentId.");
                }
                if (openIds.contains(documentLabel)) continue;
                var document = new HudDocumentCodec().read(projectDir.child(documentId));
                scanBitmapFontDocument(assetId, documentLabel, document, acc);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Cannot verify bitmap-font usage in HUD document '"
                        + documentLabel + "'.", failure);
            }
        }
    }

    private static void scanBitmapFontDocument(int assetId, String documentName,
                                               games.pixscape.runtime.hud.document.HudDocumentV1 document,
                                               UsageAccumulator acc) {
        var validation = new HudDocumentValidator().validate(document);
        if (!validation.isValid()) {
            throw new IllegalStateException("Cannot verify bitmap-font usage in invalid HUD document '"
                    + documentName + "'.");
        }
        boolean found = false;
        for (HudNode node : validation.validatedDocument().nodeIndex().values()) {
            Integer fontAssetId = HudFontReferences.assetId(node);
            if (fontAssetId != null && fontAssetId == assetId) {
                acc.occurrenceCount++;
                found = true;
            }
            Integer tooltipFontAssetId = HudFontReferences.tooltipAssetId(node);
            if (tooltipFontAssetId != null && tooltipFontAssetId == assetId) {
                acc.occurrenceCount++;
                found = true;
            }
        }
        if (found) acc.addDocument(documentName);
    }

    private void scanOtherProjectSceneFilesTiles(IntSet tileAssetIds, UsageAccumulator acc) {
        FileHandle scenesDir = resolveScenesDir();
        if (scenesDir == null || !scenesDir.exists()) {
            return;
        }

        String currentSceneName = projectConfig.getCurrentSceneName();

        for (String sceneName : projectConfig.getSceneNames()) {
            if (sceneName == null || sceneName.isBlank()) continue;
            if (sceneName.equals(currentSceneName)) continue;

            SceneMeta sceneMeta = projectConfig.getSceneMeta(sceneName);
            if (sceneMeta == null || sceneMeta.getFile() == null || sceneMeta.getFile().isBlank()) {
                continue;
            }

            FileHandle sceneFile = scenesDir.child(sceneMeta.getFile());
            if (!sceneFile.exists() || sceneFile.isDirectory()) {
                continue;
            }

            scanSceneFileTiles(sceneFile, sceneName, tileAssetIds, acc);
        }
    }

    private boolean scanCurrentWorldByAssetId(int assetId,
                                              UsageAccumulator acc,
                                              String sceneName) {
        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AssetRefComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            AssetRefComponent ref = mAssetRef.getSafe(e, null);
            if (ref == null) continue;
            if (ref.assetId != assetId) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldAnimationAssetId(int assetId,
                                                     UsageAccumulator acc,
                                                     String sceneName) {
        boolean found = false;
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AnimationComponent.class)).getEntities();
        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            AnimationComponent animation = mAnimation.getSafe(data[i], null);
            if (animation == null || animation.animationAssetIds == null
                    || !animation.animationAssetIds.contains(assetId)) {
                continue;
            }
            acc.occurrenceCount++;
            found = true;
        }
        if (found) acc.addScene(sceneName);
        return found;
    }

    private boolean scanCurrentWorldParticle(AssetMeta assetMeta,
                                             UsageAccumulator acc,
                                             String sceneName) {
        if (assetMeta.sourceRelPath() == null || assetMeta.sourceRelPath().isBlank()) {
            return false;
        }

        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(ParticleEmitterComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            ParticleEmitterComponent particle = mParticle.getSafe(e, null);
            if (particle == null) continue;
            if (!matchesParticleEffectPath(assetMeta, particle.effectPath)) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldTiles(IntSet tileAssetIds,
                                          UsageAccumulator acc,
                                          String sceneName) {
        boolean found = false;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            TiledLayerComponent tiled = mTiled.getSafe(e, null);
            if (tiled == null) continue;

            if (tiled.tileAssetIds != null) {
                for (int j = 0; j < tiled.tileAssetIds.size; j++) {
                    int assetId = tiled.tileAssetIds.get(j);
                    if (assetId <= 0) continue;
                    if (!tileAssetIds.contains(assetId)) continue;

                    acc.occurrenceCount++;
                    found = true;
                }
            }

            if (tiled.data == null) continue;

            var chunks = tiled.data.getChunks();
            while (chunks.hasNext()) {
                TileChunk chunk = chunks.next();

                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int assetId = chunk.get(lx, ly);
                        if (assetId <= 0) continue;
                        if (!tileAssetIds.contains(assetId)) continue;

                        acc.occurrenceCount++;
                        found = true;
                    }
                }
            }
        }

        if (found) {
            acc.addScene(sceneName);
        }

        return found;
    }

    private boolean scanCurrentWorldTile(int tileAssetId,
                                         UsageAccumulator acc,
                                         String sceneName) {
        IntSet ids = new IntSet();
        ids.add(tileAssetId);
        return scanCurrentWorldTiles(ids, acc, sceneName);
    }

    private void scanSceneFileByAssetId(FileHandle sceneFile,
                                        String sceneName,
                                        int assetId,
                                        UsageAccumulator acc) {
        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue assetRef = components.get("AssetRefComponent");
            if (assetRef == null || !assetRef.isObject()) continue;

            int refId = assetRef.getInt("assetId", -1);
            if (refId != assetId) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileAnimationAssetId(FileHandle sceneFile,
                                               String sceneName,
                                               int assetId,
                                               UsageAccumulator acc) {
        JsonValue root = parseScene(sceneFile);
        if (root == null) return;
        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;
        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;
            JsonValue animation = components.get("AnimationComponent");
            if (animation == null || !animation.isObject()) continue;
            JsonValue ids = animation.get("animationAssetIds");
            if (ids == null || !ids.isArray()) continue;
            for (JsonValue id = ids.child; id != null; id = id.next) {
                if (id.asInt() != assetId) continue;
                acc.occurrenceCount++;
                found = true;
                break;
            }
        }
        if (found) acc.addScene(sceneName);
    }

    private void scanSceneFileParticle(FileHandle sceneFile,
                                       String sceneName,
                                       AssetMeta assetMeta,
                                       UsageAccumulator acc) {
        if (assetMeta.sourceRelPath() == null || assetMeta.sourceRelPath().isBlank()) {
            return;
        }

        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue particle = components.get("ParticleEmitterComponent");
            if (particle == null || !particle.isObject()) continue;

            String effectPath = particle.getString("effectPath", null);
            if (!matchesParticleEffectPath(assetMeta, effectPath)) continue;

            acc.occurrenceCount++;
            found = true;
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileTiles(FileHandle sceneFile,
                                    String sceneName,
                                    IntSet tileAssetIds,
                                    UsageAccumulator acc) {
        JsonValue root = parseScene(sceneFile);
        if (root == null) return;

        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) return;

        boolean found = false;

        for (JsonValue ent = entities.child; ent != null; ent = ent.next) {
            JsonValue components = ent.get("components");
            if (components == null || !components.isObject()) continue;

            JsonValue tiled = components.get("TiledLayerComponent");
            if (tiled == null || !tiled.isObject()) continue;

            JsonValue tileAssetIdsJson = tiled.get("tileAssetIds");
            if (tileAssetIdsJson == null || !tileAssetIdsJson.isArray()) continue;

            for (JsonValue v = tileAssetIdsJson.child; v != null; v = v.next) {
                int assetId = v.asInt();
                if (assetId <= 0) continue;
                if (!tileAssetIds.contains(assetId)) continue;

                acc.occurrenceCount++;
                found = true;
            }
        }

        if (found) {
            acc.addScene(sceneName);
        }
    }

    private void scanSceneFileTile(FileHandle sceneFile,
                                   String sceneName,
                                   int tileAssetId,
                                   UsageAccumulator acc) {
        IntSet ids = new IntSet();
        ids.add(tileAssetId);
        scanSceneFileTiles(sceneFile, sceneName, ids, acc);
    }

    private IntSet collectTileAssetIdsForTileset(int tilesetId) {
        IntSet ids = new IntSet();

        for (int i = 0; i < assetMetaDatabase.size(); i++) {
            AssetMeta meta = assetMetaDatabase.assetAt(i);
            if (!(meta instanceof TileAssetMeta tileMeta)) continue;
            if (tileMeta.tilesetId != tilesetId) continue;
            ids.add(tileMeta.id());
        }

        return ids;
    }

    private FileHandle resolveScenesDir() {
        FileHandle projectDir = resolveProjectDir();
        return projectDir.child(StudioFs.DIR_SCENES);
    }

    private FileHandle resolveProjectDir() {
        if (projectConfig.projectDirectoryPath != null && !projectConfig.projectDirectoryPath.isBlank()) {
            return new FileHandle(new File(projectConfig.projectDirectoryPath));
        }
        return StudioFs.requireStudioProjectDir(projectConfig);
    }

    private JsonValue parseScene(FileHandle sceneFile) {
        try {
            return new JsonReader().parse(sceneFile);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean matchesParticleEffectPath(AssetMeta assetMeta, String effectPath) {
        if (assetMeta == null || assetMeta.sourceRelPath() == null || effectPath == null) {
            return false;
        }

        String source = normalizePath(assetMeta.sourceRelPath());
        String effect = normalizePath(effectPath);
        if (source.equals(effect)) {
            return true;
        }

        String prefix = StudioFs.DIR_ORIG_EFFECTS + "/";
        return source.startsWith(prefix)
                && source.substring(prefix.length()).equals(effect);
    }

    private static String normalizePath(String path) {
        return path != null ? path.trim().replace('\\', '/') : "";
    }

    private static final class UsageAccumulator {
        int occurrenceCount = 0;
        boolean referencedInCurrentLoadedScene = false;
        final Array<String> sceneNames = new Array<>();
        final Array<String> documentNames = new Array<>();

        void addScene(String sceneName) {
            if (sceneName == null || sceneName.isBlank()) return;
            if (!sceneNames.contains(sceneName, false)) {
                sceneNames.add(sceneName);
            }
        }

        void addDocument(String documentName) {
            if (documentName != null && !documentName.isBlank()
                    && !documentNames.contains(documentName, false)) documentNames.add(documentName);
        }

        AssetUsageReport toReport() {
            return new AssetUsageReport(
                    occurrenceCount > 0,
                    occurrenceCount,
                    sceneNames,
                    referencedInCurrentLoadedScene, documentNames
            );
        }
    }

    public record AssetUsageReport(boolean used, int occurrenceCount, Array<String> sceneNames,
                                   boolean referencedInCurrentLoadedScene,
                                   Array<String> documentNames) {
        public AssetUsageReport(boolean used,
                                int occurrenceCount,
                                Array<String> sceneNames,
                                boolean referencedInCurrentLoadedScene) {
            this(used, occurrenceCount, sceneNames, referencedInCurrentLoadedScene, new Array<>());
        }

        public AssetUsageReport(boolean used, int occurrenceCount, Array<String> sceneNames,
                                boolean referencedInCurrentLoadedScene,
                                Array<String> documentNames) {
            this.used = used;
            this.occurrenceCount = occurrenceCount;
            this.sceneNames = sceneNames != null ? new Array<>(sceneNames) : new Array<>();
            this.referencedInCurrentLoadedScene = referencedInCurrentLoadedScene;
            this.documentNames = documentNames != null ? new Array<>(documentNames) : new Array<>();
        }

        public static AssetUsageReport empty() {
            return new AssetUsageReport(false, 0, new Array<>(), false, new Array<>());
        }
    }
}
