package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditSpatialFootprintCommand implements Command, HistoryManager.SupportsNoop {
    public record Snapshot(float altitude, float offsetX, float offsetY, float radius) {
        public static Snapshot capture(SpatialFootprintComponent component) {
            if (component == null) {
                return null;
            }
            return new Snapshot(component.altitude, component.localOffsetXPx, component.localOffsetYPx, component.radiusPx);
        }

        public Snapshot withAltitude(float value) {
            return new Snapshot(value, offsetX, offsetY, radius);
        }

        public Snapshot withOffsetX(float value) {
            return new Snapshot(altitude, value, offsetY, radius);
        }

        public Snapshot withOffsetY(float value) {
            return new Snapshot(altitude, offsetX, value, radius);
        }

        public Snapshot withRadius(float value) {
            return new Snapshot(altitude, offsetX, offsetY, value);
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) {
                return false;
            }
            return equals(other);
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;
    private final float pixelsPerMeter;

    public EditSpatialFootprintCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    int entityId,
                                    Snapshot before,
                                    Snapshot after, float pixelsPerMeter) {
        this.world = world;
        this.pixelsPerMeter = pixelsPerMeter;
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
        return "Edit Spatial Footprint";
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
        if (noop || snapshot == null) {
            return;
        }
        int entityId = resolveEntityId();
        if (entityId < 0) {
            return;
        }

        ComponentMapper<SpatialFootprintComponent> mapper = world.getMapper(SpatialFootprintComponent.class);
        SpatialFootprintComponent component = mapper.getSafe(entityId, null);
        if (component == null) {
            return;
        }

        SpatialFootprintComponent candidate = component.copy();
        candidate.altitude = snapshot.altitude;
        candidate.localOffsetXPx = snapshot.offsetX;
        candidate.localOffsetYPx = snapshot.offsetY;
        candidate.radiusPx = snapshot.radius;
        games.pixscape.runtime.service.PhysicsService.setSpatialFootprint(world, entityId, candidate, pixelsPerMeter);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}
