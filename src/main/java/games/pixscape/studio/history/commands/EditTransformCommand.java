package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

import java.util.Objects;

public final class EditTransformCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float x;
        private final float y;
        private final float rotationRad;
        private final float scaleX;
        private final float scaleY;
        private final float originX;
        private final float originY;

        public Snapshot(float x,
                        float y,
                        float rotationRad,
                        float scaleX,
                        float scaleY,
                        float originX,
                        float originY) {
            this.x = x;
            this.y = y;
            this.rotationRad = rotationRad;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.originX = originX;
            this.originY = originY;
        }

        public static Snapshot capture(TransformComponent transform) {
            if (transform == null) return null;
            return new Snapshot(
                    transform.x,
                    transform.y,
                    transform.rotationRad,
                    transform.scaleX,
                    transform.scaleY,
                    transform.originX,
                    transform.originY
            );
        }

        public float originX() {
            return originX;
        }

        public float originY() {
            return originY;
        }

        public Snapshot withX(float value) {
            return new Snapshot(value, y, rotationRad, scaleX, scaleY, originX, originY);
        }

        public Snapshot withY(float value) {
            return new Snapshot(x, value, rotationRad, scaleX, scaleY, originX, originY);
        }

        public Snapshot withRotationRad(float value) {
            return new Snapshot(x, y, value, scaleX, scaleY, originX, originY);
        }

        public Snapshot withScaleX(float value) {
            return new Snapshot(x, y, rotationRad, value, scaleY, originX, originY);
        }

        public Snapshot withScaleY(float value) {
            return new Snapshot(x, y, rotationRad, scaleX, value, originX, originY);
        }

        public Snapshot withOriginX(float value) {
            return new Snapshot(x, y, rotationRad, scaleX, scaleY, value, originY);
        }

        public Snapshot withOriginY(float value) {
            return new Snapshot(x, y, rotationRad, scaleX, scaleY, originX, value);
        }

        public void apply(TransformComponent transform) {
            transform.x = x;
            transform.y = y;
            transform.rotationRad = rotationRad;
            transform.scaleX = scaleX;
            transform.scaleY = scaleY;
            transform.originX = originX;
            transform.originY = originY;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(x, other.x) == 0
                    && Float.compare(y, other.y) == 0
                    && Float.compare(rotationRad, other.rotationRad) == 0
                    && Float.compare(scaleX, other.scaleX) == 0
                    && Float.compare(scaleY, other.scaleY) == 0
                    && Float.compare(originX, other.originX) == 0
                    && Float.compare(originY, other.originY) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final TransformOp op;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditTransformCommand(World world,
                                HistoryIdRegistry historyIds,
                                int entityId,
                                TransformOp op,
                                Snapshot before,
                                Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.op = Objects.requireNonNull(op, "op");
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
        return switch (op) {
            case MOVE -> "Edit Transform Position";
            case ROTATE -> "Edit Transform Rotation";
            case SCALE -> "Edit Transform Scale";
            case ORIGIN -> "Edit Transform Origin";
        };
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

        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        TransformComponent transform = mTransform.getSafe(entityId, null);
        if (transform == null) return;

        snapshot.apply(transform);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
        }

        EventFlow.i().publish(new EventFlow.EntityChanged(entityId, op, EventFlow.tag(this)));
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}