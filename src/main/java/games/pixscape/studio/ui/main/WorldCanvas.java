package games.pixscape.studio.ui.main;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.WorldBootstrapResult;
import games.pixscape.runtime.loading.WorldConfigFactory;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderContext;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.tiled.PackedTileValue;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledSoaAllocator;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.batch.BatchFactoryStudio;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.debug.PreviewRuntimeProfiler;
import games.pixscape.studio.debug.StudioFrameProfiler;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.TiledBrushCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ops.EditorOps;
import games.pixscape.studio.ops.EditorOpsImpl;
import games.pixscape.studio.service.*;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.PolygonDrawSession;
import games.pixscape.studio.service.prefab.PrefabAssetService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.service.tiled.*;
import games.pixscape.studio.system.*;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragCursors;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.contextmenu.StudioContextMenu;
import games.pixscape.studio.ui.widget.TextInputWidget;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static games.pixscape.runtime.loading.WorldConfigFactory.DEFAULT_TILED_BUDGET;

public class WorldCanvas {

    private final StudioApplicationAdapter app;
    private MetricsBatch metricsBatch;
    private GpuSnapshotManager gpuSnapshotManager;
    private World world;
    private final Stage gridStage;
    private final OrthographicCamera camera;
    private final OrthographicCamera box2dCamera;
    // SOA
    private RenderStateSOA renderState;
    private LayerStateSOA layerState;

    // Drawer
    private final ShapeDrawer drawer;
    private final StudioDrawContext worldDrawCtx;

    // Services
    private final StudioFrameProfiler frameProfiler;
    private final FrameSystemProfiler systemProfiler;
    private final PreviewRuntimeProfiler previewRuntimeProfiler;
    private SelectionService selectionService;
    private ZOrderRuntimeService zOrderRuntimeService;
    private LayerService layerService;
    private PhysicsService physicsService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final AtlasStudioService atlasStudioService;
    private final ShaderService shaderService;
    private AlignService alignService;
    private ClipboardService clipboardService;
    private final PolygonDrawSession polygonDrawSession;
    private String defaultShaderName;
    private PrefabAssetService prefabAssetService;
    private EntityGraphInstantiationService entityGraphInstantiationService;
    private KeyboardNudgeService keyboardNudgeService;
    private IdentityRegistry prefabIdentityRegistry;
    private String cachedPrefabPhysicsPath;
    private boolean cachedPrefabContainsPhysics;

    // tiled
    private TiledPaintService tiledPaintService;
    private TiledToolService tiledToolService;
    private TiledAllocatorService tiledAllocatorService;
    private TiledSoaAllocator tiledAllocator;
    private TiledFallbackSystem tiledFallbackSystem;
    private StudioParticleFallbackSystem studioParticleFallbackSystem;
    private TiledGhostPreviewSystem tiledGhostPreviewSystem;
    private TiledBrushSession currentBrushSession;
    private TiledPreviewService tiledPreviewService;
    private RuntimeTilesetProfiles studioTilesetProfiles;
    private final TileAnimationRegistry tileAnimationRegistry;
    // tiled rect
    private boolean rectActive = false;
    private int rectStartGX;
    private int rectStartGY;
    private boolean brushHasLastCell = false;
    private int brushLastGX;
    private int brushLastGY;

    // Undo redo
    private final HistoryManager historyManager;
    private static final int UNDOREDO_MAX_SIZE = 1024;

    // Operations
    private EditorOps editorOps;
    private EditorOps.AssetsChangedListener assetsChangedListener;

    // Mouse handling
    private StudioContextMenu contextMenu;
    private final InputState inputState;
    private final CoordSpaces coordSpaces;
    private final Matrix4 tmp = new Matrix4();
    private boolean pointerInside = false;
    private boolean panning = false;
    private final Vector2 lastMouse = new Vector2();
    private final Vector2 delta = new Vector2();
    private final Vector2 tmpWorldPos = new Vector2();
    private final Vector2 tmpPrefabOrigin = new Vector2();
    private final Vector2 tmpBeforeScroll = new Vector2();
    private final Vector2 tmpAfterScroll = new Vector2();
    private final Vector2 tmpUiStageCoords = new Vector2();
    private Cursor currentCursor;
    private boolean currentCursorForbidden;


    // Box2D (lazy init + enable/disable system)
    private Box2dWorldService box2dWorldService;
    private Box2dSyncSystem box2dSyncSystem;
    private GizmoSystem gizmoSystem;
    private final GridActor gridActor;
    private boolean lastPhysicsEnabled = false;
    private float lastPpm = Float.NaN;
    private float lastGx = Float.NaN;
    private float lastGy = Float.NaN;
    private boolean lastDoSleep = true;
    private boolean physicsEnabled;


    public WorldCanvas(StudioApplicationAdapter app, ShapeDrawer sharedDrawer) {
        this.app = app;
        this.drawer = sharedDrawer;
        camera = new OrthographicCamera();
        box2dCamera = new OrthographicCamera();
        gridStage = new Stage(new ScreenViewport(camera), app.getUiStage().getBatch());
        worldDrawCtx = new StudioDrawContext((SpriteBatch) app.getUiStage().getBatch(), drawer, camera);
        coordSpaces = new CoordSpaces(camera, gridStage.getViewport(), gridStage, app.getUiStage());
        gridActor = new GridActor(worldDrawCtx);
        gridActor.setFreeMode();
        gridStage.addActor(gridActor);

        historyManager = new HistoryManager(UNDOREDO_MAX_SIZE);
        inputState = new InputState();
        frameProfiler = StudioFrameProfiler.fromSystemProperties();
        systemProfiler = frameProfiler.createSystemProfiler();
        frameProfiler.setSystemProfiler(systemProfiler);
        previewRuntimeProfiler = PreviewRuntimeProfiler.enabledFromSystemProperties(frameProfiler)
                ? PreviewRuntimeProfiler.fromSystemProperties(frameProfiler)
                : null;
        atlasStudioService = new AtlasStudioService(this);
        physicsSelectionService = new PhysicsSelectionService();
        spatialBlockSelectionService = new SpatialBlockSelectionService();
        spatialTileSelectionService = new SpatialTileSelectionService();
        shaderService = new ShaderService(app);
        polygonDrawSession = new PolygonDrawSession();
        tileAnimationRegistry = new TileAnimationRegistry();

        bindParticleControlChanges();
        addGridStageInputListener();
        handleShortcuts();
        bindPhysicsDebugEvents();
        bindEditorModeChanged();

        createWorld();
        centerCamera();
    }

    private void createWorld() {
        ProjectConfig cfg = ProjectConfig.getInstance();

        renderState = new RenderStateSOA();
        layerState = new LayerStateSOA();
        DrawList drawList = new DrawList();

        GLCaps caps = GLCaps.detect();

        BatchFactoryStudio.Result r = BatchFactoryStudio.create(atlasStudioService, caps);
        metricsBatch = r.batch;
        defaultShaderName = r.defaultShaderName;
        gpuSnapshotManager = new GpuSnapshotManager(atlasStudioService, metricsBatch);
        markSnapshotDirtyIfSceneLoaded("world-created");

        RenderStats stats = new RenderStats();
        RenderStatsSink statsSink = new RenderStatsSink(0.5f);

        new RenderContext(renderState, layerState, drawList, metricsBatch, caps);

        layerState.setCapacity(32);
        SceneMeta sceneMeta = cfg != null ? cfg.getCurrentSceneMeta() : null;

        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);


        LightIconOverlaySystem lightIconOverlaySystem =
                new LightIconOverlaySystem(worldDrawCtx, camera, renderState);

        tiledPreviewService = new TiledPreviewService();

        // NEW: inject PhysicsSelectionService
        gizmoSystem = new GizmoSystem(
                worldDrawCtx,
                inputState,
                coordSpaces,
                renderState,
                physicsSelectionService,
                spatialBlockSelectionService,
                spatialTileSelectionService,
                tiledPreviewService,
                polygonDrawSession
        );

        PickingSystem pickingSystem = new PickingSystem(
                camera,
                coordSpaces,
                inputState,
                historyManager,
                historyManager.historyIds(),
                app.getUiStage(),
                renderState,
                physicsSelectionService,
                spatialBlockSelectionService,
                spatialTileSelectionService,
                polygonDrawSession
        );

