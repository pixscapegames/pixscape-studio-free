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
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.Timer;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;
import games.pixscape.studio.importer.tmx.TmxSceneImportService;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.asset.*;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetDirectoryImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImportResult;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetProfileImportSettings;
import games.pixscape.studio.service.atlas.*;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityService;
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
    private final ClipboardService clipboardService;
    private final HistoryManager historyManager;
    private final SceneAtlasInputService sceneAtlasInputService;
    private final RuntimeAvailabilityService runtimeAvailabilityService;
    private final ProjectConfigSceneMetaBridge sceneMetaBridge;
    private final RecentProjectsService recentProjectsService;
    private final ResolvedSceneActivationPipeline sceneActivationPipeline;
    private AssetMetaDatabase assetMetaDatabase;
    private TileAnimationsMetaDatabase tileAnimationsMetaDatabase;
    private boolean currentSceneSaveRequired = false;

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
        this.historyManager = canvas.getHistoryManager();
        this.sceneAtlasInputService = new SceneAtlasInputService();
        this.runtimeAvailabilityService = new RuntimeAvailabilityService();
        this.sceneMetaBridge = new ProjectConfigSceneMetaBridge();
        this.clipboardService = canvas.getClipboardService();
        this.recentProjectsService = new RecentProjectsService();
        this.sceneActivationPipeline = new ResolvedSceneActivationPipeline(
                canvas.getEcsWorld(),
                canvas.getTileAnimationRegistry(),
                canvas.getTiledAllocatorService(),
                historyManager,
                this::rebuildRenderRuntimeForScene
        );

        registerEditorOpsCallbacks();
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
                                        AnimationComponent component,
                                        int frameCount,
                                        float fps) {
        if (sourceRelPath == null || sourceRelPath.isBlank()) {
            throw new IllegalArgumentException("Animation source path is empty.");
        }
        if (component == null) {
            throw new IllegalArgumentException("Animation component is null.");
        }

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        assetMetaDatabase = AssetMetaDatabase.load(assetsFile);

        AssetMeta meta = assetMetaDatabase.findUniqueBySourceRelPath(
                sourceRelPath,
                AssetType.ANIMATION
        );
        if (!(meta instanceof AnimationAssetMeta animation)) {
            throw new IllegalStateException("Animation asset not found: " + sourceRelPath);
        }

        animation.frameCount = Math.max(animation.frameCount, Math.max(0, frameCount));
        animation.fps = fps > 0f ? fps : (animation.fps > 0f ? animation.fps : 12f);
        animation.currentClip = component.currentClip;
        animation.clips.clear();

        if (component.clips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> entry : component.clips) {
                if (entry == null || entry.key == null || entry.key.isBlank() || entry.value == null) {
                    continue;
                }

                AnimationComponent.Clip src = entry.value;
                AnimationComponent.Clip copy = new AnimationComponent.Clip(src.start, src.end);
                copy.flipX = src.flipX;
                animation.clips.put(entry.key, copy);
            }
        }

        assetMetaDatabase.save(assetsFile);
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
        currentSceneSaveRequired = true;
    }

    public boolean requiresSaveBeforeLeavingCurrentScene() {
        return historyManager.isDirty() || currentSceneSaveRequired;
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
        currentSceneSaveRequired = false;
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

    // ---------------------------------------------------------------------
    // NEW PROJECT
    // ---------------------------------------------------------------------

    public void newProject(String projectTitle,
                           String projectFileName,
                           String projectDirectoryPath,
                           String exportDir,
                           PlatformTarget platformTarget,
                           int glSamples,
                           int tileWidth,
                           int tileHeight,
                           String projection) {
        FileHandle projectDir = null;
        boolean projectDirExistedBeforeAttempt = false;
        ProjectConfig cfg = new ProjectConfig();
        try {
            clearWorldAndRenderState();

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

            if ("None".equals(projection)) {
                meta.tiledEnabled = false;
            } else {
                meta.tiledEnabled = true;
                meta.tileWidth = tileWidth;
                meta.tileHeight = tileHeight;
                meta.tiledProjection =
                        "Isometric".equals(projection)
                                ? SceneMetaRuntime.TiledProjection.ISO
                                : SceneMetaRuntime.TiledProjection.ORTHO;
            }

            ProjectConfig.setInstance(cfg);
            bindSceneIdentityAuthorities(meta);

            projectDir = StudioFs.requireStudioProjectDir(cfg);
            projectDirExistedBeforeAttempt = projectDir.exists();
            projectDir.mkdirs();

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

            int indexL = app.getCanvas().getLayerService().addLayerTop("Main layer");
            int layerEntityId = app.getCanvas().getLayerService().getLayerEntity(indexL);
            app.getCanvas().getSelectionService().setActivelayerId(layerEntityId);
            EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));

            saveProjectAndCurrentScene();

            String canonicalTag = cfg.canonicalSceneTagCurrent();
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, canonicalTag);
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
            clipboardService.clear();

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

        ProjectConfig.setInstance(cfg);
        assetMetaDatabase = AssetMetaDatabase.load(context.assetsMetaFile());

        tileAnimationsMetaDatabase = TileAnimationsIO.load(
                projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        reloadTileAnimationRegistryFromProjectData();

        applyProjectFixedSettings(cfg);
        refreshParticleEffectsRoot(cfg);
        clipboardService.clear();

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
        clearWorldAndRenderState();
        bindSceneIdentityAuthorities(null);
        resetProjectConfigToEmptyState();
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
        clearWorldAndRenderState();

        SceneMeta meta = cfg.getSceneMeta(sceneName);
        if (meta == null) {
            throw new IllegalStateException("Missing scene metadata for scene '" + sceneName + "'.");
        }
        bindSceneIdentityAuthorities(meta);

        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);

        String canonicalTag = cfg.canonicalSceneTagFor(meta);
        if (canonicalTag == null || canonicalTag.isBlank()) {
            throw new IllegalStateException("Missing canonical scene tag for scene '" + sceneName + "'.");
        }
        canvas.getPhysicsSelectionReconciler().bindSceneContext(canonicalTag);

        FileHandle sceneFile = scenesDir.child(meta.getFile());

        sceneActivationPipeline.activate(
                new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                        cfg,
                        meta,
                        sceneFile,
                        projectDir,
                        cfg.projectTitle,
                        sceneName,
                        canonicalTag
                ));
        canvas.requestTiledFallbackValidation();
        canvas.getIdentityRegistry().rebuild();
        // UI

        int firstLayerEntityId = app.getCanvas().getLayerService().getFirstLayerEntity();
        if (firstLayerEntityId != -1) {
            app.getCanvas().getSelectionService().setActivelayerId(firstLayerEntityId);
        }

        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
        app.getCanvas().getSelectionService().clearSelection();
        refreshAssetsPanel();
        app.getBottomBar().refreshSelectBox();

    }

    // ---------------------------------------------------------------------
    // SAVE PROJECT + CURRENT SCENE (studio)
    // ---------------------------------------------------------------------

    public void saveProjectAndCurrentScene() {
        final SaveExecutionPlan plan;
        try {
            plan = prepareSaveExecutionPlan();
        } catch (IllegalStateException ex) {
            logSaveValidationFailure(ex);
            return;
        }

        saveProjectFile(plan.cfg());
        if (!plan.hasSceneToSave()) {
            finishSaveWithoutScene();
            return;
        }

        maybeRepackAtlas(plan);
        rebuildSparseFromDense();
        saveScene(canvas.getEcsWorld(), plan.sceneFile(), false);
        saveTileAnimations(plan);
        exportRuntimeBestEffort(plan.cfg(), plan.studioDir());
        finishSaveWithScene(plan.cfg());
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

        if (uiStage == null) {
            try {
                executePreparedSavePlan(plan);
                if (onSuccess != null) onSuccess.run();
            } catch (Throwable t) {
                if (onError != null) onError.accept(t);
            }
            return;
        }

        SaveProgressRunner runner = new SaveProgressRunner(uiStage);
        Array<SaveProgressRunner.Step> steps = new Array<>();

        steps.add(SaveProgressRunner.Step.sync(0.10f, "Preparing save...", () -> {
        }));
        steps.add(SaveProgressRunner.Step.sync(0.20f, "Saving project file...", () -> saveProjectFile(plan.cfg())));

        if (!plan.hasSceneToSave()) {
            steps.add(SaveProgressRunner.Step.sync(1.00f, "Finalizing...", this::finishSaveWithoutScene));
        } else {
            steps.add(SaveProgressRunner.Step.async(0.25f, "Repacking atlas...",
                    (progress, next, fail) -> maybeRepackAtlasAsync(plan, progress, next, fail)));
            steps.add(SaveProgressRunner.Step.sync(0.65f, "Rebuilding tiled sparse data...", this::rebuildSparseFromDense));
            steps.add(SaveProgressRunner.Step.sync(0.75f, "Saving scene...", () -> saveScene(canvas.getEcsWorld(), plan.sceneFile(), false)));
            steps.add(SaveProgressRunner.Step.sync(0.82f, "Saving tiled animations...", () -> saveTileAnimations(plan)));
            steps.add(SaveProgressRunner.Step.sync(0.90f, "Exporting runtime...", () -> exportRuntime(plan.cfg(), plan.studioDir())));
            steps.add(SaveProgressRunner.Step.sync(1.00f, "Finalizing...", () -> finishSaveWithScene(plan.cfg())));
        }

        runner.run(steps, onSuccess, onError);
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
            ProjectRenameService.copyProjectDirectory(currentProjectDir, targetProjectDir);
            cfg.projectDirectoryPath = targetProjectDir.path();
        }

        if (!targetProjectFileName.equals(cfg.projectFileName)) {
            ProjectRenameService.renameProjectFile(targetProjectDir, cfg, targetProjectFileName);
        }

        saveProjectAndCurrentScene();
    }

    private SaveExecutionPlan prepareSaveExecutionPlan() {
        final ProjectConfig cfg = ProjectConfig.getInstance();

        flushWorldForSerialization();

        if (cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            throw new IllegalStateException("saveProjectAndCurrentScene: projectName is empty, abort.");
        }
        requireValidExportRootOrThrow(cfg, "saveProjectAndCurrentScene");

        final FileHandle studioDir = StudioFs.requireStudioProjectDir(cfg);
        if (studioDir == null) {
            throw new IllegalStateException("saveProjectAndCurrentScene: studioDir is null, abort.");
        }

        final SceneMeta meta = cfg.getCurrentSceneMeta();
        if (meta == null || meta.getName() == null || meta.getName().isEmpty()) {
            return new SaveExecutionPlan(cfg, studioDir, null, null, null, false);
        }

        final String sceneName = meta.getName();
        final String sceneFileName = meta.getFile();
        if (sceneFileName == null || sceneFileName.isBlank()) {
            throw new IllegalStateException(
                    "saveProjectAndCurrentScene: SceneMeta.file is null or empty for '" + sceneName + "', abort.");
        }

        final FileHandle scenesDir = studioDir.child(StudioFs.DIR_SCENES);
        scenesDir.mkdirs();
        final FileHandle sceneFile = scenesDir.child(sceneFileName);
        final String canonicalTag = cfg.canonicalSceneTag(sceneName);
        return new SaveExecutionPlan(cfg, studioDir, sceneName, sceneFile, canonicalTag, true);
    }

    private void executePreparedSavePlan(SaveExecutionPlan plan) {
        saveProjectFile(plan.cfg());

        if (!plan.hasSceneToSave()) {
            finishSaveWithoutScene();
            return;
        }

        maybeRepackAtlas(plan);
        rebuildSparseFromDense();
        saveScene(canvas.getEcsWorld(), plan.sceneFile(), false);
        saveTileAnimations(plan);
        exportRuntime(plan.cfg(), plan.studioDir());
        finishSaveWithScene(plan.cfg());
    }

    private void logSaveValidationFailure(IllegalStateException ex) {
        Gdx.app.error("SceneManager", ex.getMessage());
        EventFlow.i().publish(new EventFlow.LogMessage(ex.getMessage()));
        StudioLog.warn(ex.getMessage());
    }

    private void saveTileAnimations(SaveExecutionPlan plan) {
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

    private void exportRuntime(ProjectConfig cfg, FileHandle studioDir) {
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
        historyManager.markSaved();
        clearCurrentSceneSaveRequired();
    }

    private void finishSaveWithScene(ProjectConfig cfg) {
        Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + ")");
        historyManager.markSaved();
        StudioLog.info("Project saved successfully");
        clearCurrentSceneSaveRequired();
    }

    private void saveCurrentSceneOnly(ProjectConfig cfg) {
        String sceneName = cfg.getCurrentSceneName();
        if (sceneName == null || sceneName.isEmpty()) {
            Gdx.app.log("SceneManager", "saveCurrentSceneOnly: no current scene, skipping.");
            return;
        }

        flushWorldForSerialization();

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

        FileHandle sceneFile = scenesDir.child(fileName);

        if (EditorSettings.get().autoRepackAtlases) {
            repackSceneAtlas(cfg, sceneName, projectDir);
        }
        rebuildSparseFromDense();

        saveScene(canvas.getEcsWorld(), sceneFile, false);
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

    public boolean addRuntimeAvailablePrefab(String prefabId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.addPrefab(meta, prefabId);
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

    public boolean removeRuntimeAvailablePrefab(String prefabId) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        boolean changed = runtimeAvailabilityService.removePrefab(meta, prefabId);
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

        AssetUsageScanner usageScanner = new AssetUsageScanner(
                canvas.getEcsWorld(),
                cfg,
                assetMetaDatabase
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
        markCurrentSceneSaveRequired();
        StandaloneTextureCache.clear(true);
        canvas.invalidateStandaloneAssetVisuals();

        persistRuntimeAvailabilityChange(cfg, runtimeAvailabilityChanged);
        refreshAssetsPanel();
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
                canvas.getEcsWorld(),
                assetMetaDatabase,
                tileAnimationsMetaDatabase
        );

        if (syncResult.changed()) {
            atlasStudioService.requestAsyncPack(
                    canonicalTag,
                    AsyncAtlasRepackCoordinator.RepackReason.GENERIC
            );
        }
    }

    private void maybeRepackAtlas(SaveExecutionPlan plan) {
        if (!plan.hasSceneToSave()) return;
        if (!EditorSettings.get().autoRepackAtlases) return;

        String canonicalTag = plan.canonicalTag();
        if (canonicalTag == null || canonicalTag.isBlank()) return;

        AtlasInputSyncResult syncResult = syncSceneAtlasInputForSave(plan);

        if (shouldSkipSaveAtlasRepack(plan, syncResult)) {
            logSaveAtlasRepackSkipped(canonicalTag);
            return;
        }

        ProjectFileCleanupService.deleteSceneAtlasFiles(plan.studioDir(), canonicalTag);

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
    }

    private void maybeRepackAtlasAsync(SaveExecutionPlan plan,
                                       SaveProgressRunner.ProgressHandle progress,
                                       Runnable onDone,
                                       java.util.function.Consumer<Throwable> onError) {
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

        Gdx.app.log("SceneManager",
                "Atlas input synced for save: scene=" + canonicalTag
                        + " changed=" + syncResult.changed()
                        + " copied=" + syncResult.copiedCount()
                        + " deleted=" + syncResult.deletedCount());

        if (shouldSkipSaveAtlasRepack(plan, syncResult)) {
            progress.update(0.60f, "Repacking atlas (1/1): skipped");
            logSaveAtlasRepackSkipped(canonicalTag);
            onDone.run();
            return;
        }

        if (atlasStudioService.hasAsyncPackQueuedOrRunningFor(canonicalTag)) {
            Gdx.app.log("AtlasStudioService",
                    "Save atlas repack using pending pack scene=" + canonicalTag);
            waitForAsyncPackCompletion(canonicalTag, progress, onDone, onError);
            return;
        }

        progress.update(0.30f, "Queueing atlas repack (1/1)...");

        atlasStudioService.requestAsyncPack(
                canonicalTag,
                AsyncAtlasRepackCoordinator.RepackReason.SAVE
        );

        waitForAsyncPackCompletion(canonicalTag, progress, onDone, onError);
    }

    private AtlasInputSyncResult syncSceneAtlasInputForSave(SaveExecutionPlan plan) {
        return sceneAtlasInputService.syncSceneAtlasInputForSave(
                plan.cfg(),
                canvas.getEcsWorld(),
                assetMetaDatabase,
                tileAnimationsMetaDatabase
        );
    }

    private boolean shouldSkipSaveAtlasRepack(SaveExecutionPlan plan, AtlasInputSyncResult syncResult) {
        if (plan == null || syncResult == null) return false;
        return shouldSkipSaveAtlasRepack(
                plan.studioDir(),
                plan.canonicalTag(),
                syncResult,
                hasUsableSceneAtlas(plan.studioDir(), plan.canonicalTag()),
                atlasStudioService.hasAsyncPackQueuedOrRunningFor(plan.canonicalTag())
        );
    }

    static boolean shouldSkipSaveAtlasRepack(FileHandle studioDir,
                                             String sceneTag,
                                             AtlasInputSyncResult syncResult,
                                             boolean atlasUsable,
                                             boolean pendingPackForScene) {
        if (studioDir == null || sceneTag == null || sceneTag.isBlank()) return false;
        if (syncResult == null) return false;
        if (syncResult.changed()) return false;
        if (syncResult.copiedCount() > 0) return false;
        if (syncResult.deletedCount() > 0) return false;
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

        Array<String> pageFileNames = atlasPageFileNames(atlasFile);
        if (pageFileNames.size == 0) return false;

        for (String pageFileName : pageFileNames) {
            FileHandle pageFile = atlasesDir.child(pageFileName);
            if (!pageFile.exists() || pageFile.isDirectory() || pageFile.length() <= 0L) {
                return false;
            }
        }

        return true;
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

    private void waitForAsyncPackCompletion(String canonicalTag,
                                            SaveProgressRunner.ProgressHandle progress,
                                            Runnable onDone,
                                            java.util.function.Consumer<Throwable> onError) {
        try {
            atlasStudioService.updateAsyncPack();
            atlasStudioService.applyIfPackReady();

            if (atlasStudioService.isPackInProgress()) {
                progress.update(0.45f, "Repacking atlas (1/1)...");
            } else if (atlasStudioService.isPackRequested()) {
                progress.update(0.35f, "Waiting atlas repack slot (1/1)...");
            }

            if (!canvas.getAtlasService().hasAsyncPackQueuedOrRunningFor(canonicalTag)) {
                progress.update(0.60f, "Repacking atlas (1/1): done");
                onDone.run();
                return;
            }

            Timer.schedule(new com.badlogic.gdx.utils.Timer.Task() {
                @Override
                public void run() {
                    waitForAsyncPackCompletion(canonicalTag, progress, onDone, onError);
                }
            }, 1f / 60f);
        } catch (Throwable t) {
            if (onError != null) {
                onError.accept(t);
            } else {
                Gdx.app.error("SceneManager", "Async atlas save step failed", t);
            }
        }
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
                        canvas.getEcsWorld(),
                        assetMetaDatabase,
                        tileAnimationsMetaDatabase
                );

        Gdx.app.log("SceneManager",
                "Atlas input synced for repack: scene=" + canonicalTag
                        + " changed=" + syncResult.changed()
                        + " copied=" + syncResult.copiedCount()
                        + " deleted=" + syncResult.deletedCount());

        ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, canonicalTag);

        SceneAtlasLoaderService.packSceneAtlas(
                cfg,
                canonicalTag,
                projectDir
        );

        reloadAtlasAndRebind(cfg, canonicalTag, projectDir);

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

        World world = canvas.getEcsWorld();

        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);
        ComponentMapper<LayerComponent> mLayer =
                world.getMapper(LayerComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] dataArr = bag.getData();

        for (int i = 0; i < bag.size(); i++) {

            int e = dataArr[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null || tiled.data == null) continue;

            LayerComponent layer = mLayer.getSafe(e, null);
            tiled.spatialEnabled = (layer != null && layer.spatialEnabled) || tiled.data.spatialEnabled;
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

        refreshStudioTilesetProfileRegistry(projectDir);

        SceneAtlasLoaderService.loadSceneAtlas(cfg, canonicalTag, projectDir, canvas);

        rebindTiles();

        canvas.refreshProjectBoundServices();
    }

    private void refreshStudioTilesetProfileRegistry(FileHandle projectDir) {
        if (assetMetaDatabase == null && projectDir != null) {
            FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
            if (assetsFile.exists()) {
                assetMetaDatabase = AssetMetaDatabase.load(assetsFile);
            }
        }
        canvas.refreshTilesetProfileRegistry(assetMetaDatabase);
    }

    private void rebindTiles() {
        World world = canvas.getEcsWorld();
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
        var particleSystem = canvas.getEcsWorld().getSystem(RenderParticleSyncSystem.class);
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

        String current = cfg.getCurrentSceneName();
        if (sceneName.equals(current)) return;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        try {
            cfg.setCurrentSceneByName(sceneName);
            saveProjectFile(cfg);
            loadScene(cfg, sceneName, projectDir);
            sceneMetaBridge.pushCurrentSceneMetaToUI();
            StudioLog.info("Scene opened: " + sceneName);
        } catch (RuntimeException ex) {
            throw failAfterRollback(
                    "Failed to switch scene to '" + sceneName + "'.",
                    ex,
                    () -> rollbackSceneSwitch(cfg, current, projectDir)
            );
        }
    }

    public void createNewScene(
            String sceneName,
            int tileWidth,
            int tileHeight,
            String projection
    ) {
        clipboardService.clear();
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            cfg = new ProjectConfig();
            cfg.projectFileName = "Untitled";
            ProjectConfig.setInstance(cfg);
        }

        if (cfg.getCurrentSceneName() != null) {
            saveCurrentSceneOnly(cfg);
        }

        String previousSceneName = cfg.getCurrentSceneName();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        String createdSceneFileName = null;
        try {
            // fileName is managed by nextSceneIndex in ProjectConfig
            cfg.createSceneMeta(sceneName);
            SceneMeta meta = cfg.getSceneMeta(sceneName);
            createdSceneFileName = (meta != null) ? meta.getFile() : null;

            if ("None".equals(projection)) {
                meta.tiledEnabled = false;
            } else {
                meta.tiledEnabled = true;
                meta.tileWidth = tileWidth;
                meta.tileHeight = tileHeight;
                meta.tiledProjection =
                        "Isometric".equals(projection)
                                ? SceneMetaRuntime.TiledProjection.ISO
                                : SceneMetaRuntime.TiledProjection.ORTHO;
            }

            FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
            atlasesDir.mkdirs();

            saveProjectFile(cfg);

            clearWorldAndRenderState();
            bindSceneIdentityAuthorities(meta);
            int indexL = app.getCanvas().getLayerService().addLayerTop("Main layer");
            int layerEntityId = app.getCanvas().getLayerService().getLayerEntity(indexL);
            app.getCanvas().getSelectionService().setActivelayerId(layerEntityId);

            saveCurrentSceneOnly(cfg);
            loadScene(cfg, sceneName, projectDir);
            assertCurrentSceneMetadataIntegrity(cfg, sceneName, "createNewScene");
            sceneMetaBridge.pushCurrentSceneMetaToUI();

            Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + " - " + sceneName + ")");
            StudioLog.info("Scene created: " + sceneName);
        } catch (RuntimeException ex) {
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

    public TmxSceneImportResult importTmxAsNewScene(TmxSceneImportRequest request) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) {
            throw new IllegalStateException("No project is loaded.");
        }
        String previousSceneName = cfg.getCurrentSceneName();
        if (cfg.getCurrentSceneName() != null) {
            saveCurrentSceneOnly(cfg);
        }

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

    private void activateImportedTmxScene(ProjectConfig cfg,
                                          FileHandle projectDir,
                                          TmxSceneImportResult result) {
        loadScene(cfg, result.sceneName(), projectDir);
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
                    () -> rollbackSceneDelete(
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
                    )
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

        World world = canvas.getEcsWorld();
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

        World world = canvas.getEcsWorld();
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

            for (TileChunk chunk : tiled.data.getChunks()) {
                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int logicalId = chunk.get(lx, ly);
                        if (logicalId != tileAnimationId) {
                            continue;
                        }

                        int localIndex = chunk.localIndexFor(lx, ly);
                        chunk.markLocalDirty(localIndex);

                        if (lookup != null) {
                            TileAnimationStateSupport.syncCell(chunk, localIndex, logicalId, lookup);
                        }
                    }
                }
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
        canvas.refreshTilesetProfileRegistry(assetMetaDatabase);
        refreshAssetsPanel();
        StudioLog.info("Assets imported: " + importedCount);
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
        };
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
        TilesetImportResult result = new TilesetAssetImportService(assetMetaDatabase)
                .importAtlas(tilesetAtlasImportRequestForManualImport(item, ctx.tilesRoot));
        return result.importedCount();
    }

    private int importTsxTilesetAsset(AssetImportContext ctx, ImportDialog.ImportItem item) {
        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(item.file);
        String tilesetName = descriptor.name() != null && !descriptor.name().isBlank()
                ? descriptor.name()
                : baseName(item.file.name());
        TilesetImportResult result = new TilesetAssetImportService(assetMetaDatabase)
                .importAtlas(new TilesetAtlasImportRequest(
                        descriptor.imageFile(),
                        ctx.tilesRoot,
                        descriptor.tileWidth(),
                        descriptor.tileHeight(),
                        descriptor.spacing(),
                        descriptor.margin(),
                        tilesetName
                ));
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
        return result.importedCount();
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

    public int importTilesetDirectory(FileHandle directory, TilesetProfileImportSettings profileSettings) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return 0;

        AssetImportContext ctx = prepareAssetImportContext(cfg);
        int imported = importTilesetFolderAsset(ctx, directory, profileSettings);

        assetMetaDatabase.save(ctx.projectDir.child(StudioFs.FILE_ASSETS_JSON));
        canvas.refreshTilesetProfileRegistry(assetMetaDatabase);
        refreshAssetsPanel();

        if (imported > 0) {
            StudioLog.info("Tileset directory imported: " + directory.name());
        }

        return imported;
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
                canvas.getEcsWorld(),
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

    private int importTilesetFolderAsset(AssetImportContext ctx,
                                         FileHandle directory,
                                         TilesetProfileImportSettings profileSettings) {
        TilesetImportResult result = new TilesetAssetImportService(assetMetaDatabase)
                .importDirectory(new TilesetDirectoryImportRequest(directory, ctx.tilesRoot, profileSettings));
        return result.importedCount();
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

        canvas.getPhysicsSelectionReconciler().clearSceneContext();
        World world = canvas.getEcsWorld();

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
        canvas.getSelectionService().clearSelection();
        canvas.getLayerService().reset();

        // Nettoyage caches
        StandaloneTextureCache.clear(true);
        canvas.invalidateStandaloneAssetVisuals();

        // Reset historique
        historyManager.clear();
        historyManager.historyIds().clear();
        clearCurrentSceneSaveRequired();
    }

    private void bindSceneIdentityAuthorities(SceneMeta meta) {
        canvas.getIdentityRegistry().bind(canvas.getEcsWorld(), meta);
        canvas.getPhysicsService().setPhysicsShapeIdState(meta);
    }

    private void flushWorldForSerialization() {
        if (canvas == null) return;
        canvas.getEcsWorld().process();
    }

    // ---------------------------------------------------------------------
    // UI / CALLBACKS / CONSISTENCY CHECKS
    // ---------------------------------------------------------------------

    private void registerEditorOpsCallbacks() {
        if (canvas == null) return;
        canvas.setAssetsChangedListener(this::onSceneAtlasChanged);
    }

    private void onSceneAtlasChanged(String sceneTag) {
        if (sceneTag == null || sceneTag.isBlank()) return;

        Gdx.app.log("SceneManager", "onSceneAtlasChanged: " + sceneTag);

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;

        String canonicalTag = cfg.canonicalSceneTag(sceneTag);
        if (canonicalTag == null || canonicalTag.isBlank()) {
            canonicalTag = sceneTag;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        reloadAtlasAndRebind(cfg, canonicalTag, projectDir);
        refreshAssetsPanel();
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
                                     boolean hasSceneToSave) {
    }

    private record AssetImportContext(ProjectConfig cfg, FileHandle projectDir, FileHandle imagesRoot,
                                      FileHandle tilesRoot, FileHandle animRoot, String animRootRel,
                                      FileHandle effectsRoot) {
    }

    private record ImageSize(int width, int height) {
    }

}


