package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsPrismaticJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditPrismaticJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float axisX;
        private final float axisY;
        private final boolean enableLimit;
        private final float lowerTranslationM;
        private final float upperTranslationM;
        private final boolean enableMotor;
        private final float motorSpeedMps;
        private final float maxMotorForce;

        public Snapshot(float axisX,
                        float axisY,
                        boolean enableLimit,
                        float lowerTranslationM,
                        float upperTranslationM,
                        boolean enableMotor,
                        float motorSpeedMps,
                        float maxMotorForce) {
            float[] normalizedAxis = normalizeAxis(axisX, axisY);
            this.axisX = normalizedAxis[0];
            this.axisY = normalizedAxis[1];
            this.enableLimit = enableLimit;
            this.lowerTranslationM = lowerTranslationM;
            this.upperTranslationM = upperTranslationM;
            this.enableMotor = enableMotor;
            this.motorSpeedMps = motorSpeedMps;
            this.maxMotorForce = Math.max(0f, maxMotorForce);
        }

        public static Snapshot capture(PhysicsPrismaticJointComponent prismatic) {
            if (prismatic == null) return null;
            return new Snapshot(
                    prismatic.axisX,
                    prismatic.axisY,
                    prismatic.enableLimit,
                    prismatic.lowerTranslationM,
                    prismatic.upperTranslationM,
                    prismatic.enableMotor,
                    prismatic.motorSpeedMps,
                    prismatic.maxMotorForce
            );
        }

        public Snapshot withAxis(float x, float y) {
            return new Snapshot(x, y, enableLimit, lowerTranslationM, upperTranslationM, enableMotor, motorSpeedMps, maxMotorForce);
        }

        public Snapshot withEnableLimit(boolean value) {
            return new Snapshot(axisX, axisY, value, lowerTranslationM, upperTranslationM, enableMotor, motorSpeedMps, maxMotorForce);
        }

        public Snapshot withLowerTranslationM(float value) {
            return new Snapshot(axisX, axisY, enableLimit, value, upperTranslationM, enableMotor, motorSpeedMps, maxMotorForce);
        }

        public Snapshot withUpperTranslationM(float value) {
            return new Snapshot(axisX, axisY, enableLimit, lowerTranslationM, value, enableMotor, motorSpeedMps, maxMotorForce);
        }

        public Snapshot withEnableMotor(boolean value) {
            return new Snapshot(axisX, axisY, enableLimit, lowerTranslationM, upperTranslationM, value, motorSpeedMps, maxMotorForce);
        }

        public Snapshot withMotorSpeedMps(float value) {
            return new Snapshot(axisX, axisY, enableLimit, lowerTranslationM, upperTranslationM, enableMotor, value, maxMotorForce);
        }

        public Snapshot withMaxMotorForce(float value) {
            return new Snapshot(axisX, axisY, enableLimit, lowerTranslationM, upperTranslationM, enableMotor, motorSpeedMps, value);
        }

        public void apply(PhysicsPrismaticJointComponent prismatic) {
            prismatic.axisX = axisX;
            prismatic.axisY = axisY;
            prismatic.enableLimit = enableLimit;
            prismatic.lowerTranslationM = lowerTranslationM;
            prismatic.upperTranslationM = upperTranslationM;
            prismatic.enableMotor = enableMotor;
            prismatic.motorSpeedMps = motorSpeedMps;
            prismatic.maxMotorForce = maxMotorForce;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(axisX, other.axisX) == 0
                    && Float.compare(axisY, other.axisY) == 0
                    && enableLimit == other.enableLimit
                    && Float.compare(lowerTranslationM, other.lowerTranslationM) == 0
                    && Float.compare(upperTranslationM, other.upperTranslationM) == 0
                    && enableMotor == other.enableMotor
                    && Float.compare(motorSpeedMps, other.motorSpeedMps) == 0
                    && Float.compare(maxMotorForce, other.maxMotorForce) == 0;
        }

        private static float[] normalizeAxis(float x, float y) {
            float len = (float) Math.sqrt(x * x + y * y);
            if (len < 1e-6f) {
                return new float[]{1f, 0f};
            }
            return new float[]{x / len, y / len};
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditPrismaticJointCommand(World world,
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
        return "Edit Prismatic Joint";
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

        ComponentMapper<PhysicsPrismaticJointComponent> mPrismatic = world.getMapper(PhysicsPrismaticJointComponent.class);
        PhysicsPrismaticJointComponent prismatic = mPrismatic.getSafe(entityId, null);
        if (prismatic == null) return;

        snapshot.apply(prismatic);

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
