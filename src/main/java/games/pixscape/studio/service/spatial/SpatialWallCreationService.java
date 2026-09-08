package games.pixscape.studio.service.spatial;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddSpatialBlockCommand;

/** Production selection-to-command path shared by pointer release and editor operations. */
public final class SpatialWallCreationService {
    private SpatialWallCreationService() {
    }

    public static boolean executeSelectedRectangle(World world,
                                                   HistoryManager history,
                                                   SpatialBlockSelectionService blockSelection,
                                                   SpatialTileSelectionService tileSelection) {
        if (world == null || history == null || tileSelection == null || !tileSelection.hasSelection()) return false;
        try {
            int layerEntityId = tileSelection.getMapEntityId();
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
            if (tiled == null || tiled.data == null) return false;
            SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).getSafe(layerEntityId, null);
            if (!tileSelection.canCreateSpatialBlock(
                    tiled.data, walls, tiled.defaultTileAltitude, tiled.defaultTileHeight)) return false;

            SpatialBlockData candidate = tileSelection.toSpatialBlockData(
                    tiled.data, tiled.defaultTileAltitude, tiled.defaultTileHeight);
            SpatialWallThicknessInheritance.Result inherited =
                    SpatialWallThicknessInheritance.apply(candidate, walls, tileSelection.gestureAxis());
            if (!inherited.valid) return false;
            candidate = inherited.wall;
            AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                    world, history.historyIds(), blockSelection, layerEntityId, candidate);
            if (command.isNoop()) return false;
            history.execute(command);
            return true;
        } finally {
            tileSelection.clear();
        }
    }
}
