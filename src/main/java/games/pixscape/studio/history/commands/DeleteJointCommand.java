package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryIdRegistry;

/**
 * History command for deleting/restoring a physics joint.
 */
public final class DeleteJointCommand implements Command {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final JointSnapshot snapshot;

    private long jointHistoryId = -1L;

    public DeleteJointCommand(World world,
                              HistoryIdRegistry historyIds,
                              int jointEntityId) {
        this.world = world;
        this.historyIds = historyIds;
        this.snapshot = JointSnapshot.capture(world, historyIds, jointEntityId);
        if (this.snapshot == null) {
            throw new IllegalArgumentException("Invalid joint entity for deletion: " + jointEntityId);
        }
        this.jointHistoryId = historyIds.ensureForEntity(jointEntityId);
    }

    @Override
    public String label() {
        return "Delete Joint";
    }

    @Override
    public void redo() {
        int jointEntityId = historyIds.entityOfHistoryId(jointHistoryId);
        if (jointEntityId >= 0 && world.getEntityManager().isActive(jointEntityId)) {
            IdentityRegistry.unindexEntityImmediately(world, jointEntityId);
            world.delete(jointEntityId);
        }
        historyIds.unbindHistoryId(jointHistoryId);
    }

    @Override
    public void undo() {
        int recreatedJointId = world.create();
        snapshot.restore(world, historyIds, recreatedJointId);
        historyIds.bind(recreatedJointId, jointHistoryId);
    }

    private static final class JointSnapshot {
        int type;
        boolean collideConnected;
        float anchorAx, anchorAy, anchorBx, anchorBy;
        long aHistoryId, bHistoryId;

        boolean hasDistance;
        float distLengthM, distFrequencyHz, distDampingRatio;

        boolean hasRevolute;
        boolean revEnableLimit;
        float revLowerAngleRad, revUpperAngleRad;
        boolean revEnableMotor;
        float revMotorSpeedRad, revMaxMotorTorque;

        boolean hasPrismatic;
        float prismAxisX, prismAxisY;
        boolean prismEnableLimit;
        float prismLowerTranslationM, prismUpperTranslationM;
        boolean prismEnableMotor;
        float prismMotorSpeedMps, prismMaxMotorForce;

        boolean hasWheel;
        float wheelAxisX, wheelAxisY;
        boolean wheelEnableMotor;
        float wheelMotorSpeedRad, wheelMaxMotorTorque;
        float wheelFrequencyHz, wheelDampingRatio;

        boolean hasFriction;
        float frictionMaxForce, frictionMaxTorque;

        boolean hasMotor;
        float motorLinearOffsetX, motorLinearOffsetY;
        float motorAngularOffsetRad;
        float motorMaxForce, motorMaxTorque;
        float motorCorrectionFactor;

        boolean hasWeld;
        float weldReferenceAngleRad, weldFrequencyHz, weldDampingRatio;

        boolean hasPulley;
        float pulleyGroundAx, pulleyGroundAy, pulleyGroundBx, pulleyGroundBy;
        float pulleyLengthAM, pulleyLengthBM, pulleyRatio;

        boolean hasGear;
        long gearJoint1HistoryId, gearJoint2HistoryId;
        float gearRatio;

        static JointSnapshot capture(World world, HistoryIdRegistry historyIds, int jointEntityId) {
            var mJoint = world.getMapper(PhysicsJointComponent.class);
            PhysicsJointComponent base = mJoint.getSafe(jointEntityId, null);
            if (base == null) return null;

            JointSnapshot snap = new JointSnapshot();
            snap.type = base.type;
            snap.collideConnected = base.collideConnected;
            snap.anchorAx = base.anchorAx;
            snap.anchorAy = base.anchorAy;
            snap.anchorBx = base.anchorBx;
            snap.anchorBy = base.anchorBy;
            snap.aHistoryId = (base.aEid >= 0) ? historyIds.ensureForEntity(base.aEid) : -1L;
            snap.bHistoryId = (base.bEid >= 0) ? historyIds.ensureForEntity(base.bEid) : -1L;

            var mDist = world.getMapper(PhysicsDistanceJointComponent.class);
            PhysicsDistanceJointComponent dist = mDist.getSafe(jointEntityId, null);
            if (dist != null) {
                snap.hasDistance = true;
                snap.distLengthM = dist.lengthM;
                snap.distFrequencyHz = dist.frequencyHz;
                snap.distDampingRatio = dist.dampingRatio;
            }

            var mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
            PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEntityId, null);
            if (rev != null) {
                snap.hasRevolute = true;
                snap.revEnableLimit = rev.enableLimit;
                snap.revLowerAngleRad = rev.lowerAngleRad;
                snap.revUpperAngleRad = rev.upperAngleRad;
                snap.revEnableMotor = rev.enableMotor;
                snap.revMotorSpeedRad = rev.motorSpeedRad;
                snap.revMaxMotorTorque = rev.maxMotorTorque;
            }

