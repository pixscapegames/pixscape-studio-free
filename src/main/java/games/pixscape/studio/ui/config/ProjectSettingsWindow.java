package games.pixscape.studio.ui.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.ProjectRenameService;
import games.pixscape.studio.service.RecentProjectsService;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import static games.pixscape.studio.PixscapeStudioApplication.STUDIO_TITLE;

public final class ProjectSettingsWindow extends VisWindow {

    private final ProjectConfig cfg;

    private final VisTextField tfProjectDirectory;
    private final VisTextField tfExportRoot;
    private final VisTextField tfProjectName;
    private final VisTextField tfProjectFileName;
    private final VisSelectBox<Integer> samplesBox;
    private final VisLabel restartHint;

    private final StudioApplicationAdapter app;


    public ProjectSettingsWindow(StudioApplicationAdapter app) {
        super("Project Settings");
        getTitleLabel().setAlignment(Align.center);
        this.app = app;
        this.cfg = ProjectConfig.getInstance();

        setModal(true);
        setResizable(false);
        setWidth(480f);

        VisTable content = new VisTable(true);
        content.defaults().left().pad(4);

        // --- Project root (editable with file chooser) ---
        String projectDirectory = (cfg.projectDirectoryPath != null) ? cfg.projectDirectoryPath : "";
        tfProjectDirectory = new VisTextField(projectDirectory);
        String root = (cfg.exportRootPathDir != null) ? cfg.exportRootPathDir : "";
        tfExportRoot = new VisTextField(root);
        tfProjectName = new VisTextField(cfg.projectTitle != null ? cfg.projectTitle : "");
        tfProjectFileName = new VisTextField(cfg.projectFileName != null ? cfg.projectFileName : "");

        VisTextButton btnBrowseProjectDirectory = new VisTextButton("...");
        btnBrowseProjectDirectory.setColor(CommonLayout.BUTTON_COLOR);
        VisTextButton btnBrowseRoot = new VisTextButton("...");
        btnBrowseRoot.setColor(CommonLayout.BUTTON_COLOR);

        btnBrowseProjectDirectory.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                FileChooser chooser = new FileChooser(FileChooser.Mode.OPEN);
                chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
                chooser.setSize(800, 600);

                String cur = tfProjectDirectory.getText().trim();
                if (!cur.isEmpty()) {
                    FileHandle fh = Gdx.files.absolute(cur);
                    if (fh.exists()) {
                        chooser.setDirectory(fh);
                    }
                }

                chooser.setListener(new FileChooserAdapter() {
                    @Override
                    public void selected(Array<FileHandle> files) {
                        if (files.size > 0) {
                            FileHandle dir = files.first();
                            tfProjectDirectory.setText(dir.path());
                        }
                    }
                });
                getStage().addActor(chooser.fadeIn());
            }
        });

        btnBrowseRoot.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                FileChooser chooser = new FileChooser(FileChooser.Mode.OPEN);
                chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
                chooser.setSize(800, 600);

                String cur = tfExportRoot.getText().trim();
                if (!cur.isEmpty()) {
                    FileHandle fh = Gdx.files.absolute(cur);
                    if (fh.exists()) {
                        chooser.setDirectory(fh);
                    }
                }

                chooser.setListener(new FileChooserAdapter() {
                    @Override
                    public void selected(Array<FileHandle> files) {
                        if (files.size > 0) {
                            FileHandle dir = files.first();
                            tfExportRoot.setText(dir.path());
                        }
                    }
                });
                getStage().addActor(chooser.fadeIn());
            }
        });

        // --- MSAA (editable, restart needed) ---
        samplesBox = new VisSelectBox<>();
        samplesBox.setItems(0, 2, 4, 8);
        int currentSamples = cfg.glSamples;
        if (currentSamples != 0 && currentSamples != 2 && currentSamples != 4 && currentSamples != 8)
            currentSamples = 0;
        samplesBox.setSelected(currentSamples);

        restartHint = new VisLabel("  (Require restart)");
        restartHint.setColor(0.8f, 0.8f, 0.8f, 1f);

        // Layout

        VisTable projectDirectoryRow = new VisTable(true);
        projectDirectoryRow.add(new VisLabel("Project Directory:")).left();
        projectDirectoryRow.add(tfProjectDirectory).growX();
        projectDirectoryRow.add(btnBrowseProjectDirectory).width(32f);

        content.add(projectDirectoryRow).padTop(25).growX().row();

        VisTable rootRow = new VisTable(true);
        rootRow.add(new VisLabel("Export Directory:")).left();
        rootRow.add(tfExportRoot).growX();
        rootRow.add(btnBrowseRoot).width(32f);

        content.add(rootRow).growX().row();

        VisTable nameRow = new VisTable(true);
        nameRow.add(new VisLabel("Project title:")).left();
        nameRow.add(tfProjectName).growX();

        content.add(nameRow).growX().row();

        VisTable fileNameRow = new VisTable(true);
        fileNameRow.add(new VisLabel("Project file name:")).left();
        fileNameRow.add(tfProjectFileName).growX();

        content.add(fileNameRow).growX().row();

        content.addSeparator().colspan(2).padTop(10).row();

        content.row();
        VisTable msaaRow = new VisTable(true);
        msaaRow.add(new VisLabel("Backbuffer MSAA:")).left();
        msaaRow.add(samplesBox).width(50).growX();
        msaaRow.add(restartHint).growX();
        content.add(msaaRow).row();

        // Buttons
        VisTextButton btnOk = new VisTextButton("OK");
        btnOk.setColor(CommonLayout.BUTTON_COLOR);
        VisTextButton btnCancel = new VisTextButton("Cancel");
        btnCancel.setColor(CommonLayout.BUTTON_COLOR);

        btnOk.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyAndClose();
            }
        });

        btnCancel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                remove();
            }
        });

        VisTable buttons = new VisTable(true);
        buttons.add(btnOk);
        buttons.add(btnCancel);

        content.row();
        content.add(buttons).colspan(2).right().padTop(8);

        add(content).growX();
        pack();
        centerWindow();
    }

    private void applyAndClose() {
        String exportRoot = tfExportRoot.getText().trim();
        String projectDirectory = tfProjectDirectory.getText().trim();
        if (projectDirectory.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "Project directory is required.");
            return;
        }
        if (exportRoot.isBlank()) {
            Dialogs.showErrorDialog(getStage(), "Export directory is required.");
            return;
        }

        ProjectSettingsSnapshot snapshot = ProjectSettingsSnapshot.capture(cfg, EditorSettings.get().msaaSamples);

        String newProjectFileName = tfProjectFileName.getText().trim();
        FileHandle originalStudioDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle renamedStudioDir = originalStudioDir;
        boolean locationChanged = false;
        try {
            FileHandle targetProjectDir = Gdx.files.absolute(projectDirectory);
            cfg.exportRootPathDir = exportRoot;
            SceneService.requireValidExportRootOrThrow(cfg, "projectSettings");

            if (!targetProjectDir.path().equals(originalStudioDir.path())) {
                ProjectRenameService.moveProjectDirectory(originalStudioDir, targetProjectDir);
                cfg.projectDirectoryPath = targetProjectDir.path();
                renamedStudioDir = targetProjectDir;
                locationChanged = true;
            } else {
                cfg.projectDirectoryPath = originalStudioDir.path();
            }

            if (!newProjectFileName.equals(snapshot.projectFileName())) {
                ProjectRenameService.renameProjectFile(renamedStudioDir, cfg, newProjectFileName);
            }

            cfg.projectTitle = tfProjectName.getText().trim();

            cfg.glSamples = samplesBox.getSelected();
            EditorSettings.get().msaaSamples = cfg.glSamples;

            FileHandle studioFile = StudioFs.requireStudioProjectFile(cfg);
            studioFile.parent().mkdirs();
            ProjectConfig.ProjectIO.saveProject(cfg, studioFile);

            EditorSettings.get().lastProjectPath = studioFile.path();
            new RecentProjectsService().addRecentProject(studioFile.path());
            Gdx.graphics.setTitle(STUDIO_TITLE + " (" + cfg.projectTitle + ")");
        } catch (RuntimeException ex) {
            if (locationChanged) {
                rollbackProjectDirectoryMove(renamedStudioDir, originalStudioDir, cfg, snapshot.projectDirectoryPath(), ex);
            }
            if (!newProjectFileName.equals(snapshot.projectFileName())) {
                rollbackProjectFileRename(StudioFs.requireStudioProjectDir(cfg), cfg, snapshot.projectFileName(), ex);
            }
            snapshot.restore(cfg);
            Dialogs.showErrorDialog(getStage(), ex.getMessage());
            return;
        }

        remove();
    }

    static final class ProjectSettingsSnapshot {
        private final String exportRootPathDir;
        private final String projectDirectoryPath;
        private final String projectTitle;
        private final String projectFileName;
        private final int glSamples;
        private final int editorMsaa;

        private ProjectSettingsSnapshot(String exportRootPathDir,
                                        String projectDirectoryPath,
                                        String projectTitle,
                                        String projectFileName,
                                        int glSamples,
                                        int editorMsaa) {
            this.exportRootPathDir = exportRootPathDir;
            this.projectDirectoryPath = projectDirectoryPath;
            this.projectTitle = projectTitle;
            this.projectFileName = projectFileName;
            this.glSamples = glSamples;
            this.editorMsaa = editorMsaa;
        }

        static ProjectSettingsSnapshot capture(ProjectConfig cfg, int editorMsaa) {
            return new ProjectSettingsSnapshot(
                    cfg.exportRootPathDir,
                    cfg.projectDirectoryPath,
                    cfg.projectTitle,
                    cfg.projectFileName,
                    cfg.glSamples,
                    editorMsaa
            );
        }

        void restore(ProjectConfig cfg) {
            cfg.exportRootPathDir = exportRootPathDir;
            cfg.projectDirectoryPath = projectDirectoryPath;
            cfg.projectTitle = projectTitle;
            cfg.projectFileName = projectFileName;
            cfg.glSamples = glSamples;
            EditorSettings.get().msaaSamples = editorMsaa;
        }

        String projectFileName() {
            return projectFileName;
        }

        String projectDirectoryPath() {
            return projectDirectoryPath;
        }
    }

    static void rollbackProjectFileRename(FileHandle projectDir,
                                          ProjectConfig cfg,
                                          String originalProjectFileName,
                                          RuntimeException primaryFailure) {
        try {
            ProjectRenameService.renameProjectFile(projectDir, cfg, originalProjectFileName);
        } catch (RuntimeException rollbackEx) {
            primaryFailure.addSuppressed(rollbackEx);
        }
    }

    static void rollbackProjectDirectoryMove(FileHandle movedDir,
                                             FileHandle originalDir,
                                             ProjectConfig cfg,
                                             String originalProjectDirectoryPath,
                                             RuntimeException primaryFailure) {
        try {
            ProjectRenameService.moveProjectDirectory(movedDir, originalDir);
            cfg.projectDirectoryPath = originalProjectDirectoryPath;
        } catch (RuntimeException rollbackEx) {
            primaryFailure.addSuppressed(rollbackEx);
        }
    }
}
