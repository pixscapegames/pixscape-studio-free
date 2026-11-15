package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

/** Records the already materialized TMX map in its new scene's content history. */
public final class ImportTiledMapCommand implements Command {
    private final DeleteGameObjectHierarchyCommand contents;
    private boolean initiallyApplied = true;

    public ImportTiledMapCommand(World world, HistoryIdRegistry historyIds, int mapEntity,
            TiledAllocatorService allocator) {
        contents = new DeleteGameObjectHierarchyCommand(world, historyIds,
                IntArray.with(mapEntity), null, allocator);
    }

    @Override
    public String label() {
        return "Import Tiled Map";
    }

    @Override
    public void redo() {
        if (initiallyApplied) {
            initiallyApplied = false;
        } else {
            contents.undo();
        }
    }

    @Override
    public void undo() {
        contents.redo();
    }
}
