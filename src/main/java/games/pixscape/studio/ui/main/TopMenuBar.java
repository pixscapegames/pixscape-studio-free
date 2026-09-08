package games.pixscape.studio.ui.main;

import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.modal.StudioFileChooser;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.ui.modal.Dialogs;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileTypeFilter;
import com.kotcrab.vis.ui.widget.file.StreamingFileChooserListener;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.studio.BuildInfo;
import games.pixscape.studio.PixscapeStudioApplication;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.ProjectOpenFailure;
import games.pixscape.studio.service.RecentProjectsService;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.ui.asset.ImportDialog;
import games.pixscape.studio.ui.config.ProjectSettingsWindow;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.importer.TmxImportDialog;
import games.pixscape.studio.ui.importer.TmxImportMessageDialog;
import games.pixscape.studio.ui.importer.TmxImportUiSupport;
import games.pixscape.studio.ui.log.LogWindow;
import games.pixscape.studio.ui.shaders.ShaderManagerDialog;
import games.pixscape.studio.ui.widget.CheckBoxMenuItem;

import java.util.List;
import java.util.Optional;

public class TopMenuBar extends MenuBar {

    private final StudioApplicationAdapter app;
    private final SceneService sceneService;
    private final RecentProjectsService recentProjectsService;

    private final ObjectMap<DockablePanel, CheckBoxMenuItem> panelToCheck = new ObjectMap<>();

    private final MenuItem projectSettings;

    private final MenuItem importMenuItem;
    private final MenuItem save;
    private final MenuItem saveAs;
    private final PopupMenu recentProjectsMenu;

    private final Menu editMenu;
    private final Menu resourcesMenu;

    private static final String FILE_PREF_NAME = "project.json";

