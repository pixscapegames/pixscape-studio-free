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
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.ui.modal.Dialogs;
import com.kotcrab.vis.ui.util.dialog.OptionDialogListener;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.OsFilesDropTarget;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.CursorDrawHelper;
import games.pixscape.studio.helper.ShapeHelper;
import games.pixscape.studio.helper.StudioHomeBootstrap;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.logging.StudioLogCapture;
import games.pixscape.studio.logging.StudioLogLevel;
import games.pixscape.studio.service.ProjectOpenFailure;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.ui.asset.AssetsPanel;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.docking.DockSlot;
import games.pixscape.studio.ui.layer.LayersPanel;
import games.pixscape.studio.ui.preview.HtmlPreviewLauncher;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.tree.ItemTreePanel;
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
    private SceneService sceneService;
    private ShapeDrawer drawer;
    private boolean previewActive = false;

    private VisTable root;

    private final Array<OsFilesDropTarget> osDropTargets = new Array<>();
    private String preloadLastProjectWarning;

    @Override
    public void create() {
        StudioLogCapture.install();
        StudioLogLevel.applyCurrentToGdx();
        StudioHomeBootstrap.ensureExists();

        Skin skin = new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json"));
        VisUI.load(skin);
        BitmapFont font = VisUI.getSkin().getFont("default-font");

        EditorSettings.load();

        FileHandle lastProjectFile = tryResolveLastProjectFileForStartup();

        ProjectConfig cfg = ProjectConfig.getInstance();

        root = new VisTable();
        root.setTouchable(Touchable.childrenOnly);
        uiStage = new Stage(new ScreenViewport());
        drawer = ShapeHelper.newDrawer(uiStage.getBatch());

        FileHandle projectDir = cfg.projectFileName != null && !cfg.projectFileName.isBlank()
                ? StudioFs.requireStudioProjectDir(cfg)
                : null;

        ShaderRegistry.reloadForProject(
                projectDir,
                StudioFs.DIR_ORIG_SHADERS
        );

        CursorDrawHelper.init();

        canvas = new WorldCanvas(this, drawer);

        // ---------------------------------------------------------
        // UI docking etc (unchanged)
        // ---------------------------------------------------------

        // ~100 px entre majeures
        RulerActor rulerTop = new RulerActor(
                RulerActor.Orientation.TOP,
                (OrthographicCamera) canvas.getGridStage().getCamera(),
                canvas.getCoordSpaces(),
                drawer, font
        ).setThicknessPx(22f).setTargetMajorPx(100).setMinorsPerMajor(1);

        RulerActor rulerLeft = new RulerActor(
                RulerActor.Orientation.LEFT,
                (OrthographicCamera) canvas.getGridStage().getCamera(),
                canvas.getCoordSpaces(),
                drawer, font
        ).setThicknessPx(22f).setTargetMajorPx(100).setMinorsPerMajor(1);

        dockManager = new DockManager(this, rulerLeft, rulerTop);
        sceneService = new SceneService(this, canvas);
        canvas.bindAssetMetaLookup(sceneService::getAssetMeta);
        canvas.getEditorOps().setSceneService(sceneService);

        ItemTreePanel itemTreePanel = new ItemTreePanel(this);
        itemTreePanel.setPreferredWindowSize(362, 600);
        dockManager.register(itemTreePanel, DockSlot.LEFT, true);

        PropertiesPanel propertiesPanel = new PropertiesPanel(this);
        itemTreePanel.bindPropertiesPanel(propertiesPanel);
        propertiesPanel.setPreferredWindowSize(362, 600);
        dockManager.register(propertiesPanel, DockSlot.RIGHT_TOP, true);

        LayersPanel layersPanel = new LayersPanel(this);
        layersPanel.setPreferredWindowSize(362, 500);
        dockManager.register(layersPanel, DockSlot.RIGHT_BOTTOM, true);

        AssetsPanel assetsPanel = new AssetsPanel(this);
        assetsPanel.setPreferredWindowSize(900, 500);
        assetsPanel.reloadFromProject(cfg);
        dockManager.register(assetsPanel, DockSlot.BOTTOM, true);
        sceneService.setAssetsPanel(assetsPanel);

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

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(canvas.getGridStage());
        multiplexer.addProcessor(canvas.getInputState());
        Gdx.input.setInputProcessor(multiplexer);

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
                && sceneService.requiresSaveBeforeLeavingCurrentScene();
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
                (onSuccess, onFailure) -> sceneService.saveProjectAndCurrentSceneWithProgress(
                        uiStage,
                        onSuccess,
                        onFailure
                )
        );
    }

    public WorldCanvas getCanvas() {
        return canvas;
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

    public SceneService getSceneService() {
        return sceneService;
    }

    public ShapeDrawer getDrawer() {
        return drawer;
    }


    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        Gdx.gl.glClearColor(0.12f, 0.13f, 0.15f, 0f);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

        // IMPORTANT: disable the editor scene during preview
        if (!previewActive) {
            canvas.act(dt);
            canvas.draw();
        }

        uiStage.act(dt);
        if (!previewActive) {
            canvas.cancelDndReleaseIfOutsideCanvas();
        }
        uiStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);
        root.invalidateHierarchy();
        canvas.resize(width, height);
    }


    @Override
    public void dispose() {
        dumpLiveNonDaemonThreads("dispose:start");
        HtmlPreviewLauncher.stop();
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
        this.previewActive = active;

        // Optional but convenient: update the button
        if (bottomMenuBar != null) {
            bottomMenuBar.setPreviewRunning(active);
        }
    }

    public boolean isPreviewActive() {
        return previewActive;
    }
}