        FileHandle effectsRoot = resolveEffectsRoot(cfg);
        FileHandle particleImagesRoot = resolveImagesRoot(cfg);

        final AssetMetaDatabase assetMetaDatabaseForFallback = loadAssetMetaDatabaseIfAvailable(cfg);
        studioTilesetProfiles = StudioTilesetProfileResolver.buildRuntimeProfiles(assetMetaDatabaseForFallback);

        WorldBootstrapResult bootstrap =
                WorldConfigFactory.buildWorld(
                        camera,
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        defaultShaderIdx,
                        atlasStudioService,
                        effectsRoot,
                        () -> new StudioRenderSubmitSystem(
                                renderState,
                                layerState,
                                drawList,
                                camera,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        sceneMeta,
                        DEFAULT_TILED_BUDGET,
                        tileAnimationRegistry,
                        studioTilesetProfiles,
                        systemProfiler,

                        // pre-render: systems that write into RenderStateSOA
                        pre_render -> {
                            assert assetMetaDatabaseForFallback != null;
                            pre_render.with(
                                    profiled(new AnimationFallbackSystem(renderState, atlasStudioService)),
                                    profiled(tiledFallbackSystem = new TiledFallbackSystem(
                                            renderState,
                                            atlasStudioService,
                                            assetMetaDatabaseForFallback::findById,
                                            tileAnimationRegistry
                                    )),
                                    profiled(studioParticleFallbackSystem = new StudioParticleFallbackSystem(
                                            renderState,
                                            camera,
                                            atlasStudioService,
                                            effectsRoot,
                                            particleImagesRoot,
                                            defaultShaderIdx
                                    ))
                            );
                        },

                        // post-render: UI/editor overlays and interaction systems
                        post_render -> post_render.with(
                                profiled(tiledGhostPreviewSystem = new TiledGhostPreviewSystem(
                                        worldDrawCtx,
                                        atlasStudioService,
                                        tiledPreviewService,
                                        assetMetaDatabaseForFallback::findById,
                                        TextureRegistry::getByHandle,
                                        tileAnimationRegistry
                                )),
                                profiled(new UiRefreshDispatchSystem()),
                                lightIconOverlaySystem,
                                pickingSystem,
                                gizmoSystem
                        )
                );

        world = bootstrap.getWorld();

        if (studioParticleFallbackSystem != null && bootstrap != null) {
            studioParticleFallbackSystem.setVfxRange(
                    bootstrap.getVfxStart(),
                    bootstrap.getVfxEnd()
            );
        }

        int tiledStart = bootstrap.getTiledStart();
        int tiledEnd = bootstrap.getTiledEnd();

        tiledAllocator = new TiledSoaAllocator(tiledStart, tiledEnd);
        tiledAllocatorService = new TiledAllocatorService(tiledAllocator, renderState);

        tiledPaintService = new TiledPaintService();
        tiledToolService = new TiledToolService();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }

        // Services
        layerService = new LayerService(world, tiledAllocatorService, historyManager.historyIds());
        selectionService = new SelectionService(world, layerService);
        keyboardNudgeService = new KeyboardNudgeService(world, historyManager, selectionService);
        gizmoSystem.setSelectionService(selectionService);
        physicsService = new PhysicsService(world, box2dWorldService);
        alignService = new AlignService(this);
        clipboardService = new ClipboardService(this);

        prefabIdentityRegistry = new IdentityRegistry();
        prefabIdentityRegistry.bind(world);
        prefabIdentityRegistry.rebuild();

        prefabAssetService = new PrefabAssetService(world);
        entityGraphInstantiationService = new EntityGraphInstantiationService(
                world,
                historyManager,
                prefabIdentityRegistry
        );

        // Wiring
        pickingSystem.setSelectionService(selectionService);
        pickingSystem.setLayerService(layerService);
        pickingSystem.setPhysicsService(physicsService);

        lightIconOverlaySystem.setLayerService(layerService);
        lightIconOverlaySystem.setSelectionService(selectionService);

        gizmoSystem.setLayerService(layerService);
        gizmoSystem.setPhysicsService(physicsService);

        zOrderRuntimeService = new ZOrderRuntimeService(world);

        editorOps = new EditorOpsImpl(this);

        contextMenu = new StudioContextMenu(this, app.getUiStage());
        app.getUiStage().getRoot().addListener(contextMenu);

        if (assetsChangedListener != null) {
            editorOps.setAssetsChangedListener(assetsChangedListener);
        }

