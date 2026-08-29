package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileChooserAdapter;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.modal.StudioFileChooser;
import games.pixscape.studio.ui.modal.StudioModalWindow;

public final class NewProjectWindow extends StudioModalWindow {

    private final VisTextButton ok = new VisTextButton("OK");
    private final VisTextButton cancel = new VisTextButton("Cancel");

    private final VisTextField projectTitle = new VisTextField();
    private final VisTextField projectFileName = new VisTextField();
    private final VisTextField tfProjectDirectory = new VisTextField();
    private final VisTextField tfExportRoot = new VisTextField();

    private final VisSelectBox<String> glProfileBox = new VisSelectBox<>();
    private final VisSelectBox<Integer> samplesBox = new VisSelectBox<>();
    private final VisSelectBox<String> projectionBox = new VisSelectBox<>();

    private final VisTextField tfTileWidth = new VisTextField("32");
    private final VisTextField tfTileHeight = new VisTextField("32");

    public NewProjectWindow(String title) {
        super(title);

        ok.setColor(CommonLayout.BUTTON_COLOR);
        cancel.setColor(CommonLayout.BUTTON_COLOR);

        setModal(true);
        setResizable(false);

        // IMPORTANT:
        // New Project must start blank.
        projectTitle.setText("");
        projectFileName.setText("");
        tfProjectDirectory.setText("");
        tfExportRoot.setText("");

        VisTextButton btnBrowseProjectDirectory = new VisTextButton("...");
        btnBrowseProjectDirectory.setColor(CommonLayout.BUTTON_COLOR);
        btnBrowseProjectDirectory.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                FileChooser chooser = new StudioFileChooser(FileChooser.Mode.OPEN);
                chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
                chooser.setSize(800, 600);

                String cur = tfProjectDirectory.getText().trim();
                FileHandle start = cur.isEmpty() ? StudioFs.defaultUserProjectsRoot() : Gdx.files.absolute(cur);
                if (start.exists()) chooser.setDirectory(start);

                chooser.setListener(new FileChooserAdapter() {
                    @Override
                    public void selected(Array<FileHandle> files) {
                        if (files.size > 0) {
                            tfProjectDirectory.setText(files.first().path());
                        }
                    }
                });

                getStage().addActor(chooser.fadeIn());
            }
        });

        VisTextButton btnBrowseRoot = new VisTextButton("...");
        btnBrowseRoot.setColor(CommonLayout.BUTTON_COLOR);
        btnBrowseRoot.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                FileChooser chooser = new StudioFileChooser(FileChooser.Mode.OPEN);
                chooser.setSelectionMode(FileChooser.SelectionMode.DIRECTORIES);
                chooser.setSize(800, 600);

                String cur = tfExportRoot.getText().trim();
                if (!cur.isEmpty()) {
                    FileHandle fh = Gdx.files.absolute(cur);
                    if (fh.exists()) chooser.setDirectory(fh);
                }

                chooser.setListener(new FileChooserAdapter() {
                    @Override
                    public void selected(Array<FileHandle> files) {
                        if (files.size > 0) {
                            tfExportRoot.setText(files.first().path());
                        }
                    }
                });

                getStage().addActor(chooser.fadeIn());
            }
        });

        cancel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });

        // New project defaults
        glProfileBox.setItems("GL20", "GL30");
        glProfileBox.setSelected("GL30");

        samplesBox.setItems(0, 2, 4, 8);
        samplesBox.setSelected(0);

        projectionBox.setItems("Orthogonal", "Isometric");
        projectionBox.setSelected("Orthogonal");

        VisTable content = new VisTable(true);
        content.defaults().pad(4).left();

        content.add(new VisLabel("Project title:")).left();
        content.add(projectTitle).growX().row();

        content.add(new VisLabel("Project file name:")).left();
        content.add(projectFileName).growX().row();

        content.add(new VisLabel("Project Directory:")).left();
        VisTable projectDirectoryRow = new VisTable(true);
        projectDirectoryRow.add(tfProjectDirectory).growX();
        projectDirectoryRow.add(btnBrowseProjectDirectory).width(32f);
        content.add(projectDirectoryRow).growX().row();

        content.add(new VisLabel("Export Directory:")).left();
        VisTable rootRow = new VisTable(true);
        rootRow.add(tfExportRoot).growX();
        rootRow.add(btnBrowseRoot).width(32f);
        content.add(rootRow).growX().row();

        content.addSeparator().colspan(2).padTop(6).row();

        content.add(new VisLabel("GL profile:")).left();
        content.add(glProfileBox).growX().row();

        content.add(new VisLabel("Backbuffer MSAA:")).left();
        content.add(samplesBox).growX().row();

        content.addSeparator().colspan(2).padTop(6).row();

        content.add(new VisLabel("Default Scene")).colspan(2).left().padTop(4).row();
        content.add(new VisLabel("Tiled Map Creation Defaults"))
                .colspan(2).left().padTop(4).row();

        content.add(new VisLabel("Projection:")).left();
        content.add(projectionBox).growX().row();

        content.add(new VisLabel("Tile Width (px):")).left();
        content.add(tfTileWidth).growX().row();

        content.add(new VisLabel("Tile Height (px):")).left();
        content.add(tfTileHeight).growX().row();

        VisTable buttons = new VisTable(true);
        buttons.add(ok).width(120);
        buttons.add(cancel).width(120);

        content.add(buttons).colspan(2).right().padTop(10).row();

        add(content).growX();

        setWidth(620f);
        pack();
        centerWindow();
    }

    public VisTextButton getOKButton() {
        return ok;
    }

    public VisTextButton getCancelButton() {
        return cancel;
    }

    public String getProjection() {
        return projectionBox.getSelected();
    }

    public int getTileWidth() {
        return parseIntSafe(tfTileWidth.getText(), 32);
    }

    public int getTileHeight() {
        return parseIntSafe(tfTileHeight.getText(), 32);
    }

    private int parseIntSafe(String value, int def) {
        try {
            int v = Integer.parseInt(value.trim());
            return v > 0 ? v : def;
        } catch (Exception e) {
            return def;
        }
    }

    public String getProjectTitle() {
        return projectTitle.getText().trim();
    }

    public String getProjectFileName() {
        return projectFileName.getText().trim();
    }

    public String getProjectDirectoryPath() {
        return tfProjectDirectory.getText().trim();
    }

    public String getExportRootPathDir() {
        return tfExportRoot.getText().trim();
    }

    public String getGlProfile() {
        return glProfileBox.getSelected();
    }

    public int getGlSamples() {
        return samplesBox.getSelected();
    }
}
