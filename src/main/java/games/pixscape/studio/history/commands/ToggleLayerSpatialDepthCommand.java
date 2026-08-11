package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

import java.util.ArrayList;
import java.util.List;

public final class ToggleLayerSpatialDepthCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final LayerSnapshot beforeLayer;
    private final LayerSnapshot afterLayer;
    private final List<EntitySpatialSnapshot> removedEntitySpatial;
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
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.beforeLayer = captureLayer(layerEntityId);
        this.afterLayer = beforeLayer != null
                ? beforeLayer.withSpatialEnabled(enable, defaultAltitude, Math.max(0f, defaultHeight))
                : null;
        this.removedEntitySpatial = !enable && beforeLayer != null
                ? captureSpatialEntitiesInLayer(beforeLayer.layerIndex)
                : List.of();
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
        if (!enable) {
            removeCapturedSpatialEntities();
        }
    }

    @Override
    public void undo() {
        if (noop) return;
        applyLayer(beforeLayer);
        if (!enable) {
            restoreCapturedSpatialEntities();
        }
    }

    private LayerSnapshot captureLayer(int layerEntityId) {
        if (world == null || layerEntityId < 0) return null;

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null) return null;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        boolean tiledSpatialEnabled = tiled != null && tiled.spatialEnabled;
        float defaultAltitude = tiled != null ? tiled.defaultTileAltitude : 0f;
        float defaultHeight = tiled != null ? tiled.defaultTileHeight : 0f;

        return new LayerSnapshot(
                layer.layerIndex,
                layer.spatialEnabled,
                tiled != null,
                tiledSpatialEnabled,
                defaultAltitude,
                defaultHeight
        );
    }

    private List<EntitySpatialSnapshot> captureSpatialEntitiesInLayer(int layerIndex) {
        List<EntitySpatialSnapshot> result = new ArrayList<>();
        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<SpatialHeightComponent> mSpatial = world.getMapper(SpatialHeightComponent.class);

        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, SpatialHeightComponent.class))
                .getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = data[i];
            EntityIndexComponent index = mIndex.getSafe(entityId, null);
            SpatialHeightComponent spatial = mSpatial.getSafe(entityId, null);
            if (index == null || spatial == null || index.getLayerIndex() != layerIndex) {
                continue;
            }
            long historyId = historyIds.ensureForEntity(entityId);
            result.add(new EntitySpatialSnapshot(historyId, spatial.altitude, spatial.height));
        }
        return result;
    }

    private void applyLayer(LayerSnapshot snapshot) {
        if (snapshot == null) return;

        int layerEntityId = resolveEntityId(layerHistoryId);
        if (layerEntityId < 0) return;

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null) return;

        layer.spatialEnabled = snapshot.layerSpatialEnabled;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (tiled != null) {
            tiled.spatialEnabled = snapshot.tiledSpatialEnabled;
            tiled.defaultTileAltitude = snapshot.defaultAltitude;
            tiled.defaultTileHeight = Math.max(0f, snapshot.defaultHeight);
            if (tiled.data != null) {
                tiled.data.spatialEnabled = tiled.spatialEnabled;
                tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
                tiled.data.defaultTileHeight = tiled.defaultTileHeight;
                tiled.data.markAllChunksContentDirty();
            }
        }

        markLayerDirty(layerEntityId);
        EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(layerEntityId, EventFlow.tag(this)));
    }

    private void removeCapturedSpatialEntities() {
        ComponentMapper<SpatialHeightComponent> mapper = world.getMapper(SpatialHeightComponent.class);
        for (EntitySpatialSnapshot snapshot : removedEntitySpatial) {
            int entityId = resolveEntityId(snapshot.entityHistoryId);
            if (entityId < 0 || !mapper.has(entityId)) continue;
            mapper.remove(entityId);
            markEntitySpatialDirty(entityId);
        }
    }

    private void restoreCapturedSpatialEntities() {
        ComponentMapper<SpatialHeightComponent> mapper = world.getMapper(SpatialHeightComponent.class);
        for (EntitySpatialSnapshot snapshot : removedEntitySpatial) {
            int entityId = resolveEntityId(snapshot.entityHistoryId);
            if (entityId < 0) continue;

            SpatialHeightComponent component = mapper.has(entityId)
                    ? mapper.get(entityId)
                    : mapper.create(entityId);
            component.altitude = snapshot.altitude;
            component.height = snapshot.height;
            markEntitySpatialDirty(entityId);
        }
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

    private void markEntitySpatialDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entityId, EventFlow.tag(this)));
    }

    private record LayerSnapshot(int layerIndex,
                                 boolean layerSpatialEnabled,
                                 boolean tiled,
                                 boolean tiledSpatialEnabled,
                                 float defaultAltitude,
                                 float defaultHeight) {
        LayerSnapshot withSpatialEnabled(boolean enabled, float defaultAltitude, float defaultHeight) {
            if (!tiled) {
                return new LayerSnapshot(layerIndex, enabled, false, false, 0f, 0f);
            }
            return new LayerSnapshot(layerIndex, enabled, true, enabled, defaultAltitude, defaultHeight);
        }

        boolean sameSpatialState(LayerSnapshot other) {
            if (other == null) return false;
            return layerSpatialEnabled == other.layerSpatialEnabled
                    && tiledSpatialEnabled == other.tiledSpatialEnabled
                    && Float.compare(defaultAltitude, other.defaultAltitude) == 0
                    && Float.compare(defaultHeight, other.defaultHeight) == 0;
        }
    }

    private record EntitySpatialSnapshot(long entityHistoryId, float altitude, float height) {
    }
}
