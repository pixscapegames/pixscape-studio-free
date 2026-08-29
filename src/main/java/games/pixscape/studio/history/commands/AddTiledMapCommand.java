package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.ZOrderRuntimeService;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import games.pixscape.studio.service.LayerService;

import java.util.function.IntConsumer;

/** Creates one independently authored Tiled Map inside an ordinary Pixscape layer. */
public final class AddTiledMapCommand implements Command {
    private final LayerService layerService;
    private final HistoryIdRegistry historyIds;
    private final TiledMapInitializer initializer;
    private final ZOrderRuntimeService zOrder;
    private final int targetLayerIndex;
    private final IntConsumer onMapSelected;
    private long historyId = -1L;
    private boolean capturedAuthoredState;

    public AddTiledMapCommand(LayerService layerService, int targetLayerEntityId,
                              int mapWidth, int mapHeight, TiledProjection projection,
                              int tileWidth, int tileHeight, int chunkSize,
                              IntConsumer onMapSelected) {
        this.layerService = layerService;
        World world = layerService.getWorld();
        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(targetLayerEntityId, null);
        if (layer == null || !layerService.isLayerEntity(targetLayerEntityId)) {
            throw new IllegalArgumentException("Add Tiled Map requires an ordinary Pixscape Layer.");
        }
        this.targetLayerIndex = layer.layerIndex;
        this.historyIds = layerService.historyIds();
        this.zOrder = new ZOrderRuntimeService(world);
        this.onMapSelected = onMapSelected;
        ProjectConfig config = ProjectConfig.getInstance();
        String atlasTag = config != null ? config.canonicalSceneTagCurrent() : "main";
        this.initializer = new TiledMapInitializer(world, layerService.getTiledAllocatorService())
                .configureNew(targetLayerIndex, mapWidth, mapHeight, atlasTag, projection,
                        tileWidth, tileHeight, chunkSize);
    }

    @Override public String label() { return "Add Tiled Map"; }

    @Override
    public void redo() {
        int mapEntityId = layerService.insertTiledMap(initializer, historyId);
        if (historyId <= 0L) historyId = historyIds.ensureForEntity(mapEntityId);
        if (!capturedAuthoredState) {
            zOrder.addOnTop(mapEntityId, targetLayerIndex);
            initializer.syncFrom(mapEntityId);
            capturedAuthoredState = true;
        }
        if (onMapSelected != null) onMapSelected.accept(mapEntityId);
    }

    @Override
    public void undo() {
        int mapEntityId = historyIds.entityOfHistoryId(historyId);
        if (mapEntityId < 0) return;
        initializer.syncFrom(mapEntityId);
        layerService.removeTiledMap(mapEntityId);
        if (onMapSelected != null) onMapSelected.accept(-1);
    }
}
