package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.ui.modal.Dialogs;
import com.kotcrab.vis.ui.util.dialog.OptionDialogListener;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.OsFilesDropTarget;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.ActiveDocumentCommandRouter;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.GameObjectEditorDocument;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.display.DisplayMetrics;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.CursorDrawHelper;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.helper.ShapeHelper;
import games.pixscape.studio.helper.StudioHomeBootstrap;
import games.pixscape.studio.history.commands.ToggleSpatialActorLayerCommand;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.logging.StudioLogCapture;
import games.pixscape.studio.logging.StudioLogLevel;
import games.pixscape.studio.service.ProjectOpenFailure;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.service.hud.HudImageAuthoringService;
import games.pixscape.studio.service.hud.SceneHudAssociationService;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.gameobject.GameObjectAssetService;
import games.pixscape.studio.service.runtimeavailability.SceneHudRuntimePreparationService;
import games.pixscape.studio.ui.StudioStage;
import games.pixscape.studio.ui.asset.AssetsPanel;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.docking.DockSlot;
import games.pixscape.studio.ui.document.EditorDocumentHost;
import games.pixscape.studio.ui.layer.LayersPanel;
import games.pixscape.studio.ui.hud.HudCanvasInputHost;
import games.pixscape.studio.ui.hud.HudCanvasStatusOverlay;
import games.pixscape.studio.ui.hud.HudAssetDropController;
import games.pixscape.studio.ui.hud.HudWidgetDragController;
import games.pixscape.studio.ui.hud.HudWidgetsPanel;
import games.pixscape.studio.ui.hud.HudTestInputRouter;
import games.pixscape.studio.ui.preview.HtmlPreviewLauncher;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.tree.ItemTreePanel;
import games.pixscape.studio.ui.widget.CanvasModeControls;
import games.pixscape.studio.ui.widget.CanvasModeIndicator;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.Optional;
import java.util.function.Consumer;


public class StudioApplicationAdapter extends ApplicationAdapter {
    private static final boolean DEBUG_SHUTDOWN = Boolean.getBoolean("pixscape.debug.shutdown");
    private Stage uiStage;
    private WorldCanvas canvas;
    private TopMenuBar topMenuBar;
    private ToolBar toolBar;
    private BottomMenuBar bottomMenuBar;
    private DockManager dockManager;
    private ItemTreePanel itemTreePanel;
    private AssetsPanel assetsPanel;
    private SceneService sceneService;
    private AnimationAssetAuthoringService animationAssetAuthoringService;
    private HudEditorSession hudEditorSession;
    private HudCanvasInputHost hudCanvasInputHost;
    private HudTestInputRouter hudTestInputRouter;
    private InputMultiplexer inputMultiplexer;
    private CanvasModeControls canvasModeControls;
    private RulerActor rulerLeft;
    private RulerActor rulerTop;
    private HudImageAuthoringService hudImageAuthoring;
    private HudAssetDropController hudAssetDropController;
    private HudWidgetDragController hudWidgetDragController;
    private SceneHudRuntimePreparationService sceneHudRuntimePreparationService;
    private SceneHudCompositionRenderer sceneHudCompositionRenderer;
    private SceneHudAssociationService sceneHudAssociationService;
    private SceneHudAssetDropController sceneHudAssetDropController;
    private HudDocumentPersistenceService hudDocumentPersistenceService;
    private EditorDocumentManager editorDocumentManager;
    private ActiveDocumentCommandRouter editorCommandRouter;
    private SceneEditorContext sceneEditorContext;
    private SceneEditorContext mostRecentSceneEditorContext;
    private StudioEditingModeService studioEditingModeService;
    private PropertiesPanel propertiesPanel;
    private LayersPanel layersPanel;
    private HudWidgetsPanel hudWidgetsPanel;
    private DocumentDockPanelCoordinator documentDockPanelCoordinator;
    private ShapeDrawer drawer;
    private boolean previewActive = false;
    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private final Rectangle centerBoundsLogical = new Rectangle();
    private final Rectangle centerStackBoundsLogical = new Rectangle();
    private final Rectangle sceneHudBoundsLogical = new Rectangle();
    private final Vector2 centerOriginLogical = new Vector2();
    private final Vector2 dockRootRightLogical = new Vector2();
    private final Vector2 hudDropUiCoords = new Vector2();
    private boolean displayMetricsInitialized;
    private boolean rulersVisibleBeforeHud;
    private boolean hudRulerStateCaptured;
    private boolean rulersVisibleBeforeHudTest;
    private boolean hudTestRulerStateCaptured;
    private boolean disposing;

    private VisTable root;

    private final Array<OsFilesDropTarget> osDropTargets = new Array<>();
    private String preloadLastProjectWarning;

