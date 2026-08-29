package games.pixscape.studio.ui.layer;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.Objects;
import java.util.function.Consumer;

/** Per-operation Tiled Map configuration; SceneMeta supplies defaults only. */
public final class AddTiledMapDialog extends StudioDialog {
    public record Request(int mapWidth, int mapHeight, TiledProjection projection,
                          int tileWidth, int tileHeight, int chunkSize) {}

    private final VisSelectBox<String> projection = new VisSelectBox<>();
    private final VisTextField tileWidth = new VisTextField();
    private final VisTextField tileHeight = new VisTextField();
    private final VisTextField mapWidth = new VisTextField("256");
    private final VisTextField mapHeight = new VisTextField("256");
    private final VisTextField chunkSize = new VisTextField();
    private final Consumer<Request> onCreate;

    public AddTiledMapDialog(Consumer<Request> onCreate) {
        super("Add Tiled Map");
        this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
        SceneMeta defaults = ProjectConfig.getInstance().getCurrentSceneMeta();
        projection.setItems("Orthogonal", "Isometric");
        TiledProjection defaultProjection = defaults != null
                ? defaults.tiledProjection : TiledProjection.ORTHO;
        projection.setSelected(defaultProjection == TiledProjection.ISO ? "Isometric" : "Orthogonal");
        tileWidth.setText(Integer.toString(defaults != null ? (int) defaults.tileWidth : 32));
        tileHeight.setText(Integer.toString(defaults != null ? (int) defaults.tileHeight : 32));
        chunkSize.setText(Integer.toString(defaults != null ? defaults.chunkSize : 16));

        VisTable form = new VisTable(true);
        row(form, "Projection", projection);
        row(form, "Tile width", tileWidth);
        row(form, "Tile height", tileHeight);
        row(form, "Map width", mapWidth);
        row(form, "Map height", mapHeight);
        row(form, "Chunk size", chunkSize);
        getContentTable().add(form).pad(8f);
        button("Create", true);
        button("Cancel", false);
        setModal(true);
        setResizable(false);
        pack();
    }

    private static void row(VisTable table, String label, com.badlogic.gdx.scenes.scene2d.Actor field) {
        table.add(new VisLabel(label + ":")).left();
        table.add(field).width(110f).left().row();
    }

    @Override
    protected void result(Object object) {
        if (!Boolean.TRUE.equals(object)) return;
        onCreate.accept(new Request(
                positive(mapWidth, 256), positive(mapHeight, 256),
                "Isometric".equals(projection.getSelected()) ? TiledProjection.ISO : TiledProjection.ORTHO,
                positive(tileWidth, 32), positive(tileHeight, 32), positive(chunkSize, 16)));
    }

    private static int positive(VisTextField field, int fallback) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            return value > 0 ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