    public TopMenuBar(StudioApplicationAdapter app, DockManager dockManager, SceneService sceneService) {
        this.app = app;
        this.sceneService = sceneService;
        this.recentProjectsService = new RecentProjectsService();

        final Menu file = new Menu("File");

        // --- New Project / New Scene ---
        final MenuItem newScene = new MenuItem("New");
        onClick(newScene, () -> runWithSaveIfDirty(this::newProject));

        // --- Shared FileChooser for Open ---
        final FileChooser chooser = getChooser();
        chooser.setListener(new StreamingFileChooserListener() {
            @Override
            public void selected(FileHandle file) {
                Optional<ProjectOpenFailure> failure = sceneService.tryOpenProject(file, "open project");
                if (failure.isEmpty()) {
                    refreshRecentProjectsMenu();
                    beginProject();
                } else {
                    Gdx.app.error("TopMenuBar", "Project open failed: " + file, failure.get().cause());
                    onStart();
                    Dialogs.showOKDialog(
                            app.getUiStage(),
                            "Project not loaded",
                            failure.get().message()
                    );
                }
            }
        });

        // --- Open Project ---
        final MenuItem open = new MenuItem("Open...");
        onClick(open, () -> runWithSaveIfDirty(() -> {
            chooser.setMode(FileChooser.Mode.OPEN);
            chooser.setSelectionMode(FileChooser.SelectionMode.FILES);
            chooser.setPrefsName(FILE_PREF_NAME);
            chooser.setDirectory(StudioFs.defaultUserProjectsRoot());

            getTable().getStage().addActor(chooser.fadeIn());
        }));

        recentProjectsMenu = new PopupMenu();
        final MenuItem recentProjects = new MenuItem("Recent Projects");
        recentProjects.setSubMenu(recentProjectsMenu);
        recentProjects.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                refreshRecentProjectsMenu();
            }
        });
        refreshRecentProjectsMenu();

        PopupMenu importMenu = new PopupMenu();
        importMenuItem = new MenuItem("Import");
        importMenuItem.setSubMenu(importMenu);

        MenuItem importAssetsItem = new MenuItem("Assets...");
        onClick(importAssetsItem, () -> new ImportDialog(
                app,
                items -> sceneService.importAssets(items),
                (directory, profileSettings) -> sceneService.importTilesetDirectory(directory, profileSettings)
        ).show(app.getUiStage()));
        importMenu.addItem(importAssetsItem);

        MenuItem importTmxItem = new MenuItem("Tiled map (.tmx)...");
        onClick(importTmxItem, this::openTmxImportChooser);
        importMenu.addItem(importTmxItem);

        save = new MenuItem("Save");
        onClick(save, () -> runSaveWithProgress(null));

        saveAs = new MenuItem("Save As...");
        onClick(saveAs, this::saveProjectAs);

        projectSettings = new MenuItem("Project settings...");
        onClick(projectSettings, () -> {
            if (ProjectConfig.getInstance() == null) return;
            ProjectSettingsWindow win = new ProjectSettingsWindow(app);
            getTable().getStage().addActor(win.fadeIn());
        });

        MenuItem exit = new MenuItem("Exit");
        onClick(exit, app::closeRequested);

        // --------------------------------------------------------------------
        // EDIT
        // --------------------------------------------------------------------
        editMenu = new Menu("Edit");

        final MenuItem cut = new MenuItem("Cut            ctrl+X");
        final MenuItem copy = new MenuItem("Copy         ctrl+C");
        final MenuItem paste = new MenuItem("Paste         ctrl+V");

        final MenuItem undo = new MenuItem("Undo         ctrl+Z");
        onClick(undo, () -> app.getCanvas().undoHistory());

        final MenuItem redo = new MenuItem("Redo          ctrl+Y");
        onClick(redo, () -> app.getCanvas().redoHistory());

        // --------------------------------------------------------------------
        // RESOURCES
        // --------------------------------------------------------------------
        resourcesMenu = new Menu("Resources");

        MenuItem handleShadersItem = new MenuItem("Shader manager");
        onClick(handleShadersItem, this::onHandleShadersClicked);


        // --------------------------------------------------------------------
        // VIEW
        // --------------------------------------------------------------------
        Menu viewMenu = new Menu("View");

        dockManager.register(new LogWindow(), null, false);

        for (DockablePanel panel : dockManager.getPanels()) {
            CheckBoxMenuItem cb = new CheckBoxMenuItem(panel.getTitleText(), panel.isVisible());
            panelToCheck.put(panel, cb);

            cb.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    boolean checked = cb.check.isChecked();

                    if (panel.getDockMode() == DockablePanel.DockMode.WINDOW_ONLY) {
                        if (checked) {
                            dockManager.undock(panel);   // opens the window
                        } else {
                            dockManager.hide(panel);     // closes the window
                        }
                    } else {
                        // dockable classique
                        if (checked) {
                            dockManager.show(panel);
                        } else {
                            dockManager.hide(panel);
                        }
                    }
                }
            });

            viewMenu.addItem(cb);
        }

        // Sync checkbox if dockManager changes visibility elsewhere
        dockManager.addListener((panel, visible) -> {
            CheckBoxMenuItem cb = panelToCheck.get(panel);
            if (cb == null) return;
            cb.check.setProgrammaticChangeEvents(false);
            cb.check.setChecked(visible);
            cb.check.setProgrammaticChangeEvents(true);
        });

        // --------------------------------------------------------------------
        // ABOUT
        // --------------------------------------------------------------------
        Menu helpMenu = new Menu("Help");

        MenuItem documentationItem = new MenuItem("Documentation");
        documentationItem.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.net.openURI("https://pixscape.games/docs/");
            }
        });
        MenuItem gitHubItem = new MenuItem("Runtime (GitHub)");
        gitHubItem.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.net.openURI("https://github.com/pixscapegames/pixscape-runtime");
            }
        });

        MenuItem aboutItem = new MenuItem("About Pixscape");
        aboutItem.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showAboutDialog(actor.getStage());
            }
        });

        helpMenu.addItem(documentationItem);
        helpMenu.addItem(gitHubItem);
        helpMenu.addSeparator();
        helpMenu.addItem(aboutItem);

        // --------------------------------------------------------------------
        // Assemble menus
        // --------------------------------------------------------------------
        file.addItem(newScene);
        file.addItem(open);
        file.addItem(recentProjects);
        file.addItem(importMenuItem);
        file.addItem(save);
        file.addItem(saveAs);
        file.addSeparator();
        file.addItem(projectSettings);
        file.addSeparator();
        file.addItem(exit);
        addMenu(file);

        editMenu.addItem(cut);
        editMenu.addItem(copy);
        editMenu.addItem(paste);
        editMenu.addItem(undo);
        editMenu.addItem(redo);
        addMenu(editMenu);

        resourcesMenu.addItem(handleShadersItem);
        addMenu(resourcesMenu);

        addMenu(viewMenu);
        addMenu(helpMenu);

        onStart();
    }

    private void showAboutDialog(Stage stage) {
        final Texture logoTexture = new Texture(Gdx.files.internal(PixscapeStudioApplication.PIXSCAPE_ICON));
        final Image logo = new Image(new TextureRegionDrawable(new TextureRegion(logoTexture)));

        VisDialog dialog = new StudioDialog("About Pixscape Studio") {
            private boolean disposed;

            private void disposeLogoOnce() {
                if (!disposed) {
                    disposed = true;
                    logoTexture.dispose();
                }
            }

            @Override
            protected void result(Object object) {
                if ("copy".equals(object)) {
                    Gdx.app.getClipboard().setContents(buildAboutText());
                }
                hide();
            }

            @Override
            public void hide() {
                disposeLogoOnce();
                super.hide();
            }
        };

        dialog.setResizable(false);
        dialog.setMovable(true);
        dialog.setModal(true);
        dialog.closeOnEscape();

        Table content = dialog.getContentTable();
        content.pad(16);

        Table info = new VisTable();
        info.left();
        info.defaults().left().padBottom(4);

        info.add(new VisLabel("Pixscape Studio " + BuildInfo.APP_VERSION)).left().padBottom(15).row();

        info.add(new VisLabel("Build #" + BUILD_IDENTIFIER + ", built on " + BuildInfo.BUILD_DATE))
                .left().padBottom(10).row();

        info.add(new VisLabel("Runtime version: " + System.getProperty("java.runtime.version"))).left().row();
        info.add(new VisLabel("VM: " + System.getProperty("java.vm.name"))).left().row();
        info.add(new VisLabel("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"))).left().row();

        LinkLabel siteLink = new LinkLabel("Pixscape", "https://pixscape.games");

        LinkLabel ossLink = new LinkLabel("open-source software", "internal://oss");
        ossLink.setListener(url -> showOpenSourceDialog(stage));

        LinkLabel contributorsLink = new LinkLabel("Contributors", "internal://contributors");
        contributorsLink.setListener(url -> showContributorsDialog(stage));

        Table poweredRow = new VisTable();
        poweredRow.left();
        poweredRow.add(new VisLabel("Powered by ")).left();
        poweredRow.add(ossLink).left();

        info.add(poweredRow).left().padTop(6).padBottom(6).row();
        info.add(contributorsLink).left().padBottom(6).row();

        Table copyrightRow = new VisTable();
        copyrightRow.left();
        copyrightRow.add(new VisLabel("Copyright © 2026  ")).left();
        copyrightRow.add(siteLink).left();

        info.add(copyrightRow).left().row();

        content.add(logo).size(48).top().left().padRight(16);
        content.add(info).growX().left();

        dialog.button("Copy and Close", "copy");
        dialog.button("Close", "close");

        dialog.pack();
        dialog.centerWindow();
        dialog.show(stage);
    }

    private void showOpenSourceDialog(Stage stage) {
        VisDialog dialog = new StudioDialog("Open-source software");
        dialog.setResizable(false);
        dialog.setMovable(true);
        dialog.setModal(true);
        dialog.closeOnEscape();

        Table content = dialog.getContentTable();
        content.pad(16);

        VisTable root = new VisTable();
        root.left().top();

        root.add(new VisLabel("Pixscape uses the following open-source software:"))
                .left()
                .colspan(3)
                .padBottom(12)
                .row();

        root.add(new VisLabel("Name")).left().padRight(24).padBottom(8);
        root.add(new VisLabel("Version")).left().width(100).padRight(24).padBottom(8);
        root.add(new VisLabel("License")).left().width(110).padBottom(8).row();

        for (OssEntry entry : OSS_ENTRIES) {
            root.add(new VisLabel(entry.name())).left().padRight(24).padBottom(4);
            root.add(new VisLabel(entry.version())).left().width(100).padRight(24).padBottom(4);
            root.add(new VisLabel(entry.license())).left().width(110).padBottom(4).row();
        }

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        content.add(scrollPane).width(560).height(300).grow();

        dialog.button("Close");
        dialog.pack();
        dialog.centerWindow();
        dialog.show(stage);
    }

    private void showContributorsDialog(Stage stage) {
        VisDialog dialog = new StudioDialog("Contributors");
        dialog.setResizable(false);
        dialog.setMovable(true);
        dialog.setModal(true);
        dialog.closeOnEscape();

        Table content = dialog.getContentTable();
        content.pad(16);

        VisTable root = new VisTable();
        root.left().top();

        VisLabel introduction = new VisLabel(
                "Pixscape grows thanks to people who contribute code, testing, feedback, "
                        + "documentation, art, tutorials, and community support."
        );
        introduction.setWrap(true);
        root.add(introduction).width(480).left().padBottom(14).row();

        for (ContributorEntry entry : CONTRIBUTORS) {
            LinkLabel identityLink = new LinkLabel(entry.displayName(), entry.url());
            root.add(identityLink).left().padBottom(3).row();
            root.add(new VisLabel(entry.contribution())).left().padBottom(12).row();
        }

        content.add(root).left().top();

        dialog.button("Close");
        dialog.pack();
        dialog.centerWindow();
        dialog.show(stage);
    }

    private String buildAboutText() {
        StringBuilder text = new StringBuilder()
                .append("Pixscape Studio ").append(BuildInfo.APP_VERSION).append('\n')
                .append("Build #").append(BUILD_IDENTIFIER).append('\n')
                .append("Built on ").append(BuildInfo.BUILD_DATE).append("\n\n")
                .append("Runtime version: ").append(System.getProperty("java.runtime.version")).append('\n')
                .append("VM: ").append(System.getProperty("java.vm.name")).append('\n')
                .append("OS: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch"))
                .append("\n\nContributors:\n");

        for (ContributorEntry entry : CONTRIBUTORS) {
            text.append("- ").append(entry.displayName()).append(" — ")
                    .append(entry.contribution()).append('\n');
        }

        return text.toString().stripTrailing();
    }

    private record OssEntry(String name, String version, String license) {
    }

    private record ContributorEntry(String name, String handle, String url, String contribution) {
        private String displayName() {
            return handle == null || handle.isBlank() ? name : name + " (" + handle + ")";
        }
    }

    private static final String BUILD_IDENTIFIER = "PS-001";

    private static final ContributorEntry[] CONTRIBUTORS = new ContributorEntry[]{
            new ContributorEntry(
                    "Tommy Ettinger",
                    "@tommyettinger",
                    "https://github.com/tommyettinger",
                    "LibGDX ecosystem and community support."
            ),
            new ContributorEntry(
                    "Quillraven",
                    "@Quillraven",
                    "https://github.com/Quillraven",
                    "Tiled map import testing and feedback."
            )
    };

    private static final OssEntry[] OSS_ENTRIES = new OssEntry[]{
            new OssEntry("LibGDX", BuildInfo.GDX_VERSION, "Apache-2.0"),
            new OssEntry("gdx-freetype", BuildInfo.GDX_VERSION, "Apache-2.0"),
            new OssEntry("LWJGL3 backend", BuildInfo.GDX_VERSION, "Apache-2.0"),
            new OssEntry("gdx-controllers", BuildInfo.GDX_CONTROLLERS_VERSION, "Apache-2.0"),
            new OssEntry("Box2D", BuildInfo.GDX_VERSION, "Apache-2.0"),
            new OssEntry("libgdx-utils", BuildInfo.LIBGDX_UTILS_VERSION, "Apache-2.0"),
            new OssEntry("libgdx-utils-box2d", BuildInfo.LIBGDX_UTILS_BOX2D_VERSION, "Apache-2.0"),
            new OssEntry("box2dlights", BuildInfo.BOX2DLIGHTS_VERSION, "Apache-2.0"),
            new OssEntry("ShapeDrawer", BuildInfo.SHAPEDRAWER_VERSION, "MIT"),
            new OssEntry("VisUI", BuildInfo.VISUI_VERSION, "Apache-2.0"),
            new OssEntry("libgdx-texturepacker", BuildInfo.TEXTUREPACKER_VERSION, "Apache-2.0"),
            new OssEntry("libgdx-textureunpacker", BuildInfo.TEXTUREUNPACKER_VERSION, "Apache-2.0")
    };

    private boolean isProjectDirty() {
        return sceneService.requiresSaveBeforePreview();
    }

    private void runWithSaveIfDirty(Runnable next) {
        if (next == null) return;

        if (!isProjectDirty()) {
            next.run();
            return;
        }

        // NO
        YesNoDialog.show(
                app.getUiStage(),
                VisUI.getSkin(),
                "Unsaved changes",
                "The current project has unsaved changes.\nDo you want to save before continuing?",
                () -> {
                    // YES
                    runSaveWithProgress(next);
                },
                next
        );
    }

    private void runSaveWithProgress(Runnable onSuccess) {
        sceneService.saveProjectAndCurrentSceneWithProgress(
                app.getUiStage(),
                () -> {
                    refreshRecentProjectsMenu();
                    if (onSuccess != null) onSuccess.run();
                },
                throwable -> Dialogs.showOKDialog(
                        app.getUiStage(),
                        "Save failed",
                        PreviewLaunchSupport.userMessageFor(throwable)
                )
        );
    }


    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------
    private void newProject() {
        NewProjectWindow w = new NewProjectWindow("New project");

        w.getOKButton().addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String projectTitle = w.getProjectTitle();
                String fileName = w.getProjectFileName();
                String projectDirectory = w.getProjectDirectoryPath();
                String root = w.getExportRootPathDir();

                if (projectTitle.isBlank() || fileName.isBlank() || root.isBlank()) {
                    return;
                }

                // 1) Create the project (writes .json, initializes cfg, directories, default scene, etc.)
                try {
                    sceneService.newProject(
                            projectTitle,
                            fileName,
                            projectDirectory,
                            root,
                            PlatformTarget.DESKTOP_GL30,
                            w.getGlSamples()
                    );
                } catch (RuntimeException ex) {
                    Dialogs.showOKDialog(
                            app.getUiStage(),
                            "Project creation failed",
                            ex.getMessage()
                    );
                    onStart();
                    return;
                }

                FileHandle projectFile = sceneService.getStudioProjectFile();

                assert projectFile != null;
                if (!projectFile.exists()) {
                    Gdx.app.error("TopMenuBar", "New project file not found (studio workspace): " + projectFile.path());
                    return;
                }
                Optional<ProjectOpenFailure> failure = sceneService.tryOpenProject(projectFile, "open newly created project");
                if (failure.isPresent()) {
                    Gdx.app.error("TopMenuBar", "Project open failed after creation: " + projectFile.path(), failure.get().cause());
                    onStart();
                    Dialogs.showOKDialog(
                            app.getUiStage(),
                            "Project not loaded",
                            failure.get().message()
                    );
                    return;
                }

                // 3) UI + close window
                refreshRecentProjectsMenu();
                beginProject();
                w.fadeOut();
            }
        });

        app.getUiStage().addActor(w.fadeIn());
    }

    public void beginProject() {
        projectSettings.setDisabled(false);
        importMenuItem.setDisabled(false);
        save.setDisabled(false);
        saveAs.setDisabled(false);
        editMenu.openButton.setDisabled(false);
        resourcesMenu.openButton.setDisabled(false);
    }

    public void onStart() {
        projectSettings.setDisabled(true);
        importMenuItem.setDisabled(true);
        save.setDisabled(true);
        saveAs.setDisabled(true);
        editMenu.openButton.setDisabled(true);
        resourcesMenu.openButton.setDisabled(true);
    }

    private static void onClick(MenuItem item, Runnable action) {
        item.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
    }

    private static void onClick(VisCheckBox item, Runnable action) {
        item.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
    }

    private void saveProjectAs() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) return;

        FileHandle currentFile = sceneService.getStudioProjectFile();
        FileHandle startDir = currentFile != null && currentFile.parent() != null
                ? currentFile.parent()
                : StudioFs.defaultUserProjectsRoot();

        FileChooser chooser = new StudioFileChooser(startDir, FileChooser.Mode.SAVE);
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFavoriteFolderButtonVisible(true);
        chooser.setShowSelectionCheckboxes(true);

        final FileTypeFilter typeFilter = new FileTypeFilter(true);
        typeFilter.addRule("Pixscape Studio project (*.json)", "json");
        chooser.setFileTypeFilter(typeFilter);

        chooser.setListener(new StreamingFileChooserListener() {
            @Override
            public void selected(FileHandle file) {
                try {
                    FileHandle target = file;
                    if (!StudioFs.EXT_JSON.substring(1).equalsIgnoreCase(target.extension())) {
                        target = target.sibling(StudioFs.withExt(target.name(), StudioFs.EXT_JSON));
                    }
                    sceneService.saveProjectAs(target);
                    refreshRecentProjectsMenu();
                } catch (RuntimeException ex) {
                    Dialogs.showOKDialog(
                            app.getUiStage(),
                            "Save As failed",
                            PreviewLaunchSupport.userMessageFor(ex)
                    );
                }
            }
        });

        getTable().getStage().addActor(chooser.fadeIn());
    }

    private void openTmxImportChooser() {
        if (!hasLoadedProjectForImport()) {
            Dialogs.showOKDialog(
                    app.getUiStage(),
                    "Project not loaded",
                    "Open or create a Pixscape project before importing a Tiled map."
            );
            return;
        }

        FileChooser chooser = new StudioFileChooser(studioProjectDirectoryOrDefault(), FileChooser.Mode.OPEN);
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFavoriteFolderButtonVisible(true);
        chooser.setShowSelectionCheckboxes(true);
        chooser.setPrefsName("tmx-import.json");

        final FileTypeFilter typeFilter = new FileTypeFilter(true);
        typeFilter.addRule("Tiled map (*.tmx)", "tmx");
        chooser.setFileTypeFilter(typeFilter);

        chooser.setListener(new StreamingFileChooserListener() {
            @Override
            public void selected(FileHandle file) {
                prepareTmxImport(file);
            }
        });

        app.getUiStage().addActor(chooser.fadeIn());
    }

    private boolean hasLoadedProjectForImport() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null
                && cfg.projectFileName != null
                && !cfg.projectFileName.isBlank()
                && cfg.projectDirectoryPath != null
                && !cfg.projectDirectoryPath.isBlank()
                && cfg.getCurrentSceneMeta() != null;
    }

    private FileHandle studioProjectDirectoryOrDefault() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg != null && cfg.projectDirectoryPath != null && !cfg.projectDirectoryPath.isBlank()) {
            return Gdx.files.absolute(cfg.projectDirectoryPath);
        }
        return StudioFs.defaultUserProjectsRoot();
    }

    private void prepareTmxImport(FileHandle file) {
        if (file == null || file.isDirectory() || !"tmx".equalsIgnoreCase(file.extension())) {
            Dialogs.showOKDialog(
                    app.getUiStage(),
                    "Tiled map not selected",
                    "Choose a .tmx file to import."
            );
            return;
        }

        try {
            TmxImportUiSupport.TmxImportPreparation preparation = TmxImportUiSupport.prepare(file);
            if (!preparation.planResult().hasPlan() || preparation.hasBlockingDiagnostics()) {
                TmxImportMessageDialog.show(
                        app.getUiStage(),
                        "Tiled map cannot be imported",
                        TmxImportUiSupport.formatDiagnostics(preparation.diagnostics())
                );
                return;
            }

            new TmxImportDialog(
                    preparation,
                    sceneName -> importTmxAsNewScene(file, sceneName)
            ).show(app.getUiStage());
        } catch (RuntimeException ex) {
            Gdx.app.error("TopMenuBar", "TMX import preflight failed: " + file.path(), ex);
            Dialogs.showOKDialog(
                    app.getUiStage(),
                    "Tiled map import failed",
                    PreviewLaunchSupport.userMessageFor(ex)
            );
        }
    }

    private void importTmxAsNewScene(FileHandle file, String sceneName) {
        app.runAfterCurrentSceneSaveDecision(
                "Unsaved Project",
                "Do you want to save before importing the Tiled map?",
                () -> startTmxImport(file, sceneName),
                null,
                failure -> Dialogs.showOKDialog(
                        app.getUiStage(),
                        "Save failed",
                        PreviewLaunchSupport.userMessageFor(failure)
                )
        );
    }

    private void startTmxImport(FileHandle file, String sceneName) {
        try {
            sceneService.importTmxAsNewSceneWithProgress(
                    app.getUiStage(),
                    new TmxSceneImportRequest(file, sceneName),
                    result -> showTmxImportResult(result),
                    failure -> showTmxImportFailure(file, failure)
            );
        } catch (RuntimeException ex) {
            showTmxImportFailure(file, ex);
        }
    }

    private void showTmxImportResult(TmxSceneImportResult result) {
        if (result.imported()) {
            Dialogs.showOKDialog(
                    app.getUiStage(),
                    "Tiled map imported",
                    TmxImportUiSupport.formatSuccessMessage(result)
            );
        } else {
            TmxImportMessageDialog.show(
                    app.getUiStage(),
                    "Tiled map import failed",
                    TmxImportUiSupport.formatFailureMessage(result)
            );
        }
    }

    private void showTmxImportFailure(FileHandle file, Throwable failure) {
        Gdx.app.error("TopMenuBar", "TMX import failed: " + file.path(), failure);
        Dialogs.showOKDialog(
                app.getUiStage(),
                "Tiled map import failed",
                PreviewLaunchSupport.userMessageFor(failure)
        );
    }

    private void openRecentProject(String path) {
        FileHandle projectFile = Gdx.files.absolute(path);
        if (!projectFile.exists() || projectFile.isDirectory()) {
            recentProjectsService.removeRecentProject(path);
            refreshRecentProjectsMenu();
            Dialogs.showOKDialog(
                    app.getUiStage(),
                    "Project not found",
                    "This recent project no longer exists:\n" + path
            );
            return;
        }

        runWithSaveIfDirty(() -> {
            Optional<ProjectOpenFailure> failure = sceneService.tryOpenProject(projectFile, "open recent project");
            refreshRecentProjectsMenu();
            if (failure.isEmpty()) {
                beginProject();
            } else {
                Gdx.app.error("TopMenuBar", "Recent project open failed: " + projectFile, failure.get().cause());
                onStart();
                Dialogs.showOKDialog(
                        app.getUiStage(),
                        "Project not loaded",
                        failure.get().message()
                );
            }
        });
    }

    private void refreshRecentProjectsMenu() {
        recentProjectsMenu.clearChildren();

        List<String> projects = recentProjectsService.getRecentProjects();
        if (projects.isEmpty()) {
            MenuItem none = new MenuItem("No recent projects");
            none.setDisabled(true);
            recentProjectsMenu.addItem(none);
        } else {
            for (int i = 0; i < projects.size(); i++) {
                String path = projects.get(i);
                MenuItem item = new MenuItem((i + 1) + ". " + fileNameForRecentProject(path));
                onClick(item, () -> openRecentProject(path));
                recentProjectsMenu.addItem(item);
            }
        }

        recentProjectsMenu.addSeparator();
        MenuItem clear = new MenuItem("Clear Recent Projects");
        clear.setDisabled(projects.isEmpty());
        onClick(clear, () -> {
            recentProjectsService.clearRecentProjects();
            refreshRecentProjectsMenu();
        });
        recentProjectsMenu.addItem(clear);
    }

    private static String fileNameForRecentProject(String path) {
        if (path == null || path.isBlank()) return "Untitled project";
        return Gdx.files.absolute(path).name();
    }

    public FileChooser getChooser() {
        FileHandle startDir = StudioFs.defaultUserProjectsRoot();
        if (!startDir.exists()) {
            startDir.mkdirs();
        }
        final FileChooser chooser = new StudioFileChooser(startDir, FileChooser.Mode.OPEN);
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFavoriteFolderButtonVisible(true);
        chooser.setShowSelectionCheckboxes(true);
        final FileTypeFilter typeFilter = new FileTypeFilter(true);
        typeFilter.addRule("Text files (*.json)", "json");
        chooser.setFileTypeFilter(typeFilter);
        return chooser;
    }

    private void onHandleShadersClicked() {
        ShaderManagerDialog dialog = new ShaderManagerDialog(app);
        app.getUiStage().addActor(dialog.fadeIn());
    }


}
