package games.pixscape.studio.ui.property;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditTilePropertiesCommand;
import games.pixscape.studio.ui.property.entityproperties.EditPropertiesDialog;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Inspector for one shared tile definition; history belongs to the asset catalog, not a scene. */
public final class TileAssetProperties extends VisTable {
    private final Supplier<AssetMetaDatabase> databaseSource;
    private final Supplier<FileHandle> assetsFile;
    private final Consumer<AssetMetaDatabase> publisher;
    private AssetMetaDatabase database;
    private final HistoryManager history = new HistoryManager(128);
    private int assetId = -1;
    private final VisLabel tileset = new VisLabel();
    private final VisLabel id = new VisLabel();
    private final VisLabel summary = new VisLabel();
    // Opens a copy-based property dialog; no ECS-bound button is appropriate for an asset.
    private final VisTextButton edit = new VisTextButton("Edit properties…");

    public TileAssetProperties(Supplier<AssetMetaDatabase> databaseSource, Supplier<FileHandle> assetsFile,
                               Consumer<AssetMetaDatabase> publisher) {
        super(true);
        this.databaseSource = databaseSource;
        this.assetsFile = assetsFile;
        this.publisher = publisher;
        top().left();
        id.setName("tileLocalId");
        tileset.setName("tileTileset");
        summary.setName("tileCustomPropertiesSummary");
        edit.setName("editTileCustomProperties");
        add(new VisLabel("TILE")).colspan(2).center().row();
        add(new VisLabel("Tileset:")).left();
        add(tileset).left().growX().row();
        add(new VisLabel("Id:")).left();
        add(id).left().growX().row();
        new Tooltip.Builder("Local ID within this tileset. Right-click to copy.").target(id).build();
        id.addListener(new ClickListener(Input.Buttons.RIGHT) {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (event.isTouchFocusCancel()) return;
                TileAssetMeta tile = tile();
                if (tile == null || tile.sheetIndex < 0) return;
                String value = Integer.toString(tile.sheetIndex);
                MenuItem copy = new MenuItem("Copy Id");
                copy.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, Actor actor) {
                        Gdx.app.getClipboard().setContents(value);
                    }
                });
                PopupMenu menu = new PopupMenu();
                menu.addItem(copy);
                menu.showMenu(getStage(), event.getStageX(), event.getStageY());
            }
        });
        addSeparator().colspan(2).growX().padTop(8).row();
        add(new VisLabel("Custom properties")).colspan(2).left().row();
        add(summary).left();
        add(edit).right().row();
        edit.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                TileAssetMeta selected = tile();
                if (selected == null) return;
                int target = assetId;
                AssetMetaDatabase targetDatabase = database;
                new EditPropertiesDialog("Tile custom properties", selected.properties, after -> {
                    if (databaseSource.get() != targetDatabase)
                        throw new IllegalStateException("The asset project has changed.");
                    apply(target, after);
                }, true).show(getStage());
            }
        });
        history.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) -> refresh());
    }

    public void select(int assetId) {
        AssetMetaDatabase next = databaseSource.get();
        if (database != next) {
            database = next;
            history.clear();
        }
        this.assetId = assetId;
        refresh();
    }

    void clearProject() {
        database = null;
        assetId = -1;
        history.clear();
        refresh();
    }

    void apply(int target, PropertySet after) {
        history.execute(new EditTilePropertiesCommand(database, target, after, assetsFile, publisher));
    }

    public boolean canUndo() { return database == databaseSource.get() && history.canUndo(); }
    public boolean canRedo() { return database == databaseSource.get() && history.canRedo(); }
    public boolean undo() { if (!canUndo()) return false; history.undo(); return true; }
    public boolean redo() { if (!canRedo()) return false; history.redo(); return true; }

    private TileAssetMeta tile() {
        return database != null && database == databaseSource.get()
                && database.findById(assetId) instanceof TileAssetMeta tile ? tile : null;
    }

    private void refresh() {
        TileAssetMeta tile = tile();
        TilesetAssetMeta owner = tile != null && database.findById(tile.tilesetId) instanceof TilesetAssetMeta set ? set : null;
        tileset.setText(owner != null ? owner.logicalPath() + " (#" + owner.id() + ")" : "Unavailable");
        id.setText(tile != null && tile.sheetIndex >= 0 ? Integer.toString(tile.sheetIndex) : "Not assigned");
        int count = tile != null ? tile.properties.size() : 0;
        summary.setText(count + (count == 1 ? " property" : " properties"));
        edit.setDisabled(tile == null || owner == null);
    }
}
