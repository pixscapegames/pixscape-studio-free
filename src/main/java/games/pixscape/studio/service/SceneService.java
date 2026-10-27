package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;
import games.pixscape.studio.importer.tmx.TmxSceneImportService;
import games.pixscape.studio.importer.tmx.TmxSceneImportSession;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.asset.*;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetDirectoryImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImportResult;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetProfileImportSettings;
import games.pixscape.studio.service.atlas.*;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityService;
import games.pixscape.studio.service.runtimeavailability.SceneHudRoots;
import games.pixscape.studio.service.runtimeavailability.SceneHudRuntimeExport;
import games.pixscape.studio.service.runtimeavailability.SceneHudRuntimePreparationService;
import games.pixscape.studio.ui.asset.AssetsPanel;
import games.pixscape.studio.ui.asset.ImportDialog;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.log.StudioLog;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

import static games.pixscape.studio.PixscapeStudioApplication.STUDIO_TITLE;

public final class SceneService {

    private final StudioApplicationAdapter app;
    private final WorldCanvas canvas;
    private final AtlasStudioService atlasStudioService;
    private final SceneAtlasInputService sceneAtlasInputService;
    private final RuntimeAvailabilityService runtimeAvailabilityService;
    private final ProjectConfigSceneMetaBridge sceneMetaBridge;
    private final RecentProjectsService recentProjectsService;
    private AssetMetaDatabase assetMetaDatabase;
    private TileAnimationsMetaDatabase tileAnimationsMetaDatabase;

    /**
     * Single panel for the global library (orig/images).
     */
    private AssetsPanel assetsPanel;
    private final int MY_TAG = EventFlow.tag(this);

    private static final int DEFAULT_TILE_ANIMATION_FRAME_DURATION_MS = 300;

    // ---------------------------------------------------------------------
    // CONSTRUCTION / ACCESSORS
    // ---------------------------------------------------------------------

    public SceneService(StudioApplicationAdapter app, WorldCanvas canvas) {
        this.app = app;
        this.canvas = canvas;
        this.atlasStudioService = canvas.getAtlasService();
        this.sceneAtlasInputService = new SceneAtlasInputService();
        this.runtimeAvailabilityService = new RuntimeAvailabilityService();
        this.sceneMetaBridge = new ProjectConfigSceneMetaBridge();
        this.recentProjectsService = new RecentProjectsService();

        registerEditorOpsCallbacks();
    }

    private SceneEditorContext sceneContext() {
        SceneEditorContext context = canvas.getAttachedSceneContext();
        if (context == null) context = app.getMostRecentSceneEditorContext();
        if (context == null || context.isDisposed()) {
            throw new IllegalStateException("No live Scene editor context is available.");
        }
        return context;
    }

    private ClipboardService clipboardService() {
        return canvas.getClipboardService();
    }

    public void pushActiveSceneMetaToUi() {
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
        SceneEditorContext context = canvas.getAttachedSceneContext();
        if (context != null) {
            context.selectionService().publishCurrentSelection();
        }
    }

    public void saveSceneDocumentWithProgress(SceneEditorDocument document,
                                              Runnable onSuccess,
                                              java.util.function.Consumer<Throwable> onError) {
        saveSceneDocumentsWithProgress(app.getUiStage(), List.of(document), onSuccess, onError);
    }

    public void saveSceneDocument(SceneEditorDocument document) {
        Objects.requireNonNull(document, "document");
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) throw new IllegalStateException("No project is loaded.");
        String sceneName = sceneNameFor(document, cfg);
        if (sceneName == null) {
            throw new IllegalStateException("Scene document no longer belongs to this project.");
        }

