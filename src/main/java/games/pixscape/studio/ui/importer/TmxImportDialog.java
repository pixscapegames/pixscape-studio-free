package games.pixscape.studio.ui.importer;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.importer.tmx.TmxScenePlan;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.SimpleTextField;

import java.util.function.Consumer;

public final class TmxImportDialog extends StudioDialog {

    private final TmxImportUiSupport.TmxImportPreparation preparation;
    private final Consumer<String> onImport;
    private final SimpleTextField sceneNameField = new SimpleTextField();
    private final VisLabel inlineError = new VisLabel("");

    public TmxImportDialog(TmxImportUiSupport.TmxImportPreparation preparation,
                           Consumer<String> onImport) {
        super("Import Tiled map");
        this.preparation = preparation;
        this.onImport = onImport;

        setModal(true);
        setMovable(true);
        closeOnEscape();
        build();
    }

    private void build() {
        VisTable root = new VisTable();
        root.defaults().left().padBottom(5);
        root.pad(12);

        FileHandle tmxFile = preparation.tmxFile();
        TmxScenePlan scene = preparation.plan().scene();

        addRow(root, "Source", tmxFile != null ? tmxFile.name() : "(unknown)");
        addRow(root, "Orientation / projection", scene.orientation() + " / " + scene.tiledProjection());
        addRow(root, "Map size", scene.mapWidthCells() + " x " + scene.mapHeightCells() + " cells");
        addRow(root, "Tile size", scene.tileWidth() + " x " + scene.tileHeight() + " px");
        addRow(root, "Tile layers", Integer.toString(scene.tileLayerCount()));
        addRow(root, "Tilesets", Integer.toString(preparation.tilesetCount()));
        addRow(root, "Non-empty tiles", Long.toString(scene.nonEmptyTileCount()));
        addRow(root, "Required tiled cells", Long.toString(scene.requiredTiledCells()));

        root.add(new VisLabel("Scene name")).padTop(8).padRight(12);
        sceneNameField.setText(preparation.proposedSceneName());
        root.add(sceneNameField).width(320).growX().row();

        inlineError.setColor(1f, 0.35f, 0.35f, 1f);
        inlineError.setVisible(false);
        root.add(inlineError).colspan(2).growX().row();

        if (preparation.warningCount() > 0) {
            VisLabel warnings = new VisLabel(TmxImportUiSupport.formatDiagnostics(preparation.diagnostics()));
            warnings.setWrap(true);
            ScrollPane scroll = new ScrollPane(warnings);
            scroll.setFadeScrollBars(false);
            root.add(new VisLabel("Warnings")).padTop(8).padRight(12).top();
            root.add(scroll).width(430).height(110).growX().row();
        }

        getContentTable().add(root).grow();

        VisTextButton importButton = new VisTextButton("Import");
        importButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                importRequested();
            }
        });
        getButtonsTable().add(importButton).padRight(6);
        button("Cancel", "cancel");

        pack();
        centerWindow();
    }

    private static void addRow(VisTable table, String label, String value) {
        table.add(new VisLabel(label)).padRight(12);
        VisLabel valueLabel = new VisLabel(value != null ? value : "");
        valueLabel.setWrap(true);
        table.add(valueLabel).width(320).growX().row();
    }

    private void importRequested() {
        final String sceneName;
        try {
            sceneName = TmxImportUiSupport.resolveSceneName(preparation.proposedSceneName(), sceneNameField.getText());
        } catch (IllegalArgumentException ex) {
            inlineError.setText(ex.getMessage());
            inlineError.setVisible(true);
            return;
        }

        if (onImport != null) {
            onImport.accept(sceneName);
        }
        fadeOut();
    }
}
