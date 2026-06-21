package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsRevoluteJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditRevoluteJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final boolean enableLimit;
        private final float lowerAngleRad;
        private final float upperAngleRad;
        private final boolean enableMotor;
        private final float motorSpeedRad;
        private final float maxMotorTorque;

        public Snapshot(boolean enableLimit,
                        float lowerAngleRad,
                        float upperAngleRad,
                        boolean enableMotor,
                        float motorSpeedRad,
                        float maxMotorTorque) {
            this.enableLimit = enableLimit;
            this.lowerAngleRad = lowerAngleRad;
            this.upperAngleRad = upperAngleRad;
            this.enableMotor = enableMotor;
            this.motorSpeedRad = motorSpeedRad;
            this.maxMotorTorque = Math.max(0f, maxMotorTorque);
        }

        public static Snapshot capture(PhysicsRevoluteJointComponent revolute) {
            if (revolute == null) return null;
            return new Snapshot(
                    revolute.enableLimit,
                    revolute.lowerAngleRad,
                    revolute.upperAngleRad,
                    revolute.enableMotor,
                    revolute.motorSpeedRad,
                    revolute.maxMotorTorque
            );
        }

        public Snapshot withEnableLimit(boolean value) {
            return new Snapshot(value, lowerAngleRad, upperAngleRad, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withLowerAngleRad(float value) {
            return new Snapshot(enableLimit, value, upperAngleRad, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withUpperAngleRad(float value) {
            return new Snapshot(enableLimit, lowerAngleRad, value, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withEnableMotor(boolean value) {
            return new Snapshot(enableLimit, lowerAngleRad, upperAngleRad, value, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withMotorSpeedRad(float value) {
            return new Snapshot(enableLimit, lowerAngleRad, upperAngleRad, enableMotor, value, maxMotorTorque);
        }

        public Snapshot withMaxMotorTorque(float value) {
            return new Snapshot(enableLimit, lowerAngleRad, upperAngleRad, enableMotor, motorSpeedRad, value);
        }

        public void apply(PhysicsRevoluteJointComponent revolute) {
            revolute.enableLimit = enableLimit;
            revolute.lowerAngleRad = lowerAngleRad;
            revolute.upperAngleRad = upperAngleRad;
            revolute.enableMotor = enableMotor;
            revolute.motorSpeedRad = motorSpeedRad;
            revolute.maxMotorTorque = maxMotorTorque;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return enableLimit == other.enableLimit
                    && Float.compare(lowerAngleRad, other.lowerAngleRad) == 0
                    && Float.compare(upperAngleRad, other.upperAngleRad) == 0
                    && enableMotor == other.enableMotor
                    && Float.compare(motorSpeedRad, other.motorSpeedRad) == 0
                    && Float.compare(maxMotorTorque, other.maxMotorTorque) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditRevoluteJointCommand(World world,
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
        return "Edit Revolute Joint";
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

        ComponentMapper<PhysicsRevoluteJointComponent> mRevolute = world.getMapper(PhysicsRevoluteJointComponent.class);
        PhysicsRevoluteJointComponent revolute = mRevolute.getSafe(entityId, null);
        if (revolute == null) return;

        snapshot.apply(revolute);

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
