package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsWeldJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditWeldJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float referenceAngleRad;
        private final float frequencyHz;
        private final float dampingRatio;

        public Snapshot(float referenceAngleRad, float frequencyHz, float dampingRatio) {
            this.referenceAngleRad = referenceAngleRad;
            this.frequencyHz = Math.max(0f, frequencyHz);
            this.dampingRatio = Math.max(0f, Math.min(1f, dampingRatio));
        }

        public static Snapshot capture(PhysicsWeldJointComponent weld) {
            if (weld == null) return null;
            return new Snapshot(
                    weld.referenceAngleRad,
                    weld.frequencyHz,
                    weld.dampingRatio
            );
        }

        public Snapshot withReferenceAngleRad(float value) {
            return new Snapshot(value, frequencyHz, dampingRatio);
        }

        public Snapshot withFrequencyHz(float value) {
            return new Snapshot(referenceAngleRad, value, dampingRatio);
        }

        public Snapshot withDampingRatio(float value) {
            return new Snapshot(referenceAngleRad, frequencyHz, value);
        }

        public void apply(PhysicsWeldJointComponent weld) {
            weld.referenceAngleRad = referenceAngleRad;
            weld.frequencyHz = frequencyHz;
            weld.dampingRatio = dampingRatio;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(referenceAngleRad, other.referenceAngleRad) == 0
                    && Float.compare(frequencyHz, other.frequencyHz) == 0
                    && Float.compare(dampingRatio, other.dampingRatio) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditWeldJointCommand(World world,
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
        return "Edit Weld Joint";
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

        ComponentMapper<PhysicsWeldJointComponent> mWeld =
                world.getMapper(PhysicsWeldJointComponent.class);
        PhysicsWeldJointComponent weld = mWeld.getSafe(entityId, null);
        if (weld == null) return;

        snapshot.apply(weld);

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