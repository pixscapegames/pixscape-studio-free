package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsMotorJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditMotorJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float linearOffsetX;
        private final float linearOffsetY;
        private final float angularOffsetRad;
        private final float maxForce;
        private final float maxTorque;
        private final float correctionFactor;

        public Snapshot(float linearOffsetX,
                        float linearOffsetY,
                        float angularOffsetRad,
                        float maxForce,
                        float maxTorque,
                        float correctionFactor) {
            this.linearOffsetX = linearOffsetX;
            this.linearOffsetY = linearOffsetY;
            this.angularOffsetRad = angularOffsetRad;
            this.maxForce = Math.max(0f, maxForce);
            this.maxTorque = Math.max(0f, maxTorque);
            this.correctionFactor = Math.max(0f, Math.min(1f, correctionFactor));
        }

        public static Snapshot capture(PhysicsMotorJointComponent motor) {
            if (motor == null) return null;
            return new Snapshot(
                    motor.linearOffsetX,
                    motor.linearOffsetY,
                    motor.angularOffsetRad,
                    motor.maxForce,
                    motor.maxTorque,
                    motor.correctionFactor
            );
        }

        public Snapshot withLinearOffsetX(float value) {
            return new Snapshot(value, linearOffsetY, angularOffsetRad, maxForce, maxTorque, correctionFactor);
        }

        public Snapshot withLinearOffsetY(float value) {
            return new Snapshot(linearOffsetX, value, angularOffsetRad, maxForce, maxTorque, correctionFactor);
        }

        public Snapshot withAngularOffsetRad(float value) {
            return new Snapshot(linearOffsetX, linearOffsetY, value, maxForce, maxTorque, correctionFactor);
        }

        public Snapshot withMaxForce(float value) {
            return new Snapshot(linearOffsetX, linearOffsetY, angularOffsetRad, value, maxTorque, correctionFactor);
        }

        public Snapshot withMaxTorque(float value) {
            return new Snapshot(linearOffsetX, linearOffsetY, angularOffsetRad, maxForce, value, correctionFactor);
        }

        public Snapshot withCorrectionFactor(float value) {
            return new Snapshot(linearOffsetX, linearOffsetY, angularOffsetRad, maxForce, maxTorque, value);
        }

        public void apply(PhysicsMotorJointComponent motor) {
            motor.linearOffsetX = linearOffsetX;
            motor.linearOffsetY = linearOffsetY;
            motor.angularOffsetRad = angularOffsetRad;
            motor.maxForce = maxForce;
            motor.maxTorque = maxTorque;
            motor.correctionFactor = correctionFactor;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(linearOffsetX, other.linearOffsetX) == 0
                    && Float.compare(linearOffsetY, other.linearOffsetY) == 0
                    && Float.compare(angularOffsetRad, other.angularOffsetRad) == 0
                    && Float.compare(maxForce, other.maxForce) == 0
                    && Float.compare(maxTorque, other.maxTorque) == 0
                    && Float.compare(correctionFactor, other.correctionFactor) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditMotorJointCommand(World world,
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
        return "Edit Motor Joint";
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

        ComponentMapper<PhysicsMotorJointComponent> mMotor =
                world.getMapper(PhysicsMotorJointComponent.class);
        PhysicsMotorJointComponent motor = mMotor.getSafe(entityId, null);
        if (motor == null) return;

        snapshot.apply(motor);

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