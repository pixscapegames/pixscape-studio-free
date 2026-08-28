package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class ToggleLayerSpatialDepthCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final LayerSnapshot beforeLayer;
    private final LayerSnapshot afterLayer;
    private final boolean enable;
    private final boolean noop;

    public ToggleLayerSpatialDepthCommand(World world,
                                          HistoryIdRegistry historyIds,
                                          int layerEntityId,
                                          boolean enable,
                                          float defaultAltitude,
                                          float defaultHeight) {
        this.world = world;
        this.historyIds = historyIds;
        this.enable = enable;
        this.beforeLayer = captureLayer(layerEntityId);
        this.layerHistoryId = historyIds != null && beforeLayer != null
                ? historyIds.ensureForEntity(layerEntityId)
                : -1L;
        this.afterLayer = beforeLayer != null
                ? beforeLayer.withSpatialEnabled(enable, defaultAltitude, Math.max(0f, defaultHeight))
                : null;
        this.noop = world == null
                || historyIds == null
                || layerHistoryId <= 0L
                || beforeLayer == null
                || afterLayer == null
                || beforeLayer.sameSpatialState(afterLayer);
    }

    @Override
    public String label() {
        return enable ? "Enable Layer Spatial Depth" : "Disable Layer Spatial Depth";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        if (noop) return;
        applyLayer(afterLayer);
    }

    @Override
    public void undo() {
        if (noop) return;
        applyLayer(beforeLayer);
    }

    private LayerSnapshot captureLayer(int layerEntityId) {
        if (world == null || layerEntityId < 0) return null;

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null || layer.type != LayerComponent.TYPE_TILED) return null;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (tiled == null) return null;

        return new LayerSnapshot(
                layer.spatialEnabled,
                tiled.spatialEnabled,
                tiled.defaultTileAltitude,
                tiled.defaultTileHeight
        );
    }

    private void applyLayer(LayerSnapshot snapshot) {
        if (snapshot == null) return;

        int layerEntityId = resolveEntityId(layerHistoryId);
        if (layerEntityId < 0) return;

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null || layer.type != LayerComponent.TYPE_TILED || tiled == null) return;

        layer.spatialEnabled = snapshot.layerSpatialEnabled;
        tiled.spatialEnabled = snapshot.tiledSpatialEnabled;
        tiled.defaultTileAltitude = snapshot.defaultAltitude;
        tiled.defaultTileHeight = Math.max(0f, snapshot.defaultHeight);
        if (tiled.data != null) {
            tiled.data.spatialEnabled = tiled.spatialEnabled;
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;
            tiled.data.markAllChunksContentDirty();
        }

        markLayerDirty(layerEntityId);
        EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(layerEntityId, EventFlow.tag(this)));
    }

    private int resolveEntityId(long historyId) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private void markLayerDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(entityId);
            dirty.order(entityId);
        }
    }

    private record LayerSnapshot(boolean layerSpatialEnabled,
                                 boolean tiledSpatialEnabled,
                                 float defaultAltitude,
                                 float defaultHeight) {
        LayerSnapshot withSpatialEnabled(boolean enabled, float defaultAltitude, float defaultHeight) {
            return new LayerSnapshot(enabled, enabled, defaultAltitude, defaultHeight);
        }

        boolean sameSpatialState(LayerSnapshot other) {
            if (other == null) return false;
            return layerSpatialEnabled == other.layerSpatialEnabled
                    && tiledSpatialEnabled == other.tiledSpatialEnabled
                    && Float.compare(defaultAltitude, other.defaultAltitude) == 0
                    && Float.compare(defaultHeight, other.defaultHeight) == 0;
        }
    }
}