    @Override
    public void create() {
        StudioLogCapture.install();
        StudioLogLevel.applyCurrentToGdx();
        StudioHomeBootstrap.ensureExists();
        refreshDisplayMetrics();

        Skin skin = new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json"));
        VisUI.load(skin);
        BitmapFont font = VisUI.getSkin().getFont("default-font");

        EditorSettings.load();

        FileHandle lastProjectFile = tryResolveLastProjectFileForStartup();

        ProjectConfig cfg = ProjectConfig.getInstance();

        root = new VisTable();
        root.setTouchable(Touchable.childrenOnly);
        uiStage = new StudioStage(new ScreenViewport());
        drawer = ShapeHelper.newDrawer(uiStage.getBatch());

        FileHandle projectDir = cfg.projectFileName != null && !cfg.projectFileName.isBlank()
                ? StudioFs.requireStudioProjectDir(cfg)
                : null;

        ShaderRegistry.reloadForProject(
                projectDir,
                StudioFs.DIR_ORIG_SHADERS
        );

        CursorDrawHelper.init();

        studioEditingModeService = new StudioEditingModeService();
        sceneEditorContext = new SceneEditorContext(
                cfg != null ? cfg.canonicalSceneTagCurrent() : null,
                studioEditingModeService);
        mostRecentSceneEditorContext = sceneEditorContext;
        canvas = new WorldCanvas(this, drawer, sceneEditorContext, studioEditingModeService);
        hudEditorSession = new HudEditorSession(
                () -> sceneService != null ? sceneService.getAssetMetaDatabase() : null,
                () -> uiStage != null ? uiStage.getBatch() : null,
                () -> drawer);
        studioEditingModeService.bindHudTestModeController(
                new StudioEditingModeService.HudTestModeController() {
                    @Override public boolean canEnter() { return hudEditorSession.canEnterTestMode(); }
                    @Override public boolean enter() { return hudEditorSession.enterTestMode(); }
                    @Override public void exit() { hudEditorSession.exitTestMode(); }
                    @Override public boolean isActive() { return hudEditorSession.isTestMode(); }
                });
        hudEditorSession.addListener(() -> {
            if (bottomMenuBar != null && hudCanvasActive()) {
                bottomMenuBar.setPan(hudEditorSession.hudCameraX(), hudEditorSession.hudCameraY());
            }
            studioEditingModeService.refreshHudTestMode();
            syncHudTestInputSurface();
        });
        hudDocumentPersistenceService = new HudDocumentPersistenceService();
        editorDocumentManager = new EditorDocumentManager();

        // ---------------------------------------------------------
        // UI docking etc (unchanged)
        // ---------------------------------------------------------

        // ~100 px entre majeures
        rulerTop = new RulerActor(
                RulerActor.Orientation.TOP,
                (OrthographicCamera) canvas.getGridStage().getCamera(),
                canvas.getCoordSpaces(),
                drawer, font
        ).setThicknessPx(22f).setTargetMajorPx(100).setMinorsPerMajor(1);

        rulerLeft = new RulerActor(
                RulerActor.Orientation.LEFT,
                (OrthographicCamera) canvas.getGridStage().getCamera(),
                canvas.getCoordSpaces(),
                drawer, font
        ).setThicknessPx(22f).setTargetMajorPx(100).setMinorsPerMajor(1);

        dockManager = new DockManager(this, rulerLeft, rulerTop);
        hudCanvasInputHost = new HudCanvasInputHost(hudEditorSession);
        hudTestInputRouter = new HudTestInputRouter(hudEditorSession, uiStage);
        // The HUD preview is drawn by its own Stage, while Studio routes input through uiStage.
        // Stack assigns this transparent actor the center-canvas bounds, including empty areas.
        dockManager.getCenterStack().add(centerOverlayLayer(hudCanvasInputHost));
        dockManager.getCenterStack().add(
                centerOverlayLayer(new HudCanvasStatusOverlay(hudEditorSession)));
        canvasModeControls = installCanvasModeControls(
                dockManager.getCenterStack(), studioEditingModeService);
        bindEditorDocumentLifecycle();
        sceneService = new SceneService(this, canvas);
        editorCommandRouter = new ActiveDocumentCommandRouter(
                editorDocumentManager,
                new ActiveDocumentCommandRouter.SceneCommands() {
                    @Override public void save(SceneEditorDocument document, Runnable onSuccess,
                                               Consumer<Throwable> onFailure) {
                        sceneService.saveProjectAndCurrentSceneWithProgress(
                                uiStage, onSuccess, onFailure);
                    }
                    @Override public boolean undo(SceneEditorDocument document) {
                        canvas.undoHistory();
                        return true;
                    }
                    @Override public boolean redo(SceneEditorDocument document) {
                        canvas.redoHistory();
                        return true;
                    }
                },
                hud -> {
                    hudDocumentPersistenceService.save(
                            StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()), hud);
                    hudEditorSession.refreshDocumentMetadata(hud);
                },
                new ActiveDocumentCommandRouter.GameObjectCommands() {
                    @Override public void save(GameObjectEditorDocument document) {
                        saveGameObjectDocument(document);
                    }
                    @Override public boolean undo(GameObjectEditorDocument document) {
                        document.context().historyManager().undo();
                        return true;
                    }
                    @Override public boolean redo(GameObjectEditorDocument document) {
                        document.context().historyManager().redo();
                        return true;
                    }
                });
        animationAssetAuthoringService = new AnimationAssetAuthoringService(
                sceneService::getAssetMetaDatabase,
                () -> StudioFs.requireAssetsFile(ProjectConfig.getInstance()),
                canvas::publishAssetMetaDatabase);
        canvas.bindAssetMetaLookup(sceneService::getAssetMeta);
        canvas.getEditorOps().setSceneService(sceneService);

        itemTreePanel = new ItemTreePanel(this);
        itemTreePanel.setPreferredWindowSize(362, 600);
        dockManager.register(itemTreePanel, DockSlot.LEFT, true);

        propertiesPanel = new PropertiesPanel(this);
        itemTreePanel.bindPropertiesPanel(propertiesPanel);
        propertiesPanel.setPreferredWindowSize(362, 600);
        dockManager.register(propertiesPanel, DockSlot.RIGHT_TOP, true);

        layersPanel = new LayersPanel(this);
        layersPanel.setPreferredWindowSize(362, 500);
        dockManager.register(layersPanel, DockSlot.RIGHT_BOTTOM, true);

        hudWidgetsPanel = new HudWidgetsPanel(hudEditorSession, editorDocumentManager);
        hudWidgetsPanel.setPreferredWindowSize(362, 500);
        dockManager.register(hudWidgetsPanel, DockSlot.RIGHT_BOTTOM, false);
        documentDockPanelCoordinator = new DocumentDockPanelCoordinator(
                editorDocumentManager, dockManager, layersPanel, hudWidgetsPanel, () -> uiStage);

