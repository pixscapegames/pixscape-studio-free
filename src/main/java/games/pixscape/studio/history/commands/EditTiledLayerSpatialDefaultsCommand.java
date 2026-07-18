package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditTiledLayerSpatialDefaultsCommand implements Command, HistoryManager.SupportsNoop {
    public record Snapshot(float defaultAltitude, float defaultHeight) {
        public static Snapshot capture(TiledLayerComponent component) {
            if (component == null) return null;
            return new Snapshot(component.defaultTileAltitude, component.defaultTileHeight);
        }

        public Snapshot withDefaultAltitude(float value) {
            return new Snapshot(value, defaultHeight);
        }

        public Snapshot withDefaultHeight(float value) {
            return new Snapshot(defaultAltitude, value);
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(defaultAltitude, other.defaultAltitude) == 0
                    && Float.compare(defaultHeight, other.defaultHeight) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditTiledLayerSpatialDefaultsCommand(World world,
                                                HistoryIdRegistry historyIds,
                                                int layerEntityId,
                                                Snapshot before,
                                                Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.noop = world == null
                || historyIds == null
                || layerHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Tiled Spatial Defaults";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(Snapshot snapshot) {
        if (noop || snapshot == null) return;
        int entityId = resolveEntityId();
        if (entityId < 0) return;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
        if (tiled == null) return;

        float previousDefaultAltitude = tiled.defaultTileAltitude;
        float previousDefaultHeight = tiled.defaultTileHeight;
        tiled.defaultTileAltitude = snapshot.defaultAltitude;
        tiled.defaultTileHeight = Math.max(0f, snapshot.defaultHeight);
        boolean defaultsChanged = Float.compare(previousDefaultAltitude, tiled.defaultTileAltitude) != 0
                || Float.compare(previousDefaultHeight, tiled.defaultTileHeight) != 0;
        syncRuntimeDefaults(tiled);
        syncInheritedSpatialBlocks(entityId, previousDefaultAltitude, tiled.defaultTileAltitude);
        if (defaultsChanged) advanceSpatialRevision(entityId);
        markDirty(entityId);
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private void syncRuntimeDefaults(TiledLayerComponent tiled) {
        if (tiled.data != null) {
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;
            tiled.data.markAllChunksContentDirty();
        }
    }

    private void syncInheritedSpatialBlocks(int layerEntityId, float previousDefaultAltitude, float nextDefaultAltitude) {
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(layerEntityId, null);
        if (blocks == null || blocks.blocks == null) return;
        for (int i = 0, n = blocks.blocks.size; i < n; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            if (block == null) continue;
            if (Math.abs(block.altitude - previousDefaultAltitude) > 0.0001f) continue;
            block.altitude = nextDefaultAltitude;
            if (block.physicsCollision) {
                SpatialBlockPhysicsSync.sync(world, layerEntityId, block, this);
            }
        }
    }

    private void advanceSpatialRevision(int layerEntityId) {
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(layerEntityId, null);
        if (blocks != null) blocks.revision++;
    }

    private void markDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(entityId);
        }
        EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(entityId, EventFlow.tag(this)));
    }
}
