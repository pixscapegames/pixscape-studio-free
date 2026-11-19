package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/**
 * Toggles one tile layer of the single participating map, independent of its host scene layer.
 */
public final class ToggleTiledMapSpatialDepthCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long mapHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public ToggleTiledMapSpatialDepthCommand(World world,
                                             HistoryIdRegistry historyIds,
                                             int mapEntityId,
                                             boolean enabled,
                                             float defaultAltitude,
                                             float defaultHeight) {
        this.world = world;
        this.historyIds = historyIds;

        TiledLayerComponent tiled = validMap(mapEntityId);
        boolean allowed = tiled != null && (!enabled || games.pixscape.runtime.service.SpatialScenePolicy
                .tiledActivationRejection(world, mapEntityId) == null);
        this.mapHistoryId = tiled != null && allowed
                ? historyIds.ensureForEntity(mapEntityId)
                : -1L;

        this.before = tiled != null
                ? new Snapshot(
                tiled.spatialEnabled,
                tiled.defaultTileAltitude,
                tiled.defaultTileHeight)
                : null;
        this.after = before != null
                ? before.withSpatialEnabled(
                enabled,
                defaultAltitude,
                Math.max(0f, defaultHeight))
                : null;
        this.noop = world == null
                || historyIds == null
                || mapHistoryId <= 0L
                || before == null
                || before.sameState(after);
    }

    @Override
    public String label() {
        return after != null && after.mapSpatialEnabled
                ? "Enable Tiled Map Spatial Depth"
                : "Disable Tiled Map Spatial Depth";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return noop ? CommandOutcome.NO_CHANGE : apply(after);
    }

    @Override
    public CommandOutcome undoOutcome() {
        return noop ? CommandOutcome.NO_CHANGE : apply(before);
    }

    private CommandOutcome apply(Snapshot snapshot) {
        int mapEntityId = resolve(mapHistoryId);
        TiledLayerComponent tiled = validMap(mapEntityId);
        if (tiled == null || snapshot == null) {
            return CommandOutcome.NO_CHANGE;
        }
        if (snapshot.mapSpatialEnabled && games.pixscape.runtime.service.SpatialScenePolicy
                .tiledActivationRejection(world, mapEntityId) != null) {
            return CommandOutcome.REJECTED;
        }

        tiled.spatialEnabled = snapshot.mapSpatialEnabled;
        tiled.defaultTileAltitude = snapshot.defaultAltitude;
        tiled.defaultTileHeight = Math.max(0f, snapshot.defaultHeight);
        if (tiled.data != null) {
            tiled.data.spatialEnabled = tiled.spatialEnabled;
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;
            tiled.data.markAllChunksContentDirty();
        }

        markDirty(mapEntityId);
        EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(
                mapEntityId, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private TiledLayerComponent validMap(int entityId) {
        if (world == null || historyIds == null || entityId < 0
                || !world.getEntityManager().isActive(entityId)) {
            return null;
        }
        return world.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
    }

    private int resolve(long historyId) {
        if (historyIds == null || historyId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(historyId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? entityId
                : -1;
    }

    private void markDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(entityId);
            dirty.order(entityId);
        }
    }

    private record Snapshot(boolean mapSpatialEnabled,
                            float defaultAltitude,
                            float defaultHeight) {
        Snapshot withSpatialEnabled(boolean enabled,
                                    float defaultAltitude,
                                    float defaultHeight) {
            return new Snapshot(
                    enabled,
                    defaultAltitude,
                    defaultHeight);
        }

        boolean sameState(Snapshot other) {
            return other != null
                    && mapSpatialEnabled == other.mapSpatialEnabled
                    && Float.compare(defaultAltitude, other.defaultAltitude) == 0
                    && Float.compare(defaultHeight, other.defaultHeight) == 0;
        }
    }
}
