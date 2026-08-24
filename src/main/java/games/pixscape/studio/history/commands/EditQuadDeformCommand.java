package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;

import java.util.Objects;

/** Applies one complete authored quad-deformation edit. */
public final class EditQuadDeformCommand implements Command, PreExecutionNoopCommand {
    public static final float ZERO_EPSILON = 0.0001f;

    public static final class Snapshot {
        public final boolean present;
        public final float blX, blY;
        public final float brX, brY;
        public final float trX, trY;
        public final float tlX, tlY;

        public Snapshot(boolean present,
                        float blX, float blY,
                        float brX, float brY,
                        float trX, float trY,
                        float tlX, float tlY) {
            this.present = present;
            this.blX = blX;
            this.blY = blY;
            this.brX = brX;
            this.brY = brY;
            this.trX = trX;
            this.trY = trY;
            this.tlX = tlX;
            this.tlY = tlY;
        }

        public static Snapshot absent() {
            return new Snapshot(false, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        public static Snapshot capture(QuadDeformComponent component) {
            if (component == null) return absent();
            return new Snapshot(
                    true,
                    component.blX, component.blY,
                    component.brX, component.brY,
                    component.trX, component.trY,
                    component.tlX, component.tlY);
        }

        public static Snapshot normalized(QuadDeformComponent component) {
            Snapshot snapshot = capture(component);
            return snapshot.isEffectivelyZero() ? absent() : snapshot;
        }

        public boolean isEffectivelyZero() {
            return nearZero(blX) && nearZero(blY)
                    && nearZero(brX) && nearZero(brY)
                    && nearZero(trX) && nearZero(trY)
                    && nearZero(tlX) && nearZero(tlY);
        }

        public boolean sameAs(Snapshot other) {
            return other != null
                    && present == other.present
                    && Float.compare(blX, other.blX) == 0
                    && Float.compare(blY, other.blY) == 0
                    && Float.compare(brX, other.brX) == 0
                    && Float.compare(brY, other.brY) == 0
                    && Float.compare(trX, other.trX) == 0
                    && Float.compare(trY, other.trY) == 0
                    && Float.compare(tlX, other.tlX) == 0
                    && Float.compare(tlY, other.tlY) == 0;
        }

        public void applyTo(QuadDeformComponent component) {
            component.blX = blX;
            component.blY = blY;
            component.brX = brX;
            component.brY = brY;
            component.trX = trX;
            component.trY = trY;
            component.tlX = tlX;
            component.tlY = tlY;
        }

        private static boolean nearZero(float value) {
            return Math.abs(value) <= ZERO_EPSILON;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long historyId;
    private final Snapshot before;
    private final Snapshot after;

    public EditQuadDeformCommand(World world,
                                 HistoryIdRegistry historyIds,
                                 int entityId,
                                 Snapshot before,
                                 Snapshot after) {
        this.world = Objects.requireNonNull(world, "world");
        this.historyIds = Objects.requireNonNull(historyIds, "historyIds");
        this.historyId = historyIds.ensureForEntity(entityId);
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
    }

    @Override
    public String label() {
        return "Edit Quad Deformation";
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    @Override
    public boolean isNoop() {
        return historyId <= 0L || before.sameAs(after);
    }

    private void apply(Snapshot snapshot) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return;

        ComponentMapper<QuadDeformComponent> mapper =
                world.getMapper(QuadDeformComponent.class);
        if (!snapshot.present) {
            if (mapper.has(entityId)) mapper.remove(entityId);
        } else {
            QuadDeformComponent component = mapper.has(entityId)
                    ? mapper.get(entityId)
                    : mapper.create(entityId);
            snapshot.applyTo(component);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.geometry(entityId, GeometryDirty.QUAD);
    }
}
