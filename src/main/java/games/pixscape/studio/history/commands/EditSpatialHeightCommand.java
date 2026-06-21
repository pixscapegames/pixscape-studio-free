package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditSpatialHeightCommand implements Command, HistoryManager.SupportsNoop {
    public record Snapshot(float altitude, float height) {
        public static Snapshot capture(SpatialHeightComponent component) {
            if (component == null) return null;
            return new Snapshot(component.altitude, component.height);
        }

        public Snapshot withAltitude(float value) {
            return new Snapshot(value, height);
        }

        public Snapshot withHeight(float value) {
            return new Snapshot(altitude, value);
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(altitude, other.altitude) == 0
                    && Float.compare(height, other.height) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditSpatialHeightCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    int entityId,
                                    Snapshot before,
                                    Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.entityHistoryId = historyIds != null ? historyIds.ensureForEntity(entityId) : -1L;
        this.noop = world == null
                || historyIds == null
                || entityHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Spatial Height";
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
        SpatialHeightComponent component = mapper.getSafe(entityId, null);
        if (component == null) return;

        component.altitude = snapshot.altitude;
        component.height = snapshot.height;

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}