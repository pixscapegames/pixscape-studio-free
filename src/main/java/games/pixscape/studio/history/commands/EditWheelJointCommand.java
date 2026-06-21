package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditWheelJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float axisX;
        private final float axisY;
        private final float frequencyHz;
        private final float dampingRatio;
        private final boolean enableMotor;
        private final float motorSpeedRad;
        private final float maxMotorTorque;

        public Snapshot(float axisX,
                        float axisY,
                        float frequencyHz,
                        float dampingRatio,
                        boolean enableMotor,
                        float motorSpeedRad,
                        float maxMotorTorque) {
            this.axisX = axisX;
            this.axisY = axisY;
            this.frequencyHz = frequencyHz;
            this.dampingRatio = dampingRatio;
            this.enableMotor = enableMotor;
            this.motorSpeedRad = motorSpeedRad;
            this.maxMotorTorque = maxMotorTorque;
        }

        public static Snapshot capture(PhysicsWheelJointComponent wheel) {
            if (wheel == null) return null;
            return new Snapshot(
                    wheel.axisX,
                    wheel.axisY,
                    wheel.frequencyHz,
                    wheel.dampingRatio,
                    wheel.enableMotor,
                    wheel.motorSpeedRad,
                    wheel.maxMotorTorque
            );
        }

        public Snapshot withAxisX(float value) {
            return new Snapshot(value, axisY, frequencyHz, dampingRatio, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withAxisY(float value) {
            return new Snapshot(axisX, value, frequencyHz, dampingRatio, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withFrequencyHz(float value) {
            return new Snapshot(axisX, axisY, value, dampingRatio, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withDampingRatio(float value) {
            return new Snapshot(axisX, axisY, frequencyHz, value, enableMotor, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withEnableMotor(boolean value) {
            return new Snapshot(axisX, axisY, frequencyHz, dampingRatio, value, motorSpeedRad, maxMotorTorque);
        }

        public Snapshot withMotorSpeedRad(float value) {
            return new Snapshot(axisX, axisY, frequencyHz, dampingRatio, enableMotor, value, maxMotorTorque);
        }

        public Snapshot withMaxMotorTorque(float value) {
            return new Snapshot(axisX, axisY, frequencyHz, dampingRatio, enableMotor, motorSpeedRad, value);
        }

        public void apply(PhysicsWheelJointComponent wheel) {
            wheel.axisX = axisX;
            wheel.axisY = axisY;
            wheel.frequencyHz = frequencyHz;
            wheel.dampingRatio = dampingRatio;
            wheel.enableMotor = enableMotor;
            wheel.motorSpeedRad = motorSpeedRad;
            wheel.maxMotorTorque = maxMotorTorque;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(axisX, other.axisX) == 0
                    && Float.compare(axisY, other.axisY) == 0
                    && Float.compare(frequencyHz, other.frequencyHz) == 0
                    && Float.compare(dampingRatio, other.dampingRatio) == 0
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

    public EditWheelJointCommand(World world,
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
        return "Edit Wheel Joint";
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

        ComponentMapper<PhysicsWheelJointComponent> mWheel = world.getMapper(PhysicsWheelJointComponent.class);
        PhysicsWheelJointComponent wheel = mWheel.getSafe(entityId, null);
        if (wheel == null) return;

        snapshot.apply(wheel);

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
