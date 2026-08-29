package games.pixscape.studio.ui.layer;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.Objects;
import java.util.function.Consumer;

/** Per-operation Tiled Map configuration. */
public final class AddTiledMapDialog extends StudioDialog {
    static final TiledProjection DEFAULT_PROJECTION = TiledProjection.ORTHO;
    static final int DEFAULT_TILE_WIDTH = 32;
    static final int DEFAULT_TILE_HEIGHT = 32;
    static final int DEFAULT_MAP_WIDTH = 256;
    static final int DEFAULT_MAP_HEIGHT = 256;
    static final int DEFAULT_CHUNK_SIZE = 16;

    public record Request(int mapWidth, int mapHeight, TiledProjection projection,
                          int tileWidth, int tileHeight, int chunkSize) {}

    private final VisSelectBox<String> projection = new VisSelectBox<>();
    private final VisTextField tileWidth = new VisTextField();
    private final VisTextField tileHeight = new VisTextField();
    private final VisTextField mapWidth = new VisTextField(Integer.toString(DEFAULT_MAP_WIDTH));
    private final VisTextField mapHeight = new VisTextField(Integer.toString(DEFAULT_MAP_HEIGHT));
    private final VisTextField chunkSize = new VisTextField();
    private final Consumer<Request> onCreate;

    public AddTiledMapDialog(Consumer<Request> onCreate) {
        super("Add Tiled Map");
        this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
        projection.setItems("Orthogonal", "Isometric");
        projection.setSelected(DEFAULT_PROJECTION == TiledProjection.ISO ? "Isometric" : "Orthogonal");
        tileWidth.setText(Integer.toString(DEFAULT_TILE_WIDTH));
        tileHeight.setText(Integer.toString(DEFAULT_TILE_HEIGHT));
        chunkSize.setText(Integer.toString(DEFAULT_CHUNK_SIZE));

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
                positive(mapWidth, DEFAULT_MAP_WIDTH), positive(mapHeight, DEFAULT_MAP_HEIGHT),
                "Isometric".equals(projection.getSelected()) ? TiledProjection.ISO : TiledProjection.ORTHO,
                positive(tileWidth, DEFAULT_TILE_WIDTH), positive(tileHeight, DEFAULT_TILE_HEIGHT),
                positive(chunkSize, DEFAULT_CHUNK_SIZE)));
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
