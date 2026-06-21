package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditDistanceJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float lengthM;
        private final float frequencyHz;
        private final float dampingRatio;

        public Snapshot(float lengthM, float frequencyHz, float dampingRatio) {
            this.lengthM = Math.max(0.001f, lengthM);
            this.frequencyHz = Math.max(0f, frequencyHz);
            this.dampingRatio = clamp01(dampingRatio);
        }

        public static Snapshot capture(PhysicsDistanceJointComponent distance) {
            if (distance == null) return null;
            return new Snapshot(distance.lengthM, distance.frequencyHz, distance.dampingRatio);
        }

        public Snapshot withLengthM(float value) {
            return new Snapshot(value, frequencyHz, dampingRatio);
        }

        public Snapshot withFrequencyHz(float value) {
            return new Snapshot(lengthM, value, dampingRatio);
        }

        public Snapshot withDampingRatio(float value) {
            return new Snapshot(lengthM, frequencyHz, value);
        }

        public void apply(PhysicsDistanceJointComponent distance) {
            distance.lengthM = lengthM;
            distance.frequencyHz = frequencyHz;
            distance.dampingRatio = dampingRatio;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(lengthM, other.lengthM) == 0
                    && Float.compare(frequencyHz, other.frequencyHz) == 0
                    && Float.compare(dampingRatio, other.dampingRatio) == 0;
        }

        private static float clamp01(float value) {
            return Math.max(0f, Math.min(1f, value));
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditDistanceJointCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    int jointEntityId,
                                    Snapshot before,
                                    Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.jointHistoryId = historyIds != null ? historyIds.ensureForEntity(jointEntityId) : -1L;

        this.noop = world == null
                || historyIds == null
                || jointHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Distance Joint";
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

        int entityId = resolveJointEntityId();
        if (entityId < 0) return;

        ComponentMapper<PhysicsDistanceJointComponent> mDistance = world.getMapper(PhysicsDistanceJointComponent.class);
        PhysicsDistanceJointComponent distance = mDistance.getSafe(entityId, null);
        if (distance == null) return;

        snapshot.apply(distance);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.joint(entityId, JointDirtyBits.ALL);
        }
        EventFlow.i().publish(new EventFlow.JointParametersChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveJointEntityId() {
        int entityId = historyIds.entityOfHistoryId(jointHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}
