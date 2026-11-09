package games.pixscape.studio.history.commands;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Asset-level edit: persistence succeeds before metadata is republished. No scene entity is mutated. */
public final class EditTilePropertiesCommand implements Command, PreExecutionNoopCommand {
    private final AssetMetaDatabase database;
    private final int assetId;
    private final PropertySet before, after;
    private final Supplier<FileHandle> assetsFile;
    private final Consumer<AssetMetaDatabase> publisher;

    public EditTilePropertiesCommand(AssetMetaDatabase database, int assetId, PropertySet after,
                                      Supplier<FileHandle> assetsFile, Consumer<AssetMetaDatabase> publisher) {
        this.database = database;
        this.assetId = assetId;
        this.before = tile().properties.copy();
        this.after = after.copy();
        this.assetsFile = assetsFile;
        this.publisher = publisher;
    }

    @Override public String label() { return "Edit Tile Properties"; }
    @Override public boolean isNoop() { return EditCustomPropertiesCommand.sameProperties(before, after); }
    @Override public void redo() { apply(after); }
    @Override public void undo() { apply(before); }

    private TileAssetMeta tile() {
        if (!(database.findById(assetId) instanceof TileAssetMeta tile))
            throw new IllegalStateException("Tile asset is unavailable: " + assetId);
        return tile;
    }

    private void apply(PropertySet properties) {
        TileAssetMeta tile = tile();
        PropertySet previous = tile.properties;
        tile.properties = properties.copy();
        try {
            database.save(assetsFile.get());
        } catch (RuntimeException failure) {
            tile.properties = previous;
            throw new IllegalStateException("Could not save tile properties: " + failure.getMessage(), failure);
        }
        publisher.accept(database);
    }
}
