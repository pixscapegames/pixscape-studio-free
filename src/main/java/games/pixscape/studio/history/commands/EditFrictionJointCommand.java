package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsFrictionJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditFrictionJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float maxForce;
        private final float maxTorque;

        public Snapshot(float maxForce, float maxTorque) {
            this.maxForce = Math.max(0f, maxForce);
            this.maxTorque = Math.max(0f, maxTorque);
        }

        public static Snapshot capture(PhysicsFrictionJointComponent friction) {
            if (friction == null) return null;
            return new Snapshot(
                    friction.maxForce,
                    friction.maxTorque
            );
        }

        public Snapshot withMaxForce(float value) {
            return new Snapshot(value, maxTorque);
        }

        public Snapshot withMaxTorque(float value) {
            return new Snapshot(maxForce, value);
        }

        public void apply(PhysicsFrictionJointComponent friction) {
            friction.maxForce = maxForce;
            friction.maxTorque = maxTorque;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(maxForce, other.maxForce) == 0
                    && Float.compare(maxTorque, other.maxTorque) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditFrictionJointCommand(World world,
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
        return "Edit Friction Joint";
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

        ComponentMapper<PhysicsFrictionJointComponent> mFriction =
                world.getMapper(PhysicsFrictionJointComponent.class);
        PhysicsFrictionJointComponent friction = mFriction.getSafe(entityId, null);
        if (friction == null) return;

        snapshot.apply(friction);

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