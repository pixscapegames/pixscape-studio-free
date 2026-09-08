package games.pixscape.studio.service.gameobject;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;

/** Maps the current authored Scene joint contract to asset-local Game Object joint data. */
final class GameObjectJointDataMapper {
    GameObjectAsset.GameObjectJointData fromScene(
            World world, int jointEntityId, int jointLocalId, IntIntMap entityToLocal,
            IntIntMap jointToLocal) {
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(jointEntityId);
        GenericEntitySnapshotData source = initializer.toSnapshotData(jointEntityId);
        if (!source.hasJoint) throw new IllegalArgumentException("Game Object joint capture requires PhysicsJointComponent.");
        GameObjectAsset.GameObjectJointData out = new GameObjectAsset.GameObjectJointData();
        out.jointLocalId = jointLocalId;
        out.type = source.jointType;
        out.bodyALocalEntityId = entityToLocal.get(source.jointAEid, -1);
        out.bodyBLocalEntityId = entityToLocal.get(source.jointBEid, -1);
        out.collideConnected = source.jointCollideConnected;
        out.anchorAx = source.jointAnchorAx; out.anchorAy = source.jointAnchorAy;
        out.anchorBx = source.jointAnchorBx; out.anchorBy = source.jointAnchorBy;
        switch (out.type) {
            case PhysicsJointComponent.TYPE_DISTANCE:
                out.distance = new GameObjectAsset.DistanceJointData();
                out.distance.lengthM = source.distanceLengthM; out.distance.frequencyHz = source.distanceFrequencyHz;
                out.distance.dampingRatio = source.distanceDampingRatio; break;
            case PhysicsJointComponent.TYPE_REVOLUTE:
                out.revolute = new GameObjectAsset.RevoluteJointData();
                out.revolute.enableLimit = source.revoluteEnableLimit; out.revolute.lowerAngleRad = source.revoluteLowerAngleRad;
                out.revolute.upperAngleRad = source.revoluteUpperAngleRad; out.revolute.enableMotor = source.revoluteEnableMotor;
                out.revolute.motorSpeedRad = source.revoluteMotorSpeedRad; out.revolute.maxMotorTorque = source.revoluteMaxMotorTorque; break;
            case PhysicsJointComponent.TYPE_PRISMATIC:
                out.prismatic = new GameObjectAsset.PrismaticJointData();
                out.prismatic.axisX = source.prismaticAxisX; out.prismatic.axisY = source.prismaticAxisY;
                out.prismatic.enableLimit = source.prismaticEnableLimit; out.prismatic.lowerTranslationM = source.prismaticLowerTranslationM;
                out.prismatic.upperTranslationM = source.prismaticUpperTranslationM; out.prismatic.enableMotor = source.prismaticEnableMotor;
                out.prismatic.motorSpeedMps = source.prismaticMotorSpeedMps; out.prismatic.maxMotorForce = source.prismaticMaxMotorForce; break;
            case PhysicsJointComponent.TYPE_PULLEY:
                // Asset capture localizes these world-space meter values in GameObjectAssetService.
                out.pulley = new GameObjectAsset.PulleyJointData();
                out.pulley.groundAnchorALocalX = source.pulleyGroundAx; out.pulley.groundAnchorALocalY = source.pulleyGroundAy;
                out.pulley.groundAnchorBLocalX = source.pulleyGroundBx; out.pulley.groundAnchorBLocalY = source.pulleyGroundBy;
                out.pulley.lengthAM = source.pulleyLengthAM; out.pulley.lengthBM = source.pulleyLengthBM;
                out.pulley.ratio = source.pulleyRatio; break;
            case PhysicsJointComponent.TYPE_GEAR:
                out.gear = new GameObjectAsset.GearJointData();
                out.gear.jointALocalId = jointToLocal.get(source.gearJoint1Eid, -1);
                out.gear.jointBLocalId = jointToLocal.get(source.gearJoint2Eid, -1);
                out.gear.ratio = source.gearRatio; break;
            case PhysicsJointComponent.TYPE_WHEEL:
                out.wheel = new GameObjectAsset.WheelJointData();
                out.wheel.axisX = source.wheelAxisX; out.wheel.axisY = source.wheelAxisY;
                out.wheel.enableMotor = source.wheelEnableMotor; out.wheel.motorSpeedRad = source.wheelMotorSpeedRad;
                out.wheel.maxMotorTorque = source.wheelMaxMotorTorque; out.wheel.frequencyHz = source.wheelFrequencyHz;
                out.wheel.dampingRatio = source.wheelDampingRatio; break;
            case PhysicsJointComponent.TYPE_WELD:
                out.weld = new GameObjectAsset.WeldJointData(); out.weld.referenceAngleRad = source.weldReferenceAngleRad;
                out.weld.frequencyHz = source.weldFrequencyHz; out.weld.dampingRatio = source.weldDampingRatio; break;
            case PhysicsJointComponent.TYPE_FRICTION:
                out.friction = new GameObjectAsset.FrictionJointData(); out.friction.maxForce = source.frictionMaxForce;
                out.friction.maxTorque = source.frictionMaxTorque; break;
            case PhysicsJointComponent.TYPE_MOTOR:
                out.motor = new GameObjectAsset.MotorJointData(); out.motor.linearOffsetX = source.motorLinearOffsetX;
                out.motor.linearOffsetY = source.motorLinearOffsetY; out.motor.angularOffsetRad = source.motorAngularOffsetRad;
                out.motor.maxForce = source.motorMaxForce; out.motor.maxTorque = source.motorMaxTorque;
                out.motor.correctionFactor = source.motorCorrectionFactor; break;
            default: throw new IllegalArgumentException("Game Object assets do not support Physics joint type " + out.type + ".");
        }
        return out;
    }