        assetsPanel = new AssetsPanel(this);
        assetsPanel.setPreferredWindowSize(900, 500);
        assetsPanel.reloadFromProject(cfg);
        dockManager.register(assetsPanel, DockSlot.BOTTOM, true);
        sceneService.setAssetsPanel(assetsPanel);
        hudImageAuthoring = new HudImageAuthoringService(
                () -> StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()),
                sceneService::getAssetMetaDatabase,
                editorDocumentManager,
                hudEditorSession);
        sceneHudRuntimePreparationService = new SceneHudRuntimePreparationService(
                () -> StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()), ProjectConfig::getInstance,
                sceneService::getAssetMetaDatabase, editorDocumentManager, hudDocumentPersistenceService);
        sceneHudCompositionRenderer = new SceneHudCompositionRenderer(
                () -> StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()),
                ProjectConfig::getInstance,
                editorDocumentManager,
                hudDocumentPersistenceService,
                sceneHudRuntimePreparationService);
        sceneHudAssociationService = new SceneHudAssociationService(
                () -> StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()),
                ProjectConfig::getInstance,
                hudDocumentPersistenceService,
                sceneHudRuntimePreparationService::invalidateScene,
                (sceneTag, hudScreenId) -> {
                    EventFlow.i().publish(new EventFlow.SceneHudAssociationChanged(
                            sceneTag, hudScreenId, EventFlow.tag(this)));
                    if (layersPanel != null) {
                        layersPanel.requestHudAssociationRefresh(sceneTag);
                    }
                });
        sceneHudAssetDropController = new SceneHudAssetDropController(
                sceneHudAssociationService, editorDocumentManager);
        hudAssetDropController = new HudAssetDropController(hudEditorSession,
                hudImageAuthoring, editorDocumentManager, this::showHudCommandFailure);
        hudWidgetDragController = new HudWidgetDragController(hudEditorSession,
                editorDocumentManager, hudWidgetsPanel, hudCanvasInputHost);

        Table frame = new Table();
        frame.setTouchable(Touchable.childrenOnly);
        frame.add(dockManager.getRoot()).grow();

        topMenuBar = new TopMenuBar(this, dockManager, sceneService);
        topMenuBar.getTable().pad(3, 3, 3, 3);

        toolBar = new ToolBar(this);

        bottomMenuBar = new BottomMenuBar(this);
        bottomMenuBar.setPan(canvas.getGridStage().getCamera().position.x, canvas.getGridStage().getCamera().position.y);
        bottomMenuBar.setZoom(((OrthographicCamera) canvas.getGridStage().getCamera()).zoom);

        root.setFillParent(true);
        root.top().left();
        root.add(topMenuBar.getTable()).growX().row();
        root.add(toolBar).height(ToolBar.HEIGHT).growX().row();

        root.add(frame).grow().row();
        root.add(bottomMenuBar).height(BottomMenuBar.HEIGHT).growX().row();

        uiStage.addActor(root);

        // Native HUD touch focus must receive drag/up before Studio can consume those events.
        inputMultiplexer = new InputMultiplexer(
                hudTestInputRouter, uiStage, canvas.getGridStage());
        Gdx.input.setInputProcessor(inputMultiplexer);
        syncHudTestInputSurface();

        Lwjgl3Graphics g = (Lwjgl3Graphics) Gdx.graphics;
        Lwjgl3Window window = g.getWindow();
        // Keep the Studio as a normal desktop window. OS-level floating/always-on-top
        // breaks Alt+Tab by forcing Pixscape above other applications.
        window.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void filesDropped(String[] files) {
                Gdx.app.postRunnable(() -> onFilesDropped(files));
            }

            @Override
            public boolean closeRequested() {
                Gdx.app.postRunnable(StudioApplicationAdapter.this::closeRequested);
                return false;
            }
        });

        // 2) Actually open the project AFTER SceneService + UI exist
        if (lastProjectFile != null) {
            final FileHandle f = lastProjectFile;
            Gdx.app.postRunnable(() -> {
                Gdx.app.log("Studio", "Auto-opening last project: " + f.path());
                Optional<ProjectOpenFailure> failure = sceneService.tryOpenProject(f, "auto-open last project");
                if (failure.isEmpty()) {
                    topMenuBar.beginProject();
                } else {
                    Gdx.app.error("Studio", "Auto-open failed: " + f.path(), failure.get().cause());
                    topMenuBar.onStart();
                    Dialogs.showOKDialog(uiStage, "Project not loaded", failure.get().message());
                    clearLastProjectPath();
                }
            });
        } else if (preloadLastProjectWarning != null && !preloadLastProjectWarning.isBlank()) {
            final String warning = preloadLastProjectWarning;
            Gdx.app.postRunnable(() -> Dialogs.showOKDialog(
                    uiStage,
                    "Project preload failed",
                    warning
            ));
        }
    }


    private FileHandle tryResolveLastProjectFileForStartup() {
        String last = EditorSettings.get().lastProjectPath;
        if (last == null || last.isBlank()) return null;

        FileHandle projectFile = resolveProjectFileFromSetting(last);
        if (projectFile == null) {
            preloadLastProjectWarning = "The last project could not be preloaded.\n\nReason: Invalid project file path in editor settings.";
            clearLastProjectPath();
            return null;
        }
        if (!projectFile.exists()) {
            Gdx.app.error("Studio", "Skipping preload: project file is missing: " + projectFile.path());
            preloadLastProjectWarning = "The last project could not be preloaded.\n\nReason: Project file is missing.";
            clearLastProjectPath();
            return null;
        }
        return projectFile;
    }

    private void clearLastProjectPath() {
        EditorSettings.get().lastProjectPath = null;
        EditorSettings.save();
    }

    public void pushOsDropTarget(OsFilesDropTarget t) {
        if (t == null) return;
        // avoid duplicates
        for (int i = 0; i < osDropTargets.size; i++) if (osDropTargets.get(i) == t) return;
        osDropTargets.add(t);
    }

    public void popOsDropTarget(OsFilesDropTarget t) {
        if (t == null) return;
        for (int i = osDropTargets.size - 1; i >= 0; i--) {
            if (osDropTargets.get(i) == t) {
                osDropTargets.removeIndex(i);
                break;
            }
        }
    }


    private FileHandle resolveProjectFileFromSetting(String lastProjectPath) {
        FileHandle fh = Gdx.files.absolute(lastProjectPath);
        return fh.isDirectory() ? null : fh;
    }

    public void onFilesDropped(String[] files) {
        if (files == null || files.length == 0) return;

        // 1) topmost target wins
        for (int i = osDropTargets.size - 1; i >= 0; i--) {
            OsFilesDropTarget t = osDropTargets.get(i);
            if (t != null && t.onOsFilesDropped(files)) {
                return;
            }
        }
    }

    public boolean closeRequested() {
        dumpLiveNonDaemonThreads("closeRequested");
        runAfterCurrentSceneSaveDecision(
                "Unsaved Project",
                "Do you want to save before quitting?",
                Gdx.app::exit,
                null,
                throwable -> Dialogs.showOKDialog(
                        uiStage,
                        "Save failed",
                        PreviewLaunchSupport.userMessageFor(throwable)
                )
        );
        return true;
    }

    public void runAfterCurrentSceneSaveDecision(String title,
                                                 String message,
                                                 Runnable continuation,
                                                 Runnable onCancel,
                                                 Consumer<Throwable> onSaveFailure) {
        boolean saveRequired = sceneService != null
                && (sceneService.requiresSaveBeforeLeavingAnyScene()
                || hasDirtyHudDocuments() || hasDirtyGameObjectDocuments());
        CurrentSceneSaveDecisionGuard.request(
                saveRequired,
                title,
                message,
                continuation,
                onCancel,
                onSaveFailure,
                (dialogTitle, dialogMessage, save, dontSave, cancel) -> Dialogs.showOptionDialog(
                        uiStage,
                        dialogTitle,
                        dialogMessage,
                        Dialogs.OptionDialogType.YES_NO_CANCEL,
                        new OptionDialogListener() {
                            @Override
                            public void yes() {
                                save.run();
                            }

                            @Override
                            public void no() {
                                dontSave.run();
                            }

                            @Override
                            public void cancel() {
                                cancel.run();
                            }
                        }
                ),
                (onSuccess, onFailure) -> sceneService.saveAllDirtyScenesWithProgress(
                        uiStage,
                        () -> {
                            try {
                                saveAllDirtyHudDocuments();
                                saveAllDirtyGameObjectDocuments();
                                onSuccess.run();
                            } catch (RuntimeException failure) {
                                onFailure.accept(failure);
                            }
                        },
                        onFailure
                )
        );
    }

    private boolean hasDirtyHudDocuments() {
        return editorDocumentManager != null && editorDocumentManager.documents().stream()
                .filter(HudScreenEditorDocument.class::isInstance)
                .map(HudScreenEditorDocument.class::cast)
                .anyMatch(HudScreenEditorDocument::isDirty);
    }

    private boolean hasDirtyGameObjectDocuments() {
        return editorDocumentManager != null && editorDocumentManager.documents().stream()
                .filter(GameObjectEditorDocument.class::isInstance)
                .map(GameObjectEditorDocument.class::cast)
                .anyMatch(GameObjectEditorDocument::isDirty);
    }

    public boolean hasDirtyEditorDocuments() {
        return hasDirtyHudDocuments() || hasDirtyGameObjectDocuments() || (sceneService != null
                && sceneService.requiresSaveBeforeLeavingAnyScene());
    }

    private void saveAllDirtyHudDocuments() {
        FileHandle projectDir = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
        for (OpenEditorDocument document : editorDocumentManager.documents()) {
            if (document instanceof HudScreenEditorDocument hud && hud.isDirty()) {
                hudDocumentPersistenceService.save(projectDir, hud);
                hudEditorSession.refreshDocumentMetadata(hud);
            }
        }
    }

    public WorldCanvas getCanvas() {
        return canvas;
    }

    /** Compatibility accessor for the active or most-recent live Scene context. */
    public SceneEditorContext getSceneEditorContext() {
        return getMostRecentSceneEditorContext();
    }

    public SceneEditorContext getMostRecentSceneEditorContext() {
        SceneEditorContext attached = canvas != null ? canvas.getAttachedSceneContext() : null;
        if (attached != null) return attached;
        return mostRecentSceneEditorContext != null && !mostRecentSceneEditorContext.isDisposed()
                ? mostRecentSceneEditorContext : null;
    }

    public HudEditorSession getHudEditorSession() {
        return hudEditorSession;
    }

    public EditorDocumentManager getEditorDocumentManager() {
        return editorDocumentManager;
    }

    boolean canDropHudScreenOnScene(DragPayload payload, SceneEditorContext context) {
        return sceneHudAssetDropController != null
                && sceneHudAssetDropController.canAccept(payload, context);
    }

    boolean dropHudScreenOnScene(DragPayload payload, SceneEditorContext context) {
        boolean handled = sceneHudAssetDropController != null
                && sceneHudAssetDropController.drop(payload, context);
        if (handled && layersPanel != null && context != null) {
            layersPanel.requestHudAssociationRefresh(context.sceneIdentity());
        }
        return handled;
    }

    public boolean removeHudScreenAssociation(SceneEditorContext context) {
        if (sceneHudAssociationService == null || context == null) return false;
        return editorDocumentManager.documents().stream()
                .filter(SceneEditorDocument.class::isInstance)
                .map(SceneEditorDocument.class::cast)
                .filter(document -> document.context() == context)
                .findFirst()
                .map(sceneHudAssociationService::remove)
                .orElse(false);
    }

    /** Supplies the document-aware owner for DockManager's generic center content. */
    public Actor createDockCenterHost(Actor centerContent) {
        return new EditorDocumentHost(editorDocumentManager, centerContent);
    }

    static VisTable centerOverlayLayer(Actor content) {
        VisTable layer = new VisTable();
        layer.setTouchable(Touchable.childrenOnly);
        layer.add(content).grow().minSize(0f).prefSize(0f).maxSize(0f);
        return layer;
    }

    /**
     * Installs the interactive mode action in DockManager's ruler-aware overlay and keeps that
     * overlay above the full-size HUD input surface. Empty overlay space remains transparent.
     */
    static CanvasModeControls installCanvasModeControls(
            Group centerStack, StudioEditingModeService modeService) {
        CanvasModeIndicator indicator = findActor(centerStack, CanvasModeIndicator.class);
        if (indicator == null || !(indicator.getParent() instanceof Table overlay)) {
            throw new IllegalStateException("Canvas mode indicator is not mounted in its overlay.");
        }
        @SuppressWarnings("rawtypes") Cell cell = overlay.getCell(indicator);
        if (cell == null) {
            throw new IllegalStateException("Canvas mode indicator has no overlay cell.");
        }
        CanvasModeControls controls = new CanvasModeControls(indicator, modeService);
        cell.setActor(controls);
        configureModeOverlay(overlay);
        return controls;
    }

    /** Reattaches the controls after DockManager has rebuilt the overlay for a ruler change. */
    static void restoreCanvasModeControls(CanvasModeControls controls) {
        if (controls == null) return;
        CanvasModeIndicator indicator = controls.indicator();
        if (indicator.getParent() == controls) {
            if (controls.getParent() instanceof Table overlay) configureModeOverlay(overlay);
            return;
        }
        if (!(indicator.getParent() instanceof Table overlay)) {
            throw new IllegalStateException("Canvas mode indicator lost its ruler overlay.");
        }
        @SuppressWarnings("rawtypes") Cell cell = overlay.getCell(indicator);
        if (cell == null) {
            throw new IllegalStateException("Canvas mode indicator has no rebuilt overlay cell.");
        }
        controls.rebuildContent();
        cell.setActor(controls);
        configureModeOverlay(overlay);
    }

    private static void configureModeOverlay(Table overlay) {
        overlay.setTouchable(Touchable.childrenOnly);
        overlay.toFront();
    }

    private static <T extends Actor> T findActor(Actor root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Group group) {
            for (Actor child : group.getChildren()) {
                T match = findActor(child, type);
                if (match != null) return match;
            }
        }
        return null;
    }

    public HudScreenEditorDocument openHudScreen(String screenId) {
        String normalized = games.pixscape.runtime.hud.HudScreenAssetId.normalize(screenId);
        var key = new games.pixscape.studio.document.EditorDocumentKey(
                EditorDocumentType.HUD_SCREEN, normalized);
        OpenEditorDocument existing = editorDocumentManager.find(key);
        if (existing instanceof HudScreenEditorDocument hud) {
            editorDocumentManager.activate(key);
            return hud;
        }
        int slash = normalized.lastIndexOf('/');
        String title = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        FileHandle projectDir = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
        try {
            var loaded = hudDocumentPersistenceService.load(projectDir, normalized);
            return editorDocumentManager.openHudScreen(new HudScreenEditorDocument(
                    normalized, title, loaded.asset(), loaded.document()));
        } catch (RuntimeException failure) {
            Dialogs.showOKDialog(uiStage, "HUD cannot be opened",
                    PreviewLaunchSupport.userMessageFor(failure));
            return null;
        }
    }

    /** Opens an isolated editable copy of one Game Object asset, keyed by its logical asset ID. */
    public GameObjectEditorDocument openGameObject(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) return null;
        FileHandle assetFile = new FileHandle(assetPath);
        String assetId;
        try {
            assetId = games.pixscape.runtime.gameobject.GameObjectAssetId.normalize(assetFile.name());
        } catch (RuntimeException failure) {
            Dialogs.showOKDialog(uiStage, "Game Object cannot be opened",
                    PreviewLaunchSupport.userMessageFor(failure));
            return null;
        }
        var key = new games.pixscape.studio.document.EditorDocumentKey(
                EditorDocumentType.GAME_OBJECT, assetId);
        OpenEditorDocument existing = editorDocumentManager.find(key);
        if (existing instanceof GameObjectEditorDocument gameObject) {
            editorDocumentManager.activate(key);
            return gameObject;
        }
        if (!assetFile.exists() || assetFile.isDirectory()) {
            Dialogs.showOKDialog(uiStage, "Game Object cannot be opened",
                    "The Game Object asset file is missing.");
            return null;
        }

        SceneEditorContext previous = canvas.getAttachedSceneContext();
        SceneEditorContext candidate = null;
        try {
            // Stable IDs exist only inside this isolated asset-editing World and are never
            // published to ProjectConfig or a project Scene.
            candidate = canvas.createSceneContext(null, new SceneMeta());
            canvas.attach(candidate);
            var asset = canvas.getGameObjectAssetService().loadGameObjectAsset(assetFile);
            int layerIndex = candidate.layerService().addLayerTop("Game Object");
            int layerEntityId = candidate.layerService().getLayerEntity(layerIndex);
            // Commit the internal Layer before instantiation: jointed assets normalize their
            // logical order as the last child of the construction composite.
            candidate.world().process();
            if (canvas.getGameObjectAssetService().requiresSpatialLayer(asset)) {
                candidate.historyManager().execute(new ToggleSpatialActorLayerCommand(
                        candidate.world(), candidate.historyManager().historyIds(),
                        candidate.layerService(), layerEntityId, true));
            }
            candidate.selectionService().setActivelayerId(layerEntityId);

            var result = canvas.getGameObjectAssetService().instantiateGameObject(
                    assetFile, assetId, layerIndex, 0f, 0f);
            RenderRebindHelper.rebindEntitiesAfterAtlasChange(
                    canvas, null, canvas.getAssetVisualResolver(), result.createdIds(), null);
            candidate.world().process();
            int rootEntityId = result.sourceToCreated().get(asset.rootSourceEntityId, -1);
            if (rootEntityId < 0) {
                throw new IllegalStateException("Game Object root could not be materialized for editing.");
            }
            candidate.selectionService().selectOnly(rootEntityId);
            canvas.focusCameraAt(0f, 0f);
            // Loading an asset establishes the document baseline; the temporary construction is not undoable.
            candidate.historyManager().clear();
            candidate.markSaved();

            String title = assetFile.nameWithoutExtension();
            return editorDocumentManager.openGameObject(new GameObjectEditorDocument(
                    assetId, title, assetFile, candidate, rootEntityId));
        } catch (RuntimeException failure) {
            if (candidate != null) {
                canvas.releaseSceneContext(candidate);
                candidate.dispose();
            }
            if (previous != null && !previous.isDisposed()) canvas.attach(previous);
            Dialogs.showOKDialog(uiStage, "Game Object cannot be opened",
                    PreviewLaunchSupport.userMessageFor(failure));
            return null;
        }
    }

    /** Publishes only the isolated Game Object edit context; no project Scene is read or changed. */
    private void saveGameObjectDocument(GameObjectEditorDocument document) {
        SceneEditorContext context = document.context();
        IntArray root = new IntArray(new int[]{document.rootEntityId()});
        var graph = new EntityGraphCaptureService(context.world()).captureForGameObject(root);
        new GameObjectAssetService(
                context.world(), null, null, null, null, null,
                canvas.physicsServiceFor(context)).saveGameObject(document.assetFile(), graph);
        context.markSaved();
        EventFlow.i().publish(new EventFlow.GameObjectsChanged(EventFlow.tag(this)));
    }

    private void saveAllDirtyGameObjectDocuments() {
        for (OpenEditorDocument document : editorDocumentManager.documents()) {
            if (document instanceof GameObjectEditorDocument gameObject && gameObject.isDirty()) {
                saveGameObjectDocument(gameObject);
            }
        }
    }

    /** Routes Save to the exact active editor document. */
    public void saveActiveDocumentWithProgress(Runnable onSuccess, Consumer<Throwable> onFailure) {
        editorCommandRouter.save(onSuccess, onFailure);
    }

    public boolean undoActiveDocument() {
        if (studioEditingModeService != null && studioEditingModeService.isHudTestMode()) return false;
        try {
            return editorCommandRouter.undo();
        } catch (RuntimeException failure) {
            showHudCommandFailure(failure);
        }
        return false;
    }

    public boolean redoActiveDocument() {
        if (studioEditingModeService != null && studioEditingModeService.isHudTestMode()) return false;
        try {
            return editorCommandRouter.redo();
        } catch (RuntimeException failure) {
            showHudCommandFailure(failure);
        }
        return false;
    }

    private void showHudCommandFailure(Throwable failure) {
        Dialogs.showOKDialog(uiStage, "HUD edit failed", PreviewLaunchSupport.userMessageFor(failure));
    }

    public Stage getUiStage() {
        return uiStage;
    }

    public BottomMenuBar getBottomBar() {
        return bottomMenuBar;
    }

    public DockManager getDockManager() {
        return dockManager;
    }

    public ItemTreePanel getItemTreePanel() {
        return itemTreePanel;
    }

    public AssetsPanel getAssetsPanel() {
        return assetsPanel;
    }

    public SceneService getSceneService() {
        return sceneService;
    }

    public AnimationAssetAuthoringService getAnimationAssetAuthoringService() {
        return animationAssetAuthoringService;
    }

    public SceneHudRuntimePreparationService getSceneHudRuntimePreparationService() {
        return sceneHudRuntimePreparationService;
    }

    public boolean isSceneHudCompositionVisible(String sceneIdentity) {
        return sceneHudCompositionRenderer == null
                || sceneHudCompositionRenderer.isVisible(sceneIdentity);
    }

    public void setSceneHudCompositionVisible(String sceneIdentity, boolean visible) {
        if (sceneHudCompositionRenderer != null) {
            sceneHudCompositionRenderer.setVisible(sceneIdentity, visible);
        }
    }

    /** Project lifecycle boundary for Scene-local HUD Runtime preparation. */
    public void onHudRuntimeProjectChanging() {
        if (studioEditingModeService != null) studioEditingModeService.setHudTestMode(false);
        if (sceneHudCompositionRenderer != null) sceneHudCompositionRenderer.onProjectChanging();
        if (sceneHudRuntimePreparationService != null) sceneHudRuntimePreparationService.onProjectChanging();
    }

    /** Binds future HUD Runtime requests after the new project is authoritative; no eager builds. */
    public void bindHudRuntimeProject(FileHandle projectDir) {
        if (sceneHudRuntimePreparationService != null) sceneHudRuntimePreparationService.bindProject(projectDir);
    }

    public ShapeDrawer getDrawer() {
        return drawer;
    }


    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        if (sceneHudRuntimePreparationService != null) sceneHudRuntimePreparationService.update();
        refreshDisplayMetrics();
        if (!hasRenderableDisplay()) return;

        ensureStudioUiViewport();
        Gdx.gl.glClearColor(0.12f, 0.13f, 0.15f, 0f);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

        uiStage.act(dt);

        OpenEditorDocument activeDocument = editorDocumentManager.activeDocument();
        sceneHudAssetDropController.observeActiveDocument();
        hudAssetDropController.observeActiveDocument();
        if (isWorldDocument(activeDocument)) {
            hudAssetDropController.leaveHudTarget();
        }
        boolean centerResolved = !previewActive && resolveCenterBoundsLogical(
                isWorldDocument(activeDocument));
        // IMPORTANT: disable the editor scene during preview.
        if (centerResolved) {
            if (activeDocument instanceof HudScreenEditorDocument) {
                hudCanvasInputHost.coverStageBounds(centerBoundsLogical);
                hudEditorSession.act(dt);
                hudEditorSession.draw(centerBoundsLogical);
                EventFlow.i().flush();
            } else if (isWorldDocument(activeDocument)) {
                canvas.resize(
                        Math.round(centerBoundsLogical.x),
                        Math.round(centerBoundsLogical.y),
                        Math.round(centerBoundsLogical.width),
                        Math.round(centerBoundsLogical.height));
                canvas.getGridStage().getViewport().apply(false);
                canvas.act(dt);
                canvas.draw();
                if (activeDocument instanceof SceneEditorDocument
                        && sceneHudCompositionRenderer != null) {
                    try {
                        sceneHudCompositionRenderer.render(
                                activeDocument.key().domainId(),
                                hudCanvasBounds(centerStackBoundsLogical,
                                        dockManager.isRulersVisible(), sceneHudBoundsLogical),
                                dt);
                    } finally {
                        canvas.getGridStage().getViewport().apply(false);
                    }
                }
            }
        }

        if (!isWorldDocument(activeDocument)) {
            hudAssetDropController.update(displayMetrics.logicalHeight(),
                    centerResolved && activeDocument instanceof HudScreenEditorDocument
                            && isHudDropUnobstructed());
        }

        if (!previewActive && isWorldDocument(editorDocumentManager.activeDocument())) {
            canvas.cancelDndReleaseIfOutsideCanvas();
        }

        // The Studio UI owns the full window and is always the final pass.
        uiStage.getViewport().apply(false);
        uiStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        refreshDisplayMetrics();
        if (width > 0 && height > 0) uiStage.getViewport().update(width, height, true);
        root.invalidateHierarchy();
    }

    private boolean hasRenderableDisplay() {
        return displayMetrics.logicalWidth() > 0 && displayMetrics.logicalHeight() > 0
                && displayMetrics.framebufferWidth() > 0 && displayMetrics.framebufferHeight() > 0;
    }

    private void ensureStudioUiViewport() {
        var viewport = uiStage.getViewport();
        if (viewport.getScreenX() != 0 || viewport.getScreenY() != 0
                || viewport.getScreenWidth() != displayMetrics.logicalWidth()
                || viewport.getScreenHeight() != displayMetrics.logicalHeight()) {
            // The full-window UI camera is intentionally centered only when its size changes.
            viewport.update(displayMetrics.logicalWidth(), displayMetrics.logicalHeight(), true);
        }
        viewport.apply(false);
    }

    private boolean resolveCenterBoundsLogical(boolean sceneDocument) {
        Actor center = dockManager != null ? dockManager.getCenterStack() : null;
        if (center == null || center.getStage() != uiStage) return false;

        validateLayoutAncestry(center);
        centerOriginLogical.set(0f, 0f);
        center.localToStageCoordinates(centerOriginLogical);
        if (!Float.isFinite(centerOriginLogical.x) || !Float.isFinite(centerOriginLogical.y)
                || !Float.isFinite(center.getWidth()) || !Float.isFinite(center.getHeight())) {
            return false;
        }

        int x = Math.round(centerOriginLogical.x);
        int y = Math.round(centerOriginLogical.y);
        int centerRight = Math.round(centerOriginLogical.x + center.getWidth());
        Actor dockRoot = dockManager.getRoot();
        dockRootRightLogical.set(dockRoot.getWidth(), 0f);
        dockRoot.localToStageCoordinates(dockRootRightLogical);
        if (!Float.isFinite(dockRootRightLogical.x)) return false;
        // A docked panel may retain the center host's preferred width for one layout pass while
        // its floating window is being created.  World canvas already uses the Dock root's edge;
        // HUD must use that same available workspace rectangle.
        int right = Math.round(dockRootRightLogical.x);
        int top = Math.round(centerOriginLogical.y + center.getHeight());
        centerStackBoundsLogical.set(x, y, centerRight - x, top - y);
        centerBoundsLogical.set(x, y, right - x, top - y);
        if (!sceneDocument) {
            hudCanvasBounds(centerBoundsLogical, dockManager.isRulersVisible(), centerBoundsLogical);
        }
        if (centerBoundsLogical.x < 0 || centerBoundsLogical.y < 0
                || centerBoundsLogical.width <= 0 || centerBoundsLogical.height <= 0
                || centerBoundsLogical.x + centerBoundsLogical.width > displayMetrics.logicalWidth()
                || centerBoundsLogical.y + centerBoundsLogical.height > displayMetrics.logicalHeight()) {
            return false;
        }
        return true;
    }

    static Rectangle hudCanvasBounds(Rectangle centerBounds, boolean rulersVisible, Rectangle out) {
        out.set(centerBounds);
        if (!rulersVisible) return out;
        out.x += RulerActor.LEFT_WIDTH;
        out.width -= RulerActor.LEFT_WIDTH;
        out.height -= RulerActor.TOP_HEIGHT;
        return out;
    }

    private boolean isHudDropUnobstructed() {
        hudDropUiCoords.set(Gdx.input.getX(), Gdx.input.getY());
        uiStage.screenToStageCoordinates(hudDropUiCoords);
        return isHudDropUiHitAllowed(
                uiStage.hit(hudDropUiCoords.x, hudDropUiCoords.y, true), hudCanvasInputHost);
    }

    static boolean isHudDropUiHitAllowed(Actor hit, HudCanvasInputHost inputHost) {
        return hit == null || hit == inputHost;
    }

    private static void validateLayoutAncestry(Actor actor) {
        if (actor == null) return;
        validateLayoutAncestry(actor.getParent());
        if (actor instanceof Layout layout) layout.validate();
    }

    private void refreshDisplayMetrics() {
        float previousScaleX = displayMetrics.scaleX();
        float previousScaleY = displayMetrics.scaleY();
        displayMetrics.update(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight(),
                Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight()
        );

        boolean scaleChanged = Math.abs(displayMetrics.scaleX() - previousScaleX) > 0.0001f
                || Math.abs(displayMetrics.scaleY() - previousScaleY) > 0.0001f;
        if (!displayMetricsInitialized || scaleChanged) {
            displayMetricsInitialized = true;
            Gdx.app.log("StudioDisplay", String.format(
                    java.util.Locale.ROOT,
                    "logical=%dx%d framebuffer=%dx%d scale=%.2fx%.2f HiDPI=%s",
                    displayMetrics.logicalWidth(),
                    displayMetrics.logicalHeight(),
                    displayMetrics.framebufferWidth(),
                    displayMetrics.framebufferHeight(),
                    displayMetrics.scaleX(),
                    displayMetrics.scaleY(),
                    displayMetrics.isHiDpi() ? "yes" : "no"
            ));
        }
    }


    @Override
    public void dispose() {
        if (disposing) return;
        disposing = true;
        dumpLiveNonDaemonThreads("dispose:start");
        HtmlPreviewLauncher.stop();
        if (sceneHudCompositionRenderer != null) {
            sceneHudCompositionRenderer.close();
            sceneHudCompositionRenderer = null;
        }
        if (sceneHudRuntimePreparationService != null) {
            sceneHudRuntimePreparationService.close();
            sceneHudRuntimePreparationService = null;
        }
        sceneHudAssetDropController = null;
        sceneHudAssociationService = null;
        if (hudAssetDropController != null) {
            hudAssetDropController.close();
            hudAssetDropController = null;
        }
        if (hudWidgetDragController != null) {
            hudWidgetDragController.close();
            hudWidgetDragController = null;
        }
        if (hudImageAuthoring != null) {
            hudImageAuthoring.close();
            hudImageAuthoring = null;
        }
        if (editorDocumentManager != null) {
            if (canvas != null) canvas.detach();
            for (OpenEditorDocument document : editorDocumentManager.documents()) {
                SceneEditorContext context = documentContext(document);
                if (context != null) {
                    if (itemTreePanel != null) itemTreePanel.releaseSceneContext(context);
                    if (propertiesPanel != null) propertiesPanel.releaseSceneContext(context);
                    if (canvas != null) canvas.releaseSceneContext(context);
                }
            }
            editorDocumentManager.clearForTeardown();
        }
        if (hudEditorSession != null) {
            hudEditorSession.dispose();
            hudEditorSession = null;
        }
        if (canvasModeControls != null) {
            canvasModeControls.dispose();
            canvasModeControls = null;
        }
        if (dockManager != null) {
            dockManager.dispose();
        }
        if (canvas != null) {
            canvas.dispose();
            canvas = null;
        }
        if (uiStage != null) {
            uiStage.dispose();
            uiStage = null;
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
        dumpLiveNonDaemonThreads("dispose:end");
        StudioLogCapture.restorePreviousLogger();
    }

    private void bindEditorDocumentLifecycle() {
        editorDocumentManager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                applyDocumentActivation(previous, current);
            }
            @Override public void documentClosed(OpenEditorDocument document) {
                SceneEditorContext closedContext = documentContext(document);
                if (closedContext != null) {
                    if (itemTreePanel != null) itemTreePanel.releaseSceneContext(closedContext);
                    if (propertiesPanel != null) propertiesPanel.releaseSceneContext(closedContext);
                    canvas.releaseSceneContext(closedContext);
                    if (document instanceof SceneEditorDocument scene
                            && mostRecentSceneEditorContext == scene.context()) {
                        mostRecentSceneEditorContext = editorDocumentManager.documents().stream()
                                .filter(SceneEditorDocument.class::isInstance)
                                .map(SceneEditorDocument.class::cast)
                                .map(SceneEditorDocument::context)
                                .filter(context -> !context.isDisposed())
                                .reduce((first, second) -> second)
                                .orElse(null);
                        if (mostRecentSceneEditorContext != null) {
                            editorDocumentManager.documents().stream()
                                    .filter(SceneEditorDocument.class::isInstance)
                                    .map(SceneEditorDocument.class::cast)
                                    .filter(candidate -> candidate.context() == mostRecentSceneEditorContext)
                                    .findFirst()
                                    .ifPresent(StudioApplicationAdapter.this::updateCurrentSceneCompatibility);
                        }
                    }
                }
            }
        });
        editorDocumentManager.setDirtySceneCloseHandler(this::requestDirtySceneClose);
        editorDocumentManager.setDirtyHudCloseHandler(this::requestDirtyHudClose);
        editorDocumentManager.setDirtyGameObjectCloseHandler(this::requestDirtyGameObjectClose);
        EventFlow.i().subscribe(EventFlow.SceneNameChanged.class, event -> {
            ProjectConfig cfg = ProjectConfig.getInstance();
            if (cfg == null) return;
            String canonical = cfg.canonicalSceneTag(event.newName());
            if (canonical == null) return;
            editorDocumentManager.updateTitle(
                    new games.pixscape.studio.document.EditorDocumentKey(
                            EditorDocumentType.SCENE, canonical),
                    event.newName());
        });
        hudEditorSession.addListener(this::captureActiveHudState);
    }

    private void applyDocumentActivation(OpenEditorDocument previous, OpenEditorDocument current) {
        if (disposing) return;
        if (studioEditingModeService != null) studioEditingModeService.setHudTestMode(false);
        SceneEditorContext previousContext = documentContext(previous);
        if (previousContext != null && canvas.isAttached(previousContext)) {
            EventFlow.i().flush();
        }
        captureHudState(previous);

        if (previousContext != null && canvas.isAttached(previousContext)) {
            previousContext.rememberSceneSubmode(
                    canvas.getStudioEditingModeService().getCurrentMode());
        }

        if (current instanceof HudScreenEditorDocument hud) {
            canvas.detach();
            if (toolBar != null) toolBar.suspendSceneContext();
            hudCanvasInputHost.activateHudInput();
            String selectedNodeId = hud.selectedNodeId();
            if (hudEditorSession == null) {
                projectDocumentEditingMode(
                        canvas.getStudioEditingModeService(), current, EventFlow.tag(this));
                enterHudRulerProjection();
                return;
            }
            hudEditorSession.suspend();
            FileHandle projectDir = StudioFs.requireStudioProjectDir(ProjectConfig.getInstance());
            hudEditorSession.open(projectDir, hud);
            hudEditorSession.selectNode(selectedNodeId);
            hud.capture(hudEditorSession);
            projectDocumentEditingMode(
                    canvas.getStudioEditingModeService(), current, EventFlow.tag(this));
            enterHudRulerProjection();
            return;
        }

        if (hudEditorSession != null) hudEditorSession.suspend();
        hudCanvasInputHost.suspendHudInput();
        SceneEditorContext currentContext = documentContext(current);
        if (currentContext != null) {
            if (current instanceof SceneEditorDocument scene) updateCurrentSceneCompatibility(scene);
            canvas.attach(currentContext);
            projectDocumentEditingMode(
                    canvas.getStudioEditingModeService(), current, EventFlow.tag(this));
            if (current instanceof SceneEditorDocument) mostRecentSceneEditorContext = currentContext;
            rebindScenePanels(currentContext, current instanceof SceneEditorDocument);
        } else {
            canvas.detach();
            if (toolBar != null) toolBar.suspendSceneContext();
            projectDocumentEditingMode(
                    canvas.getStudioEditingModeService(), null, EventFlow.tag(this));
        }
        leaveHudRulerProjection();
        if (current == null && hudEditorSession != null) hudEditorSession.close();
    }

    static void projectDocumentEditingMode(StudioEditingModeService modeService,
                                           OpenEditorDocument document,
                                           int sourceTag) {
        if (document instanceof SceneEditorDocument scene) {
            modeService.activateSceneDocument(scene.context().sceneSubmode(), sourceTag);
        } else if (document instanceof GameObjectEditorDocument gameObject) {
            modeService.activateSceneDocument(gameObject.context().sceneSubmode(), sourceTag);
        } else if (document instanceof HudScreenEditorDocument) {
            modeService.activateHudDocument(sourceTag);
        } else {
            modeService.deactivateDocument(sourceTag);
        }
    }

    private void updateCurrentSceneCompatibility(SceneEditorDocument document) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return;
        for (String name : cfg.getSceneNames()) {
            if (document.key().domainId().equals(cfg.canonicalSceneTag(name))) {
                cfg.setCurrentSceneByName(name);
                return;
            }
        }
    }

    private void rebindScenePanels(SceneEditorContext context, boolean projectScene) {
        if (toolBar != null) toolBar.bindSceneContext(context);
        if (itemTreePanel != null) itemTreePanel.bindSceneContext(context);
        if (propertiesPanel != null) propertiesPanel.bindSceneContext(context);
        if (layersPanel != null) layersPanel.bindSceneContext(context);
        if (bottomMenuBar != null) bottomMenuBar.refreshSelectBox();
        if (projectScene && sceneService != null) sceneService.pushActiveSceneMetaToUi();
    }

    private void requestDirtySceneClose(SceneEditorDocument document) {
        Dialogs.showOptionDialog(
                uiStage,
                "Unsaved Scene",
                "Save changes to \"" + document.title() + "\" before closing?",
                Dialogs.OptionDialogType.YES_NO_CANCEL,
                new OptionDialogListener() {
                    @Override public void yes() {
                        sceneService.saveSceneDocumentWithProgress(
                                document,
                                () -> editorDocumentManager.closeNow(document.key()),
                                throwable -> Dialogs.showOKDialog(
                                        uiStage, "Save failed",
                                        PreviewLaunchSupport.userMessageFor(throwable)));
                    }
                    @Override public void no() {
                        editorDocumentManager.closeNow(document.key());
                    }
                    @Override public void cancel() { }
                });
    }

    private void requestDirtyHudClose(HudScreenEditorDocument document) {
        requestDirtyAssetClose(document, "Unsaved HUD", () -> {
            hudDocumentPersistenceService.save(
                    StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()), document);
            hudEditorSession.refreshDocumentMetadata(document);
        });
    }

    private void requestDirtyGameObjectClose(GameObjectEditorDocument document) {
        requestDirtyAssetClose(document, "Unsaved Game Object", () -> saveGameObjectDocument(document));
    }

    private void requestDirtyAssetClose(OpenEditorDocument document, String dialogTitle, Runnable save) {
        Dialogs.showOptionDialog(
                uiStage,
                dialogTitle,
                "Save changes to \"" + document.title() + "\" before closing?",
                Dialogs.OptionDialogType.YES_NO_CANCEL,
                new OptionDialogListener() {
                    @Override public void yes() {
                        try {
                            save.run();
                            editorDocumentManager.closeNow(document.key());
                        } catch (RuntimeException failure) {
                            Dialogs.showOKDialog(uiStage, "Save failed",
                                    PreviewLaunchSupport.userMessageFor(failure));
                        }
                    }
                    @Override public void no() { editorDocumentManager.closeNow(document.key()); }
                    @Override public void cancel() { }
                });
    }

    private static SceneEditorContext documentContext(OpenEditorDocument document) {
        if (document instanceof SceneEditorDocument scene) return scene.context();
        if (document instanceof GameObjectEditorDocument gameObject) return gameObject.context();
        return null;
    }

    private static boolean isWorldDocument(OpenEditorDocument document) {
        return document instanceof SceneEditorDocument || document instanceof GameObjectEditorDocument;
    }

    private void captureActiveHudState() {
        captureHudState(editorDocumentManager.activeDocument());
    }

    private void captureHudState(OpenEditorDocument document) {
        if (document instanceof HudScreenEditorDocument hud) hud.capture(hudEditorSession);
    }

    private void enterHudRulerProjection() {
        if (!hudRulerStateCaptured) {
            rulersVisibleBeforeHud = dockManager.isRulersVisible();
            hudRulerStateCaptured = true;
        }
        if (hudEditorSession.hudCamera() != null && hudEditorSession.hudViewport() != null) {
            rulerTop.setProjection(hudEditorSession.hudCamera(), hudEditorSession.hudViewport());
            rulerLeft.setProjection(hudEditorSession.hudCamera(), hudEditorSession.hudViewport());
        }
        dockManager.setRulersVisible(rulersVisibleBeforeHud);
        restoreCanvasModeControls(canvasModeControls);
    }

    public boolean hudCanvasActive() {
        return editorDocumentManager.activeDocument() instanceof HudScreenEditorDocument;
    }

    public boolean hudTestModeActive() {
        return studioEditingModeService != null && studioEditingModeService.isHudTestMode();
    }

    /** Performs the HUD-only Delete shortcut when focus belongs to its canvas or hierarchy. */
    public boolean deleteFocusedHudNode(Actor focus) {
        if (!hudCanvasActive() || studioEditingModeService.isHudTestMode()
                || hasVisibleModalDialog(uiStage != null ? uiStage.getRoot() : null)
                || !ownsHudKeyboardFocus(focus)) return false;
        return hudEditorSession.deleteSelectedNode();
    }

    private boolean ownsHudKeyboardFocus(Actor focus) {
        for (Actor actor = focus; actor != null; actor = actor.getParent()) {
            if (actor == hudCanvasInputHost) return true;
        }
        return itemTreePanel != null && itemTreePanel.ownsHudKeyboardFocus(focus);
    }

    private static boolean hasVisibleModalDialog(Actor actor) {
        if (actor instanceof Dialog dialog && dialog.isVisible() && dialog.isModal()) return true;
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                if (hasVisibleModalDialog(child)) return true;
            }
        }
        return false;
    }

    public void centerActiveCanvasCamera() {
        if (hudCanvasActive()) {
            hudEditorSession.centerHudCamera();
            bottomMenuBar.setPan(hudEditorSession.hudCameraX(), hudEditorSession.hudCameraY());
        } else {
            canvas.centerCamera();
        }
    }

    public void setActiveCanvasRulersVisible(boolean visible) {
        dockManager.setRulersVisible(visible);
        restoreCanvasModeControls(canvasModeControls);
    }

    private void leaveHudRulerProjection() {
        if (!hudRulerStateCaptured) return;
        rulerTop.clearProjection();
        rulerLeft.clearProjection();
        dockManager.setRulersVisible(rulersVisibleBeforeHud);
        restoreCanvasModeControls(canvasModeControls);
        hudRulerStateCaptured = false;
    }

    private void dumpLiveNonDaemonThreads(String phase) {
        if (!DEBUG_SHUTDOWN) return;
        if (Gdx.app == null) return;
        Thread current = Thread.currentThread();
        Thread.getAllStackTraces().forEach((thread, stack) -> {
            if (thread == null) return;
            if (!thread.isAlive()) return;
            if (thread.isDaemon()) return;

            String msg = "Live non-daemon thread [" + phase + "] " +
                    "name=" + thread.getName() +
                    " id=" + thread.getId() +
                    " state=" + thread.getState() +
                    " current=" + (thread == current);
            Gdx.app.log("ShutdownDiag", msg);

            for (StackTraceElement ste : stack) {
                Gdx.app.log("ShutdownDiag", "  at " + ste);
            }
        });
    }

    public void setPreviewActive(boolean active) {
        if (this.previewActive == active) return;
        if (active && studioEditingModeService != null) studioEditingModeService.setHudTestMode(false);
        this.previewActive = active;

        if (!active) {
            restoreStudioShadersAfterPreview();
        }

        // Optional but convenient: update the button
        if (bottomMenuBar != null) {
            bottomMenuBar.setPreviewRunning(active);
        }
    }

    private void restoreStudioShadersAfterPreview() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = cfg != null
                && cfg.projectFileName != null
                && !cfg.projectFileName.isBlank()
                ? StudioFs.requireStudioProjectDir(cfg)
                : null;
        ShaderRegistry.reloadForProject(projectDir, StudioFs.DIR_ORIG_SHADERS);
        EventFlow.i().publish(new EventFlow.ShaderListChanged(EventFlow.tag(this)));
    }

    public boolean isPreviewActive() {
        return previewActive;
    }

    private void syncHudTestInputSurface() {
        if (hudCanvasInputHost == null || editorDocumentManager == null) return;
        boolean testing = studioEditingModeService != null && studioEditingModeService.isHudTestMode();
        if (inputMultiplexer != null && canvas != null) {
            setWorldInputEnabled(inputMultiplexer, canvas.getGridStage(), !testing);
        }
        if (testing) {
            hudCanvasInputHost.suspendHudInput();
            if (dockManager != null && !hudTestRulerStateCaptured) {
                rulersVisibleBeforeHudTest = dockManager.isRulersVisible();
                hudTestRulerStateCaptured = true;
                dockManager.setRulersVisible(false);
                restoreCanvasModeControls(canvasModeControls);
            }
        } else if (hudCanvasActive()) {
            hudCanvasInputHost.activateHudInput();
            restoreHudEditRulersAfterTest();
        } else {
            restoreHudEditRulersAfterTest();
        }
    }

    private void restoreHudEditRulersAfterTest() {
        if (!hudTestRulerStateCaptured || dockManager == null) return;
        dockManager.setRulersVisible(rulersVisibleBeforeHudTest);
        restoreCanvasModeControls(canvasModeControls);
        hudTestRulerStateCaptured = false;
    }

    static void setWorldInputEnabled(InputMultiplexer multiplexer, Stage worldStage,
                                     boolean enabled) {
        boolean present = multiplexer.getProcessors().contains(worldStage, true);
        if (enabled && !present) multiplexer.addProcessor(worldStage);
        else if (!enabled && present) multiplexer.removeProcessor(worldStage);
    }
}