            var mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
            PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEntityId, null);
            if (prism != null) {
                snap.hasPrismatic = true;
                snap.prismAxisX = prism.axisX;
                snap.prismAxisY = prism.axisY;
                snap.prismEnableLimit = prism.enableLimit;
                snap.prismLowerTranslationM = prism.lowerTranslationM;
                snap.prismUpperTranslationM = prism.upperTranslationM;
                snap.prismEnableMotor = prism.enableMotor;
                snap.prismMotorSpeedMps = prism.motorSpeedMps;
                snap.prismMaxMotorForce = prism.maxMotorForce;
            }

            var mWheel = world.getMapper(PhysicsWheelJointComponent.class);
            PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEntityId, null);
            if (wheel != null) {
                snap.hasWheel = true;
                snap.wheelAxisX = wheel.axisX;
                snap.wheelAxisY = wheel.axisY;
                snap.wheelEnableMotor = wheel.enableMotor;
                snap.wheelMotorSpeedRad = wheel.motorSpeedRad;
                snap.wheelMaxMotorTorque = wheel.maxMotorTorque;
                snap.wheelFrequencyHz = wheel.frequencyHz;
                snap.wheelDampingRatio = wheel.dampingRatio;
            }

            var mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
            PhysicsFrictionJointComponent friction = mFriction.getSafe(jointEntityId, null);
            if (friction != null) {
                snap.hasFriction = true;
                snap.frictionMaxForce = friction.maxForce;
                snap.frictionMaxTorque = friction.maxTorque;
            }

            var mMotor = world.getMapper(PhysicsMotorJointComponent.class);
            PhysicsMotorJointComponent motor = mMotor.getSafe(jointEntityId, null);
            if (motor != null) {
                snap.hasMotor = true;
                snap.motorLinearOffsetX = motor.linearOffsetX;
                snap.motorLinearOffsetY = motor.linearOffsetY;
                snap.motorAngularOffsetRad = motor.angularOffsetRad;
                snap.motorMaxForce = motor.maxForce;
                snap.motorMaxTorque = motor.maxTorque;
                snap.motorCorrectionFactor = motor.correctionFactor;
            }

            var mWeld = world.getMapper(PhysicsWeldJointComponent.class);
            PhysicsWeldJointComponent weld = mWeld.getSafe(jointEntityId, null);
            if (weld != null) {
                snap.hasWeld = true;
                snap.weldReferenceAngleRad = weld.referenceAngleRad;
                snap.weldFrequencyHz = weld.frequencyHz;
                snap.weldDampingRatio = weld.dampingRatio;
            }

            var mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
            PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEntityId, null);
            if (pulley != null) {
                snap.hasPulley = true;
                snap.pulleyGroundAx = pulley.groundAx;
                snap.pulleyGroundAy = pulley.groundAy;
                snap.pulleyGroundBx = pulley.groundBx;
                snap.pulleyGroundBy = pulley.groundBy;
                snap.pulleyLengthAM = pulley.lengthAM;
                snap.pulleyLengthBM = pulley.lengthBM;
                snap.pulleyRatio = pulley.ratio;
            }

            var mGear = world.getMapper(PhysicsGearJointComponent.class);
            PhysicsGearJointComponent gear = mGear.getSafe(jointEntityId, null);
            if (gear != null) {
                snap.hasGear = true;
                snap.gearJoint1HistoryId = (gear.joint1Eid >= 0) ? historyIds.ensureForEntity(gear.joint1Eid) : -1L;
                snap.gearJoint2HistoryId = (gear.joint2Eid >= 0) ? historyIds.ensureForEntity(gear.joint2Eid) : -1L;
                snap.gearRatio = gear.ratio;
            }

            return snap;
        }

        void restore(World world, HistoryIdRegistry historyIds, int jointEntityId) {
            int aEntityId = historyIds.entityOfHistoryId(aHistoryId);
            int bEntityId = historyIds.entityOfHistoryId(bHistoryId);

            var mJoint = world.getMapper(PhysicsJointComponent.class);
            PhysicsJointComponent base = mJoint.has(jointEntityId) ? mJoint.get(jointEntityId) : mJoint.create(jointEntityId);
            base.type = type;
            base.aEid = aEntityId;
            base.bEid = bEntityId;
            base.collideConnected = collideConnected;
            base.anchorAx = anchorAx;
            base.anchorAy = anchorAy;
            base.anchorBx = anchorBx;
            base.anchorBy = anchorBy;

            if (hasGear) {
                int joint1EntityId = historyIds.entityOfHistoryId(gearJoint1HistoryId);
                int joint2EntityId = historyIds.entityOfHistoryId(gearJoint2HistoryId);

                var mGear = world.getMapper(PhysicsGearJointComponent.class);
                PhysicsGearJointComponent gear =
                        mGear.has(jointEntityId) ? mGear.get(jointEntityId) : mGear.create(jointEntityId);
                gear.joint1Eid = joint1EntityId;
                gear.joint2Eid = joint2EntityId;
                gear.ratio = gearRatio;

                PhysicsJointComponent source1 = mJoint.getSafe(joint1EntityId, null);
                PhysicsJointComponent source2 = mJoint.getSafe(joint2EntityId, null);
                if (source1 != null) base.aEid = source1.bEid;
                if (source2 != null) base.bEid = source2.bEid;
            }

            if (hasDistance) {
                var mDist = world.getMapper(PhysicsDistanceJointComponent.class);
                PhysicsDistanceJointComponent dist = mDist.has(jointEntityId) ? mDist.get(jointEntityId) : mDist.create(jointEntityId);
                dist.lengthM = distLengthM;
                dist.frequencyHz = distFrequencyHz;
                dist.dampingRatio = distDampingRatio;
            }

            if (hasRevolute) {
                var mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
                PhysicsRevoluteJointComponent rev = mRev.has(jointEntityId) ? mRev.get(jointEntityId) : mRev.create(jointEntityId);
                rev.enableLimit = revEnableLimit;
                rev.lowerAngleRad = revLowerAngleRad;
                rev.upperAngleRad = revUpperAngleRad;
                rev.enableMotor = revEnableMotor;
                rev.motorSpeedRad = revMotorSpeedRad;
                rev.maxMotorTorque = revMaxMotorTorque;
            }

            if (hasPrismatic) {
                var mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
                PhysicsPrismaticJointComponent prism = mPrism.has(jointEntityId) ? mPrism.get(jointEntityId) : mPrism.create(jointEntityId);
                prism.axisX = prismAxisX;
                prism.axisY = prismAxisY;
                prism.enableLimit = prismEnableLimit;
                prism.lowerTranslationM = prismLowerTranslationM;
                prism.upperTranslationM = prismUpperTranslationM;
                prism.enableMotor = prismEnableMotor;
                prism.motorSpeedMps = prismMotorSpeedMps;
                prism.maxMotorForce = prismMaxMotorForce;
            }

            if (hasWheel) {
                var mWheel = world.getMapper(PhysicsWheelJointComponent.class);
                PhysicsWheelJointComponent wheel = mWheel.has(jointEntityId) ? mWheel.get(jointEntityId) : mWheel.create(jointEntityId);
                wheel.axisX = wheelAxisX;
                wheel.axisY = wheelAxisY;
                wheel.enableMotor = wheelEnableMotor;
                wheel.motorSpeedRad = wheelMotorSpeedRad;
                wheel.maxMotorTorque = wheelMaxMotorTorque;
                wheel.frequencyHz = wheelFrequencyHz;
                wheel.dampingRatio = wheelDampingRatio;
            }

            if (hasFriction) {
                var mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
                PhysicsFrictionJointComponent friction =
                        mFriction.has(jointEntityId) ? mFriction.get(jointEntityId) : mFriction.create(jointEntityId);
                friction.maxForce = frictionMaxForce;
                friction.maxTorque = frictionMaxTorque;
            }

            if (hasMotor) {
                var mMotor = world.getMapper(PhysicsMotorJointComponent.class);
                PhysicsMotorJointComponent motor =
                        mMotor.has(jointEntityId) ? mMotor.get(jointEntityId) : mMotor.create(jointEntityId);
                motor.linearOffsetX = motorLinearOffsetX;
                motor.linearOffsetY = motorLinearOffsetY;
                motor.angularOffsetRad = motorAngularOffsetRad;
                motor.maxForce = motorMaxForce;
                motor.maxTorque = motorMaxTorque;
                motor.correctionFactor = motorCorrectionFactor;
            }

            if (hasWeld) {
                var mWeld = world.getMapper(PhysicsWeldJointComponent.class);
                PhysicsWeldJointComponent weld =
                        mWeld.has(jointEntityId) ? mWeld.get(jointEntityId) : mWeld.create(jointEntityId);
                weld.referenceAngleRad = weldReferenceAngleRad;
                weld.frequencyHz = weldFrequencyHz;
                weld.dampingRatio = weldDampingRatio;
            }

            if (hasPulley) {
                var mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
                PhysicsPulleyJointComponent pulley =
                        mPulley.has(jointEntityId) ? mPulley.get(jointEntityId) : mPulley.create(jointEntityId);
                pulley.groundAx = pulleyGroundAx;
                pulley.groundAy = pulleyGroundAy;
                pulley.groundBx = pulleyGroundBx;
                pulley.groundBy = pulleyGroundBy;
                pulley.lengthAM = pulleyLengthAM;
                pulley.lengthBM = pulleyLengthBM;
                pulley.ratio = pulleyRatio;
            }

        }
    }
}