    GenericEntityInitializer toInitializer(World world, GameObjectAsset.GameObjectJointData source) {
        GenericEntitySnapshotData out = new GenericEntitySnapshotData();
        out.hasJoint = true; out.jointType = source.type;
        out.jointAEid = source.bodyALocalEntityId; out.jointBEid = source.bodyBLocalEntityId;
        out.jointCollideConnected = source.collideConnected;
        out.jointAnchorAx = source.anchorAx; out.jointAnchorAy = source.anchorAy;
        out.jointAnchorBx = source.anchorBx; out.jointAnchorBy = source.anchorBy;
        switch (source.type) {
            case PhysicsJointComponent.TYPE_DISTANCE:
                out.hasDistanceJoint = true; out.distanceLengthM = source.distance.lengthM;
                out.distanceFrequencyHz = source.distance.frequencyHz; out.distanceDampingRatio = source.distance.dampingRatio; break;
            case PhysicsJointComponent.TYPE_REVOLUTE:
                out.hasRevoluteJoint = true; out.revoluteEnableLimit = source.revolute.enableLimit;
                out.revoluteLowerAngleRad = source.revolute.lowerAngleRad; out.revoluteUpperAngleRad = source.revolute.upperAngleRad;
                out.revoluteEnableMotor = source.revolute.enableMotor; out.revoluteMotorSpeedRad = source.revolute.motorSpeedRad;
                out.revoluteMaxMotorTorque = source.revolute.maxMotorTorque; break;
            case PhysicsJointComponent.TYPE_PRISMATIC:
                out.hasPrismaticJoint = true; out.prismaticAxisX = source.prismatic.axisX; out.prismaticAxisY = source.prismatic.axisY;
                out.prismaticEnableLimit = source.prismatic.enableLimit; out.prismaticLowerTranslationM = source.prismatic.lowerTranslationM;
                out.prismaticUpperTranslationM = source.prismatic.upperTranslationM; out.prismaticEnableMotor = source.prismatic.enableMotor;
                out.prismaticMotorSpeedMps = source.prismatic.motorSpeedMps; out.prismaticMaxMotorForce = source.prismatic.maxMotorForce; break;
            case PhysicsJointComponent.TYPE_PULLEY:
                out.hasPulleyJoint = true; out.pulleyGroundAx = source.pulley.groundAnchorALocalX;
                out.pulleyGroundAy = source.pulley.groundAnchorALocalY; out.pulleyGroundBx = source.pulley.groundAnchorBLocalX;
                out.pulleyGroundBy = source.pulley.groundAnchorBLocalY; out.pulleyLengthAM = source.pulley.lengthAM;
                out.pulleyLengthBM = source.pulley.lengthBM; out.pulleyRatio = source.pulley.ratio; break;
            case PhysicsJointComponent.TYPE_GEAR:
                out.hasGearJoint = true; out.gearJoint1Eid = source.gear.jointALocalId;
                out.gearJoint2Eid = source.gear.jointBLocalId; out.gearRatio = source.gear.ratio; break;
            case PhysicsJointComponent.TYPE_WHEEL:
                out.hasWheelJoint = true; out.wheelAxisX = source.wheel.axisX; out.wheelAxisY = source.wheel.axisY;
                out.wheelEnableMotor = source.wheel.enableMotor; out.wheelMotorSpeedRad = source.wheel.motorSpeedRad;
                out.wheelMaxMotorTorque = source.wheel.maxMotorTorque; out.wheelFrequencyHz = source.wheel.frequencyHz;
                out.wheelDampingRatio = source.wheel.dampingRatio; break;
            case PhysicsJointComponent.TYPE_WELD:
                out.hasWeldJoint = true; out.weldReferenceAngleRad = source.weld.referenceAngleRad;
                out.weldFrequencyHz = source.weld.frequencyHz; out.weldDampingRatio = source.weld.dampingRatio; break;
            case PhysicsJointComponent.TYPE_FRICTION:
                out.hasFrictionJoint = true; out.frictionMaxForce = source.friction.maxForce; out.frictionMaxTorque = source.friction.maxTorque; break;
            case PhysicsJointComponent.TYPE_MOTOR:
                out.hasMotorJoint = true; out.motorLinearOffsetX = source.motor.linearOffsetX;
                out.motorLinearOffsetY = source.motor.linearOffsetY; out.motorAngularOffsetRad = source.motor.angularOffsetRad;
                out.motorMaxForce = source.motor.maxForce; out.motorMaxTorque = source.motor.maxTorque;
                out.motorCorrectionFactor = source.motor.correctionFactor; break;
            default: throw new IllegalArgumentException("Unsupported Game Object joint type " + source.type + ".");
        }
        return new GenericEntityInitializer(world).applySnapshotData(out);
    }
}
