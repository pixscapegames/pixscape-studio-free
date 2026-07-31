package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class ToggleSpatialHeightCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean enable;
    private final boolean noop;

    public ToggleSpatialHeightCommand(World world,
                                      HistoryIdRegistry historyIds,
                                      int entityId,
                                      boolean enable,
                                      float defaultAltitude,
                                      float defaultHeight) {
        this.world = world;
        this.historyIds = historyIds;
        this.enable = enable;
        this.entityHistoryId = historyIds != null ? historyIds.ensureForEntity(entityId) : -1L;

        ComponentMapper<SpatialHeightComponent> mapper =
                world != null ? world.getMapper(SpatialHeightComponent.class) : null;
        SpatialHeightComponent existing =
                mapper != null && entityId >= 0 ? mapper.getSafe(entityId, null) : null;
        this.before = Snapshot.capture(existing);
        this.after = enable ? new Snapshot(true, defaultAltitude, defaultHeight) : Snapshot.disabled();
        this.noop = world == null
                || historyIds == null
                || entityHistoryId <= 0L
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return enable ? "Enable Spatial Height" : "Disable Spatial Height";
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

        ComponentMapper<SpatialHeightComponent> mapper = world.getMapper(SpatialHeightComponent.class);
        if (!snapshot.enabled) {
            if (mapper.has(entityId)) {
                mapper.remove(entityId);
            }
        } else {
            SpatialHeightComponent component = mapper.has(entityId)
                    ? mapper.get(entityId)
                    : mapper.create(entityId);
            component.altitude = snapshot.altitude;
            component.height = snapshot.height;
        }

        markSpatialDirty(entityId);
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private void markSpatialDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entityId);
        }
    }

    static final class Snapshot {
        final boolean enabled;
        final float altitude;
        final float height;

        Snapshot(boolean enabled, float altitude, float height) {
            this.enabled = enabled;
            this.altitude = altitude;
            this.height = height;
        }

        static Snapshot capture(SpatialHeightComponent component) {
            if (component == null) return disabled();
            return new Snapshot(true, component.altitude, component.height);
        }

        static Snapshot disabled() {
            return new Snapshot(false, 0f, 0f);
        }

        boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return enabled == other.enabled
                    && Float.compare(altitude, other.altitude) == 0
                    && Float.compare(height, other.height) == 0;
        }
    }
}