        SceneEditorContext previous = canvas.getAttachedSceneContext();
        String previousSceneName = cfg.getCurrentSceneName();
        boolean hudActive = app.getEditorDocumentManager().isActive(EditorDocumentType.HUD_SCREEN);
        try {
            if (!canvas.isAttached(document.context())) canvas.attach(document.context());
            cfg.setCurrentSceneByName(sceneName);
            saveCurrentSceneOnly(cfg);
            document.context().markSaved();
            saveProjectFile(cfg);
        } finally {
            if (previousSceneName != null && cfg.getSceneMeta(previousSceneName) != null) {
                cfg.setCurrentSceneByName(previousSceneName);
            }
            if (previous != null && previous != document.context() && !previous.isDisposed()) {
                canvas.attach(previous);
            } else if (hudActive) {
                canvas.detach();
            }
        }
    }

    private static String sceneNameFor(SceneEditorDocument document, ProjectConfig cfg) {
        for (String name : cfg.getSceneNames()) {
            if (document.key().domainId().equals(cfg.canonicalSceneTag(name))) return name;
        }
        return null;
    }

    public TileAnimationRegistry getTileAnimationRegistry() {
        return canvas.getTileAnimationRegistry();
    }

    public void requestTiledFallbackValidation() {
        canvas.requestTiledFallbackValidation();
    }

    public void setAssetsPanel(AssetsPanel panel) {
        this.assetsPanel = panel;
    }

    public RuntimeAvailabilityService getRuntimeAvailabilityService() {
        return runtimeAvailabilityService;
    }

    public AnimationAssetMeta findAnimationAssetMetaBySourceRelPath(String sourceRelPath) {
        AssetMeta meta = findAssetMetaBySourceRelPath(sourceRelPath, AssetType.ANIMATION);
        return meta instanceof AnimationAssetMeta animation ? animation : null;
    }

    public AssetMeta findAssetMetaBySourceRelPath(String sourceRelPath, AssetType type) {
        if (sourceRelPath == null || sourceRelPath.isBlank() || type == null) return null;
        ensureAssetMetaDatabaseLoaded();
        return assetMetaDatabase != null
                ? assetMetaDatabase.findUniqueBySourceRelPath(sourceRelPath, type)
                : null;
    }

    public void saveAnimationAssetClips(String sourceRelPath,
                                        AnimationAssetMeta edited,
                                        int frameCount) {
        if (sourceRelPath == null || sourceRelPath.isBlank()) {
            throw new IllegalArgumentException("Animation source path is empty.");
        }
        if (edited == null) {
            throw new IllegalArgumentException("Animation metadata is null.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        AssetMetaDatabase reloadedDatabase = AssetMetaDatabase.load(assetsFile);

        AssetMeta meta = reloadedDatabase.findUniqueBySourceRelPath(
                sourceRelPath,
                AssetType.ANIMATION
        );
        if (!(meta instanceof AnimationAssetMeta animation)) {
            throw new IllegalStateException("Animation asset not found: " + sourceRelPath);
        }

        animation.frameCount = Math.max(animation.frameCount, Math.max(0, frameCount));
        animation.fps = edited.fps;
        animation.currentClip = edited.currentClip;
        animation.clips.clear();

        if (edited.clips != null) {
            for (ObjectMap.Entry<String, AnimationClipMeta> entry : edited.clips) {
                if (entry == null
                        || entry.key == null
                        || entry.key.isBlank()
                        || entry.value == null) {
                    throw new IllegalArgumentException(
                            "Animation asset contains an invalid authored clip.");
                }
                animation.clips.put(entry.key, entry.value.copy());
            }
        }

        StudioAnimationAssets.validate(animation);
        reloadedDatabase.save(assetsFile);
        assetMetaDatabase = reloadedDatabase;
        canvas.publishAssetMetaDatabase(assetMetaDatabase);
        AnimationAssetEntityReconciler.reconcile(
                sceneContext().world(),
                animation.id(),
                animation,
                canvas.getAnimationPreviewRefresher()::refreshSelectedFrame,
                entityId -> EventFlow.i().publish(
                        new EventFlow.AnimationChanged(entityId, MY_TAG))
        );
        markCurrentSceneSaveRequired();
        refreshAssetsPanel();
    }

    private void ensureAssetMetaDatabaseLoaded() {
        if (assetMetaDatabase != null) return;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        assetMetaDatabase = AssetMetaDatabase.load(projectDir.child(StudioFs.FILE_ASSETS_JSON));
    }

    // ---------------------------------------------------------------------
    // PREVIEW
    // ---------------------------------------------------------------------

    public void markCurrentSceneSaveRequired() {
        sceneContext().markExplicitSaveRequired();
    }

    public boolean requiresSaveBeforeLeavingCurrentScene() {
        return sceneContext().isDirty();
    }

    public boolean requiresSaveBeforeLeavingAnyScene() {
        return app.getEditorDocumentManager().documents().stream()
                .filter(SceneEditorDocument.class::isInstance)
                .map(SceneEditorDocument.class::cast)
                .anyMatch(SceneEditorDocument::isDirty);
    }

    public void saveAllDirtyScenesWithProgress(Stage uiStage,
                                               Runnable onSuccess,
                                               java.util.function.Consumer<Throwable> onError) {
        try {
            List<SceneEditorDocument> dirty = app.getEditorDocumentManager().documents().stream()
                    .filter(SceneEditorDocument.class::isInstance)
                    .map(SceneEditorDocument.class::cast)
                    .filter(SceneEditorDocument::isDirty)
                    .toList();
            saveSceneDocumentsWithProgress(uiStage, dirty, onSuccess, onError);
        } catch (Throwable failure) {
            if (onError != null) onError.accept(failure);
        }
    }

    private void saveSceneDocumentsWithProgress(Stage uiStage, List<SceneEditorDocument> documents,
                                                Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        Array<SaveProgressRunner.Step> steps = new Array<>();
        for (int i = 0; i < documents.size(); i++) {
            SceneEditorDocument document = documents.get(i);
            steps.add(SaveProgressRunner.Step.sync((float) i / documents.size(),
                    "Saving scene " + (i + 1) + "/" + documents.size() + ": " + document.title(),
                    () -> saveSceneDocument(document)));
        }
        steps.add(SaveProgressRunner.Step.sync(1f, "Finalizing...", () -> {}));
        runSaveSteps(uiStage, steps, () -> {
            for (SceneEditorDocument document : documents) saveSceneDocument(document);
        }, onSuccess, onError);
    }

    /** One progress/error/continuation boundary for menu Save, tab close and application exit. */
    static void runSaveSteps(Stage uiStage, Array<SaveProgressRunner.Step> steps, Runnable withoutUi,
                              Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        if (uiStage != null) {
            new SaveProgressRunner(uiStage).run(steps, onSuccess, onError);
            return;
        }
        try {
            withoutUi.run();
            if (onSuccess != null) onSuccess.run();
        } catch (Throwable failure) {
            if (onError != null) onError.accept(failure);
        }
    }

    public boolean requiresSaveBeforePreview() {
        return requiresSaveBeforeLeavingCurrentScene()
                || isRuntimeExportMissingOrUnusableForPreview(ProjectConfig.getInstance());
    }

    static boolean isRuntimeExportMissingOrUnusableForPreview(ProjectConfig cfg) {
        if (cfg == null) {
            return false;
        }

        if (cfg.exportRootPathDir == null || cfg.exportRootPathDir.isBlank()) {
            return false;
        }

        final Path exportRoot;
        try {
            exportRoot = RuntimeExportPaths.userRootPath(cfg);
        } catch (InvalidPathException ex) {
            return false;
        }
        if (exportRoot == null) {
            return false;
        }

        Path runtimeRoot = exportRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        if (!Files.isDirectory(runtimeRoot)) {
            return true;
        }

        Path projectFile = runtimeRoot.resolve(RuntimeExport.PROJECT_JSON);
        if (!Files.isRegularFile(projectFile) || isEmptyFile(projectFile)) {
            return true;
        }

        String currentSceneName = cfg.getCurrentSceneName();
        if (currentSceneName == null || currentSceneName.isBlank()) {
            return false;
        }

        try {
            JsonValue root = new JsonReader().parse(Files.readString(projectFile));
            if (root == null || !root.isObject()) {
                return true;
            }
            String kind = root.getString("projectKind", null);
            if (!RuntimeExport.RUNTIME_PROJECT_KIND.equals(kind)) {
                return true;
            }

            String exportedCurrentSceneName = root.getString("currentSceneName", null);
            if (!currentSceneName.equals(exportedCurrentSceneName)) {
                return true;
            }

            JsonValue scenes = root.get("scenes");
            if (scenes == null || !scenes.isObject()) {
                return true;
            }
            JsonValue scene = scenes.get(currentSceneName);
            if (scene == null || !scene.isObject()) {
                return true;
            }

            String sceneFileName = RuntimeFs.filenameOnly(scene.getString("file", null));
            if (sceneFileName == null || sceneFileName.isBlank()) {
                return true;
            }

            String scenesDir = root.getString("scenesDir", RuntimeFs.DIR_SCENES);
            if (scenesDir == null || scenesDir.isBlank()) {
                scenesDir = RuntimeFs.DIR_SCENES;
            }

            Path sceneFile = runtimeRoot.resolve(scenesDir).resolve(sceneFileName);
            if (!Files.isRegularFile(sceneFile) || isEmptyFile(sceneFile)) {
                return true;
            }

            String sceneTag = cfg.canonicalSceneTag(currentSceneName);
            if (sceneTag != null && !sceneTag.isBlank()
                    && cfg.projectDirectoryPath != null && !cfg.projectDirectoryPath.isBlank()) {
                FileHandle atlasInput = new FileHandle(Path.of(cfg.projectDirectoryPath)
                        .resolve(StudioFs.DIR_ATLASES).resolve(StudioFs.DIR_INPUT)
                        .resolve(sceneTag).toFile());
                if (AtlasStudioService.isRepackRequired(atlasInput)
                        || (atlasInput.exists() && !games.pixscape.studio.service.atlas.SceneAtlasCoverage.coversInput(
                        atlasInput,
                        new FileHandle(runtimeRoot.resolve(RuntimeFs.DIR_ATLASES)
                                .resolve(sceneTag + ".atlas").toFile())))) {
                    return true;
                }
            }

            if (currentSceneHudExportIsStale(cfg, runtimeRoot)) {
                return true;
            }

            Set<Integer> requiredTileAssetIds = runtimeTiledTileAssetIds(cfg, runtimeRoot, root);
            if (!requiredTileAssetIds.isEmpty()) {
                Path tilesetProfiles = runtimeRoot.resolve(RuntimeFs.FILE_TILESET_PROFILES_JSON);
                if (!Files.isRegularFile(tilesetProfiles) || isEmptyFile(tilesetProfiles)) {
                    return true;
                }
                return !tilesetProfilesContainAllTileAssets(tilesetProfiles, requiredTileAssetIds);
            }

            return false;
        } catch (RuntimeException | IOException ex) {
            return true;
        }
    }

    private static boolean currentSceneHudExportIsStale(ProjectConfig cfg,
                                                        Path runtimeRoot) throws IOException {
        SceneMeta scene = cfg.getCurrentSceneMeta();
        List<String> screenIds = SceneHudRoots.collect(scene);
        if (screenIds.isEmpty()) return false;

        if (cfg.projectDirectoryPath == null || cfg.projectDirectoryPath.isBlank()) return true;
        Path studioRoot = Path.of(cfg.projectDirectoryPath).toAbsolutePath().normalize();
        Path exportedRoot = runtimeRoot.toAbsolutePath().normalize();
        for (String screenId : screenIds) {
            String assetPath = screenId + HudScreenAsset.EXTENSION;
            Path authoredAsset = resolveInside(studioRoot, assetPath);
            Path exportedAsset = resolveInside(exportedRoot, assetPath);
            if (!sameFile(authoredAsset, exportedAsset)) return true;

            JsonValue asset = new JsonReader().parse(Files.readString(authoredAsset));
            String documentId = asset.getString("documentId", null);
            if (documentId == null || documentId.isBlank()) continue;
            if (!sameFile(resolveInside(studioRoot, documentId),
                    resolveInside(exportedRoot, documentId))) return true;
        }
        return false;
    }

    private static Path resolveInside(Path root, String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (Path.of(relative).isAbsolute() || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("HUD preview path escapes its project: " + relative);
        }
        return resolved;
    }

    private static boolean sameFile(Path authored, Path exported) throws IOException {
        return Files.isRegularFile(authored) && Files.isRegularFile(exported)
                && Files.mismatch(authored, exported) == -1L;
    }

    private static Set<Integer> runtimeTiledTileAssetIds(ProjectConfig cfg,
                                                         Path runtimeRoot,
                                                         JsonValue runtimeProjectRoot) throws IOException {
        Set<Integer> out = new HashSet<>();
        TileAnimationsMetaDatabase tileAnimationsDb = loadRuntimeTileAnimations(runtimeRoot);
        collectRuntimeAvailabilityTileAssetIds(cfg, out, tileAnimationsDb);
        collectRuntimeSceneTileAssetIds(runtimeRoot, runtimeProjectRoot, out, tileAnimationsDb);
        return out;
    }

    private static TileAnimationsMetaDatabase loadRuntimeTileAnimations(Path runtimeRoot) {
        if (runtimeRoot == null) {
            return TileAnimationsIO.createEmpty();
        }
        Path file = runtimeRoot.resolve(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        if (!Files.isRegularFile(file)) {
            return TileAnimationsIO.createEmpty();
        }
        return TileAnimationsIO.load(new FileHandle(file.toFile()));
    }

    private static void collectRuntimeAvailabilityTileAssetIds(ProjectConfig cfg,
                                                               Set<Integer> out,
                                                               TileAnimationsMetaDatabase tileAnimationsDb) {
        if (cfg == null || cfg.getScenesMap() == null) {
            return;
        }

        for (ObjectMap.Entry<String, SceneMeta> entry : cfg.getScenesMap()) {
            SceneMeta scene = entry != null ? entry.value : null;
            if (scene == null || scene.runtimeAvailability == null) {
                continue;
            }
            if (scene.runtimeAvailability.tiledTileAssetIds != null) {
                for (Integer assetId : scene.runtimeAvailability.tiledTileAssetIds) {
                    if (assetId != null && assetId > 0) {
                        out.add(assetId);
                    }
                }
            }
            if (scene.runtimeAvailability.tiledAnimationIds != null) {
                for (Integer tileAnimationId : scene.runtimeAvailability.tiledAnimationIds) {
                    if (!addTiledAnimationFrameAssetIds(tileAnimationsDb, tileAnimationId, out)
                            && tileAnimationId != null && tileAnimationId > 0) {
                        out.add(tileAnimationId);
                    }
                }
            }
        }
    }

    private static void collectRuntimeSceneTileAssetIds(Path runtimeRoot,
                                                       JsonValue runtimeProjectRoot,
                                                       Set<Integer> out,
                                                       TileAnimationsMetaDatabase tileAnimationsDb) throws IOException {
        JsonValue scenes = runtimeProjectRoot != null ? runtimeProjectRoot.get("scenes") : null;
        if (runtimeRoot == null || scenes == null || !scenes.isObject()) {
            return;
        }

        String scenesDir = runtimeProjectRoot.getString("scenesDir", RuntimeFs.DIR_SCENES);
        if (scenesDir == null || scenesDir.isBlank()) {
            scenesDir = RuntimeFs.DIR_SCENES;
        }

        for (JsonValue scene = scenes.child; scene != null; scene = scene.next) {
            String sceneFileName = RuntimeFs.filenameOnly(scene.getString("file", null));
            if (sceneFileName == null || sceneFileName.isBlank()) {
                continue;
            }

            Path sceneFile = runtimeRoot.resolve(scenesDir).resolve(sceneFileName);
            if (!Files.isRegularFile(sceneFile) || isEmptyFile(sceneFile)) {
                throw new IOException("Missing runtime scene file: " + sceneFile);
            }
            collectTiledLayerTileAssetIds(new JsonReader().parse(Files.readString(sceneFile)), out, tileAnimationsDb);
        }
    }

    private static void collectTiledLayerTileAssetIds(JsonValue root,
                                                     Set<Integer> out,
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
                                            Set<Integer> out,
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
                                                          Set<Integer> out) {
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

    private static boolean tilesetProfilesContainAllTileAssets(Path tilesetProfiles,
                                                               Set<Integer> requiredTileAssetIds) throws IOException {
        if (requiredTileAssetIds == null || requiredTileAssetIds.isEmpty()) {
            return true;
        }

        JsonValue root = new JsonReader().parse(Files.readString(tilesetProfiles));
        JsonValue tilesets = root != null ? root.get("tilesets") : null;
        if (tilesets == null || !tilesets.isArray()) {
            return false;
        }

        Set<Integer> exportedTileAssetIds = new HashSet<>();
        for (JsonValue tileset = tilesets.child; tileset != null; tileset = tileset.next) {
            JsonValue tileAssetIds = tileset.get("tileAssetIds");
            if (tileAssetIds == null || !tileAssetIds.isArray()) {
                continue;
            }
            for (JsonValue tileAssetId = tileAssetIds.child; tileAssetId != null; tileAssetId = tileAssetId.next) {
                exportedTileAssetIds.add(tileAssetId.asInt());
            }
        }
        return exportedTileAssetIds.containsAll(requiredTileAssetIds);
    }

    private static boolean isEmptyFile(Path file) {
        try {
            return Files.size(file) <= 0L;
        } catch (IOException ex) {
            return true;
        }
    }

    private void clearCurrentSceneSaveRequired() {
        SceneEditorContext context = canvas.getAttachedSceneContext();
        if (context == null) context = app.getMostRecentSceneEditorContext();
        if (context != null && !context.isDisposed()) context.markSaved();
    }

    // ---------------------------------------------------------------------
    // STUDIO PATHS
    // ---------------------------------------------------------------------

    private FileHandle studioProjectFile(ProjectConfig cfg) {
        FileHandle dir = StudioFs.requireStudioProjectDir(cfg);
        if (!dir.exists()) dir.mkdirs();
        String base = (cfg != null && cfg.projectFileName != null && !cfg.projectFileName.isBlank()) ? cfg.projectFileName : "Untitled";
        return dir.child(StudioFs.withExt(base, StudioFs.EXT_JSON));
    }

    public FileHandle getStudioProjectFile() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return (cfg != null) ? studioProjectFile(cfg) : null;
    }

    // ---------------------------------------------------------------------
    // ASSET METADATA ACCESS
    // ---------------------------------------------------------------------

    public int resolveAssetIdBySourceRelPath(String sourceRelPath, AssetType type) {
        if (assetMetaDatabase == null || sourceRelPath == null || sourceRelPath.isBlank()) return -1;
        AssetMeta meta = assetMetaDatabase.findUniqueBySourceRelPath(sourceRelPath, type);
        return (meta != null) ? meta.id() : -1;
    }

    public AssetMeta getAssetMeta(int assetId) {
        if (assetId <= 0) return null;
        ensureAssetMetaDatabaseLoaded();
        return assetMetaDatabase != null ? assetMetaDatabase.findById(assetId) : null;
    }

    public AssetMetaDatabase getAssetMetaDatabase() {
        ensureAssetMetaDatabaseLoaded();
        if (assetMetaDatabase == null) {
            throw new IllegalStateException("Asset metadata is unavailable.");
        }
        return assetMetaDatabase;
    }
    public void prepareGameObjectNormals(World world,SceneMeta editingMeta) {
        if(!editingMeta.useNormalMaps){
            world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class).publishAuthoringNormals(null);
            return;
        }
        ProjectConfig cfg=ProjectConfig.getInstance();
        java.util.Set<String> required=new SceneAtlasInputService().collectRequiredAtlasInputPathsForScene(
                cfg,world,editingMeta,assetMetaDatabase,tileAnimationsMetaDatabase);
        canvas.getAtlasService().prepareAuthoringNormals(StudioFs.requireStudioProjectDir(cfg),world,required,assetMetaDatabase);
    }

    // ---------------------------------------------------------------------
    // NEW PROJECT
    // ---------------------------------------------------------------------

    public void newProject(String projectTitle,
                           String projectFileName,
                           String projectDirectoryPath,
                           String exportDir,
                           PlatformTarget platformTarget,
                           int glSamples) {
        FileHandle projectDir = null;
        boolean projectDirExistedBeforeAttempt = false;
        ProjectConfig cfg = new ProjectConfig();
        try {
            app.onHudRuntimeProjectChanging();
            app.getEditorDocumentManager().clear();

            cfg.projectTitle = projectTitle;
            cfg.projectFileName = projectFileName;
            cfg.projectDirectoryPath = (projectDirectoryPath == null || projectDirectoryPath.isBlank())
                    ? StudioFs.defaultProjectDirectoryPath(projectFileName)
                    : projectDirectoryPath;
            cfg.exportRootPathDir = exportDir;
            requireValidExportRootOrThrow(cfg, "newProject");

            cfg.glSamples = glSamples;

            cfg.createSceneMeta("MainScene");
            SceneMeta meta = cfg.getCurrentSceneMeta();

            ProjectConfig.setInstance(cfg);
            String canonicalTag = cfg.canonicalSceneTagCurrent();
            SceneEditorContext context = canvas.createSceneContext(canonicalTag, meta);
            canvas.attach(context);
            context.bindSceneIdentity(canonicalTag);
            bindSceneIdentityAuthorities(meta);

            projectDir = StudioFs.requireStudioProjectDir(cfg);
            projectDirExistedBeforeAttempt = projectDir.exists();
            projectDir.mkdirs();
            app.bindHudRuntimeProject(projectDir);

            projectDir.child(StudioFs.DIR_SCENES).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_IMAGES).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_TILES).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_ANIMATIONS).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_EFFECTS).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_SHADERS).mkdirs();
            projectDir.child(StudioFs.DIR_ORIG_AUDIO).mkdirs();
            projectDir.child(StudioFs.DIR_ATLASES).mkdirs();

            applyProjectFixedSettings(cfg);
            canvas.refreshProjectBoundServices();

            refreshParticleEffectsRoot(cfg);

            assetMetaDatabase = new AssetMetaDatabase();
            assetMetaDatabase.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));

            tileAnimationsMetaDatabase = TileAnimationsIO.createEmpty();
            TileAnimationsIO.save(
                    tileAnimationsMetaDatabase,
                    projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
            );

            int indexL = sceneContext().layerService().addLayerTop("Main layer");
            int layerEntityId = sceneContext().layerService().getLayerEntity(indexL);
            sceneContext().selectionService().setActivelayerId(layerEntityId);
            EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));

            saveProjectAndCurrentScene();

            app.getEditorDocumentManager().openScene(canonicalTag, meta.getName(), context);
            SceneAtlasLoaderService.packSceneAtlas(cfg, canonicalTag, projectDir);
            refreshAssetsPanel();
            app.getBottomBar().refreshSelectBox();

            FileHandle projectFile = studioProjectFile(cfg);
            if (!projectFile.exists()) {
                throw new IllegalStateException("Project file was not created: " + projectFile.path());
            }

            FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
            if (!assetsFile.exists()) {
                throw new IllegalStateException("Assets registry file was not created: " + assetsFile.path());
            }

            EditorSettings.get().lastProjectPath = projectFile.path();
            recentProjectsService.addRecentProject(projectFile.path());
            clipboardService().clear();

            Gdx.graphics.setTitle("Pixscape 2D Game Studio (" + projectTitle + ")");
            StudioLog.info("Project created: " + projectTitle);
        } catch (RuntimeException ex) {
            cleanupFailedNewProjectDir(projectDir, projectDirExistedBeforeAttempt);
            unloadProjectToEmptyEditor();
            throw new IllegalStateException("Project creation failed and the editor was reset to an empty state.", ex);
        }
    }

    // ---------------------------------------------------------------------
    // OPEN PROJECT (studio)
    // ---------------------------------------------------------------------

    public Optional<ProjectOpenFailure> tryOpenProject(FileHandle projectFile, String flowLabel) {
        try {
            openProjectStrict(projectFile);
            return Optional.empty();
        } catch (Exception ex) {
            unloadProjectToEmptyEditor();
            return Optional.of(new ProjectOpenFailure(flowLabel, projectFile, ex));
        }
    }

    private void openProjectStrict(FileHandle projectFile) {
        OpenProjectContext context = loadOpenContextOrThrow(projectFile);
        ProjectConfig cfg = context.config();
        FileHandle projectDir = context.projectDir();
        String sceneName = context.sceneName();

        app.onHudRuntimeProjectChanging();
        app.getEditorDocumentManager().clear();
        ProjectConfig.setInstance(cfg);
        assetMetaDatabase = AssetMetaDatabase.load(context.assetsMetaFile());
        app.bindHudRuntimeProject(projectDir);

        reloadTileAnimationsFromProject(projectDir);

        applyProjectFixedSettings(cfg);
        refreshParticleEffectsRoot(cfg);
        clipboardService().clear();

        try {
            loadScene(cfg, sceneName, projectDir);
            sceneMetaBridge.pushCurrentSceneMetaToUI();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot load current scene '" + sceneName + "'.", ex);
        }

        EditorSettings.get().lastProjectPath = projectFile.path();
        recentProjectsService.addRecentProject(projectFile.path());

        Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + ")");
        StudioLog.info("Project opened: " + cfg.projectTitle);
    }

    static OpenProjectContext loadOpenContextOrThrow(FileHandle projectFile) {
        if (projectFile == null) {
            throw new IllegalStateException("Project file is null.");
        }
        if (!projectFile.exists() || projectFile.isDirectory()) {
            throw new IllegalStateException("Project file is missing or invalid: " + projectFile.path());
        }

        rejectRuntimeExportProjectOrInvalidStudioKind(projectFile);

        ProjectConfig cfg = ProjectConfig.ProjectIO.loadProject(projectFile);
        FileHandle projectDir = projectFile.parent();
        validateProjectPathSafetyOrThrow(projectFile, projectDir, cfg);

        String sceneName = cfg.getCurrentSceneName();
        SceneMeta meta = cfg.getSceneMeta(sceneName);
        if (meta == null || meta.getFile() == null || meta.getFile().isBlank()) {
            throw new IllegalStateException("Current scene metadata is missing for scene: " + sceneName);
        }

        FileHandle sceneFile = projectDir.child(StudioFs.DIR_SCENES).child(meta.getFile());
        if (!sceneFile.exists()) {
            throw new IllegalStateException("Scene file is missing: " + sceneFile.path());
        }

        FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        if (!metaFile.exists()) {
            throw new IllegalStateException("Assets registry file is missing: " + metaFile.path());
        }

        return new OpenProjectContext(cfg, projectDir, sceneName, metaFile);
    }

    public void unloadProjectToEmptyEditor() {
        app.onHudRuntimeProjectChanging();
        app.getEditorDocumentManager().clear();
        resetProjectConfigToEmptyState();
        SceneEditorContext empty = canvas.createSceneContext(null, null);
        canvas.attach(empty);
        bindSceneIdentityAuthorities(null);
        refreshAssetsPanel();
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
        clearCurrentSceneSaveRequired();
        canvas.refreshProjectBoundServices();
        Gdx.graphics.setTitle(STUDIO_TITLE);
    }

    // ---------------------------------------------------------------------
    // LOAD SCENE
    // ---------------------------------------------------------------------

    void loadScene(ProjectConfig cfg, String sceneName, FileHandle projectDir) {
        SceneMeta meta = cfg.getSceneMeta(sceneName);
        if (meta == null) {
            throw new IllegalStateException("Missing scene metadata for scene '" + sceneName + "'.");
        }
        String canonicalTag = cfg.canonicalSceneTagFor(meta);
        if (canonicalTag == null || canonicalTag.isBlank()) {
            throw new IllegalStateException("Missing canonical scene tag for scene '" + sceneName + "'.");
        }
        var key = new games.pixscape.studio.document.EditorDocumentKey(
                games.pixscape.studio.document.EditorDocumentType.SCENE, canonicalTag);
        var open = app.getEditorDocumentManager().find(key);
        if (open instanceof games.pixscape.studio.document.SceneEditorDocument) {
            app.getEditorDocumentManager().activate(key);
            return;
        }

        String previousSceneName = cfg.getCurrentSceneName();
        SceneEditorContext previousContext = canvas.getAttachedSceneContext();
        SceneEditorContext candidate = null;
        try {
            FileHandle sceneFile = projectDir.child(StudioFs.DIR_SCENES).child(meta.getFile());
            if (!sceneFile.exists()) {
                throw new IllegalStateException("Scene file is missing: " + sceneFile.path());
            }
            candidate = canvas.createSceneContext(canonicalTag, meta);
            EventFlow.i().flush();
            canvas.attach(candidate);
            cfg.setCurrentSceneByName(sceneName);
            candidate.bindSceneIdentity(canonicalTag);
            bindSceneIdentityAuthorities(meta);
            canvas.getPhysicsSelectionReconciler().bindSceneContext(canonicalTag);

            ResolvedSceneActivationPipeline pipeline = new ResolvedSceneActivationPipeline(
                    candidate.world(),
                    canvas.getTileAnimationRegistry(),
                    canvas.getTiledAllocatorService(),
                    candidate.historyManager(),
                    this::rebuildRenderRuntimeForScene);
            pipeline.activate(new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                    cfg, meta, sceneFile, projectDir, cfg.projectTitle, sceneName, canonicalTag));

            ensureAssetMetaDatabaseLoaded();
            int reconciledAnimations = AnimationAssetEntityReconciler.reconcileAll(
                    candidate.world(),
                    assetMetaDatabase::findById,
                    canvas.getAnimationPreviewRefresher()::refreshSelectedFrame,
                    entityId -> EventFlow.i().publish(
                            new EventFlow.AnimationChanged(entityId, MY_TAG)));
            if (reconciledAnimations > 0) candidate.markExplicitSaveRequired();
            canvas.requestTiledFallbackValidation();
            candidate.identityRegistry().rebuild();

            int firstLayerEntityId = candidate.layerService().getFirstLayerEntity();
            if (firstLayerEntityId != -1) {
                candidate.selectionService().setActivelayerId(firstLayerEntityId);
            }
            candidate.selectionService().clearSelection();
            candidate.historyManager().markSaved();

            app.getEditorDocumentManager().openScene(canonicalTag, sceneName, candidate);
            if (previousContext != null && !isDocumentOwned(previousContext)) {
                canvas.releaseSceneContext(previousContext);
                previousContext.dispose();
            }
            EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
            refreshAssetsPanel();
            app.getBottomBar().refreshSelectBox();
        } catch (RuntimeException ex) {
            EventFlow.i().discardPending();
            // Publication transferred ownership. A later UI failure must not destroy an open document.
            if (isDocumentOwned(candidate)) throw ex;
            if (candidate != null && !candidate.isDisposed()) {
                canvas.releaseSceneContext(candidate);
                candidate.dispose();
            }
            if (previousSceneName != null && cfg.getSceneMeta(previousSceneName) != null) {
                cfg.setCurrentSceneByName(previousSceneName);
            }
            if (previousContext != null && !previousContext.isDisposed()) {
                canvas.attach(previousContext);
            }
            throw ex;
        }

    }

    private boolean isDocumentOwned(SceneEditorContext context) {
        return app.getEditorDocumentManager().ownsContext(context);
    }

    // ---------------------------------------------------------------------
    // SAVE PROJECT + CURRENT SCENE (studio)
    // ---------------------------------------------------------------------

    public void saveProjectAndCurrentScene() {
        final SaveExecutionPlan plan = prepareSaveExecutionPlan();
        requireSavePlanCurrent(plan);
        saveProjectFile(plan.cfg());
        if (!plan.hasSceneToSave()) {
            finishSaveWithoutScene();
            return;
        }

        maybeRepackAtlas(plan);
        rebuildPlannedScene(plan);
        savePlannedScene(plan);
        saveTileAnimations(plan);
        exportRuntimeBestEffort(plan.cfg(), plan.studioDir());
        finishSaveWithScene(plan);
    }

    public void saveProjectAndCurrentSceneWithProgress(Stage uiStage,
                                                       Runnable onSuccess,
                                                       java.util.function.Consumer<Throwable> onError) {
        final SaveExecutionPlan plan;
        try {
            plan = prepareSaveExecutionPlan();
        } catch (Throwable t) {
            if (onError != null) onError.accept(t);
            return;
        }

        Array<SaveProgressRunner.Step> steps = new Array<>();

        steps.add(saveStep(plan, 0.10f, "Preparing save...", () -> {
        }));
        steps.add(saveStep(plan, 0.20f, "Saving project file...", () -> saveProjectFile(plan.cfg())));

        if (!plan.hasSceneToSave()) {
            steps.add(saveStep(plan, 1.00f, "Finalizing...", this::finishSaveWithoutScene));
        } else {
            steps.add(SaveProgressRunner.Step.async(0.25f, "Repacking atlas...",
                    (progress, next, fail) -> maybeRepackAtlasAsync(plan, progress, next, fail)));
            steps.add(saveStep(plan, 0.65f, "Rebuilding tiled sparse data...", () -> rebuildPlannedScene(plan)));
            steps.add(saveStep(plan, 0.75f, "Saving scene...", () -> savePlannedScene(plan)));
            steps.add(saveStep(plan, 0.82f, "Saving tiled animations...", () -> saveTileAnimations(plan)));
            steps.add(saveStep(plan, 0.90f, "Exporting runtime...", () -> exportRuntime(plan.cfg(), plan.studioDir())));
            steps.add(saveStep(plan, 1.00f, "Finalizing...", () -> finishSaveWithScene(plan)));
        }

        runSaveSteps(uiStage, steps, () -> executePreparedSavePlan(plan),
                () -> completePlannedSave(plan, onSuccess, onError), onError);
    }

    public void saveProjectAs(FileHandle selectedProjectFile) {
        if (selectedProjectFile == null) {
            throw new IllegalArgumentException("selectedProjectFile is null");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle currentProjectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle targetProjectDir = selectedProjectFile.isDirectory()
                ? selectedProjectFile
                : selectedProjectFile.parent();
        String targetProjectFileName = selectedProjectFile.isDirectory()
                ? cfg.projectFileName
                : StudioFs.baseName(selectedProjectFile.name());

        if (targetProjectDir == null || targetProjectFileName == null || targetProjectFileName.isBlank()) {
            throw new IllegalStateException("Save As target must include a project file name.");
        }

        requireValidExportRootOrThrow(cfg, "saveProjectAs");

        saveProjectAndCurrentScene();

        boolean copiedToNewDirectory = !targetProjectDir.path().equals(currentProjectDir.path());
        if (copiedToNewDirectory) {
            app.onHudRuntimeProjectChanging();
            try {
                ProjectRenameService.copyProjectDirectory(currentProjectDir, targetProjectDir);
                cfg.projectDirectoryPath = targetProjectDir.path();
            } finally {
                app.bindHudRuntimeProject(StudioFs.requireStudioProjectDir(cfg));
            }
        }

        if (!targetProjectFileName.equals(cfg.projectFileName)) {
            ProjectRenameService.renameProjectFile(targetProjectDir, cfg, targetProjectFileName);
        }

        saveProjectAndCurrentScene();
    }

    private SaveExecutionPlan prepareSaveExecutionPlan() {
        return prepareSaveExecutionPlan(true);
    }

    private SaveExecutionPlan prepareSaveExecutionPlan(boolean requireExportRoot) {
        final ProjectConfig cfg = ProjectConfig.getInstance();

        if (cfg == null) throw new IllegalStateException("No project is loaded.");
        if (cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            throw new IllegalStateException("saveProjectAndCurrentScene: projectName is empty, abort.");
        }
        if (requireExportRoot) requireValidExportRootOrThrow(cfg, "saveProjectAndCurrentScene");

        final FileHandle studioDir = StudioFs.requireStudioProjectDir(cfg);
        if (studioDir == null) {
            throw new IllegalStateException("saveProjectAndCurrentScene: studioDir is null, abort.");
        }

        final SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null || meta.getName() == null || meta.getName().isEmpty()) {
            return new SaveExecutionPlan(cfg, studioDir, null, null, null, false,
                    null, null, null, 0, 0, cfg.exportRootPathDir);
        }

        final String sceneName = meta.getName();
        final String sceneFileName = meta.getFile();
        if (sceneFileName == null || sceneFileName.isBlank()) {
            throw new IllegalStateException(
                    "saveProjectAndCurrentScene: SceneMeta.file is null or empty for '" + sceneName + "', abort.");
        }

        final FileHandle scenesDir = studioDir.child(StudioFs.DIR_SCENES);
        final FileHandle sceneFile = scenesDir.child(sceneFileName);
        final String canonicalTag = cfg.canonicalSceneTag(sceneName);
        SceneEditorContext source = sceneContext();
        if (canonicalTag == null || !canonicalTag.equals(source.sceneIdentity())
                || source.sceneMeta() != meta) {
            throw new IllegalStateException("Select the Scene document before saving or launching Preview; "
                    + "an asset editor cannot be saved as the project Scene.");
        }
        SaveExecutionPlan plan = new SaveExecutionPlan(cfg, studioDir, sceneName, sceneFile,
                canonicalTag, true, source, source.world(), meta, source.generation(),
                source.attachCount(), cfg.exportRootPathDir);
        requireSavePlanCurrent(plan);
        source.world().process();
        return plan;
    }

    /** Validate before any save-side mutation, including delayed atlas callbacks. */
    private void requireSavePlanCurrent(SaveExecutionPlan plan) {
        if (ProjectConfig.getInstance() != plan.cfg()
                || !plan.studioDir().equals(StudioFs.requireStudioProjectDir(plan.cfg()))
                || !Objects.equals(plan.exportRoot(), plan.cfg().exportRootPathDir)) {
            throw new IllegalStateException("Save cancelled: the project or its destination changed.");
        }
        if (!plan.hasSceneToSave()) {
            if (plan.cfg().getCurrentSceneMeta() != null)
                throw new IllegalStateException("Save cancelled: the current Scene changed.");
            return;
        }
        SceneEditorContext source = plan.source();
        if (source.isDisposed() || source.generation() != plan.sourceGeneration()
                || source.attachCount() != plan.sourceAttachCount()
                || sceneContext() != source || source.world() != plan.sourceWorld()
                || source.sceneMeta() != plan.sourceMeta()
                || !Objects.equals(source.sceneIdentity(), plan.canonicalTag())
                || plan.cfg().getCurrentSceneMeta() != plan.sourceMeta()
                || !Objects.equals(plan.cfg().getCurrentSceneName(), plan.sceneName())
                || !plan.sceneFile().equals(plan.studioDir().child(StudioFs.DIR_SCENES)
                        .child(plan.sourceMeta().getFile()))) {
            throw new IllegalStateException("Save cancelled: the Scene document changed or was closed. "
                    + "Save again from the intended Scene document.");
        }
    }

    private SaveProgressRunner.Step saveStep(SaveExecutionPlan plan, float progress,
                                             String message, Runnable action) {
        return SaveProgressRunner.Step.sync(progress, message, () -> {
            requireSavePlanCurrent(plan);
            action.run();
        });
    }

    private void completePlannedSave(SaveExecutionPlan plan, Runnable onSuccess,
                                     java.util.function.Consumer<Throwable> onError) {
        try {
            requireSavePlanCurrent(plan);
            if (onSuccess != null) onSuccess.run();
        } catch (Throwable failure) {
            if (onError != null) onError.accept(failure);
        }
    }

    private void rebuildPlannedScene(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        rebuildSparseFromDense(plan.sourceWorld());
    }

    private void savePlannedScene(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        saveScene(plan.sourceWorld(), plan.sceneFile(), false);
    }

    private void executePreparedSavePlan(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        saveProjectFile(plan.cfg());

        if (!plan.hasSceneToSave()) {
            finishSaveWithoutScene();
            return;
        }

        maybeRepackAtlas(plan);
        rebuildPlannedScene(plan);
        savePlannedScene(plan);
        saveTileAnimations(plan);
        exportRuntime(plan.cfg(), plan.studioDir());
        finishSaveWithScene(plan);
    }

    private void saveTileAnimations(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        TileAnimationsIO.save(
                tileAnimationsMetaDatabase,
                plan.studioDir().child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
    }

    private void exportRuntimeBestEffort(ProjectConfig cfg, FileHandle studioDir) {
        try {
            exportRuntime(cfg, studioDir);
        } catch (Exception ex) {
            Gdx.app.error("SceneManager", "Runtime export failed", ex);
            StudioLog.warn("Runtime export failed");
        }
    }

    /** A HUD document Save also refreshes the game's derived assets. */
    public void exportRuntimeAfterHudSave() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        exportRuntime(cfg, StudioFs.requireStudioProjectDir(cfg));
    }

    private void exportRuntime(ProjectConfig cfg, FileHandle studioDir) {
        SceneHudRuntimeExport.requireSavedDocuments(studioDir, cfg,
                app.getEditorDocumentManager().documents().stream()
                        .filter(document -> document instanceof HudScreenEditorDocument)
                        .filter(OpenEditorDocument::isDirty)
                        .map(document -> ((HudScreenEditorDocument) document).screenId())
                        .toList());
        FileHandle userRootDir = RuntimeExportPaths.userRootFileHandle(cfg);
        if (userRootDir == null) {
            throw new IllegalStateException("Runtime export root is not configured.");
        }
        userRootDir.mkdirs();
        RuntimeExport.exportRuntime(cfg, studioDir, userRootDir);
    }

    private void finishSaveWithoutScene() {
        String log = "saveProjectAndCurrentScene: no current scene, skipping scene save.";
        Gdx.app.log("SceneManager", log);
        EventFlow.i().publish(new EventFlow.LogMessage(log));
    }

    private void finishSaveWithScene(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        Gdx.graphics.setTitle(STUDIO_TITLE + " (" + plan.cfg().projectTitle + ")");
        plan.source().markSaved();
        StudioLog.info("Project saved successfully");
    }

    private void saveCurrentSceneOnly(ProjectConfig cfg) {
        SaveExecutionPlan plan = prepareSaveExecutionPlan(false);
        requireSavePlanCurrent(plan);
        String sceneName = cfg.getCurrentSceneName();
        if (sceneName == null || sceneName.isEmpty()) {
            Gdx.app.log("SceneManager", "saveCurrentSceneOnly: no current scene, skipping.");
            return;
        }


        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null) return;

        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);
        scenesDir.mkdirs();

        String fileName = meta.getFile();
        if (fileName == null || fileName.isEmpty()) {
            Gdx.app.error("SceneManager",
                    "saveCurrentSceneOnly: SceneMeta.file is null or empty for '" + sceneName + "', skipping save.");
            return;
        }


        if (EditorSettings.get().autoRepackAtlases) {
            repackSceneAtlas(cfg, sceneName, projectDir);
        }
        rebuildPlannedScene(plan);

        savePlannedScene(plan);
        if (tileAnimationsMetaDatabase != null) {
            TileAnimationsIO.save(
                    tileAnimationsMetaDatabase,
                    projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
            );
        }
        Gdx.app.log("SceneManager",
                "saveCurrentSceneOnly: scene '" + sceneName + "' saved.");
    }

    /**
     * Saves a scene to the given file.
     *
     * @param world       ECS world
     * @param outFile     output file (for example, scenes/scene1.json)
     * @param contentOnly if true, saves only "scene" content (entities with EntityIndexComponent)
     */
    public static void saveScene(World world, FileHandle outFile, boolean contentOnly) {
        if (world == null) throw new IllegalArgumentException("world is null");
        if (outFile == null) throw new IllegalArgumentException("outFile is null");

        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (!(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            JsonArtemisSerializer ser = new JsonArtemisSerializer(world);
            ser.setUsePrototypes(true); // compact
            wsm.setSerializer(ser);
        }

        IntBag entitiesToSave = contentOnly
                ? world.getAspectSubscriptionManager().get(Aspect.all(EntityIndexComponent.class)).getEntities()
                : world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();

        games.pixscape.runtime.loading.ContentLayerValidator.validateWorld(
                world, "Scene '" + outFile.path() + "'");

        SaveFileFormat format = new SaveFileFormat(entitiesToSave);
        SceneVolatileStateSnapshot volatileState = clearVolatileSceneStateForSave(world, entitiesToSave);
        try {
            StudioIO.writeAtomic(outFile, out -> wsm.save(out, format));
        } finally {
            volatileState.restore(world);
        }
    }

    private static SceneVolatileStateSnapshot clearVolatileSceneStateForSave(World world, IntBag entitiesToSave) {
        ComponentMapper<VisibilityComponent> mVisibility = world.getMapper(VisibilityComponent.class);
        ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class);
        ComponentMapper<SpatialPhysicsFootprintComponent> mSpatialFootprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class);

        Array<VisibilityRuntimeSnapshot> visibilityStates = new Array<>();
        Array<CompiledPhysicsSnapshot> compiledStates = new Array<>();
        Array<SpatialPhysicsFootprintSnapshot> spatialFootprintStates = new Array<>();

        int[] data = entitiesToSave.getData();
        for (int i = 0, n = entitiesToSave.size(); i < n; i++) {
            int entityId = data[i];

            VisibilityComponent visibility = mVisibility.getSafe(entityId, null);
            if (visibility != null && (!visibility.culledByFrustum || visibility.inView)) {
                visibilityStates.add(new VisibilityRuntimeSnapshot(
                        entityId,
                        visibility.culledByFrustum,
                        visibility.inView
                ));
                visibility.culledByFrustum = true;
                visibility.inView = false;
            }

            PhysicsCompiledFixturesComponent compiled =
                    mCompiled.getSafe(entityId, null);
            if (compiled != null) {
                Array<CompiledFixtureData> fixtures =
                        new Array<>(true, compiled.fixtures.size, CompiledFixtureData.class);
                for (int fixtureIndex = 0;
                     fixtureIndex < compiled.fixtures.size;
                     fixtureIndex++) {
                    fixtures.add(compiled.fixtures.get(fixtureIndex).copy());
                }
                compiledStates.add(new CompiledPhysicsSnapshot(
                        entityId, fixtures, compiled.generation, compiled.valid));
                mCompiled.remove(entityId);
            }

            SpatialPhysicsFootprintComponent spatialFootprint =
                    mSpatialFootprint.getSafe(entityId, null);
            if (spatialFootprint != null) {
                spatialFootprintStates.add(new SpatialPhysicsFootprintSnapshot(
                        entityId,
                        spatialFootprint.valid,
                        spatialFootprint.localOffsetXPx,
                        spatialFootprint.localOffsetYPx,
                        spatialFootprint.radiusPx,
                        spatialFootprint.physicsGeneration));
                mSpatialFootprint.remove(entityId);
            }
        }

        return new SceneVolatileStateSnapshot(
                visibilityStates, compiledStates, spatialFootprintStates);
    }

    private record SceneVolatileStateSnapshot(
            Array<VisibilityRuntimeSnapshot> visibilityStates,
            Array<CompiledPhysicsSnapshot> compiledStates,
            Array<SpatialPhysicsFootprintSnapshot> spatialFootprintStates) {
        void restore(World world) {
            ComponentMapper<VisibilityComponent> mVisibility = world.getMapper(VisibilityComponent.class);
            ComponentMapper<PhysicsCompiledFixturesComponent> mCompiled =
                    world.getMapper(PhysicsCompiledFixturesComponent.class);
            ComponentMapper<SpatialPhysicsFootprintComponent> mSpatialFootprint =
                    world.getMapper(SpatialPhysicsFootprintComponent.class);

            for (VisibilityRuntimeSnapshot snapshot : visibilityStates) {
                VisibilityComponent visibility = mVisibility.getSafe(snapshot.entityId(), null);
                if (visibility == null) continue;
                visibility.culledByFrustum = snapshot.culledByFrustum();
                visibility.inView = snapshot.inView();
            }

            for (CompiledPhysicsSnapshot snapshot : compiledStates) {
                PhysicsCompiledFixturesComponent compiled =
                        mCompiled.create(snapshot.entityId());
                compiled.fixtures.clear();
                for (int i = 0; i < snapshot.fixtures().size; i++) {
                    compiled.fixtures.add(snapshot.fixtures().get(i).copy());
                }
                compiled.generation = snapshot.generation();
                compiled.valid = snapshot.valid();
            }

            for (SpatialPhysicsFootprintSnapshot snapshot : spatialFootprintStates) {
                SpatialPhysicsFootprintComponent footprint =
                        mSpatialFootprint.create(snapshot.entityId());
                footprint.valid = snapshot.valid();
                footprint.localOffsetXPx = snapshot.localOffsetXPx();
                footprint.localOffsetYPx = snapshot.localOffsetYPx();
                footprint.radiusPx = snapshot.radiusPx();
                footprint.physicsGeneration = snapshot.physicsGeneration();
            }
        }
    }

    private record CompiledPhysicsSnapshot(
            int entityId,
            Array<CompiledFixtureData> fixtures,
            int generation,
            boolean valid) {
    }

    private record SpatialPhysicsFootprintSnapshot(
            int entityId,
            boolean valid,
            float localOffsetXPx,
            float localOffsetYPx,
            float radiusPx,
            int physicsGeneration) {
    }

    private record VisibilityRuntimeSnapshot(int entityId, boolean culledByFrustum, boolean inView) {
    }

    /**
     * Saves only the Studio project file (<projectName>.json).
     */
    private void saveProjectFile(ProjectConfig cfg) {
        if (cfg == null) return;

        String projectName = cfg.projectFileName;
        if (projectName == null || projectName.isEmpty()) {
            Gdx.app.error("SceneManager", "saveProjectFile: invalid projectName, skipping.");
            return;
        }
        try {
            requireValidExportRootOrThrow(cfg, "saveProjectFile");
        } catch (IllegalStateException ex) {
            Gdx.app.error("SceneManager", ex.getMessage());
            EventFlow.i().publish(new EventFlow.LogMessage(ex.getMessage()));
            StudioLog.warn(ex.getMessage());
            return;
        }

        FileHandle projectFile = studioProjectFile(cfg);

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        recentProjectsService.addRecentProject(projectFile.path());

        String log = "saveProjectFile: project '" + projectName + "' saved.";
        Gdx.app.log("SceneManager", log);
        EventFlow.i().publish(new EventFlow.LogMessage(log));
    }

    // ---------------------------------------------------------------------
    // ATLAS INPUT / ATLAS REPACK
    // ---------------------------------------------------------------------

    public void requestAsyncPack(String sceneTag) {
        atlasStudioService.requestAsyncPack(sceneTag);
    }

    public boolean ensureSceneAtlasInputHasAsset(String sceneTag, int assetId) {
        if (sceneTag == null || sceneTag.isBlank()) return false;
        if (assetId <= 0 || assetMetaDatabase == null) return false;
        if (atlasStudioService.resolveBinding(assetId, sceneTag) != null) return false;

        AssetMeta meta = assetMetaDatabase.findById(assetId);
        if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) {
            return false;
        }

        return sceneAtlasInputService.ensureAssetInInput(
                ProjectConfig.getInstance(),
                sceneTag,
                meta.sourceRelPath()
        );
    }

    public boolean ensureImageInAtlasInput(String sceneTag, String projectRelativePath) {
        return sceneAtlasInputService.ensureImageInInput(
                ProjectConfig.getInstance(),
                sceneTag,
                projectRelativePath
        );
    }

    public boolean ensureAnimationDirInAtlasInput(String sceneTag, String animationRelativeDir) {

        if (sceneTag == null || sceneTag.isEmpty()) {
            throw new IllegalStateException("Scene tag must not be null");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle animationDir = projectDir.child(animationRelativeDir);
        if (!animationDir.exists() || !animationDir.isDirectory()) {
            throw new IllegalStateException("Animation dir not found: " + animationDir.path());
        }

        FileHandle inputDir = projectDir
                .child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(sceneTag);

        inputDir.mkdirs();

        boolean changed = false;
        for (FileHandle frame : animationDir.list()) {
            if (frame == null || frame.isDirectory()) continue;
            String ext = frame.extension() != null ? frame.extension().toLowerCase(Locale.ROOT) : "";
            if (!StudioFs.EXT_PNG.substring(1).equals(ext)) continue;

            FileHandle dest = inputDir.child(frame.name());
            if (!dest.exists() || dest.length() != frame.length()) {
                frame.copyTo(dest);
                changed = true;
            }
        }
        return changed;
    }

    public boolean addRuntimeAvailableGameObject(String gameObjectId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addGameObject(meta, gameObjectId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean addRuntimeAvailableSprite(int assetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addSprite(meta, assetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableSprite(int assetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeSprite(meta, assetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean addRuntimeAvailableAnimation(int assetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addAnimation(meta, assetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableAnimation(int assetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeAnimation(meta, assetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean addRuntimeAvailableParticle(String effectPath) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addParticle(meta, effectPath);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableParticle(String effectPath) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeParticle(meta, effectPath);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableGameObject(String gameObjectId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeGameObject(meta, gameObjectId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean addRuntimeAvailableTiledAnimation(int tileAnimationId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addTiledAnimation(meta, tileAnimationId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableTiledAnimation(int tileAnimationId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeTiledAnimation(meta, tileAnimationId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean addRuntimeAvailableTiledTile(int tileAssetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addTiledTile(meta, tileAssetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public boolean removeRuntimeAvailableTiledTile(int tileAssetId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removeTiledTile(meta, tileAssetId);
        persistRuntimeAvailabilityChange(cfg, changed);
        return changed;
    }

    public void deleteProjectAssets(Array<Integer> assetIds) {
        if (assetIds == null || assetIds.size == 0) {
            return;
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        assetMetaDatabase = AssetMetaDatabase.load(assetsFile);

        Array<AssetMeta> metasToDelete = resolveAssetsForDeletion(assetIds);
        if (metasToDelete.size == 0) {
            return;
        }

        boolean requiresLiveScene = false;
        for (AssetMeta meta : metasToDelete) {
            if (meta.type() != AssetType.FONT && meta.type() != AssetType.SKIN) {
                requiresLiveScene = true;
                break;
            }
        }
        AssetUsageScanner usageScanner = new AssetUsageScanner(
                requiresLiveScene ? sceneContext().world() : null,
                cfg,
                assetMetaDatabase,
                app.getEditorDocumentManager().documents().stream()
                        .filter(HudScreenEditorDocument.class::isInstance)
                        .map(HudScreenEditorDocument.class::cast).toList()
        );
        for (AssetMeta meta : metasToDelete) {
            AssetUsageScanner.AssetUsageReport usage = usageScanner.scanAsset(meta.id());
            if (usage.used()) {
                throw new IllegalStateException(buildAssetInUseMessage(meta, usage));
            }
        }

        boolean runtimeAvailabilityChanged = false;
        for (AssetMeta meta : metasToDelete) {
            deleteAssetSource(projectDir, meta);
            runtimeAvailabilityChanged |= runtimeAvailabilityService.removeDeletedAsset(cfg, meta);
            assetMetaDatabase.removeById(meta.id());
        }

        assetMetaDatabase.save(assetsFile);
        if (canvas.getAttachedSceneContext() != null) markCurrentSceneSaveRequired();
        StandaloneTextureCache.clear(true);
        canvas.invalidateStandaloneAssetVisuals();

        persistRuntimeAvailabilityChange(cfg, runtimeAvailabilityChanged);
        refreshAssetsPanel();
        app.getHudEditorSession().refreshAssetOptions();
    }

    private Array<AssetMeta> resolveAssetsForDeletion(Array<Integer> assetIds) {
        Array<AssetMeta> metas = new Array<>();
        HashSet<Integer> seen = new HashSet<>();

        for (Integer assetId : assetIds) {
            if (assetId == null || assetId <= 0 || !seen.add(assetId)) {
                continue;
            }

            AssetMeta meta = assetMetaDatabase.findById(assetId);
            if (meta == null) {
                throw new IllegalStateException("Asset metadata not found for id: " + assetId);
            }
            metas.add(meta);
        }

        return metas;
    }

    private void deleteAssetSource(FileHandle projectDir, AssetMeta meta) {
        if (meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) {
            return;
        }

        FileHandle file = projectDir.child(meta.sourceRelPath());
        if (meta.type() == AssetType.FONT || meta.type() == AssetType.SKIN) file = file.parent();
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            file.deleteDirectory();
        } else {
            file.delete();
        }

        if (file.exists()) {
            throw new IllegalStateException("Failed to delete asset source: " + meta.sourceRelPath());
        }
    }

    private String buildAssetInUseMessage(AssetMeta meta, AssetUsageScanner.AssetUsageReport usage) {
        StringBuilder message = new StringBuilder();
        message.append("Cannot delete asset \"")
                .append(meta.logicalPath() != null ? meta.logicalPath() : meta.id())
                .append("\" because it is still used in the project.");

        if (usage != null && usage.sceneNames() != null && usage.sceneNames().size > 0) {
            message.append(" Used in scene");
            if (usage.sceneNames().size > 1) {
                message.append("s");
            }
            message.append(": ");

            for (int i = 0; i < usage.sceneNames().size; i++) {
                if (i > 0) message.append(", ");
                message.append(usage.sceneNames().get(i));
            }
        }

        if (usage != null && usage.documentNames() != null && usage.documentNames().size > 0) {
            message.append(" Used in HUD document");
            if (usage.documentNames().size > 1) message.append("s");
            message.append(": ");
            for (int i = 0; i < usage.documentNames().size; i++) {
                if (i > 0) message.append(", ");
                message.append(usage.documentNames().get(i));
            }
        }
        return message.toString();
    }

    private void persistRuntimeAvailabilityChange(ProjectConfig cfg, boolean changed) {
        if (!changed || cfg == null || cfg.getCurrentSceneMeta() == null) {
            return;
        }

        markCurrentSceneSaveRequired();
        saveProjectFile(cfg);

        String canonicalTag = cfg.canonicalSceneTagCurrent();
        if (canonicalTag == null || canonicalTag.isBlank()) {
            return;
        }

        AtlasInputSyncResult syncResult = sceneAtlasInputService.syncSceneAtlasInputForSave(
                cfg,
                sceneContext().world(),
                assetMetaDatabase,
                tileAnimationsMetaDatabase
        );

        if (syncResult.changed()) {
            atlasStudioService.markRepackRequired(StudioFs.requireStudioProjectDir(cfg), canonicalTag);
            atlasStudioService.requestAsyncPack(
                    canonicalTag,
                    AsyncAtlasRepackCoordinator.RepackReason.GENERIC
            );
        }
    }

    private void maybeRepackAtlas(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        if (!plan.hasSceneToSave()) return;
        if (!EditorSettings.get().autoRepackAtlases) return;

        String canonicalTag = plan.canonicalTag();
        if (canonicalTag == null || canonicalTag.isBlank()) return;

        AtlasInputSyncResult syncResult = syncSceneAtlasInputForSave(plan);
        if (atlasInputsChanged(syncResult)) {
            atlasStudioService.markRepackRequired(plan.studioDir(), canonicalTag);
        }

        if (shouldSkipSaveAtlasRepack(plan, syncResult)) {
            logSaveAtlasRepackSkipped(canonicalTag);
            return;
        }

        SceneAtlasLoaderService.packSceneAtlas(
                plan.cfg(),
                canonicalTag,
                plan.studioDir()
        );

        SceneAtlasLoaderService.loadSceneAtlas(
                plan.cfg(),
                canonicalTag,
                plan.studioDir(),
                canvas
        );
        atlasStudioService.clearRepackRequired(plan.studioDir(), canonicalTag);
    }

    private void maybeRepackAtlasAsync(SaveExecutionPlan plan,
                                       SaveProgressRunner.ProgressHandle progress,
                                       Runnable onDone,
                                       java.util.function.Consumer<Throwable> onError) {
        requireSavePlanCurrent(plan);
        if (!plan.hasSceneToSave() || !EditorSettings.get().autoRepackAtlases) {
            progress.update(0.60f, "Repacking atlas (1/1): skipped");
            onDone.run();
            return;
        }

        String canonicalTag = plan.canonicalTag();
        if (canonicalTag == null || canonicalTag.isBlank()) {
            progress.update(0.60f, "Repacking atlas (1/1): skipped");
            onDone.run();
            return;
        }

        progress.update(0.25f, "Synchronizing atlas input (1/1)...");

        AtlasInputSyncResult syncResult = syncSceneAtlasInputForSave(plan);
        if (atlasInputsChanged(syncResult)) {
            atlasStudioService.markRepackRequired(plan.studioDir(), canonicalTag);
        }

        if (shouldSkipSaveAtlasRepack(plan, syncResult)) {
            progress.update(0.60f, "Repacking atlas (1/1): skipped");
            logSaveAtlasRepackSkipped(canonicalTag);
            onDone.run();
            return;
        }

        if (syncResult.changed()) {
            // A pending worker may have enumerated the input before this sync added an image.
            Gdx.app.log("AtlasStudioService", "Save atlas repack scene=" + canonicalTag + " reason=inputs-changed");
            long generation = atlasStudioService.requestAsyncPack(
                    canonicalTag,
                    AsyncAtlasRepackCoordinator.RepackReason.SAVE
            );
            waitForAsyncPackCompletion(plan, canonicalTag, generation, progress, onDone, onError);
            return;
        }

        if (atlasStudioService.hasAsyncPackQueuedOrRunningFor(canonicalTag)) {
            Gdx.app.log("AtlasStudioService",
                    "Save atlas repack using pending pack scene=" + canonicalTag);
            waitForAsyncPackCompletion(plan, canonicalTag, atlasStudioService.currentPackGeneration(),
                    progress, onDone, onError);
            return;
        }

        progress.update(0.30f, "Queueing atlas repack (1/1)...");

        Gdx.app.log("AtlasStudioService", "Save atlas repack scene=" + canonicalTag
                + " reason=atlas-missing-or-invalid");
        long generation = atlasStudioService.requestAsyncPack(
                canonicalTag,
                AsyncAtlasRepackCoordinator.RepackReason.SAVE
        );

        waitForAsyncPackCompletion(plan, canonicalTag, generation, progress, onDone, onError);
    }

    private AtlasInputSyncResult syncSceneAtlasInputForSave(SaveExecutionPlan plan) {
        requireSavePlanCurrent(plan);
        return sceneAtlasInputService.syncSceneAtlasInputForSave(
                plan.cfg(),
                plan.sourceWorld(),
                assetMetaDatabase,
                tileAnimationsMetaDatabase
        );
    }

    private boolean shouldSkipSaveAtlasRepack(SaveExecutionPlan plan, AtlasInputSyncResult syncResult) {
        if (plan == null || syncResult == null) return false;
        return shouldSkipSaveAtlasRepack(plan.studioDir(), plan.canonicalTag(), syncResult);
    }


    private boolean shouldSkipSaveAtlasRepack(FileHandle studioDir,
                                              String sceneTag,
                                              AtlasInputSyncResult syncResult) {
        return shouldSkipSaveAtlasRepack(studioDir, sceneTag, syncResult,
                atlasStudioService.hasAsyncPackQueuedOrRunningFor(sceneTag),
                () -> hasUsableSceneAtlas(studioDir, sceneTag));
    }

    static boolean shouldSkipSaveAtlasRepack(FileHandle studioDir,
                                             String sceneTag,
                                             AtlasInputSyncResult syncResult,
                                             boolean pendingPackForScene,
                                             java.util.function.BooleanSupplier atlasUsable) {
        if (studioDir == null || sceneTag == null || sceneTag.isBlank() || syncResult == null
                || atlasInputsChanged(syncResult)) return false;
        if (pendingPackForScene) return false;
        return atlasUsable.getAsBoolean();
    }

    private static boolean atlasInputsChanged(AtlasInputSyncResult result) {
        return result != null && (result.changed() || result.copiedCount() > 0
                || result.deletedCount() > 0);
    }

    static boolean shouldSkipSaveAtlasRepack(FileHandle studioDir,
                                             String sceneTag,
                                             AtlasInputSyncResult syncResult,
                                             boolean atlasUsable,
                                             boolean pendingPackForScene) {
        if (studioDir == null || sceneTag == null || sceneTag.isBlank()) return false;
        if (syncResult == null) return false;
        if (atlasInputsChanged(syncResult)) return false;
        if (!atlasUsable) return false;
        return !pendingPackForScene;
    }

    private boolean hasUsableSceneAtlas(FileHandle studioDir, String sceneTag) {
        return hasUsableSceneAtlas(studioDir, sceneTag, atlasStudioService);
    }

    static boolean hasUsableSceneAtlas(FileHandle studioDir,
                                       String sceneTag,
                                       AtlasStudioService atlasStudioService) {
        if (studioDir == null || sceneTag == null || sceneTag.isBlank()) return false;
        if (atlasStudioService == null || atlasStudioService.getAtlas(sceneTag) == null) return false;

        FileHandle atlasesDir = studioDir.child(StudioFs.DIR_ATLASES);
        FileHandle atlasFile = atlasesDir.child(StudioFs.withExt(sceneTag, StudioFs.EXT_ATLAS));
        if (!atlasFile.exists() || atlasFile.isDirectory() || atlasFile.length() <= 0L) return false;

        return atlasStudioService.coversCurrentInput(sceneTag,
                atlasesDir.child(StudioFs.DIR_INPUT).child(sceneTag), atlasFile);
    }

    static Array<String> atlasPageFileNames(FileHandle atlasFile) {
        Array<String> pageFileNames = new Array<>();
        if (atlasFile == null || !atlasFile.exists() || atlasFile.isDirectory()) return pageFileNames;

        String text = atlasFile.readString("UTF-8");
        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.contains(":")) continue;
            if (!trimmed.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
            if (!pageFileNames.contains(trimmed, false)) {
                pageFileNames.add(trimmed);
            }
        }
        return pageFileNames;
    }

    private static void logSaveAtlasRepackSkipped(String sceneTag) {
        Gdx.app.log("AtlasStudioService",
                "Save atlas repack skipped scene=" + sceneTag
                        + " inputsUnchanged=true atlasValid=true");
    }

    private void waitForAsyncPackCompletion(SaveExecutionPlan plan, String canonicalTag,
                                            long generation,
                                            SaveProgressRunner.ProgressHandle progress,
                                            Runnable onDone,
                                            java.util.function.Consumer<Throwable> onError) {
        try {
            requireSavePlanCurrent(plan);
            atlasStudioService.updateAsyncPack();
            atlasStudioService.applyIfPackReady();

            long currentGeneration = atlasStudioService.currentPackGeneration();
            if (currentGeneration != generation) {
                if (!canonicalTag.equals(atlasStudioService.currentPackSceneTag())) {
                    throw new IllegalStateException("Atlas pack for scene '" + canonicalTag
                            + "' was superseded by another scene");
                }
                generation = currentGeneration;
            }

            if (packPublishedOrThrow(canonicalTag, generation,
                    atlasStudioService.lastPackCompletion(),
                    atlasStudioService.hasAsyncPackQueuedOrRunningFor(canonicalTag))) {
                progress.update(0.60f, "Repacking atlas (1/1): done");
                onDone.run();
                return;
            }

            if (atlasStudioService.isPackInProgress()) {
                progress.update(0.45f, "Repacking atlas (1/1)...");
            } else if (atlasStudioService.isPackRequested()) {
                progress.update(0.35f, "Waiting atlas repack slot (1/1)...");
            }

            final long awaitedGeneration = generation;
            // Save completion must advance while libGDX's lifecycle Timer is paused.
            // Like the other save steps, poll on the application's next runnable cycle.
            Gdx.app.postRunnable(() -> waitForAsyncPackCompletion(
                    plan, canonicalTag, awaitedGeneration, progress, onDone, onError));
        } catch (Throwable t) {
            if (onError != null) {
                onError.accept(t);
            } else {
                Gdx.app.error("SceneManager", "Async atlas save step failed", t);
            }
        }
    }

    static boolean packPublishedOrThrow(String sceneTag, long generation,
                                        AtlasStudioService.PackCompletion completion,
                                        boolean pending) {
        if (completion != null && completion.generation() == generation
                && sceneTag.equals(completion.sceneTag())) {
            if (completion.failure() != null) throw new IllegalStateException(
                    "Atlas pack failed for scene '" + sceneTag + "' generation " + generation,
                    completion.failure());
            return true;
        }
        if (!pending) throw new IllegalStateException("Atlas pack ended without publication for scene '"
                + sceneTag + "' generation " + generation);
        return false;
    }

    private void repackSceneAtlas(ProjectConfig cfg,
                                  String sceneName,
                                  FileHandle projectDir) {
        if (cfg == null) return;
        if (projectDir == null) return;
        if (sceneName == null || sceneName.isBlank()) return;

        String canonicalTag = cfg.canonicalSceneTag(sceneName);
        if (canonicalTag == null || canonicalTag.isBlank()) {
            Gdx.app.error("SceneManager",
                    "repackSceneAtlas: unable to determine the canonical tag for '" + sceneName + "'");
            return;
        }

        AtlasInputSyncResult syncResult =
                sceneAtlasInputService.syncSceneAtlasInputForSave(
                        cfg,
                        sceneContext().world(),
                        assetMetaDatabase,
                        tileAnimationsMetaDatabase
                );
        if (atlasInputsChanged(syncResult)) {
            atlasStudioService.markRepackRequired(projectDir, canonicalTag);
        }

        if (shouldSkipSaveAtlasRepack(projectDir, canonicalTag, syncResult)) {
            logSaveAtlasRepackSkipped(canonicalTag);
            return;
        }

        SceneAtlasLoaderService.packSceneAtlas(
                cfg,
                canonicalTag,
                projectDir
        );

        reloadAtlasAndRebind(cfg, canonicalTag, projectDir);
        atlasStudioService.clearRepackRequired(projectDir, canonicalTag);

        GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
        if (snapshotManager != null) {
            snapshotManager.markDirty(canonicalTag, "scene-repack-complete");
        }

        refreshAssetsPanel();

        Gdx.app.log("SceneManager", "Repack complete: " + canonicalTag);
    }

    private void reloadAtlasAndRebind(ProjectConfig cfg,
                                      String canonicalTag,
                                      FileHandle projectDir) {
        rebuildRenderRuntimeForScene(cfg, canonicalTag, projectDir);
    }

    // ---------------------------------------------------------------------
    // RENDER REBUILD PIPELINE
    // ---------------------------------------------------------------------

    private void rebuildSparseFromDense() {
        rebuildSparseFromDense(sceneContext().world());
    }

    static void rebuildSparseFromDense(World world) {
        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] dataArr = bag.getData();

        for (int i = 0; i < bag.size(); i++) {

            int e = dataArr[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null || tiled.data == null) continue;

            tiled.spatialEnabled = tiled.data.spatialEnabled;
            tiled.defaultTileAltitude = tiled.data.defaultTileAltitude;
            tiled.defaultTileHeight = tiled.data.defaultTileHeight;

            tiled.ensureSparseTileStorageConsistency();
            tiled.tileXs.clear();
            tiled.tileYs.clear();
            tiled.tileAssetIds.clear();
            tiled.tileTransformFlags.clear();

            var chunks = tiled.data.getChunks();

            while (chunks.hasNext()) {
                var chunk = chunks.next();

                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                        int assetId = chunk.get(lx, ly);
                        if (assetId == 0) continue;

                        int gx = chunk.chunkX * tiled.data.chunkSize + lx;
                        int gy = chunk.chunkY * tiled.data.chunkSize + ly;
                        byte flags = TileTransformFlags.sanitize(chunk.transformFlags[ly * chunk.chunkWidth + lx]);

                        tiled.tileXs.add(gx);
                        tiled.tileYs.add(gy);
                        tiled.tileAssetIds.add(assetId);
                        tiled.tileTransformFlags.add(flags);
                    }
                }
            }
        }
    }

    private void rebuildRenderRuntimeForScene(ProjectConfig cfg,
                                              String canonicalTag,
                                              FileHandle projectDir) {
        if (canonicalTag == null || canonicalTag.isBlank()) return;

        publishStudioAssetMetadata(projectDir);

        java.util.Set<String> normalSources=sceneAtlasInputService.collectRequiredAtlasInputPathsForScene(
                cfg,sceneContext().world(),cfg.getCurrentSceneMeta(),assetMetaDatabase,tileAnimationsMetaDatabase);
        if(cfg.getCurrentSceneMeta().useNormalMaps)
            atlasStudioService.prepareSceneNormals(projectDir,canonicalTag,normalSources,assetMetaDatabase);

        SceneAtlasLoaderService.loadSceneAtlas(cfg, canonicalTag, projectDir, canvas);

        rebindTiles();

        canvas.refreshProjectBoundServices();
    }

    private void publishStudioAssetMetadata(FileHandle projectDir) {
        if (assetMetaDatabase == null && projectDir != null) {
            FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
            if (assetsFile.exists()) {
                assetMetaDatabase = AssetMetaDatabase.load(assetsFile);
            }
        }
        canvas.publishAssetMetaDatabase(assetMetaDatabase);
    }

    private void rebindTiles() {
        World world = sceneContext().world();
        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();

        for (int i = 0; i < bag.size(); i++) {
            TiledLayerComponent tiled = mTiled.get(data[i]);
            if (tiled != null && tiled.data != null) {
                tiled.data.markAllChunksContentDirty();
            }
        }
    }

    private void refreshParticleEffectsRoot(ProjectConfig cfg) {
        var particleSystem = sceneContext().world().getSystem(RenderParticleSyncSystem.class);
        if (particleSystem == null) return;

        FileHandle effectsRoot = null;
        if (cfg != null && cfg.projectFileName != null && !cfg.projectFileName.isBlank()) {
            effectsRoot = StudioFs.requireStudioProjectDir(cfg).child(StudioFs.DIR_ORIG_EFFECTS);
        }
        particleSystem.setEffectsRoot(effectsRoot);
    }

    private void applyProjectFixedSettings(ProjectConfig cfg) {
        if (cfg == null) return;

        // shaders root = project STUDIO directory
        FileHandle shadersRoot = StudioFs.requireStudioProjectDir(cfg);
        ShaderRegistry.reloadForProject(shadersRoot, StudioFs.DIR_ORIG_SHADERS);
        ShaderRegistry.saveProjectIndices();
        EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));

        // MSAA (restart required)
        int projSamples = cfg.glSamples;
        int editorSamples = EditorSettings.get().msaaSamples;
        if (editorSamples != projSamples) {
            EditorSettings.get().msaaSamples = projSamples;
            EditorSettings.save();
            String msg = "Project MSAA is " + projSamples + "x. Restart the studio to apply.";
            Gdx.app.log("SceneService", msg);
            EventFlow.i().publish(new EventFlow.LogMessage(msg));
        }
    }

    // ---------------------------------------------------------------------
    // CHANGE / CREATE / DELETE SCENE
    // ---------------------------------------------------------------------

    public void changeSceneNow(String sceneName) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;
        if (sceneName == null || sceneName.isBlank()) return;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        try {
            loadScene(cfg, sceneName, projectDir);
            sceneMetaBridge.pushCurrentSceneMetaToUI();
            StudioLog.info("Scene opened: " + sceneName);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to open scene '" + sceneName + "'.", ex);
        }
    }

    public void createNewScene(String desiredSceneName) {
        clipboardService().clear();
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            cfg = new ProjectConfig();
            cfg.projectFileName = "Untitled";
            ProjectConfig.setInstance(cfg);
        }

        String sceneName = cfg.uniqueSceneName(desiredSceneName);
        String previousSceneName = cfg.getCurrentSceneName();
        SceneEditorContext previousContext = canvas.getAttachedSceneContext();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        String createdSceneFileName = null;
        SceneEditorContext candidate = null;
        try {
            // fileName is managed by nextSceneIndex in ProjectConfig
            cfg.createSceneMeta(sceneName);
            SceneMeta meta = cfg.getSceneMeta(sceneName);
            createdSceneFileName = (meta != null) ? meta.getFile() : null;

            FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
            atlasesDir.mkdirs();

            String canonicalTag = cfg.canonicalSceneTagFor(meta);
            candidate = canvas.createSceneContext(canonicalTag, meta);
            canvas.attach(candidate);
            candidate.bindSceneIdentity(canonicalTag);
            bindSceneIdentityAuthorities(meta);
            int indexL = candidate.layerService().addLayerTop("Main layer");
            int layerEntityId = candidate.layerService().getLayerEntity(indexL);
            candidate.selectionService().setActivelayerId(layerEntityId);
            candidate.world().process();

            saveCurrentSceneOnly(cfg);
            candidate.markSaved();
            saveProjectFile(cfg);
            app.getEditorDocumentManager().openScene(canonicalTag, sceneName, candidate);
            assertCurrentSceneMetadataIntegrity(cfg, sceneName, "createNewScene");
            sceneMetaBridge.pushCurrentSceneMetaToUI();

            Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + " - " + sceneName + ")");
            StudioLog.info("Scene created: " + sceneName);
            if (previousContext != null && !isDocumentOwned(previousContext)) {
                canvas.releaseSceneContext(previousContext);
                previousContext.dispose();
            }
        } catch (RuntimeException ex) {
            if (isDocumentOwned(candidate)) throw ex;
            if (candidate != null && !candidate.isDisposed()) {
                canvas.releaseSceneContext(candidate);
                candidate.dispose();
            }
            if (previousContext != null && !previousContext.isDisposed()) {
                canvas.attach(previousContext);
            }
            final ProjectConfig rollbackCfg = cfg;
            final String rollbackPreviousSceneName = previousSceneName;
            final String rollbackSceneName = sceneName;
            final String rollbackCreatedSceneFileName = createdSceneFileName;
            final FileHandle rollbackProjectDir = projectDir;
            throw failAfterRollback(
                    "Failed to create scene '" + sceneName + "'.",
                    ex,
                    () -> rollbackSceneCreate(
                            rollbackCfg,
                            rollbackPreviousSceneName,
                            rollbackSceneName,
                            rollbackCreatedSceneFileName,
                            rollbackProjectDir
                    )
            );
        }
    }

    /**
     * Imports and activates a TMX scene after the caller has resolved the current-scene save decision.
     */
    public TmxSceneImportResult importTmxAsNewScene(TmxSceneImportRequest request) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }
        String previousSceneName = cfg.getCurrentSceneName();

        ensureAssetMetaDatabaseLoaded();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        TmxSceneImportService importService = new TmxSceneImportService(cfg, projectDir, assetMetaDatabase);
        TmxSceneImportResult result = importService.importScene(request);

        if (result.imported()) {
            try {
                activateImportedTmxScene(cfg, projectDir, result);
            } catch (RuntimeException activationFailure) {
                throw recoverTmxImportActivationFailure(
                        result,
                        previousSceneName,
                        activationFailure,
                        () -> restorePreviousSceneAfterTmxActivationFailure(cfg, previousSceneName, projectDir)
                );
            }
        }

        return result;
    }

    public Array<AnimationAssetMeta> getAnimationAssetMetas() {
        ensureAssetMetaDatabaseLoaded();
        Array<AnimationAssetMeta> animations = new Array<>();
        if (assetMetaDatabase == null) return animations;
        for (int i = 0; i < assetMetaDatabase.size(); i++) {
            AssetMeta meta = assetMetaDatabase.assetAt(i);
            if (meta instanceof AnimationAssetMeta animation) animations.add(animation);
        }
        animations.sort((left, right) -> {
            int byName = AssetDisplayInfo.from(left).displayName()
                    .compareToIgnoreCase(AssetDisplayInfo.from(right).displayName());
            return byName != 0 ? byName : Integer.compare(left.id(), right.id());
        });
        return animations;
    }

    /**
     * Imports and activates a TMX scene with progress after the caller has resolved the
     * current-scene save decision.
     */
    public void importTmxAsNewSceneWithProgress(
            Stage uiStage,
            TmxSceneImportRequest request,
            java.util.function.Consumer<TmxSceneImportResult> onComplete,
            java.util.function.Consumer<Throwable> onError) {
        if (uiStage == null) {
            try {
                TmxSceneImportResult result = importTmxAsNewScene(request);
                if (onComplete != null) onComplete.accept(result);
            } catch (Throwable failure) {
                if (onError != null) onError.accept(failure);
            }
            return;
        }

        TmxImportProgressContext context = new TmxImportProgressContext();
        context.cfg = ProjectConfig.getInstance();
        if (context.cfg == null) {
            if (onError != null) onError.accept(new IllegalStateException("No project is loaded."));
            return;
        }
        context.previousSceneName = context.cfg.getCurrentSceneName();

        SaveProgressRunner runner = new SaveProgressRunner(
                uiStage,
                "Importing Tiled map",
                "Preparing import..."
        );
        Array<SaveProgressRunner.Step> steps = new Array<>();
        steps.add(SaveProgressRunner.Step.sync(0.00f, "Preparing import...", () -> {
            ensureAssetMetaDatabaseLoaded();
            context.projectDir = StudioFs.requireStudioProjectDir(context.cfg);
            TmxSceneImportService importService = new TmxSceneImportService(
                    context.cfg, context.projectDir, assetMetaDatabase
            );
            context.session = importService.beginImport(request);
        }));
        steps.add(tmxImportStep(0.08f, "Reading and validating Tiled map...", context,
                () -> context.session.prepare()));
        steps.add(tmxImportStep(0.18f, "Creating imported scene...", context,
                () -> context.session.createScene()));
        steps.add(tmxImportStep(0.30f, "Importing tilesets and images...", context,
                () -> context.session.importAssets()));
        steps.add(tmxImportStep(0.62f, "Creating layers and tiles...", context,
                () -> context.session.materializeAndSaveScene()));
        steps.add(tmxImportStep(0.82f, "Updating scene atlas...", context,
                () -> context.session.updateAtlas()));
        steps.add(tmxImportStep(0.94f, "Saving project metadata...", context,
                () -> context.result = context.session.persistAndFinish()));
        steps.add(SaveProgressRunner.Step.sync(0.98f, "Opening imported scene...", () -> {
            try {
                activateImportedTmxScene(context.cfg, context.projectDir, context.result);
            } catch (RuntimeException activationFailure) {
                throw recoverTmxImportActivationFailure(
                        context.result,
                        context.previousSceneName,
                        activationFailure,
                        () -> restorePreviousSceneAfterTmxActivationFailure(
                                context.cfg, context.previousSceneName, context.projectDir
                        )
                );
            }
        }));
        steps.add(SaveProgressRunner.Step.sync(1.00f, "Import complete", () -> {
        }));

        runner.run(
                steps,
                () -> {
                    if (onComplete != null) onComplete.accept(context.result);
                },
                onError,
                () -> context.terminal
        );
    }

    private SaveProgressRunner.Step tmxImportStep(float progress,
                                                  String message,
                                                  TmxImportProgressContext context,
                                                  Runnable action) {
        return SaveProgressRunner.Step.async(progress, message, (ignoredProgress, next, fail) -> {
            try {
                action.run();
                if (context.session.finished() && context.result == null) {
                    context.result = context.session.result();
                    context.terminal = true;
                }
                next.run();
            } catch (RuntimeException failure) {
                if (context.session != null && context.session.mutationStarted()) {
                    context.result = context.session.rollback(failure);
                    context.terminal = true;
                    next.run();
                } else {
                    fail.accept(failure);
                }
            }
        });
    }

    private static final class TmxImportProgressContext {
        private ProjectConfig cfg;
        private String previousSceneName;
        private FileHandle projectDir;
        private TmxSceneImportSession session;
        private TmxSceneImportResult result;
        private boolean terminal;
    }

    private void activateImportedTmxScene(ProjectConfig cfg,
                                          FileHandle projectDir,
                                          TmxSceneImportResult result) {
        reloadTileAnimationsFromProject(projectDir);
        loadScene(cfg, result.sceneName(), projectDir);
        canvas.centerCamera();
        assertCurrentSceneMetadataIntegrity(cfg, result.sceneName(), "importTmxAsNewScene");
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
        refreshAssetsPanel();
        Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + " - " + result.sceneName() + ")");
        StudioLog.info("TMX scene imported: " + result.sceneName());
    }

    private void restorePreviousSceneAfterTmxActivationFailure(ProjectConfig cfg,
                                                              String previousSceneName,
                                                              FileHandle projectDir) {
        if (cfg == null || !hasSceneName(previousSceneName) || cfg.getSceneMeta(previousSceneName) == null) {
            unloadProjectToEmptyEditor();
            return;
        }

        cfg.setCurrentSceneByName(previousSceneName);
        reloadTileAnimationsFromProject(projectDir);
        loadScene(cfg, previousSceneName, projectDir);
        assertCurrentSceneMetadataIntegrity(cfg, previousSceneName, "importTmxAsNewScene.restorePrevious");
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
        refreshAssetsPanel();
    }

    static IllegalStateException recoverTmxImportActivationFailure(TmxSceneImportResult result,
                                                                   String previousSceneName,
                                                                   RuntimeException activationFailure,
                                                                   Runnable previousSceneRestorer) {
        RuntimeException failure = activationFailure != null
                ? activationFailure
                : new IllegalStateException("TMX import activation failed.");

        boolean rollbackSucceeded = false;
        boolean rollbackAvailable = result != null && result.rollback() != null;
        if (rollbackAvailable) {
            try {
                result.rollback().rollback();
                rollbackSucceeded = true;
            } catch (RuntimeException rollbackFailure) {
                failure = attachRollbackFailure(failure, rollbackFailure);
            }
        }

        boolean previousRestoreAttempted = hasSceneName(previousSceneName) && previousSceneRestorer != null;
        boolean previousRestoreSucceeded = false;
        if (previousRestoreAttempted) {
            try {
                previousSceneRestorer.run();
                previousRestoreSucceeded = true;
            } catch (RuntimeException restoreFailure) {
                failure = attachRollbackFailure(failure, restoreFailure);
            }
        }

        String message;
        if (rollbackSucceeded && previousRestoreSucceeded) {
            message = "TMX scene import activation failed; imported scene was rolled back and previous scene was restored.";
        } else if (rollbackSucceeded) {
            message = "TMX scene import activation failed; imported scene was rolled back.";
        } else if (!rollbackAvailable && previousRestoreSucceeded) {
            message = "TMX scene import activation failed; no import rollback transaction was available, but previous scene was restored.";
        } else if (!rollbackAvailable) {
            message = "TMX scene import activation failed and no import rollback transaction was available.";
        } else {
            message = "TMX scene import activation failed and rollback did not complete.";
        }

        return new IllegalStateException(message, failure);
    }

    public void deleteScene(String sceneName) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        SceneMeta meta = cfg.getSceneMeta(sceneName);
        if (meta == null) return;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);
        FileHandle sceneFile = scenesDir.child(meta.getFile());

        byte[] sceneBackup = ProjectFileCleanupService.snapshotFile(sceneFile);

        String canonicalTag = cfg.canonicalSceneTagFor(meta);
        if (canonicalTag == null || canonicalTag.isBlank()) {
            throw new IllegalStateException("Cannot delete scene: canonical atlas tag is blank.");
        }

        FileHandle atlasDir = projectDir.child(StudioFs.DIR_ATLASES);
        Array<ProjectFileCleanupService.FileSnapshotEntry> atlasPageBackups =
                ProjectFileCleanupService.snapshotAtlasPages(atlasDir, canonicalTag);

        FileHandle atlasInputDir = StudioFs.requireAtlasInputDir(cfg, canonicalTag);
        Array<ProjectFileCleanupService.FileSnapshotEntry> atlasInputBackup =
                ProjectFileCleanupService.snapshotDirectory(atlasInputDir);
        FileHandle normalInputDir=games.pixscape.studio.service.atlas.NormalAtlasPackingService.input(atlasInputDir);
        Array<ProjectFileCleanupService.FileSnapshotEntry> normalInputBackup=ProjectFileCleanupService.snapshotDirectory(normalInputDir);

        String previousCurrentSceneName = cfg.getCurrentSceneName();
        boolean wasActive = sceneName.equals(previousCurrentSceneName);

        String nextSceneName = null;
        if (wasActive) {
            Array<String> names = cfg.getSceneNames();
            for (String name : names) {
                if (!sceneName.equals(name)) {
                    nextSceneName = name;
                    break;
                }
            }
        }

        try {
            ProjectFileCleanupService.deleteFileAndBackups(sceneFile);
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, canonicalTag);
            ProjectFileCleanupService.deleteSceneAtlasInput(projectDir, canonicalTag);

            atlasStudioService.unload(canonicalTag);

            cfg.removeSceneMeta(sceneName);
            app.getEditorDocumentManager().closeNow(
                    new games.pixscape.studio.document.EditorDocumentKey(
                            games.pixscape.studio.document.EditorDocumentType.SCENE,
                            canonicalTag));

            if (wasActive) {
                if (nextSceneName == null || cfg.getSceneMeta(nextSceneName) == null) {
                    throw new IllegalStateException(
                            "Cannot delete the active scene: no fallback scene available."
                    );
                }

                saveProjectFile(cfg);
                loadScene(cfg, nextSceneName, projectDir);
                sceneMetaBridge.pushCurrentSceneMetaToUI();
                app.getBottomBar().refreshSelectBox();
            } else {
                saveProjectAndCurrentScene();
            }

            StudioLog.info("Scene deleted: " + sceneName);
        } catch (RuntimeException ex) {
            throw failAfterRollback(
                    "Failed to delete scene '" + sceneName + "'.",
                    ex,
                    () -> {
                        ProjectFileCleanupService.restoreDirectoryFromSnapshot(normalInputDir,normalInputBackup);
                        rollbackSceneDelete(
                            cfg,
                            sceneName,
                            meta,
                            previousCurrentSceneName,
                            sceneFile,
                            sceneBackup,
                            atlasDir,
                            atlasPageBackups,
                            atlasInputDir,
                            atlasInputBackup,
                            projectDir
                    );}
            );
        }
    }

    // ---------------------------------------------------------------------
    // SCENE ROLLBACK HELPERS
    // ---------------------------------------------------------------------

    private IllegalStateException failAfterRollback(String baseMessage,
                                                    RuntimeException primaryFailure,
                                                    Runnable rollbackAction) {
        try {
            rollbackAction.run();
            return new IllegalStateException(baseMessage + " Previous stable state was restored.", primaryFailure);
        } catch (RuntimeException rollbackFailure) {
            RuntimeException mergedFailure = attachRollbackFailure(primaryFailure, rollbackFailure);
            try {
                unloadProjectToEmptyEditor();
            } catch (RuntimeException safeFallbackFailure) {
                mergedFailure.addSuppressed(safeFallbackFailure);
            }
            return new IllegalStateException(
                    baseMessage + " Rollback failed; editor was reset to an empty safe state.",
                    mergedFailure
            );
        }
    }

    private void rollbackSceneSwitch(ProjectConfig cfg, String previousSceneName, FileHandle projectDir) {
        if (!rollbackSceneSwitchConfigPointer(cfg, previousSceneName)) {
            unloadProjectToEmptyEditor();
            return;
        }
        saveProjectFile(cfg);
        loadScene(cfg, previousSceneName, projectDir);
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
    }

    private void rollbackSceneCreate(ProjectConfig cfg,
                                     String previousSceneName,
                                     String createdSceneName,
                                     String createdSceneFileName,
                                     FileHandle projectDir) {
        if (cfg == null) {
            unloadProjectToEmptyEditor();
            return;
        }

        rollbackSceneCreateState(cfg, previousSceneName, createdSceneName, createdSceneFileName, projectDir);

        if (hasSceneName(previousSceneName)) {
            saveProjectFile(cfg);
            loadScene(cfg, previousSceneName, projectDir);
        } else {
            unloadProjectToEmptyEditor();
            return;
        }

        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
    }

    private void rollbackSceneDelete(ProjectConfig cfg,
                                     String deletedSceneName,
                                     SceneMeta deletedMeta,
                                     String previousCurrentSceneName,
                                     FileHandle deletedSceneFile,
                                     byte[] deletedSceneBackup,
                                     FileHandle atlasDir,
                                     Array<ProjectFileCleanupService.FileSnapshotEntry> atlasPageBackups,
                                     FileHandle atlasInputDir,
                                     Array<ProjectFileCleanupService.FileSnapshotEntry> atlasInputBackup,
                                     FileHandle projectDir) {
        rollbackDeletedSceneMeta(cfg, deletedSceneName, deletedMeta, previousCurrentSceneName);
        ProjectFileCleanupService.restoreFileFromSnapshot(deletedSceneFile, deletedSceneBackup);
        ProjectFileCleanupService.restoreAtlasPagesFromSnapshot(atlasDir, atlasPageBackups);
        ProjectFileCleanupService.restoreDirectoryFromSnapshot(atlasInputDir, atlasInputBackup);

        if (cfg == null || previousCurrentSceneName == null || previousCurrentSceneName.isBlank()) {
            unloadProjectToEmptyEditor();
            return;
        }

        cfg.setCurrentSceneByName(previousCurrentSceneName);
        saveProjectFile(cfg);
        loadScene(cfg, previousCurrentSceneName, projectDir);
        sceneMetaBridge.pushCurrentSceneMetaToUI();
        app.getBottomBar().refreshSelectBox();
    }

    static byte[] snapshotFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return null;
        return file.readBytes();
    }

    static RuntimeException attachRollbackFailure(RuntimeException primaryFailure, RuntimeException rollbackFailure) {
        if (primaryFailure != null && rollbackFailure != null) {
            primaryFailure.addSuppressed(rollbackFailure);
        }
        return primaryFailure;
    }

    static void rollbackCreatedSceneMeta(ProjectConfig cfg, String createdSceneName) {
        if (cfg == null || createdSceneName == null || createdSceneName.isBlank()) return;
        cfg.removeSceneMeta(createdSceneName);
    }

    static void assertCurrentSceneMetadataIntegrity(ProjectConfig cfg, String expectedSceneName, String operation) {
        if (cfg == null) throw new IllegalStateException(operation + ": ProjectConfig is null.");
        if (expectedSceneName == null || expectedSceneName.isBlank()) {
            throw new IllegalStateException(operation + ": expected scene name is blank.");
        }
        String currentSceneName = cfg.getCurrentSceneName();
        if (!expectedSceneName.equals(currentSceneName)) {
            throw new IllegalStateException(
                    operation + ": current scene mismatch (expected '" + expectedSceneName
                            + "', got '" + currentSceneName + "').");
        }
        SceneMeta currentMeta = cfg.getCurrentSceneMeta();
        if (currentMeta == null) {
            throw new IllegalStateException(operation + ": current scene meta is missing for '" + expectedSceneName + "'.");
        }
        if (!expectedSceneName.equals(currentMeta.getName())) {
            throw new IllegalStateException(
                    operation + ": current scene meta name mismatch (expected '" + expectedSceneName
                            + "', got '" + currentMeta.getName() + "').");
        }
    }

    static boolean rollbackSceneSwitchConfigPointer(ProjectConfig cfg, String previousSceneName) {
        if (cfg == null || !hasSceneName(previousSceneName)) return false;
        cfg.setCurrentSceneByName(previousSceneName);
        return true;
    }

    static void rollbackSceneCreateState(ProjectConfig cfg,
                                         String previousSceneName,
                                         String createdSceneName,
                                         String createdSceneFileName,
                                         FileHandle projectDir) {
        if (cfg == null) return;
        rollbackCreatedSceneMeta(cfg, createdSceneName);
        if (projectDir != null && hasSceneName(createdSceneFileName)) {
            FileHandle createdSceneFile = projectDir.child(StudioFs.DIR_SCENES).child(createdSceneFileName);
            if (createdSceneFile.exists()) createdSceneFile.delete();
        }
        if (hasSceneName(previousSceneName)) {
            cfg.setCurrentSceneByName(previousSceneName);
        }
    }

    static void rollbackDeletedSceneMeta(ProjectConfig cfg,
                                         String deletedSceneName,
                                         SceneMeta deletedMeta,
                                         String previousCurrentSceneName) {
        if (cfg == null || deletedMeta == null || deletedSceneName == null || deletedSceneName.isBlank()) return;
        cfg.getScenesMap().put(deletedSceneName, deletedMeta);
        if (previousCurrentSceneName != null && !previousCurrentSceneName.isBlank()) {
            cfg.setCurrentSceneByName(previousCurrentSceneName);
        }
    }

    private static boolean hasSceneName(String sceneName) {
        return sceneName != null && !sceneName.isBlank();
    }

    // ---------------------------------------------------------------------
    // TILE ANIMATIONS
    // ---------------------------------------------------------------------

    private void reloadTileAnimationsFromProject(FileHandle projectDir) {
        tileAnimationsMetaDatabase = TileAnimationsIO.load(
                projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        reloadTileAnimationRegistryFromProjectData();
    }

    private void reloadTileAnimationRegistryFromProjectData() {
        TileAnimationRegistry registry = canvas.getTileAnimationRegistry();
        registry.clear();

        if (tileAnimationsMetaDatabase == null || tileAnimationsMetaDatabase.animations == null) {
            canvas.requestTiledFallbackValidation();
            return;
        }

        for (TileAnimationProjectDefData projectDef : tileAnimationsMetaDatabase.animations) {
            if (!isRuntimeReadyTileAnimation(projectDef)) {
                continue;
            }

            TileAnimationDefData runtimeData = new TileAnimationDefData();
            runtimeData.id = projectDef.id;
            runtimeData.frameAssetIds = projectDef.frameAssetIds;
            runtimeData.frameDurationsMs = projectDef.frameDurationsMs;

            registry.put(runtimeData);
        }
        canvas.requestTiledFallbackValidation();
    }

    private boolean isRuntimeReadyTileAnimation(TileAnimationProjectDefData def) {
        if (def == null) return false;
        if (def.id <= 0) return false;
        if (def.frameAssetIds == null || def.frameDurationsMs == null) return false;
        if (def.frameAssetIds.length == 0) return false;
        if (def.frameAssetIds.length != def.frameDurationsMs.length) return false;

        for (int i = 0; i < def.frameAssetIds.length; i++) {
            if (def.frameAssetIds[i] <= 0) return false;
            if (def.frameDurationsMs[i] <= 0) return false;
        }

        return true;
    }

    public int createEmptyTileAnimation(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tile animation name is empty.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);

        if (tileAnimationsMetaDatabase == null) {
            tileAnimationsMetaDatabase = TileAnimationsIO.load(tileAnimationsFile);
        }

        if (assetMetaDatabase == null) {
            assetMetaDatabase = AssetMetaDatabase.load(assetsFile);
        }

        String trimmedName = name.trim();

        for (TileAnimationProjectDefData existing : tileAnimationsMetaDatabase.animations) {
            if (existing != null && trimmedName.equalsIgnoreCase(existing.name)) {
                throw new IllegalStateException("A tiled animation with this name already exists.");
            }
        }

        int id = assetMetaDatabase.allocateNextId();
        assetMetaDatabase.save(assetsFile);

        TileAnimationProjectDefData def = new TileAnimationProjectDefData();
        def.id = id;
        def.name = trimmedName;
        def.frameAssetIds = new int[0];
        def.frameDurationsMs = new int[0];

        tileAnimationsMetaDatabase.animations.add(def);
        TileAnimationsIO.save(tileAnimationsMetaDatabase, tileAnimationsFile);

        reloadTileAnimationRegistryFromProjectData();
        markCurrentSceneSaveRequired();

        return def.id;
    }

    public void addTileToTileAnimation(int tileAnimationId, int tileAssetId) {
        if (tileAnimationId <= 0) {
            throw new IllegalArgumentException("Invalid tiled animation id.");
        }
        if (tileAssetId <= 0) {
            throw new IllegalArgumentException("Invalid tile asset id.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);

        if (tileAnimationsMetaDatabase == null) {
            tileAnimationsMetaDatabase = TileAnimationsIO.load(tileAnimationsFile);
        }

        TileAnimationProjectDefData def = findTileAnimationProjectDef(tileAnimationId);
        if (def == null) {
            throw new IllegalStateException("Tiled animation not found.");
        }

        def.frameAssetIds = appendInt(def.frameAssetIds, tileAssetId);
        def.frameDurationsMs = appendInt(def.frameDurationsMs, DEFAULT_TILE_ANIMATION_FRAME_DURATION_MS);

        TileAnimationsIO.save(tileAnimationsMetaDatabase, tileAnimationsFile);
        reloadTileAnimationRegistryFromProjectData();
        markTileAnimationUsersDirty(tileAnimationId);
        markCurrentSceneSaveRequired();
    }

    public void deleteTileAnimation(int tileAnimationId) {
        if (tileAnimationId <= 0) {
            throw new IllegalArgumentException("Invalid tiled animation id.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);

        if (tileAnimationsMetaDatabase == null) {
            tileAnimationsMetaDatabase = TileAnimationsIO.load(tileAnimationsFile);
        }

        if (isTileAnimationUsedInCurrentScene(tileAnimationId)) {
            throw new IllegalStateException(
                    "Cannot delete this tiled animation because it is still used in the current scene."
            );
        }

        TileAnimationProjectDefData toRemove = null;
        for (TileAnimationProjectDefData def : tileAnimationsMetaDatabase.animations) {
            if (def != null && def.id == tileAnimationId) {
                toRemove = def;
                break;
            }
        }

        if (toRemove == null) {
            throw new IllegalStateException("Tiled animation not found.");
        }

        tileAnimationsMetaDatabase.animations.removeValue(toRemove, true);
        TileAnimationsIO.save(tileAnimationsMetaDatabase, tileAnimationsFile);

        reloadTileAnimationRegistryFromProjectData();
        boolean runtimeAvailabilityChanged = removeRuntimeAvailabilityTiledAnimationReferences(cfg, tileAnimationId);
        markCurrentSceneSaveRequired();

        if (canvas.getTiledPaintService().getActiveTileAssetId() == tileAnimationId) {
            canvas.getTiledPaintService().setActiveTileAssetId(-1);
        }

        persistRuntimeAvailabilityChange(cfg, runtimeAvailabilityChanged);
        refreshAssetsPanel();
    }

    private boolean removeRuntimeAvailabilityTiledAnimationReferences(ProjectConfig cfg, int tileAnimationId) {
        if (cfg == null || tileAnimationId <= 0) {
            return false;
        }

        boolean changed = false;
        for (com.badlogic.gdx.utils.ObjectMap.Entry<String, SceneMeta> entry : cfg.getScenesMap()) {
            if (entry == null || entry.value == null) continue;
            changed |= runtimeAvailabilityService.removeTiledAnimation(entry.value, tileAnimationId);
        }

        // Runtime availability is updated in metadata; atlas inputs remain scene-local until scene-world loading supports multi-scene sync.
        return changed;
    }

    public void removeFrameFromTileAnimation(int tileAnimationId, int frameIndex) {
        if (tileAnimationId <= 0) {
            throw new IllegalArgumentException("Invalid tiled animation id.");
        }

        TileAnimationProjectDefData def = findTileAnimationProjectDef(tileAnimationId);
        if (def == null) {
            throw new IllegalStateException("Tiled animation not found.");
        }

        if (def.frameAssetIds == null || def.frameDurationsMs == null) {
            throw new IllegalStateException("Tiled animation data is invalid.");
        }

        if (frameIndex < 0 || frameIndex >= def.frameAssetIds.length) {
            throw new IllegalArgumentException("Invalid frame index.");
        }

        def.frameAssetIds = removeIntAt(def.frameAssetIds, frameIndex);
        def.frameDurationsMs = removeIntAt(def.frameDurationsMs, frameIndex);

        persistTileAnimationsAndRefresh(tileAnimationId);
    }

    public void moveFrameInTileAnimation(int tileAnimationId, int fromIndex, int toIndex) {
        if (tileAnimationId <= 0) {
            throw new IllegalArgumentException("Invalid tiled animation id.");
        }

        TileAnimationProjectDefData def = findTileAnimationProjectDef(tileAnimationId);
        if (def == null) {
            throw new IllegalStateException("Tiled animation not found.");
        }

        if (def.frameAssetIds == null || def.frameDurationsMs == null) {
            throw new IllegalStateException("Tiled animation data is invalid.");
        }

        if (fromIndex < 0 || fromIndex >= def.frameAssetIds.length) {
            throw new IllegalArgumentException("Invalid source frame index.");
        }

        if (toIndex < 0 || toIndex >= def.frameAssetIds.length) {
            throw new IllegalArgumentException("Invalid target frame index.");
        }

        def.frameAssetIds = moveInt(def.frameAssetIds, fromIndex, toIndex);
        def.frameDurationsMs = moveInt(def.frameDurationsMs, fromIndex, toIndex);

        persistTileAnimationsAndRefresh(tileAnimationId);
    }

    public void updateTileAnimationFrameDuration(int tileAnimationId, int frameIndex, int durationMs) {
        if (tileAnimationId <= 0) {
            throw new IllegalArgumentException("Invalid tiled animation id.");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("Frame duration must be > 0.");
        }

        TileAnimationProjectDefData def = findTileAnimationProjectDef(tileAnimationId);
        if (def == null) {
            throw new IllegalStateException("Tiled animation not found.");
        }

        if (def.frameDurationsMs == null || frameIndex < 0 || frameIndex >= def.frameDurationsMs.length) {
            throw new IllegalArgumentException("Invalid frame index.");
        }

        def.frameDurationsMs[frameIndex] = durationMs;

        persistTileAnimationsAndRefresh(tileAnimationId);
    }

    private void persistTileAnimationsAndRefresh(int tileAnimationId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);

        TileAnimationsIO.save(tileAnimationsMetaDatabase, tileAnimationsFile);
        reloadTileAnimationRegistryFromProjectData();
        markCurrentSceneSaveRequired();
        markTileAnimationUsersDirty(tileAnimationId);
    }

    private TileAnimationProjectDefData findTileAnimationProjectDef(int tileAnimationId) {
        if (tileAnimationsMetaDatabase == null || tileAnimationsMetaDatabase.animations == null) {
            return null;
        }

        for (TileAnimationProjectDefData def : tileAnimationsMetaDatabase.animations) {
            if (def != null && def.id == tileAnimationId) {
                return def;
            }
        }
        return null;
    }

    private boolean isTileAnimationUsedInCurrentScene(int tileAnimationId) {
        if (tileAnimationId <= 0) {
            return false;
        }

        World world = sceneContext().world();
        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);

        IntBag tiledLayers = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = tiledLayers.getData();

        for (int i = 0; i < tiledLayers.size(); i++) {
            TiledLayerComponent tiled = mTiled.get(data[i]);
            if (tiled == null || tiled.data == null) {
                continue;
            }

            for (TileChunk chunk : tiled.data.getChunks()) {
                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int logicalId = chunk.get(lx, ly);
                        if (logicalId == tileAnimationId) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private void markTileAnimationUsersDirty(int tileAnimationId) {
        if (tileAnimationId <= 0) {
            return;
        }

        World world = sceneContext().world();
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);

        IntBag tiledLayers = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = tiledLayers.getData();
        TileAnimationLookup lookup = canvas.getTileAnimationRegistry();

        for (int i = 0; i < tiledLayers.size(); i++) {
            TiledLayerComponent tiled = mTiled.get(data[i]);
            if (tiled == null || tiled.data == null) {
                continue;
            }

            boolean changed=false;
            for (TileChunk chunk : tiled.data.getChunks()) {
                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int logicalId = chunk.get(lx, ly);
                        if (logicalId != tileAnimationId) {
                            continue;
                        }

                        int localIndex = chunk.localIndexFor(lx, ly);
                        changed=true;
                        chunk.markLocalDirty(localIndex);

                        if (lookup != null) {
                            TileAnimationStateSupport.syncCell(chunk, localIndex, logicalId, lookup);
                        }
                    }
                }
            }
            if(changed){
                // Definition edits are cold authored mutations, unlike playback frame changes.
                DirtyTrackerSystem dirty=world.getSystem(DirtyTrackerSystem.class);
                if(dirty!=null)dirty.geometry(data[i],games.pixscape.runtime.render.GeometryDirty.ALL);
                EventFlow.i().publish(new EventFlow.EntityChanged(data[i],
                        games.pixscape.studio.history.commands.TransformOp.SCALE,EventFlow.tag(this)));
            }
        }
    }

    private static int[] removeIntAt(int[] source, int index) {
        if (source == null || index < 0 || index >= source.length) {
            return source != null ? source : new int[0];
        }

        int[] out = new int[source.length - 1];
        if (index > 0) {
            System.arraycopy(source, 0, out, 0, index);
        }
        if (index < source.length - 1) {
            System.arraycopy(source, index + 1, out, index, source.length - index - 1);
        }
        return out;
    }

    private static int[] moveInt(int[] source, int fromIndex, int toIndex) {
        if (source == null) return new int[0];
        if (fromIndex < 0 || fromIndex >= source.length) return source;
        if (toIndex < 0 || toIndex >= source.length) return source;
        if (fromIndex == toIndex) return source;

        int[] out = java.util.Arrays.copyOf(source, source.length);
        int value = out[fromIndex];

        if (fromIndex < toIndex) {
            System.arraycopy(out, fromIndex + 1, out, fromIndex, toIndex - fromIndex);
        } else {
            System.arraycopy(out, toIndex, out, toIndex + 1, fromIndex - toIndex);
        }

        out[toIndex] = value;
        return out;
    }

    private static int[] appendInt(int[] source, int value) {
        int[] in = source != null ? source : new int[0];
        int[] out = java.util.Arrays.copyOf(in, in.length + 1);
        out[in.length] = value;
        return out;
    }

    // ---------------------------------------------------------------------
    // IMPORT ASSETS
    // ---------------------------------------------------------------------

    public void importAssets(Array<ImportDialog.ImportItem> items) {
        if (items == null || items.size == 0) return;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        AssetImportContext ctx = prepareAssetImportContext(cfg);

        int importedCount = 0;

        for (ImportDialog.ImportItem item : items) {
            importedCount += importSingleAsset(ctx, item);
        }

        assetMetaDatabase.save(ctx.projectDir.child(StudioFs.FILE_ASSETS_JSON));
        canvas.publishAssetMetaDatabase(assetMetaDatabase);
        refreshAssetsPanel();
        app.getHudEditorSession().refreshAssetOptions();
        StudioLog.info("Assets imported: " + importedCount);
    }

    public void importAssetsWithProgress(Array<ImportDialog.ImportItem> items, Stage stage,
            Runnable complete, java.util.function.Consumer<Throwable> failure) {
        class ImportQueue {
            int index;
            void advance() {
                if (index >= items.size) { app.getHudEditorSession().refreshAssetOptions(); complete.run(); return; }
                ImportDialog.ImportItem item = items.get(index++);
                if (item.tilesetPlan == null) {
                    try { importAssets(new Array<>(new ImportDialog.ImportItem[]{item})); advance(); }
                    catch (Throwable error) { failure.accept(error); }
                    return;
                }
                applyTilesetImportPlanWithProgress(item.tilesetPlan, stage, result -> {
                    try {
                        if (item.type == ImportDialog.ImportType.TILESET_TSX && item.tilesetPlan.targetId() <= 0)
                            importInitialTsxAnimations(prepareAssetImportContext(ProjectConfig.getInstance()),
                                    new TsxTilesetImportParser().parse(item.file), result);
                        advance();
                    } catch (Throwable error) { failure.accept(error); }
                }, failure);
            }
        }
        new ImportQueue().advance();
    }

    private AssetImportContext prepareAssetImportContext(ProjectConfig cfg) {
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle imagesRoot = projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        imagesRoot.mkdirs();

        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        tilesRoot.mkdirs();

        String animRootRel = StudioFs.DIR_ORIG_ANIMATIONS;
        FileHandle animRoot = projectDir.child(animRootRel);
        animRoot.mkdirs();

        FileHandle effectsRoot = projectDir.child(StudioFs.DIR_ORIG_EFFECTS);
        effectsRoot.mkdirs();

        return new AssetImportContext(
                cfg,
                projectDir,
                imagesRoot,
                tilesRoot,
                animRoot,
                animRootRel,
                effectsRoot
        );
    }

    private int importSingleAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        if (item == null || item.file == null || !item.file.exists()) {
            return 0;
        }

        ImportDialog.ImportType type = resolveImportType(item);

        return switch (type) {
            case IMAGE -> importImageAsset(ctx, item);
            case TILESET -> importTilesetAtlasAsset(ctx, item);
            case TILESET_TSX -> importTsxTilesetAsset(ctx, item);
            case SPRITESHEET -> importSpritesheetAsset(ctx, item);
            case PARTICLE_EFFECT -> importParticleEffectAsset(ctx, item);
            case FONT -> importBitmapFontAsset(ctx, item);
            case SKIN -> importScene2dSkinAsset(ctx, item);
        };
    }

    private int importBitmapFontAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        new games.pixscape.studio.service.asset.BitmapFontAssetImportService(assetMetaDatabase)
                .importNew(item.file, ctx.projectDir);
        return 1;
    }

    private int importScene2dSkinAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        new Scene2dSkinAssetImportService(assetMetaDatabase).importNew(item.file, ctx.projectDir);
        return 1;
    }

    public void reimportScene2dSkinAsset(int assetId,
                                         String expectedSourceRelPath,
                                         FileHandle expectedProjectDir,
                                         FileHandle descriptor) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) throw new IllegalStateException("No project is loaded.");
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        requireSameProject(expectedProjectDir, projectDir);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        assetMetaDatabase = AssetMetaDatabase.load(assetsFile);
        AssetMeta original = assetMetaDatabase.findById(assetId);
        if (original == null || original.type() != AssetType.SKIN
                || !Objects.equals(original.sourceRelPath(), expectedSourceRelPath)) {
            throw new IllegalStateException("The selected Skin Asset no longer belongs to the original project context.");
        }
        new Scene2dSkinAssetImportService(assetMetaDatabase)
                .reimport(assetId, descriptor, projectDir);
        assetMetaDatabase.save(assetsFile);
        canvas.publishAssetMetaDatabase(assetMetaDatabase);
        try {
            refreshHudAfterReimport(app.getSceneHudRuntimePreparationService()
                    .invalidateReimportedSkin(original.sourceRelPath()));
        } finally { refreshAssetsPanel(); }
    }

    public void reimportBitmapFontAsset(int assetId,
                                        String expectedSourceRelPath,
                                        FileHandle expectedProjectDir,
                                        FileHandle descriptor) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) throw new IllegalStateException("No project is loaded.");
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        requireSameProject(expectedProjectDir, projectDir);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        assetMetaDatabase = AssetMetaDatabase.load(assetsFile);
        AssetMeta original = assetMetaDatabase.findById(assetId);
        if (original == null || original.type() != AssetType.FONT
                || !Objects.equals(original.sourceRelPath(), expectedSourceRelPath)) {
            throw new IllegalStateException("The selected Font Asset no longer belongs to the original project context.");
        }
        new BitmapFontAssetImportService(assetMetaDatabase)
                .reimport(assetId, descriptor, projectDir);
        assetMetaDatabase.save(assetsFile);
        canvas.publishAssetMetaDatabase(assetMetaDatabase);
        try {
            refreshHudAfterReimport(app.getSceneHudRuntimePreparationService()
                    .invalidateReimportedFont(assetId));
        } finally { refreshAssetsPanel(); }
    }

    private void refreshHudAfterReimport(SceneHudRuntimePreparationService.ReimportImpact impact) {
        var editor = app.getHudEditorSession();
        refreshHudAfterReimport(impact, editor.screenId(), editor::reloadResources,
                editor::markResourcesStale);
    }

    static void refreshHudAfterReimport(SceneHudRuntimePreparationService.ReimportImpact impact,
                                        String activeScreen, Runnable reloadResources,
                                        Runnable markResourcesStale) {
        if (activeScreen == null) return;
        if (impact.openScreensToReload().contains(activeScreen)) {
            reloadResources.run();
        } else if (impact.uncertainOpenScreens().contains(activeScreen)) {
            markResourcesStale.run(); // Keep the last valid preview until its usage can be resolved.
        }
    }

    static void requireSameProject(FileHandle expectedProjectDir,
                                   FileHandle currentProjectDir) {
        if (expectedProjectDir == null || currentProjectDir == null) {
            throw new IllegalStateException("The project changed before asset reimport completed.");
        }
        try {
            if (!Files.isSameFile(expectedProjectDir.file().toPath(),
                    currentProjectDir.file().toPath())) {
                throw new IllegalStateException("The project changed before asset reimport completed.");
            }
        } catch (IOException | SecurityException failure) {
            throw new IllegalStateException("Unable to verify the asset reimport project context.", failure);
        }
    }

    static ImportDialog.ImportType resolveImportType(ImportDialog.ImportItem item) {
        if (item == null) {
            return ImportDialog.ImportType.IMAGE;
        }
        if (item.type != null) {
            return item.type;
        }
        return resolveAuto(item.file);
    }

    private int importImageAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        if (!isImage(item.file)) {
            warnUnsupported(item.file);
            return 0;
        }

        String base = baseName(item.file.name());
        String logical = StudioFs.PREFIX_IMAGES + base;

        AssetMeta meta = assetMetaDatabase.registerIfAbsent(
                AssetType.IMAGE,
                logical,
                null,
                AssetMeta.AssetScope.USER
        );

        int id = meta.id();

        String newFileName = base + "__a" + id + "." + item.file.extension();
        FileHandle dst = ctx.imagesRoot.child(newFileName);

        if (!dst.exists()) {
            item.file.copyTo(dst);
        }

        assetMetaDatabase.updateSourceRelPath(
                meta.id(),
                StudioFs.DIR_ORIG_IMAGES + "/" + newFileName
        );
        return 1;
    }

    private int importTilesetAtlasAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        if (item.tilesetPlan != null) return applyTilesetImportPlan(item.tilesetPlan).importedCount();
        TilesetImportResult result = new TilesetAssetImportService(assetMetaDatabase)
                .importAtlas(tilesetAtlasImportRequestForManualImport(item, ctx.tilesRoot));
        return result.importedCount();
    }

    private int importTsxTilesetAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        // Reimport preserves authored animations. Initial TSX imports still use their descriptor animations.
        if (item.tilesetPlan != null && item.tilesetPlan.targetId() > 0)
            return applyTilesetImportPlan(item.tilesetPlan).importedCount();
        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(item.file);
        String tilesetName = descriptor.name() != null && !descriptor.name().isBlank()
                ? descriptor.name()
                : baseName(item.file.name());
        TilesetImportResult result = item.tilesetPlan != null ? applyTilesetImportPlan(item.tilesetPlan)
                : new TilesetAssetImportService(assetMetaDatabase).importAtlas(new TilesetAtlasImportRequest(
                        descriptor.imageFile(),
                        ctx.tilesRoot,
                        descriptor.tileWidth(),
                        descriptor.tileHeight(),
                        descriptor.spacing(),
                        descriptor.margin(),
                        tilesetName
                ));
        importInitialTsxAnimations(ctx, descriptor, result);
        return result.importedCount();
    }

    private void importInitialTsxAnimations(AssetImportContext ctx, TsxTilesetDescriptor descriptor, TilesetImportResult result) {
        String tilesetName = result.tilesetLogicalPath().substring(StudioFs.PREFIX_TILES.length());
        if (result.importedCount() > 0 && descriptor.tileAnimations() != null && !descriptor.tileAnimations().isEmpty()) {
            FileHandle tileAnimationsFile = ctx.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
            if (tileAnimationsMetaDatabase == null) {
                tileAnimationsMetaDatabase = TileAnimationsIO.load(tileAnimationsFile);
            }
            TiledAnimationImportSupport.importTileAnimations(
                    assetMetaDatabase,
                    tileAnimationsMetaDatabase,
                    tilesetName,
                    descriptor.tileAnimations(),
                    result.localTileAssetIds()
            );
            TileAnimationsIO.save(tileAnimationsMetaDatabase, tileAnimationsFile);
            reloadTileAnimationRegistryFromProjectData();
        }
    }

    static TilesetAtlasImportRequest tilesetAtlasImportRequestForManualImport(ImportDialog.ImportItem item,
                                                                              FileHandle tilesRoot) {
        return new TilesetAtlasImportRequest(
                item.file,
                tilesRoot,
                item.tileWidth,
                item.tileHeight,
                item.tileSpacing,
                item.tileMargin,
                null,
                item.tilesetProfileSettings()
        );
    }

    static TilesetAtlasImportRequest tilesetAtlasImportRequestForTsxImport(FileHandle tsxFile,
                                                                            FileHandle tilesRoot) {
        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsxFile);
        String tilesetName = descriptor.name() != null && !descriptor.name().isBlank()
                ? descriptor.name()
                : baseName(tsxFile.name());
        return new TilesetAtlasImportRequest(
                descriptor.imageFile(),
                tilesRoot,
                descriptor.tileWidth(),
                descriptor.tileHeight(),
                descriptor.spacing(),
                descriptor.margin(),
                tilesetName
        );
    }

    public int importTilesetDirectory(FileHandle directory) {
        return importTilesetDirectory(directory, null);
    }

    public java.util.List<TilesetAssetMeta> tilesetImportCandidates(FileHandle source, String name) {
        return new TilesetReimportService(assetMetaDatabase).candidates(source, name);
    }

    private java.util.List<SceneEditorContext> tilesetImportContexts() {
        java.util.List<SceneEditorContext> contexts = new java.util.ArrayList<>();
        for (OpenEditorDocument document : app.getEditorDocumentManager().documents()) {
            if (document instanceof games.pixscape.studio.document.ContextEditorDocument owner
                    && !owner.isClosed() && owner.context().isInitialized()) contexts.add(owner.context());
        }
        SceneEditorContext active = app.getMostRecentSceneEditorContext();
        if (active != null && active.isInitialized() && !contexts.contains(active)) contexts.add(active);
        return contexts;
    }

    private TilesetReimportService.UsageProof tilesetUsageProof() {
        return ids -> {
            java.util.List<HudScreenEditorDocument> hud = new java.util.ArrayList<>();
            for (OpenEditorDocument document : app.getEditorDocumentManager().documents())
                if (document instanceof HudScreenEditorDocument open) hud.add(open);
            java.util.List<World> worlds = new java.util.ArrayList<>();
            for (SceneEditorContext context : tilesetImportContexts()) worlds.add(context.world());
            return new AssetUsageScanner(worlds.isEmpty() ? null : worlds.get(0),
                    ProjectConfig.getInstance(), assetMetaDatabase, hud).tileRetentionReasons(ids, worlds);
        };
    }

    public TilesetReimportService.Plan prepareTilesetDirectoryImport(FileHandle source,
            TilesetProfileImportSettings settings, int targetId) {
        return new TilesetReimportService(assetMetaDatabase).prepareDirectory(new TilesetDirectoryImportRequest(
                source, StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()).child(StudioFs.DIR_ORIG_TILES), settings),
                targetId, tilesetUsageProof());
    }

    public TilesetReimportService.Plan prepareTilesetAtlasImport(ImportDialog.ImportItem item, int targetId) {
        FileHandle tiles = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()).child(StudioFs.DIR_ORIG_TILES);
        var request = item.type == ImportDialog.ImportType.TILESET_TSX
                ? tilesetAtlasImportRequestForTsxImport(item.file, tiles)
                : tilesetAtlasImportRequestForManualImport(item, tiles);
        return new TilesetReimportService(assetMetaDatabase).prepareAtlas(request, targetId, tilesetUsageProof());
    }

    private boolean tilesetImportInProgress;

    public boolean isTilesetImportInProgress() { return tilesetImportInProgress; }

    private record TilesetImportInputs(ProjectConfig cfg, FileHandle project,
            java.util.Map<String, SceneEditorContext> openScenes,
            java.util.Map<String, java.util.Set<String>> requiredPaths,
            java.util.List<SceneEditorContext> contexts, TilesetReimportService.UsageProof usage) {}

    /** Capture authored Worlds on the UI thread; the worker only reads these file/input snapshots. */
    private TilesetImportInputs captureTilesetImportInputs(TilesetReimportService.Plan plan) {
        if (atlasStudioService.hasAsyncPackQueuedOrRunningFor(null))
            throw new IllegalArgumentException("An atlas publication is pending. Finish it before applying the tileset import.");
        FileHandle project = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
        ProjectConfig cfg = ProjectConfig.getInstance();
        java.util.Map<String, SceneEditorContext> openScenes = new java.util.LinkedHashMap<>();
        for (OpenEditorDocument document : app.getEditorDocumentManager().documents())
            if (document instanceof games.pixscape.studio.document.SceneEditorDocument open && !open.isClosed())
                openScenes.put(open.context().sceneIdentity(), open.context());
        SceneEditorContext activeScene = app.getMostRecentSceneEditorContext();
        if (activeScene != null && activeScene.isInitialized()) {
            String tag = activeScene.sceneIdentity();
            // Game Object editors also have a World, but no authored Scene identity.
            if (tag != null) openScenes.putIfAbsent(tag, activeScene);
        }
        java.util.Map<String, java.util.Set<String>> required = new java.util.LinkedHashMap<>();
        for (var entry : openScenes.entrySet()) required.put(entry.getKey(),
                sceneAtlasInputService.collectRequiredAtlasInputPathsForScene(cfg, entry.getValue().world(),
                        entry.getValue().sceneMeta(), assetMetaDatabase, tileAnimationsMetaDatabase));
        java.util.Set<Integer> members = new java.util.HashSet<>();
        if (plan.targetId() > 0) for (int i = 0; i < assetMetaDatabase.size(); i++)
            if (assetMetaDatabase.assetAt(i) instanceof TileAssetMeta tile && tile.tilesetId == plan.targetId()) members.add(tile.id());
        var retained = members.isEmpty() ? java.util.Map.<Integer, String>of() : tilesetUsageProof().retentionReasons(members);
        TilesetReimportService.UsageProof usage = ids -> {
            java.util.Map<Integer, String> reasons = new java.util.LinkedHashMap<>();
            for (int id : ids) if (retained.containsKey(id)) reasons.put(id, retained.get(id));
            return reasons;
        };
        return new TilesetImportInputs(cfg, project, openScenes, required, tilesetImportContexts(), usage);
    }

    public TilesetImportResult applyTilesetImportPlan(TilesetReimportService.Plan plan) {
        return applyTilesetImportPlan(plan, captureTilesetImportInputs(plan), ignored -> {}, Runnable::run);
    }

    /** Existing long-task dialog keeps Stage responsive; source/atlas work runs without a rendering World. */
    public void applyTilesetImportPlanWithProgress(TilesetReimportService.Plan plan, Stage stage,
            java.util.function.Consumer<TilesetImportResult> complete, java.util.function.Consumer<Throwable> failure) {
        if (tilesetImportInProgress) { failure.accept(new IllegalStateException("A tileset import is already running.")); return; }
        tilesetImportInProgress = true;
        canvas.setResourceImportBusy(true);
        final TilesetImportInputs[] inputs = {null};
        final TilesetImportResult[] result = {null};
        SaveProgressRunner runner = new SaveProgressRunner(stage, "Importing tileset", "Checking project references...");
        Array<SaveProgressRunner.Step> steps = new Array<>();
        steps.add(SaveProgressRunner.Step.sync(Float.NaN, "Checking project references...", () -> inputs[0] = captureTilesetImportInputs(plan)));
        steps.add(SaveProgressRunner.Step.async(Float.NaN, "Preparing color and normal sources...", (progress, next, fail) -> {
            Thread worker = new Thread(() -> {
                try {
                    result[0] = applyTilesetImportPlan(plan, inputs[0],
                            message -> Gdx.app.postRunnable(() -> progress.update(Float.NaN, message)),
                            SceneService::runImportPublicationOnUi);
                    next.run();
                } catch (Throwable error) { fail.accept(error); }
            }, "pixscape-tileset-import");
            worker.setDaemon(true); worker.start();
        }));
        runner.run(steps, () -> {
            tilesetImportInProgress = false; canvas.setResourceImportBusy(false); complete.accept(result[0]);
        }, error -> {
            tilesetImportInProgress = false; canvas.setResourceImportBusy(false); failure.accept(error);
        });
    }

    private static void runImportPublicationOnUi(Runnable action) {
        java.util.concurrent.CompletableFuture<Void> accepted = new java.util.concurrent.CompletableFuture<>();
        Gdx.app.postRunnable(() -> {
            try { action.run(); accepted.complete(null); }
            catch (Throwable failure) { accepted.completeExceptionally(failure); }
        });
        accepted.join();
    }

    private TilesetImportResult applyTilesetImportPlan(TilesetReimportService.Plan plan, TilesetImportInputs inputs,
            java.util.function.Consumer<String> progress, java.util.function.Consumer<Runnable> onUi) {
        var importer = new TilesetReimportService(assetMetaDatabase);
        var fresh = plan.grid() == null
                ? importer.prepareDirectory(new TilesetDirectoryImportRequest(plan.source(), plan.tilesRoot(), plan.settings()),
                    plan.targetId(), inputs.usage())
                : importer.prepareAtlas(plan.grid(), plan.targetId(), inputs.usage());
        if (!fresh.matches().equals(plan.matches()) || !fresh.removed().equals(plan.removed())
                || !fresh.retained().keySet().equals(plan.retained().keySet())
                || !fresh.sources().equals(plan.sources()))
            throw new IllegalArgumentException("Source membership or usage changed. Review the tileset update again.");
        final var prepared = fresh;
        FileHandle project = inputs.project(); ProjectConfig cfg = inputs.cfg();
        var openScenes = inputs.openScenes();
        java.util.Set<String> repackTags = new java.util.LinkedHashSet<>();
        java.util.Set<Integer> changed = new java.util.HashSet<>(plan.matches().values());
        changed.addAll(plan.removed());
        java.util.Set<String> previousPaths = new java.util.HashSet<>();
        java.util.Map<Integer, String> previousById = new java.util.LinkedHashMap<>();
        for (int id : changed) {
            AssetMeta meta = assetMetaDatabase.findById(id);
            if (meta != null && meta.sourceRelPath() != null) {
                previousPaths.add(meta.sourceRelPath()); previousById.put(id, meta.sourceRelPath());
            }
        }
        for (var entry : inputs.requiredPaths().entrySet())
            if (entry.getValue().stream().anyMatch(previousPaths::contains)) repackTags.add(entry.getKey());
        for (String name : cfg.getSceneNames()) {
            String tag = cfg.canonicalSceneTag(name);
            if (sceneAtlasInputService.containsImportedColorInput(project, tag, previousPaths)) repackTags.add(tag);
            // This marker also protects closed Scenes whose disposable inputs have not been materialized.
            if (!previousPaths.isEmpty()) atlasStudioService.markRepackRequired(project, tag);
        }
        TilesetImportResult result;
        try {
            progress.accept("Copying color and normal sources...");
            result = importer.apply(prepared, (candidate, colors) -> {
                progress.accept("Validating color packing...");
                AtlasStudioService.validateTilesetImportCandidate(candidate, colors);
            }, () -> {
                java.util.Set<String> paths = new java.util.HashSet<>();
                for (int i = 0; i < assetMetaDatabase.size(); i++) {
                    AssetMeta meta = assetMetaDatabase.assetAt(i);
                    if (meta instanceof TileAssetMeta tile && meta.logicalPath().startsWith("tiles/" + prepared.base() + "/")) {
                        paths.add(tile.sourceRelPath()); changed.add(tile.id());
                    }
                }
                atlasStudioService.repackImportedScenes(cfg, project, repackTags, () -> {
                    java.util.Map<String, String> replacements = new java.util.LinkedHashMap<>();
                    for (var entry : previousById.entrySet()) {
                        AssetMeta current = assetMetaDatabase.findById(entry.getKey());
                        replacements.put(entry.getValue(), current == null ? null : current.sourceRelPath());
                    }
                    for (String tag : repackTags) {
                        atlasStudioService.markRepackRequired(project, tag);
                        SceneEditorContext context = openScenes.get(tag);
                        if (context != null) {
                            java.util.Set<String> required = new java.util.HashSet<>();
                            for (String path : inputs.requiredPaths().get(tag)) {
                                String replaced = replacements.containsKey(path) ? replacements.get(path) : path;
                                if (replaced != null) required.add(replaced);
                            }
                            sceneAtlasInputService.syncSceneAtlasInput(cfg, tag, project, required,assetMetaDatabase);
                        } else {
                            sceneAtlasInputService.refreshImportedColorInputs(project,tag,replacements);
                            games.pixscape.studio.service.atlas.NormalAtlasPackingService.sync(project,
                                    project.child(StudioFs.DIR_ATLASES).child(StudioFs.DIR_INPUT).child(tag),assetMetaDatabase,null);
                        }
                    }
                }, () -> {
                    assetMetaDatabase.save(project.child(StudioFs.FILE_ASSETS_JSON));
                    progress.accept("Uploading and rebinding loaded resources...");
                    onUi.accept(() -> {
                    StandaloneTextureCache.reloadProjectRelative(paths);
                    canvas.publishAssetMetaDatabase(assetMetaDatabase);
                    // Invalidate the old generations before loading the newly prepared color atlases.
                    if (!previousPaths.isEmpty()) canvas.importedAssetPixelsChanged(changed);
                    for (String tag : repackTags)
                        if (atlasStudioService.getAtlas(tag) != null || openScenes.containsKey(tag))
                            atlasStudioService.load(tag, project.child(StudioFs.DIR_ATLASES).child(tag + ".atlas"));
                    if (!repackTags.isEmpty()) canvas.rebindImportedSceneResources(repackTags);
                    for(SceneEditorContext context:inputs.contexts())if(context.sceneIdentity()==null)
                        prepareGameObjectNormals(context.world(),context.sceneMeta());
                    });
                }, progress);
            });
        } catch (RuntimeException failure) {
            progress.accept("Restoring previous resources...");
            try {
                onUi.accept(() -> {
                StandaloneTextureCache.reloadProjectRelative(previousPaths);
                canvas.publishAssetMetaDatabase(assetMetaDatabase);
                canvas.importedAssetPixelsChanged(changed);
                for (String tag : repackTags) {
                    FileHandle atlas = project.child(StudioFs.DIR_ATLASES).child(tag + ".atlas");
                    if (atlasStudioService.getAtlas(tag) != null) {
                        if (atlas.exists()) atlasStudioService.load(tag, atlas);
                        else atlasStudioService.unload(tag);
                    }
                }
                if (!repackTags.isEmpty()) canvas.rebindImportedSceneResources(repackTags);
                for(SceneEditorContext context:inputs.contexts())if(context.sceneIdentity()==null)
                    prepareGameObjectNormals(context.world(),context.sceneMeta());
                refreshAssetsPanel();
                });
            } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw new IllegalArgumentException("Tileset update failed; previous resources restored. " + failure.getMessage(), failure);
        }
        onUi.accept(() -> {
            if (!previousPaths.isEmpty())
                for (SceneEditorContext context : inputs.contexts()) context.historyManager().resetAfterIrreversibleChange();
            refreshAssetsPanel();
            StudioLog.info("Tileset import applied (not undoable). " + plan.summary());
        });
        return result;
    }

    public int importTilesetDirectory(FileHandle directory, TilesetProfileImportSettings profileSettings) {
        if (ProjectConfig.getInstance() == null) return 0;
        return applyTilesetImportPlan(prepareTilesetDirectoryImport(directory, profileSettings, -1)).importedCount();
    }

    public void deleteTilesetDirectory(String relativeTilesPath) {
        if (relativeTilesPath == null || relativeTilesPath.isBlank()) {
            throw new IllegalArgumentException("Tileset path is empty.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        if (assetMetaDatabase == null) {
            throw new IllegalStateException("Asset database is not loaded.");
        }

        String normalizedPath = relativeTilesPath.replace('\\', '/').trim();
        if (normalizedPath.isEmpty()) {
            throw new IllegalArgumentException("Tileset path is empty.");
        }

        String tilesetLogicalPath = StudioFs.PREFIX_TILES + normalizedPath;
        AssetMeta meta = assetMetaDatabase.findByLogicalPath(tilesetLogicalPath);

        if (!(meta instanceof games.pixscape.studio.asset.TilesetAssetMeta tilesetMeta)) {
            throw new IllegalStateException("Tileset not found: " + normalizedPath);
        }

        AssetUsageScanner usageScanner = new AssetUsageScanner(
                sceneContext().world(),
                cfg,
                assetMetaDatabase
        );

        AssetUsageScanner.AssetUsageReport usage = usageScanner.scanTileset(tilesetMeta.id());
        if (usage.used()) {
            throw new IllegalStateException(buildTilesetInUseMessage(normalizedPath, usage));
        }

        boolean runtimeAvailabilityChanged =
                runtimeAvailabilityService.removeDeletedTileset(cfg, assetMetaDatabase, tilesetMeta.id());

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle tilesetDir = tilesRoot.child(normalizedPath);

        if (tilesetDir.exists()) {
            tilesetDir.deleteDirectory();
        }

        // Remove child tile metas first
        String tilePrefix = tilesetLogicalPath + "/";
        assetMetaDatabase.removeByLogicalPathPrefix(tilePrefix);

        // Remove tileset meta itself
        assetMetaDatabase.removeByLogicalPath(tilesetLogicalPath);

        assetMetaDatabase.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));

        // Clear current tile selection if it pointed to a removed tile
        canvas.getTiledPaintService().setActiveTileAssetId(-1);

        persistRuntimeAvailabilityChange(cfg, runtimeAvailabilityChanged);
        refreshAssetsPanel();

        StudioLog.info("Tileset deleted: " + normalizedPath);
    }

    private int importSpritesheetAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        if (!isImage(item.file)) {
            warnUnsupported(item.file);
            return 0;
        }

        String base = baseName(item.file.name());
        String logical = StudioFs.PREFIX_ANIMATIONS + base;

        AnimationAssetMeta meta = (AnimationAssetMeta) assetMetaDatabase.registerIfAbsent(
                AssetType.ANIMATION,
                logical,
                null,
                AssetMeta.AssetScope.USER
        );

        int id = meta.id();
        String physicalName = base + "__a" + id;

        FileHandle animDir = ctx.animRoot.child(physicalName);
        animDir.mkdirs();

        ImageSize size = readImageSize(item.file);
        int columns = Math.max(1, size.width / Math.max(1, item.tileWidth));
        int rows = Math.max(1, size.height / Math.max(1, item.tileHeight));

        splitGridImage(
                item.file,
                animDir,
                Math.max(1, item.tileWidth),
                Math.max(1, item.tileHeight),
                physicalName
        );

        assetMetaDatabase.updateSourceRelPath(
                meta.id(),
                ctx.animRootRel + "/" + physicalName
        );
        meta.frameCount = columns * rows;
        meta.fps = meta.fps > 0f ? meta.fps : 12f;
        if (meta.clips == null) meta.clips = new ObjectMap<>();
        if (meta.clips.size == 0) {
            meta.currentClip = "default";
            meta.clips.put("default", new AnimationClipMeta(0, meta.frameCount - 1));
        }
        return 1;
    }

    private int importParticleEffectAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        if (!isParticle(item.file)) {
            warnUnsupported(item.file);
            return 0;
        }

        return importParticleEffectWithImages(ctx.cfg, ctx.projectDir, item.file) ? 1 : 0;
    }

    private boolean importParticleEffectWithImages(ProjectConfig cfg,
                                                   FileHandle projectDir,
                                                   FileHandle originalPFile) {

        if (cfg == null || projectDir == null || originalPFile == null || !originalPFile.exists())
            return false;

        FileHandle effectsRoot = projectDir.child(StudioFs.DIR_ORIG_EFFECTS);
        effectsRoot.mkdirs();

        FileHandle imagesRoot = projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        imagesRoot.mkdirs();

        ParticleEffect effect = new ParticleEffect();
        try {
            effect.loadEmitters(originalPFile);
        } catch (Exception ex) {
            Gdx.app.error("Import", "Failed to parse particle file: " + originalPFile.path(), ex);
            StudioLog.error("Asset import failed: " + originalPFile.name());
            return false;
        }

        Map<String, FileHandle> resolvedImages = new LinkedHashMap<>();
        boolean missingImage = false;

        for (ParticleEmitter emitter : effect.getEmitters()) {
            for (String imagePath : emitter.getImagePaths()) {

                String fileName = new File(imagePath.replace('\\', '/')).getName();
                FileHandle srcImg = resolveParticleImage(originalPFile, imagePath);

                if (srcImg == null || !srcImg.exists()) {
                    Gdx.app.error("Import", "Particle image missing: " + imagePath);
                    StudioLog.warn("Asset import warning: missing particle image " + fileName);
                    missingImage = true;
                    continue;
                }

                resolvedImages.putIfAbsent(fileName, srcImg);
            }
        }

        if (missingImage) {
            StudioLog.error("Asset import failed: particle effect references missing images.");
            return false;
        }

        Map<String, String> renameMap = new LinkedHashMap<>();

        for (Map.Entry<String, FileHandle> imageEntry : resolvedImages.entrySet()) {
            String fileName = imageEntry.getKey();
            FileHandle srcImg = imageEntry.getValue();
            String base = baseName(fileName);

            AssetMeta meta = assetMetaDatabase.registerIfAbsent(
                    AssetType.IMAGE,
                    StudioFs.PREFIX_IMAGES + base,
                    null,
                    AssetMeta.AssetScope.INTERNAL
            );

            int id = meta.id();

            String newName = base + "__a" + id + "." + srcImg.extension();
            FileHandle dstImg = imagesRoot.child(newName);

            if (!dstImg.exists()) {
                srcImg.copyTo(dstImg);
            }

            assetMetaDatabase.updateSourceRelPath(
                    meta.id(),
                    StudioFs.DIR_ORIG_IMAGES + "/" + newName
            );

            renameMap.put(fileName, newName);
        }

        // Rewrite the .p file
        String content = originalPFile.readString("UTF-8");

        for (Map.Entry<String, String> entry : renameMap.entrySet()) {
            content = content.replace(entry.getKey(), entry.getValue());
        }

        FileHandle dstP = effectsRoot.child(originalPFile.name());
        dstP.writeString(content, false, "UTF-8");

        assetMetaDatabase.registerIfAbsent(
                AssetType.PARTICLE,
                StudioFs.PREFIX_EFFECTS + StudioFs.baseName(originalPFile.name()),
                StudioFs.DIR_ORIG_EFFECTS + "/" + dstP.name(),
                AssetMeta.AssetScope.USER
        );

        canvas.invalidateStudioParticleFallbacks();
        return true;
    }

    static FileHandle resolveParticleImage(FileHandle originalPFile, String imagePath) {

        String normalized = imagePath.replace('\\', '/');
        String fileName = new File(normalized).getName();

        FileHandle relativePath = originalPFile.parent().child(normalized);
        if (relativePath.exists()) return relativePath;

        FileHandle relative = originalPFile.parent().child(fileName);
        if (relative.exists()) return relative;

        FileHandle abs = Gdx.files.absolute(normalized);
        if (abs.exists()) return abs;

        return null;
    }

    private String buildTilesetInUseMessage(String tilesetPath,
                                            AssetUsageScanner.AssetUsageReport usage) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cannot delete tileset '")
                .append(tilesetPath)
                .append("' because it is still used in the project.");

        if (usage == null || !usage.used()) {
            return sb.toString();
        }

        if (usage.sceneNames() != null && usage.sceneNames().size > 0) {
            sb.append("\n\nUsed in scene");
            if (usage.sceneNames().size > 1) {
                sb.append("s");
            }
            sb.append(":");

            for (String sceneName : usage.sceneNames()) {
                sb.append("\n- ").append(sceneName);
            }
        }

        if (usage.occurrenceCount() > 0) {
            sb.append("\n\nOccurrences: ").append(usage.occurrenceCount());
        }

        return sb.toString();
    }

    private ImageSize readImageSize(FileHandle file) {
        Pixmap pixmap = new Pixmap(file);
        try {
            return new ImageSize(pixmap.getWidth(), pixmap.getHeight());
        } finally {
            pixmap.dispose();
        }
    }

    private void warnUnsupported(FileHandle file) {
        String ext = file.extension();
        StudioLog.warn("Unsupported file format: " +
                (ext == null || ext.isBlank() ? file.name() : ("." + ext.toLowerCase(Locale.ROOT))));
    }

    private void splitGridImage(FileHandle sourceFile,
                                FileHandle outputDir,
                                int tileWidth,
                                int tileHeight,
                                String prefix) {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source image is missing");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory is null");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new IllegalArgumentException("Tile size must be > 0");
        }

        outputDir.mkdirs();

        Pixmap source = new Pixmap(sourceFile);
        try {
            int imageWidth = source.getWidth();
            int imageHeight = source.getHeight();

            int columns = imageWidth / tileWidth;
            int rows = imageHeight / tileHeight;

            if (columns <= 0 || rows <= 0) {
                throw new IllegalStateException(
                        "Image is smaller than the requested grid size: "
                                + imageWidth + "x" + imageHeight
                                + " for tiles " + tileWidth + "x" + tileHeight
                );
            }

            if ((imageWidth % tileWidth) != 0 || (imageHeight % tileHeight) != 0) {
                StudioLog.warn(
                        "Grid split will ignore extra pixels: image="
                                + imageWidth + "x" + imageHeight
                                + ", tile=" + tileWidth + "x" + tileHeight
                );
            }

            int index = 0;

            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    Pixmap tile = new Pixmap(tileWidth, tileHeight, source.getFormat());
                    try {
                        tile.drawPixmap(
                                source,
                                0, 0,
                                x * tileWidth, y * tileHeight,
                                tileWidth, tileHeight
                        );

                        String fileName = buildSplitTileFileName(prefix, index);
                        FileHandle out = outputDir.child(fileName);
                        PixmapIO.writePNG(out, tile);
                    } finally {
                        tile.dispose();
                    }
                    index++;
                }
            }
        } finally {
            source.dispose();
        }
    }

    private String buildSplitTileFileName(String prefix, int index) {
        if (prefix == null || prefix.isBlank()) {
            return index + StudioFs.EXT_PNG;
        }
        return prefix + "_" + String.format(Locale.ROOT, "%04d", index) + StudioFs.EXT_PNG;
    }

    private static boolean isImage(FileHandle f) {
        return f != null && StudioFs.isImageFile(f.name());
    }

    private static boolean isParticle(FileHandle f) {
        return f != null && StudioFs.isParticleFile(f.name());
    }

    private static boolean isTsx(FileHandle f) {
        return f != null && "tsx".equalsIgnoreCase(f.extension());
    }

    private static ImportDialog.ImportType resolveAuto(FileHandle f) {
        if (f != null && StudioFs.isParticleFile(f.name())) {
            return ImportDialog.ImportType.PARTICLE_EFFECT;
        }
        if (isTsx(f)) {
            return ImportDialog.ImportType.TILESET_TSX;
        }
        if (isImage(f)) {
            return ImportDialog.ImportType.IMAGE;
        }
        return ImportDialog.ImportType.IMAGE;
    }

    private static String baseName(String name) {
        return StudioFs.baseName(name);
    }

    private static String fileNameFromPath(String path) {
        return StudioFs.fileNameFromPath(path);
    }

    // ---------------------------------------------------------------------
    // WORLD / RENDER STATE RESET
    // ---------------------------------------------------------------------

    private void clearWorldAndRenderState() {

        if (canvas == null) return;

        canvas.resetEditingContexts();
        canvas.getPhysicsSelectionReconciler().clearSceneContext();
        World world = sceneContext().world();

        // Delete all entities
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            world.delete(data[i]);
        }

        // Process to flush deletions
        world.process();

        // Reset render memory
        canvas.clearRenderMemory();
        canvas.getTiledAllocatorService().reset();

        // Reset services studio
        sceneContext().selectionService().clearSelection();
        sceneContext().layerService().reset();

        // Nettoyage caches
        StandaloneTextureCache.clear(true);
        canvas.invalidateStandaloneAssetVisuals();

        // Reset historique
        clearCurrentSceneSaveRequired();
    }

    private void bindSceneIdentityAuthorities(SceneMeta meta) {
        sceneContext().identityRegistry().bind(sceneContext().world(), meta);
        canvas.getPhysicsService().setPhysicsShapeIdState(meta);
    }

    private void flushWorldForSerialization() {
        if (canvas == null) return;
        sceneContext().world().process();
    }

    // ---------------------------------------------------------------------
    // UI / CALLBACKS / CONSISTENCY CHECKS
    // ---------------------------------------------------------------------

    private void registerEditorOpsCallbacks() {
        if (canvas == null) return;
        canvas.setAtlasInputsChangedListener(this::onSceneAtlasInputsChanged);
    }

    private void onSceneAtlasInputsChanged(String sceneTag) {
        if (sceneTag == null || sceneTag.isBlank()) return;

        Gdx.app.log("SceneManager", "onSceneAtlasInputsChanged: " + sceneTag);
    }

    private void refreshAssetsPanel() {
        canvas.invalidateAssetVisualMetadata();
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        if (assetsPanel != null) {
            assetsPanel.reloadFromProject(cfg);
            return;
        }

        AssetsPanel found = null;
        for (DockablePanel panel : app.getDockManager().getPanels()) {
            if (panel instanceof AssetsPanel ap) {
                found = ap;
                break;
            }
        }
        if (found != null) {
            assetsPanel = found;
            assetsPanel.reloadFromProject(cfg);
        }
    }

    // ---------------------------------------------------------------------
    // PROJECT VALIDATION HELPERS
    // ---------------------------------------------------------------------

    public static void requireValidExportRootOrThrow(ProjectConfig cfg, String operation) {
        if (cfg == null) throw new IllegalArgumentException("cfg is null");
        String exportRoot = cfg.exportRootPathDir;
        if (exportRoot == null || exportRoot.isBlank()) {
            String op = (operation == null || operation.isBlank()) ? "operation" : operation;
            throw new IllegalStateException(op + ": exportRootPathDir is required and cannot be blank.");
        }
        try {
            RuntimeExportPaths.userRootPath(Path.of(exportRoot));
        } catch (InvalidPathException ex) {
            String op = (operation == null || operation.isBlank()) ? "operation" : operation;
            throw new IllegalStateException(op + ": exportRootPathDir is invalid: " + exportRoot, ex);
        }
    }

    private static void rejectRuntimeExportProjectOrInvalidStudioKind(FileHandle projectFile) {
        JsonValue root;
        try {
            root = new com.badlogic.gdx.utils.JsonReader().parse(projectFile);
        } catch (Exception ex) {
            return;
        }
        String kind = (root != null) ? root.getString("projectKind", null) : null;
        if (RuntimeExport.RUNTIME_PROJECT_KIND.equals(kind)) {
            throw new IllegalStateException("This is an exported Pixscape runtime project, not a Studio project.");
        }
        if (kind != null && !ProjectConfig.STUDIO_PROJECT_KIND.equals(kind)) {
            throw new IllegalStateException("Unsupported project file kind: " + kind);
        }
    }

    private static void validateProjectPathSafetyOrThrow(FileHandle projectFile, FileHandle projectDir, ProjectConfig cfg) {
        if (projectFile == null || projectFile.isDirectory()) {
            throw new IllegalStateException("Project file is missing or invalid: " + (projectFile == null ? "<null>" : projectFile.path()));
        }
        if (projectDir == null || !projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalStateException("Project directory is missing or invalid.");
        }
        requireValidExportRootOrThrow(cfg, "openProject");
    }

    static void cleanupFailedNewProjectDir(FileHandle projectDir, boolean projectDirExistedBeforeAttempt) {
        if (projectDir == null || !projectDir.exists() || projectDirExistedBeforeAttempt) return;
        projectDir.deleteDirectory();
    }

    static void resetProjectConfigToEmptyState() {
        ProjectConfig.setInstance(new ProjectConfig());
    }

    // ---------------------------------------------------------------------
    // INNER TYPES
    // ---------------------------------------------------------------------

    record OpenProjectContext(ProjectConfig config, FileHandle projectDir, String sceneName,
                              FileHandle assetsMetaFile) {
    }

    static final class SpatialSceneActivationException extends IllegalStateException {
        SpatialSceneActivationException(String message) {
            super(message);
        }
    }

    static final class TiledMapResolutionException extends IllegalStateException {
        TiledMapResolutionException(String message) {
            super(message);
        }
    }

    private record SaveExecutionPlan(ProjectConfig cfg,
                                     FileHandle studioDir,
                                     String sceneName,
                                     FileHandle sceneFile,
                                     String canonicalTag,
                                     boolean hasSceneToSave,
                                     SceneEditorContext source,
                                     World sourceWorld,
                                     SceneMeta sourceMeta,
                                     long sourceGeneration,
                                     int sourceAttachCount,
                                     String exportRoot) {
    }

    private record AssetImportContext(ProjectConfig cfg, FileHandle projectDir, FileHandle imagesRoot,
                                      FileHandle tilesRoot, FileHandle animRoot, String animRootRel,
                                      FileHandle effectsRoot) {
    }

    private record ImageSize(int width, int height) {
    }

}