        if (sceneMeta != null && sceneMeta.physicsEnabled) {
            ensureBox2dFromMeta(sceneMeta);
        }
    }

    private <T extends BaseSystem> T profiled(T system) {
        if (system instanceof ProfiledSystem) {
            ((ProfiledSystem) system).setSystemProfiler(systemProfiler);
        }
        return system;
    }

    public void refreshProjectBoundServices() {
        ProjectConfig cfg = ProjectConfig.getInstance();

        FileHandle effectsRoot = resolveEffectsRoot(cfg);
        FileHandle imagesRoot = resolveImagesRoot(cfg);

        RenderParticleSyncSystem runtimeParticleSystem =
                world.getSystem(RenderParticleSyncSystem.class);

        if (runtimeParticleSystem != null) {
            runtimeParticleSystem.setEffectsRoot(effectsRoot);
            runtimeParticleSystem.invalidateAllEffects();
        }

        if (studioParticleFallbackSystem != null) {
            studioParticleFallbackSystem.setEffectsRoot(effectsRoot);
            studioParticleFallbackSystem.setImagesRoot(imagesRoot);
            studioParticleFallbackSystem.invalidateAll();
        }

        if (gpuSnapshotManager != null) {
            markSnapshotDirtyIfSceneLoaded("project-bound-services-refreshed");
        }
    }

    private AssetMetaDatabase loadAssetMetaDatabaseIfAvailable(ProjectConfig cfg) {
        AssetMetaDatabase empty = new AssetMetaDatabase();

        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            return empty;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle metaFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);

        if (!metaFile.exists()) {
            return empty;
        }

        return AssetMetaDatabase.load(metaFile);
    }

    public void bindAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        if (tiledFallbackSystem != null && assetMetaLookup != null) {
            tiledFallbackSystem.setAssetMetaLookup(assetMetaLookup);
        }
        if (tiledGhostPreviewSystem != null && assetMetaLookup != null) {
            tiledGhostPreviewSystem.setAssetMetaLookup(assetMetaLookup);
        }
        refreshTilesetProfileRegistry();
    }

    public void refreshTilesetProfileRegistry() {
        refreshTilesetProfileRegistry(loadAssetMetaDatabaseIfAvailable(ProjectConfig.getInstance()));
    }

    public void refreshTilesetProfileRegistry(AssetMetaDatabase assetMetaDatabase) {
        if (studioTilesetProfiles == null) {
            studioTilesetProfiles = RuntimeTilesetProfiles.empty();
        }
        StudioTilesetProfileResolver.reloadRuntimeProfiles(
                studioTilesetProfiles,
                assetMetaDatabase
        );
    }

    private void bindParticleControlChanges() {
        EventFlow.i().subscribe(EventFlow.ParticleControlRequested.class, this::onParticleControl);
    }

    private void onParticleControl(EventFlow.ParticleControlRequested evt) {
        if (world == null) return;
        int e = evt.entityId();
        if (!world.getEntityManager().isActive(e)) return;

        ComponentMapper<ParticleEmitterComponent> mEmitter =
                world.getMapper(ParticleEmitterComponent.class);
        ParticleEmitterComponent comp = mEmitter.getSafe(e, null);
        if (comp == null) return;

        switch (evt.particleControlType()) {
            case PLAY -> {
                comp.playRequested = true;
                comp.paused = false;
            }
            case PAUSE -> {
                comp.paused = true;
            }
            case RESTART -> {
                comp.restartRequested = true;
                comp.paused = false;
            }
        }
    }

    private FileHandle resolveEffectsRoot(ProjectConfig cfg) {
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            return null;
        }
        return StudioFs.requireStudioProjectDir(cfg).child(StudioFs.DIR_ORIG_EFFECTS);
    }

    private FileHandle resolveImagesRoot(ProjectConfig cfg) {
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) {
            return null;
        }
        return StudioFs.requireStudioProjectDir(cfg).child(StudioFs.DIR_ORIG_IMAGES);
    }

    public void invalidateStudioParticleFallbacks() {
        if (studioParticleFallbackSystem != null) {
            studioParticleFallbackSystem.invalidateAll();
        }
    }

    private void bindEditorModeChanged() {

        EventFlow.i().subscribe(EventFlow.EditorModeChanged.class, ev -> {

            boolean tile = ev.mode() == EventFlow.EditorMode.TILE;

            gizmoSystem.setEntityGizmoEnabled(!tile);
            if (!tile) {
                spatialTileSelectionService.clear();
            }

            ProjectConfig cfg = ProjectConfig.getInstance();
            if (cfg == null) return;

            SceneMeta meta = cfg.getCurrentSceneMeta();
            if (meta == null) return;

            if (tile) {
                configureTileMode(meta);
            } else {
                configureEntityMode();
            }
        });
    }

    private void configureTileMode(SceneMeta meta) {
        gridActor.setTiledMode(meta.tiledProjection, meta.tileWidth, meta.tileHeight);
        int layerEntity = selectionService.getActivelayerId();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntity, null);

        if (tiled != null && tiled.data != null) {
            TiledMapLayerData map = tiled.data;

            if (meta.tiledProjection == games.pixscape.runtime.loading.SceneMetaRuntime.TiledProjection.ORTHO) {
                float minX = map.originX;
                float minY = map.originY;
                float maxX = minX + map.mapWidth * map.tileWidth;
                float maxY = minY + map.mapHeight * map.tileHeight;

                gridActor.setMapBounds(minX, minY, maxX, maxY);
            } else {
                gridActor.clearBounds();
            }

            gridActor.bindTo(map);
        }
        gizmoSystem.enableTiledOverlay(meta.tileWidth, meta.tileHeight);
    }

    private void configureEntityMode() {
        gridActor.setFreeMode();
        gridActor.clearBounds();
        gridActor.unbind();
        gizmoSystem.disableTiledOverlay();
    }

    public void setAssetsChangedListener(EditorOps.AssetsChangedListener listener) {
        this.assetsChangedListener = listener;
        if (editorOps != null) {
            editorOps.setAssetsChangedListener(listener);
        }
    }


    // ---------------------------------------------------------------------
    // DnD from AssetsPanel / SceneAtlasPanel
    // ---------------------------------------------------------------------

    private void updateDndDrop() {
        final boolean active = DragContext.get().active();
        if (!active && currentCursor == null) return;

        final int mx = Gdx.input.getX();
        final int my = Gdx.input.getY();

        DragPayload peek = DragContext.get().peek();
        if (peek == null) {
            clearCursorIfAny();
            return;
        }

        boolean acceptHere = switch (peek.type) {
            case "particle", "anim-sheet", "atlas-region", "image-file", "prefab", "tile-asset", "tiled-animation" -> true;
            default -> false;
        };

        final boolean inside = (mx >= 0 && mx < Gdx.graphics.getWidth()
                && my >= 0 && my < Gdx.graphics.getHeight());
        final boolean overCanvas = inside && isDropOverWorldCanvas(mx, my);
        DropAllowedResult dropAllowed = (overCanvas && acceptHere)
                ? validateAssetDropOnCurrentLayer(peek)
                : DropAllowedResult.forbidden();

        // --- Ghost cursor ---
        if (active && overCanvas && acceptHere) {
            setDndCursor(peek, !dropAllowed.allowed);
        } else if (!active || !overCanvas || !acceptHere) {
            clearCursorIfAny();
        }

        // --- Consume the drop only if accepted here ---
        DragPayload p = acceptHere ? DragContext.get().consumeIfReleasedInside(overCanvas) : null;
        if (p != null) {
            if (!isDropOverWorldCanvas(mx, my)) {
                return;
            }

            if (!validateAssetDropOnCurrentLayer(p).allowed) {
                cleanupDndPayload(p);
                return;
            }

            switch (p.type) {
                case "particle" -> handleEffectDrop(p, mx, my);
                case "anim-sheet" -> handleAnimSheetDrop(p, mx, my);
                case "atlas-region" -> handleImageDrop(p, mx, my);
                case "image-file" -> handleImageFileDrop(p, mx, my);
                case "prefab" -> handlePrefabDrop(p, mx, my);
            }
            cleanupDndPayload(p);
        }
    }

    public void cancelDndReleaseIfOutsideCanvas() {
        if (!DragContext.get().releasePending()) {
            return;
        }

        final int mx = Gdx.input.getX();
        final int my = Gdx.input.getY();
        final boolean inside = (mx >= 0 && mx < Gdx.graphics.getWidth()
                && my >= 0 && my < Gdx.graphics.getHeight());

        if (inside && isDropOverWorldCanvas(mx, my)) {
            return;
        }

        DragPayload payload = DragContext.get().consumeReleased();
        cleanupDndPayload(payload);
    }

    private boolean isDropOverWorldCanvas(int screenX, int screenY) {
        if (app == null || app.getUiStage() == null) {
            return true;
        }

        tmpUiStageCoords.set(screenX, screenY);
        app.getUiStage().screenToStageCoordinates(tmpUiStageCoords);

        Actor uiHit = app.getUiStage().hit(tmpUiStageCoords.x, tmpUiStageCoords.y, true);
        return uiHit == null;
    }

    private DropAllowedResult validateAssetDropOnCurrentLayer(DragPayload p) {
        if (p == null || p.type == null) return DropAllowedResult.forbidden();
        if (selectionService == null || layerService == null) return DropAllowedResult.forbidden();

        int activeLayerId = selectionService.getActivelayerId();
        if (activeLayerId < 0) return DropAllowedResult.forbidden();

        int layerType = layerService.getLayerTypeByEntity(activeLayerId);

        boolean tilePayload = switch (p.type) {
            case "tile-asset", "tiled-animation" -> true;
            default -> false;
        };

        return switch (layerType) {
            case LayerComponent.TYPE_TILED -> tilePayload
                    ? DropAllowedResult.allowed()
                    : DropAllowedResult.forbidden();
            case LayerComponent.TYPE_LIGHT -> DropAllowedResult.forbidden();
            case LayerComponent.TYPE_PHYSICS -> DropAllowedResult.allowed();
            default -> {
                if (tilePayload) {
                    yield DropAllowedResult.forbidden();
                }
                if ("prefab".equals(p.type) && payloadPrefabContainsPhysics(p)) {
                    yield DropAllowedResult.forbidden();
                }
                yield DropAllowedResult.allowed();
            }
        };
    }

    private void cleanupDndPayload(DragPayload p) {
        if (p != null && p.ghostPixmap != null) {
            p.ghostPixmap.dispose();
            p.ghostPixmap = null;
        }

        if (currentCursor != null) {
            currentCursor.dispose();
            currentCursor = null;
        }
        currentCursorForbidden = false;

        Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
    }

    private boolean payloadPrefabContainsPhysics(DragPayload p) {
        if (p == null || p.path == null || p.path.trim().isEmpty()) {
            return false;
        }

        if (p.path.equals(cachedPrefabPhysicsPath)) {
            return cachedPrefabContainsPhysics;
        }

        cachedPrefabPhysicsPath = p.path;
        cachedPrefabContainsPhysics = false;

        FileHandle prefabFile = Gdx.files.absolute(p.path);
        if (!prefabFile.exists()) {
            return false;
        }

        try {
            EntityGraph graph = prefabAssetService.loadPrefab(prefabFile);
            cachedPrefabContainsPhysics = prefabContainsPhysics(graph);
        } catch (RuntimeException ex) {
            Gdx.app.error("PrefabDrop", "Failed to inspect prefab physics: " + p.path, ex);
        }

        return cachedPrefabContainsPhysics;
    }

    private void setDndCursor(DragPayload payload, boolean forbidden) {
        if (currentCursor != null && currentCursorForbidden == forbidden) {
            return;
        }

        clearCursorIfAny();

        currentCursor = forbidden
                ? DragCursors.makeForbiddenCursor()
                : DragCursors.makeGhostCursor(payload.ghostPixmap, 6, 6);

        currentCursorForbidden = forbidden;

        if (currentCursor != null) {
            Gdx.graphics.setCursor(currentCursor);
        }
    }

    private void ensureGhostCursorFromPayload() {
        if (currentCursor != null) currentCursor.dispose();
        var p = DragContext.get().peek();
        if (p != null && p.ghostPixmap != null) {
            currentCursor = DragCursors.makeGhostCursor(p.ghostPixmap, 6, 6);
            currentCursorForbidden = false;
            if (currentCursor != null) Gdx.graphics.setCursor(currentCursor);
        }
    }

    private void clearCursorIfAny() {
        boolean hadCursor = currentCursor != null;
        if (currentCursor != null) {
            currentCursor.dispose();
            currentCursor = null;
        }
        currentCursorForbidden = false;
        if (hadCursor) {
            Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
        }
    }

    private static final class DropAllowedResult {
        private final boolean allowed;

        private DropAllowedResult(boolean allowed) {
            this.allowed = allowed;
        }

        private static DropAllowedResult allowed() {
            return new DropAllowedResult(true);
        }

        private static DropAllowedResult forbidden() {
            return new DropAllowedResult(false);
        }
    }

    private void handleShortcuts() {
        app.getUiStage().addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {

                // Detect whether the user is editing text (custom widgets included)
                Actor focus = event.getStage() != null ? event.getStage().getKeyboardFocus()
                        : app.getUiStage().getKeyboardFocus();
                boolean typing = isTextEditingFocus(focus);

                boolean ctrl = inputState.isCtrl();

                // --- Undo / Redo : ALWAYS active, even while typing ---
                if (ctrl) {
                    if ((keycode == Input.Keys.Z) || keycode == Input.Keys.W) {
                        selectionService.clearSelection();
                        physicsSelectionService.clearSelectionOnly();
                        spatialBlockSelectionService.clearSelectionOnly();
                        spatialTileSelectionService.clear();
                        historyManager.undo();
                        return true;
                    }

                    if (keycode == Input.Keys.Y) {
                        selectionService.clearSelection();
                        physicsSelectionService.clearSelectionOnly();
                        spatialBlockSelectionService.clearSelectionOnly();
                        spatialTileSelectionService.clear();
                        historyManager.redo();
                        return true;
                    }
                    if (!typing && keycode == Input.Keys.C) {
                        clipboardService.copySelection();
                        return true;
                    }

                    if (!typing && keycode == Input.Keys.X) {
                        clipboardService.cutSelection();
                        return true;
                    }

                    if (!typing && keycode == Input.Keys.V) {
                        clipboardService.paste();
                        return true;
                    }
                }

                if (!typing && !ctrl && KeyboardNudgeService.isArrowKey(keycode)) {
                    return keyboardNudgeService != null && keyboardNudgeService.keyDown(keycode);
                }

                // --- Delete : only when not editing a text field ---
                if (!typing) {
                    if (keycode == Input.Keys.FORWARD_DEL || keycode == Input.Keys.BACKSPACE) {

                        // 1) priority to selected spatial block
                        if (spatialBlockSelectionService.hasSelectedBlock()) {
                            editorOps.deleteSelectedSpatialBlock();
                            return true;
                        }

                        // 2) priority to selected shape
                        int focusedBodyEid = physicsSelectionService.getFocusedBodyEid();
                        long selectedFixtureId = physicsSelectionService.getSelectedFixtureId();
                        if (focusedBodyEid >= 0 && selectedFixtureId > 0) {
                            editorOps.deleteFixture(focusedBodyEid, selectedFixtureId);
                            return true;
                        }

                        // 3) priority to selected joint
                        int selectedJointEid = physicsSelectionService.getSelectedJointEid();
                        if (selectedJointEid >= 0) {
                            editorOps.deleteJoint(selectedJointEid);
                            return true;
                        }

                        // 4) otherwise entity deletion
                        IntArray sel = selectionService.getSelectionSnapshot();
                        if (sel.size > 0) {
                            editorOps.deleteEntities(sel);
                            selectionService.clearSelection();
                            return true;
                        }
                    }
                }

                return false;
            }

            @Override
            public boolean keyUp(InputEvent event, int keycode) {
                if (!KeyboardNudgeService.isArrowKey(keycode)) {
                    return false;
                }

                Actor focus = event.getStage() != null ? event.getStage().getKeyboardFocus()
                        : app.getUiStage().getKeyboardFocus();
                if ((keyboardNudgeService == null || !keyboardNudgeService.isActive()) && isTextEditingFocus(focus)) {
                    return false;
                }

                return keyboardNudgeService != null && keyboardNudgeService.keyUp(keycode);
            }
        });
    }

    private static boolean isTextEditingFocus(Actor focus) {
        if (focus == null) return false;

        for (Actor a = focus; a != null; a = a.getParent()) {
            if (a instanceof TextInputWidget) return true;
            if (a instanceof TextField) return true;
            if (a instanceof VisTextField) return true;
        }
        return false;
    }

    private void addGridStageInputListener() {

        gridStage.addListener(new InputListener() {

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                pointerInside = true;
                if (DragContext.get().active())
                    ensureGhostCursorFromPayload();
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                pointerInside = false;
                if (!DragContext.get().active())
                    clearCursorIfAny();
            }

            @Override
            public boolean touchDown(InputEvent event,
                                     float x,
                                     float y,
                                     int pointer,
                                     int button) {

                lastMouse.set(x, y);

                if (handlePan(button)) {
                    return true;
                }

                SceneMeta currentMeta = ProjectConfig.getInstance().getCurrentSceneMeta();
                if (currentMeta == null) {
                    return false;
                }
                boolean isTileMode = currentMeta.editorMode == SceneMeta.EditorMode.TILE;

                if (!isTileMode || button != Input.Buttons.LEFT) {
                    return false;
                }

                if (!isTiledToolInputEnabled()) {
                    return false;
                }

                if (handleRectDown()) {
                    return true;
                }

                if (handleBrushDown()) {
                    return true;
                }

                int layerEntityId = selectionService.getActivelayerId();
                if (layerEntityId == -1) {
                    return false;
                }

                if (tiledToolService.is(TiledToolService.Mode.FILL)) {
                    performFill(layerEntityId);
                    return true;
                }

                return false;
            }

            @Override
            public void touchDragged(InputEvent event,
                                     float x,
                                     float y,
                                     int pointer) {

                delta.set(x, y).sub(lastMouse).scl(-1);

                if (panning) {
                    camera.translate(delta);
                    camera.update();
                    box2DcameraUpdate();
                    app.getBottomBar().setPan(
                            camera.position.x,
                            camera.position.y
                    );
                    return;
                }

                handleBrushDrag();
                handleRectDrag();
            }

            @Override
            public void touchUp(InputEvent event,
                                float x,
                                float y,
                                int pointer,
                                int button) {

                if (panning) {
                    panning = false;
                }

                if (currentBrushSession != null) {
                    if (!currentBrushSession.isEmpty()) {
                        long historyId =
                                historyManager.historyIds().ensureForEntity(
                                        currentBrushSession.getLayerEntityId()
                                );
                        historyManager.execute(
                                new TiledBrushCommand(
                                        world,
                                        app.getSceneService(),
                                        historyManager.historyIds(),
                                        historyId,
                                        currentBrushSession.getPreviousValues(),
                                        currentBrushSession.getNewValues()
                                )
                        );
                    }
                    currentBrushSession = null;
                    brushHasLastCell = false;
                }
                if (isTiledToolInputEnabled() && tiledToolService.is(TiledToolService.Mode.ERASE)) {
                    gizmoSystem.refreshOverlayMouse();
                    SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
                    if (meta != null) {
                        gizmoSystem.enableTiledOverlay(meta.tileWidth, meta.tileHeight);
                    }
                }

                handleRectUp();
            }

            @Override
            public boolean scrolled(InputEvent event,
                                    float x,
                                    float y,
                                    float amountX,
                                    float amountY) {

                coordSpaces.screenToWorld(
                        Gdx.input.getX(),
                        Gdx.input.getY(),
                        tmpBeforeScroll
                );

                float factor = (amountY > 0 ? 1.1f : 0.9f);
                camera.zoom = Math.max(0.1f,
                        Math.min(5f, camera.zoom * factor));

                coordSpaces.screenToWorld(
                        Gdx.input.getX(),
                        Gdx.input.getY(),
                        tmpAfterScroll
                );

                final Vector2 diff =
                        tmpBeforeScroll.sub(tmpAfterScroll);

                camera.position.add(diff.x, diff.y, 0);
                camera.update();
                box2DcameraUpdate();

                app.getBottomBar().setZoom(1 / camera.zoom);

                return true;
            }
        });
    }

    private boolean handlePan(int button) {
        if (button != Input.Buttons.MIDDLE) {
            return false;
        }
        panning = true;
        return true;
    }

    private boolean handleBrushDown() {
        if (!isTiledToolInputEnabled()) {
            return false;
        }
        if (!tiledToolService.is(TiledToolService.Mode.BRUSH)
                && !tiledToolService.is(TiledToolService.Mode.ERASE)) {
            return false;
        }

        int layerEntityId = selectionService.getActivelayerId();
        if (layerEntityId == -1) {
            return false;
        }

        currentBrushSession = new TiledBrushSession(layerEntityId);
        brushHasLastCell = false;

        applyBrushAtMouse(layerEntityId);

        if (tiledToolService.is(TiledToolService.Mode.ERASE)) {
            gizmoSystem.disableTiledOverlay();
        }

        return true;
    }

    private void handleBrushDrag() {
        if (currentBrushSession == null) {
            return;
        }
        if (!isTiledToolInputEnabled()) {
            return;
        }
        if (!tiledToolService.is(TiledToolService.Mode.BRUSH)
                && !tiledToolService.is(TiledToolService.Mode.ERASE)) {
            return;
        }

        int layerEntityId = selectionService.getActivelayerId();
        applyBrushAtMouse(layerEntityId);
    }

    private boolean handleRectDown() {
        if (!isTiledToolInputEnabled()) {
            return false;
        }
        if (!tiledToolService.is(TiledToolService.Mode.RECT)) {
            return false;
        }

        int layerEntityId = selectionService.getActivelayerId();
        if (layerEntityId == -1) {
            return false;
        }

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);

        if (tiled == null || tiled.data == null) {
            return false;
        }

        computeTileUnderMouse(tiled, tmpWorldPos);

        rectStartGX = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        rectStartGY = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        rectActive = true;
        return true;
    }

    private void handleRectDrag() {
        if (!rectActive) {
            return;
        }
        if (!isTiledToolInputEnabled()) {
            return;
        }

        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class)
                        .getSafe(selectionService.getActivelayerId(), null);

        if (tiled == null || tiled.data == null) {
            return;
        }

        computeTileUnderMouse(tiled, tmpWorldPos);

        int gx = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        int gy = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        int minGX = Math.min(rectStartGX, gx);
        int maxGX = Math.max(rectStartGX, gx);
        int minGY = Math.min(rectStartGY, gy);
        int maxGY = Math.max(rectStartGY, gy);

        if (tiled.data.projection == games.pixscape.runtime.loading.SceneMetaRuntime.TiledProjection.ISO) {
            gizmoSystem.showTiledRectPreview(tiled.data, minGX, minGY, maxGX, maxGY);
        } else {
            float worldX0 = tiled.data.tileToWorldX(minGX, minGY);
            float worldY0 = tiled.data.tileToWorldY(minGX, minGY);

            float worldX1 = tiled.data.tileToWorldX(maxGX, maxGY) + tiled.data.tileWidth;
            float worldY1 = tiled.data.tileToWorldY(maxGX, maxGY) + tiled.data.tileHeight;

            gizmoSystem.showRectPreview(worldX0, worldY0, worldX1, worldY1);
        }
    }

    private void handleRectUp() {
        if (!rectActive) {
            return;
        }

        rectActive = false;
        gizmoSystem.hideRectPreview();

        int layerEntityId = selectionService.getActivelayerId();
        if (layerEntityId == -1) {
            return;
        }

        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class)
                        .getSafe(layerEntityId, null);

        if (tiled == null || tiled.data == null) {
            return;
        }

        computeTileUnderMouse(tiled, tmpWorldPos);

        int endGX = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        int endGY = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        int minX = Math.min(rectStartGX, endGX);
        int maxX = Math.max(rectStartGX, endGX);
        int minY = Math.min(rectStartGY, endGY);
        int maxY = Math.max(rectStartGY, endGY);

        TiledBrushSession session = new TiledBrushSession(layerEntityId);

        int assetId;
        byte flags;

        if (tiledToolService.is(TiledToolService.Mode.ERASE)) {
            SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
            if (meta != null) {
                gizmoSystem.enableTiledOverlay(meta.tileWidth, meta.tileHeight);
            }
            assetId = 0;
            flags = TileTransformFlags.NONE;
        } else {
            if (!tiledPaintService.hasActiveTile()) {
                return;
            }
            assetId = tiledPaintService.getActiveTileAssetId();
            flags = tiledToolService.getActiveTransformFlags();
        }

        for (int gx = minX; gx <= maxX; gx++) {
            for (int gy = minY; gy <= maxY; gy++) {
                if (!tiled.data.isInside(gx, gy)) {
                    continue;
                }

                session.apply(tiled, gx, gy, assetId, flags);
            }
        }

        if (!session.isEmpty()) {
            long historyId =
                    historyManager.historyIds().ensureForEntity(layerEntityId);

            historyManager.execute(
                    new TiledBrushCommand(
                            world,
                            app.getSceneService(),
                            historyManager.historyIds(),
                            historyId,
                            session.getPreviousValues(),
                            session.getNewValues()
                    )
            );
        }
    }

    private void applyBrushAtMouse(int layerEntityId) {

        if (layerEntityId == -1)
            return;

        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class)
                        .getSafe(layerEntityId, null);

        if (tiled == null || tiled.data == null)
            return;

        coordSpaces.screenToWorldLogical(
                Gdx.input.getX(),
                Gdx.input.getY(),
                selectionService.getActiveLayerIndex(),
                layerService,
                tmpWorldPos
        );

        int gx = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        int gy = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        if (!tiled.data.isInside(gx, gy))
            return;

        int assetId;
        byte flags;

        if (tiledToolService.is(TiledToolService.Mode.ERASE)) {
            assetId = 0;
            flags = TileTransformFlags.NONE;
        } else {
            if (!tiledPaintService.hasActiveTile())
                return;
            assetId = tiledPaintService.getActiveTileAssetId();
            flags = tiledToolService.getActiveTransformFlags();
        }

        if (!brushHasLastCell) {
            currentBrushSession.apply(tiled, gx, gy, assetId, flags);
            brushLastGX = gx;
            brushLastGY = gy;
            brushHasLastCell = true;
            return;
        }

        applyBrushLine(tiled, brushLastGX, brushLastGY, gx, gy, assetId, flags, currentBrushSession);

        brushLastGX = gx;
        brushLastGY = gy;
        brushHasLastCell = true;
    }

    private void applyBrushLine(TiledLayerComponent tiled,
                                int x0,
                                int y0,
                                int x1,
                                int y1,
                                int assetId,
                                byte flags,
                                TiledBrushSession session) {

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);

        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;

        int err = dx - dy;

        int x = x0;
        int y = y0;

        while (true) {
            if (tiled.data.isInside(x, y)) {
                session.apply(tiled, x, y, assetId, flags);
            }

            if (x == x1 && y == y1) {
                break;
            }

            int e2 = err << 1;

            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }

            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private void performFill(int layerEntityId) {

        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class)
                        .getSafe(layerEntityId, null);

        if (tiled == null || tiled.data == null)
            return;

        computeTileUnderMouse(tiled, tmpWorldPos);

        int startGX = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        int startGY = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        if (!tiled.data.isInside(startGX, startGY))
            return;

        int targetId = tiled.data.getTile(startGX, startGY);

        int targetPacked = PackedTileValue.pack(
                tiled.data.getTile(startGX, startGY),
                tiled.data.getTileTransformFlags(startGX, startGY)
        );

        int replacementId;
        byte replacementFlags;

        if (tiledToolService.is(TiledToolService.Mode.ERASE)) {
            replacementId = 0;
            replacementFlags = TileTransformFlags.NONE;
        } else {
            if (!tiledPaintService.hasActiveTile()) return;
            replacementId = tiledPaintService.getActiveTileAssetId();
            replacementFlags = tiledToolService.getActiveTransformFlags();
        }

        int replacementPacked = PackedTileValue.pack(replacementId, replacementFlags);

        if (targetPacked == replacementPacked) return;

        TiledBrushSession session =
                new TiledBrushSession(layerEntityId);

        floodFill(tiled, startGX, startGY, targetPacked, replacementId, replacementFlags, session);

        if (!session.isEmpty()) {

            long historyId =
                    historyManager.historyIds().ensureForEntity(layerEntityId);

            historyManager.execute(
                    new TiledBrushCommand(
                            world,
                            app.getSceneService(),
                            historyManager.historyIds(),
                            historyId,
                            session.getPreviousValues(),
                            session.getNewValues()
                    )
            );
        }
    }

    private void floodFill(TiledLayerComponent tiled,
                           int startGX,
                           int startGY,
                           int targetPacked,
                           int replacementId,
                           byte replacementFlags,
                           TiledBrushSession session) {

        int mapWidth = tiled.data.mapWidth;
        int mapHeight = tiled.data.mapHeight;

        boolean[] visited = new boolean[mapWidth * mapHeight];
        IntArray stack = new IntArray(false, Math.min(mapWidth * mapHeight, 4096));

        int startIndex = startGY * mapWidth + startGX;
        visited[startIndex] = true;
        stack.add(pack(startGX, startGY));

        while (stack.size > 0) {
            int key = stack.pop();
            int gx = unpackX(key);
            int gy = unpackY(key);

            session.apply(tiled, gx, gy, replacementId, replacementFlags);

            pushIfValid(gx + 1, gy, tiled, targetPacked, mapWidth, visited, stack);
            pushIfValid(gx - 1, gy, tiled, targetPacked, mapWidth, visited, stack);
            pushIfValid(gx, gy + 1, tiled, targetPacked, mapWidth, visited, stack);
            pushIfValid(gx, gy - 1, tiled, targetPacked, mapWidth, visited, stack);
        }
    }

    private void pushIfValid(int gx,
                             int gy,
                             TiledLayerComponent tiled,
                             int targetPacked,
                             int mapWidth,
                             boolean[] visited,
                             IntArray stack) {

        if (!tiled.data.isInside(gx, gy)) {
            return;
        }

        int index = gy * mapWidth + gx;

        if (visited[index]) {
            return;
        }

        int currentPacked = PackedTileValue.pack(
                tiled.data.getTile(gx, gy),
                tiled.data.getTileTransformFlags(gx, gy)
        );

        if (currentPacked != targetPacked) {
            return;
        }

        visited[index] = true;
        stack.add(pack(gx, gy));
    }

    private static int pack(int x, int y) {
        return (x << 16) ^ (y & 0xFFFF);
    }

    private static int unpackX(int key) {
        return key >> 16;
    }

    private static int unpackY(int key) {
        return key & 0xFFFF;
    }

    private void computeTileUnderMouse(TiledLayerComponent tiled, Vector2 out) {

        coordSpaces.screenToWorldLogical(
                Gdx.input.getX(),
                Gdx.input.getY(),
                selectionService.getActiveLayerIndex(),
                layerService,
                out
        );
    }

    private void bindPhysicsDebugEvents() {

        EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class, ev -> {
            physicsEnabled = ev.enabled();

            if (!physicsEnabled) {
                disableBox2dRuntimeSync();
                return;
            }
            SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
            ensureBox2dFromMeta(meta);
        });

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, ev -> {
            SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();

            physicsEnabled = meta != null && meta.physicsEnabled;

            if (physicsEnabled) {
                ensureBox2dFromMeta(meta);
            } else {
                disableBox2dRuntimeSync();
            }
        });
    }

    private void disableBox2dRuntimeSync() {
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
        }
        if (physicsService != null) {
            physicsService.setBox2d(null);
        }
    }

    private void ensureBox2dFromMeta(SceneMeta meta) {
        if (meta == null || !meta.physicsEnabled) {
            return;
        }

        // --- scene settings ---
        final float ppm = (meta.pixelsPerMeter > 0f) ? meta.pixelsPerMeter : 100f;
        final float gx = meta.gravityX;
        final float gy = meta.gravityY;
        final boolean doSleep = meta.doSleep;

        final boolean firstInit = (box2dWorldService == null);

        final boolean ppmChanged = firstInit || ppm != lastPpm;
        final boolean gravChanged = firstInit || gx != lastGx || gy != lastGy;
        final boolean sleepChanged = firstInit || doSleep != lastDoSleep;

        // -------------------------------------------------
        // 1) Lazy init Box2D world
        // -------------------------------------------------
        if (firstInit) {
            box2dWorldService = new Box2dWorldService(
                    ppm,
                    new Vector2(gx, gy),
                    doSleep
            );
            box2DcameraUpdate();
        }
        // -------------------------------------------------
        // 2) Update params if changed
        // -------------------------------------------------
        else {
            if (ppmChanged) {
                box2dWorldService.setPpm(ppm);
            }

            if (gravChanged) {
                box2dWorldService.setGravity(gx, gy);
            }

            if (sleepChanged) {
                box2dWorldService.setDoSleep(doSleep);
            }

            if (ppmChanged) {
                box2DcameraUpdate();
            }
        }

        if (physicsService != null) {
            physicsService.setBox2d(box2dWorldService);
        }

        if (box2dSyncSystem != null) {
            box2dSyncSystem.setBox2d(box2dWorldService);
            box2dSyncSystem.setEnabled(true);
            box2dSyncSystem.setStepEnabled(false);
        }

        // -------------------------------------------------
        // 3) Cache update
        // -------------------------------------------------
        lastPhysicsEnabled = true;
        lastPpm = ppm;
        lastGx = gx;
        lastGy = gy;
        lastDoSleep = doSleep;
    }

    private void box2DcameraUpdate() {
        if (box2dWorldService == null) return;

        float ppm = box2dWorldService.ppm;

        box2dCamera.position.set(
                camera.position.x / ppm,
                camera.position.y / ppm,
                0f
        );

        box2dCamera.zoom = camera.zoom;
        box2dCamera.viewportWidth = camera.viewportWidth / ppm;
        box2dCamera.viewportHeight = camera.viewportHeight / ppm;
        box2dCamera.update();
    }

    public void centerCamera() {
        camera.position.set(0, 0, 0);
        camera.update();
        box2DcameraUpdate();
    }


    // ---------------------------------------------------------------------
    // DnD atlas-region -> packed sprite
    // ---------------------------------------------------------------------

    public void handleImageDrop(DragPayload p, float screenX, float screenY) {
        coordSpaces.screenToWorldLogical(screenX, screenY, selectionService.getActiveLayerIndex(), layerService, tmpWorldPos);
        editorOps.createSpriteFromAtlas(p.atlasTag, p.regionPath, tmpWorldPos.x, tmpWorldPos.y, p.guid);
    }

    // ---------------------------------------------------------------------
    // DnD image-file -> sprite standalone
    // ---------------------------------------------------------------------

    public void handleImageFileDrop(DragPayload p, float screenX, float screenY) {
        coordSpaces.screenToWorldLogical(screenX, screenY, selectionService.getActiveLayerIndex(), layerService, tmpWorldPos);
        editorOps.createStandaloneSprite(p.path, tmpWorldPos.x, tmpWorldPos.y, p.guid);
    }


    public void handleEffectDrop(DragPayload p, float screenX, float screenY) {
        coordSpaces.screenToWorldLogical(screenX, screenY, selectionService.getActiveLayerIndex(), layerService, tmpWorldPos);
        editorOps.createParticleEffect(p.path, tmpWorldPos.x, tmpWorldPos.y, p.guid);
    }


    private void handleAnimSheetDrop(DragPayload p, float screenX, float screenY) {
        coordSpaces.screenToWorldLogical(screenX, screenY, selectionService.getActiveLayerIndex(), layerService, tmpWorldPos);
        editorOps.createAnimationSprite(p.path, tmpWorldPos.x, tmpWorldPos.y, p.guid);

    }

    public void handlePrefabDrop(DragPayload p, float screenX, float screenY) {
        if (p == null || p.path == null || p.path.trim().isEmpty()) {
            return;
        }

        coordSpaces.screenToWorldLogical(
                screenX,
                screenY,
                selectionService.getActiveLayerIndex(),
                layerService,
                tmpWorldPos
        );

        FileHandle prefabFile = Gdx.files.absolute(p.path);
        if (!prefabFile.exists()) {
            Gdx.app.error("PrefabDrop", "Prefab file does not exist: " + p.path);
            return;
        }

        EntityGraph graph;
        try {
            graph = prefabAssetService.loadPrefab(prefabFile);
        } catch (RuntimeException ex) {
            Gdx.app.error("PrefabDrop", "Failed to load prefab: " + p.path, ex);
            return;
        }

        if (graph == null || graph.isEmpty()) {
            Gdx.app.error("PrefabDrop", "Prefab graph is empty: " + p.path);
            return;
        }

        if (prefabContainsPhysics(graph) && !activeLayerAcceptsPhysicsPrefabs()) {
            return;
        }

        computePrefabOrigin(graph, tmpPrefabOrigin);

        String sceneTag = currentSceneTag();
        boolean atlasInputChanged = ensurePrefabRenderAssetsInSceneAtlas(graph, sceneTag);

        EntityGraphInstantiationResult result;
        if (prefabIdentityRegistry != null) {
            prefabIdentityRegistry.rebuild();
        }

        try {
            result = entityGraphInstantiationService.instantiate(
                    graph,
                    selectionService.getActiveLayerIndex(),
                    tmpWorldPos.x - tmpPrefabOrigin.x,
                    tmpWorldPos.y - tmpPrefabOrigin.y,
                    "Instantiate Prefab"
            );
        } catch (RuntimeException ex) {
            Gdx.app.error("PrefabDrop", "Failed to instantiate prefab: " + p.path, ex);
            return;
        }

        if (sceneTag != null && !sceneTag.isBlank()) {
            forceCreatedEntitiesSceneAtlasTag(result.createdIds(), sceneTag);
            RenderRebindHelper.rebindEntitiesAfterAtlasChange(
                    this,
                    sceneTag,
                    atlasStudioService,
                    result.createdIds(),
                    "prefab-render-assets-rebound"
            );

            if (atlasInputChanged) {
                atlasStudioService.requestAsyncPack(sceneTag);
            }
        }

        selectCreatedEntities(result.createdIds());
    }

    private boolean prefabContainsPhysics(EntityGraph graph) {
        if (graph == null || graph.isEmpty()) {
            return false;
        }

        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntitySnapshotData snapshot =
                    entry.initializer().toSnapshotData(entry.sourceEntityId());

            if (snapshot.hasPhysicsBody ||
                    snapshot.hasPhysicsAuthoring ||
                    snapshot.hasJoint ||
                    snapshot.hasDistanceJoint ||
                    snapshot.hasRevoluteJoint ||
                    snapshot.hasPrismaticJoint ||
                    snapshot.hasWheelJoint ||
                    snapshot.hasFrictionJoint ||
                    snapshot.hasMotorJoint ||
                    snapshot.hasWeldJoint ||
                    snapshot.hasPulleyJoint ||
                    snapshot.hasGearJoint ||
                    snapshot.fixtures.size > 0) {
                return true;
            }
        }

        return false;
    }

    private boolean activeLayerAcceptsPhysicsPrefabs() {
        if (selectionService == null || layerService == null) {
            return false;
        }

        int activeLayerEntityId = selectionService.getActivelayerId();
        if (activeLayerEntityId < 0) {
            return false;
        }

        return layerService.getLayerTypeByEntity(activeLayerEntityId) == LayerComponent.TYPE_PHYSICS;
    }

    boolean ensurePrefabRenderAssetsInSceneAtlas(EntityGraph graph, String sceneTag) {
        SceneService sceneService = (app != null) ? app.getSceneService() : null;
        if (sceneService == null || sceneTag == null || sceneTag.isBlank()) {
            return false;
        }
        return ensurePrefabRenderAssetsInSceneAtlas(graph, assetId -> sceneService.ensureSceneAtlasInputHasAsset(sceneTag, assetId));
    }

    static boolean ensurePrefabRenderAssetsInSceneAtlas(EntityGraph graph, IntPredicate ensureAssetInSceneAtlasInput) {
        if (ensureAssetInSceneAtlasInput == null || graph == null || graph.isEmpty()) {
            return false;
        }

        boolean changed = false;
        IntSet assetIds = new IntSet();
        for (EntityGraphEntry entry : graph.entries()) {
            if (entry == null || entry.initializer() == null) continue;
            GenericEntitySnapshotData snapshot = entry.initializer().toSnapshotData(entry.sourceEntityId());
            if (snapshot == null || !snapshot.hasAssetRef || snapshot.assetRefAssetId <= 0) continue;
            assetIds.add(snapshot.assetRefAssetId);
        }

        for (IntSet.IntSetIterator it = assetIds.iterator(); it.hasNext; ) {
            int assetId = it.next();
            changed |= ensureAssetInSceneAtlasInput.test(assetId);
        }
        return changed;
    }

    private void forceCreatedEntitiesSceneAtlasTag(IntArray createdIds, String sceneTag) {
        if (createdIds == null || createdIds.size == 0 || sceneTag == null || sceneTag.isBlank()) return;
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        for (int i = 0; i < createdIds.size; i++) {
            int eid = createdIds.get(i);
            AssetRefComponent assetRef = mAssetRef.getSafe(eid, null);
            if (assetRef == null) continue;
            assetRef.atlasTag = sceneTag;
        }
    }

    private void selectCreatedEntities(IntArray createdIds) {
        if (createdIds == null || createdIds.size == 0) {
            return;
        }

        selectionService.clearSelection();
        selectionService.selectOnly(createdIds.get(0));

        for (int i = 1; i < createdIds.size; i++) {
            selectionService.selectAdd(createdIds.get(i));
        }
    }

    private void computePrefabOrigin(EntityGraph graph, Vector2 out) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean any = false;

        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntityInitializer.PreviewVisualData visual =
                    entry.initializer().toPreviewVisualData();

            if (!visual.hasTransform) {
                continue;
            }

            minX = Math.min(minX, visual.x);
            minY = Math.min(minY, visual.y);
            maxX = Math.max(maxX, visual.x);
            maxY = Math.max(maxY, visual.y);
            any = true;
        }

        if (!any) {
            out.set(0f, 0f);
            return;
        }

        out.set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f);
    }


    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public void act(float dt) {
        if (frameProfiler.isEnabled()) {
            actProfiled(dt);
            return;
        }

        world.setDelta(dt);
        gridStage.act(dt);
        if (keyboardNudgeService != null) {
            keyboardNudgeService.update(dt);
        }
        updateDndDrop();
        atlasStudioService.updateAsyncPack();
        atlasStudioService.applyIfPackReady();
        updateTiledPreview();
    }

    private void actProfiled(float dt) {
        frameProfiler.beginFrame();
        long totalStart = frameProfiler.begin(StudioFrameProfiler.ACT_TOTAL);
        try {
            world.setDelta(dt);
            gridStage.act(dt);
            if (keyboardNudgeService != null) {
                keyboardNudgeService.update(dt);
            }
            updateDndDrop();

            long phaseStart = frameProfiler.begin(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK);
            atlasStudioService.updateAsyncPack();
            frameProfiler.end(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK, phaseStart);

            phaseStart = frameProfiler.begin(StudioFrameProfiler.ATLAS_APPLY_IF_PACK_READY);
            atlasStudioService.applyIfPackReady();
            frameProfiler.end(StudioFrameProfiler.ATLAS_APPLY_IF_PACK_READY, phaseStart);

            phaseStart = frameProfiler.begin(StudioFrameProfiler.PREVIEW_UPDATE_TILED_PREVIEW);
            updateTiledPreview();
            frameProfiler.end(StudioFrameProfiler.PREVIEW_UPDATE_TILED_PREVIEW, phaseStart);
        } finally {
            frameProfiler.end(StudioFrameProfiler.ACT_TOTAL, totalStart);
        }
    }

    public void draw() {
        if (frameProfiler.isEnabled()) {
            drawProfiled();
            return;
        }

        gridStage.draw();
        if (gpuSnapshotManager != null && !atlasStudioService.isPackInProgress()) {
            String sceneTag = currentSceneTag();
            if (sceneTag != null && !sceneTag.isBlank()) {
                gpuSnapshotManager.syncIfDirty(sceneTag);
            }
        }
        EventFlow.i().flush();
        world.process();
        if (gpuSnapshotManager != null) {
            gpuSnapshotManager.flushDeferredDisposals();
        }
    }

    private void drawProfiled() {
        long totalStart = frameProfiler.begin(StudioFrameProfiler.DRAW_TOTAL);
        try {
            gridStage.draw();
            if (gpuSnapshotManager != null && !atlasStudioService.isPackInProgress()) {
                String sceneTag = currentSceneTag();
                if (sceneTag != null && !sceneTag.isBlank()) {
                    long phaseStart = frameProfiler.begin(StudioFrameProfiler.GPU_SNAPSHOT_SYNC_IF_DIRTY);
                    gpuSnapshotManager.syncIfDirty(sceneTag);
                    frameProfiler.end(StudioFrameProfiler.GPU_SNAPSHOT_SYNC_IF_DIRTY, phaseStart);
                }
            }

            long phaseStart = frameProfiler.begin(StudioFrameProfiler.EVENT_FLOW_FLUSH);
            EventFlow.i().flush();
            frameProfiler.end(StudioFrameProfiler.EVENT_FLOW_FLUSH, phaseStart);

            phaseStart = frameProfiler.begin(StudioFrameProfiler.WORLD_PROCESS);
            if (systemProfiler != null && systemProfiler.enabled()) {
                systemProfiler.beginFrame();
            }
            world.process();
            frameProfiler.end(StudioFrameProfiler.WORLD_PROCESS, phaseStart);

            if (gpuSnapshotManager != null) {
                phaseStart = frameProfiler.begin(StudioFrameProfiler.GPU_SNAPSHOT_FLUSH_DEFERRED_DISPOSALS);
                gpuSnapshotManager.flushDeferredDisposals();
                frameProfiler.end(StudioFrameProfiler.GPU_SNAPSHOT_FLUSH_DEFERRED_DISPOSALS, phaseStart);
            }
        } finally {
            frameProfiler.end(StudioFrameProfiler.DRAW_TOTAL, totalStart);
            if (previewRuntimeProfiler != null && previewRuntimeProfiler.isEnabled()) {
                previewRuntimeProfiler.onFrame(
                        frameProfiler,
                        systemProfiler,
                        isPreviewSceneReady(),
                        isPreviewRuntimeReady()
                );
            }
            frameProfiler.endFrame();
        }
    }

    private void updateTiledPreview() {
        if (world == null || selectionService == null || tiledToolService == null || tiledPaintService == null) {
            return;
        }

        if (!isTiledToolInputEnabled()) {
            tiledPreviewService.clear();
            return;
        }

        SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
        if (meta == null || meta.editorMode != SceneMeta.EditorMode.TILE) {
            tiledPreviewService.clear();
            return;
        }

        if (rectActive || currentBrushSession != null) {
            tiledPreviewService.clear();
            return;
        }

        int layerEntityId = selectionService.getActivelayerId();
        if (layerEntityId == -1) {
            tiledPreviewService.clear();
            return;
        }

        TiledLayerComponent tiled =
                world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);

        if (tiled == null || tiled.data == null) {
            tiledPreviewService.clear();
            return;
        }

        coordSpaces.screenToWorldLogical(
                Gdx.input.getX(),
                Gdx.input.getY(),
                selectionService.getActiveLayerIndex(),
                layerService,
                tmpWorldPos
        );

        int gx = tiled.data.worldToTileX(tmpWorldPos.x, tmpWorldPos.y);
        int gy = tiled.data.worldToTileY(tmpWorldPos.x, tmpWorldPos.y);

        if (!tiled.data.isInside(gx, gy)) {
            tiledPreviewService.clear();
            return;
        }

        if (tiledToolService.is(TiledToolService.Mode.ERASE)) {
            int assetId = tiled.data.getTile(gx, gy);
            if (assetId <= 0) {
                tiledPreviewService.clear();
                return;
            }
            tiledPreviewService.showTintedCoverage(
                    tiled.data,
                    tiled.atlasTag,
                    gx,
                    gy,
                    assetId,
                    tiled.data.getTileTransformFlags(gx, gy),
                    0.05f,
                    0.92f,
                    1f,
                    0.5f
            );
            return;
        }

        if (!tiledPaintService.hasActiveTile()) {
            tiledPreviewService.clear();
            return;
        }

        tiledPreviewService.show(
                tiled.data,
                tiled.atlasTag,
                gx,
                gy,
                tiledPaintService.getActiveTileAssetId(),
                tiledToolService.getActiveTransformFlags()
        );
    }

    private boolean isTiledToolInputEnabled() {
        return spatialBlockSelectionService == null || !spatialBlockSelectionService.isEditingActive();
    }


    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public AtlasStudioService getAtlasService() {
        return atlasStudioService;
    }

    public SelectionService getSelectionService() {
        return selectionService;
    }

    public LayerService getLayerService() {
        return layerService;
    }

    public ZOrderRuntimeService getZOrderService() {
        return zOrderRuntimeService;
    }

    public ShaderService getShaderService() {
        return shaderService;
    }

    public PhysicsService getPhysicsService() {
        return physicsService;
    }

    public PhysicsSelectionService getPhysicsSelectionService() {
        return physicsSelectionService;
    }

    public SpatialBlockSelectionService getSpatialBlockSelectionService() {
        return spatialBlockSelectionService;
    }

    public SpatialTileSelectionService getSpatialTileSelectionService() {
        return spatialTileSelectionService;
    }

    public TiledPaintService getTiledPaintService() {
        return tiledPaintService;
    }

    public TiledToolService getTileToolService() {
        return tiledToolService;
    }

    public PolygonDrawSession getPolygonDrawSession() {
        return polygonDrawSession;
    }

    public TiledAllocatorService getTiledAllocatorService() {
        return tiledAllocatorService;
    }

    public AlignService getAlignService() {
        return alignService;
    }

    public ClipboardService getClipboardService() {
        return clipboardService;
    }

    public TileAnimationRegistry getTileAnimationRegistry() {
        return tileAnimationRegistry;
    }

    public World getEcsWorld() {
        return world;
    }

    public Stage getGridStage() {
        return gridStage;
    }

    public StudioDrawContext getWorldDrawCtx() {
        return worldDrawCtx;
    }

    public GpuSnapshotManager getGpuSnapshotManager() {
        return gpuSnapshotManager;
    }

    public MetricsBatch getMetricsBatch() {
        return metricsBatch;
    }

    public RenderStateSOA getRenderState() {
        return renderState;
    }

    public CoordSpaces getCoordSpaces() {
        return coordSpaces;
    }

    public InputState getInputState() {
        return inputState;
    }

    public EditorOpsImpl getEditorOps() {
        return (EditorOpsImpl) editorOps;
    }

    public String getDefaultShaderName() {
        return defaultShaderName;
    }


    public void resize(int w, int h) {
        gridStage.getViewport().update(w, h, false);
        if (box2dCamera != null) {
            box2DcameraUpdate();
        }
    }

    public void dispose() {
        gridStage.dispose();

        atlasStudioService.disposeAsyncPack();
        atlasStudioService.unloadAll();

        if (studioParticleFallbackSystem != null) {
            studioParticleFallbackSystem.invalidateAll();
        }

        if (world != null) {
            world.dispose();
            world = null;
        }

        if (gpuSnapshotManager != null) {
            gpuSnapshotManager.disposeAll();
        }

        if (box2dWorldService != null) {
            box2dWorldService.dispose();
            box2dWorldService = null;
        }
    }

    private String currentSceneTag() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;
        return cfg.canonicalSceneTagCurrent();
    }

    private boolean isPreviewRuntimeReady() {
        return world != null
                && renderState != null
                && layerState != null
                && isPreviewSceneReady();
    }

    private boolean isPreviewSceneReady() {
        String sceneTag = currentSceneTag();
        return sceneTag != null
                && !sceneTag.isBlank();
    }

    private void markSnapshotDirtyIfSceneLoaded(String reason) {
        if (gpuSnapshotManager == null) return;
        String sceneTag = currentSceneTag();
        if (sceneTag == null || sceneTag.isBlank()) return;
        gpuSnapshotManager.markDirty(sceneTag, reason);
    }
}
