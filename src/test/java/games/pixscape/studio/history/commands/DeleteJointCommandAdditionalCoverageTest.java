package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class DeleteJointCommandAdditionalCoverageTest {

    private static final int[] JOINT_TYPES = {
            PhysicsJointComponent.TYPE_DISTANCE,
            PhysicsJointComponent.TYPE_REVOLUTE,
            PhysicsJointComponent.TYPE_PRISMATIC,
            PhysicsJointComponent.TYPE_WHEEL,
            PhysicsJointComponent.TYPE_FRICTION,
            PhysicsJointComponent.TYPE_MOTOR,
            PhysicsJointComponent.TYPE_WELD,
            PhysicsJointComponent.TYPE_PULLEY,
            PhysicsJointComponent.TYPE_GEAR
    };

    @Test
    public void nonJointEntityIsRejectedAndPreserved() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        world.process();

        try {
            new DeleteJointCommand(world, historyIds, entityId);
            Assert.fail("A non-joint entity must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "Invalid joint entity for deletion"));
        }

        Assert.assertTrue(world.getEntityManager().isActive(entityId));
    }

    @Test
    public void deleteEntitiesRestoresEveryJointTypeThroughInitializerAndRemapsReferences() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 1f);
        int bodyB = createBody(world, historyIds, 2f);
        long bodyAHistoryId = historyIds.historyIdOfEntity(bodyA);
        long bodyBHistoryId = historyIds.historyIdOfEntity(bodyB);

        int[] joints = new int[JOINT_TYPES.length];
        long[] jointHistoryIds = new long[JOINT_TYPES.length];
        for (int i = 0; i < JOINT_TYPES.length - 1; i++) {
            joints[i] = createJoint(world, historyIds, JOINT_TYPES[i], bodyA, bodyB);
            jointHistoryIds[i] = historyIds.historyIdOfEntity(joints[i]);
        }
        joints[JOINT_TYPES.length - 1] = createGearJoint(
                world, historyIds, bodyA, bodyB, joints[1], joints[2]);
        jointHistoryIds[JOINT_TYPES.length - 1] =
                historyIds.historyIdOfEntity(joints[JOINT_TYPES.length - 1]);

        IntArray deleted = new IntArray(2);
        deleted.add(bodyA);
        deleted.add(bodyB);
        history.execute(new DeleteEntitiesCommand(world, historyIds, deleted));
        world.process();

        for (int cycle = 0; cycle < 2; cycle++) {
            consumeFreedEntityIds(world, JOINT_TYPES.length + 2);

            history.undo();
            world.process();

            int restoredBodyA = historyIds.entityOfHistoryId(bodyAHistoryId);
            int restoredBodyB = historyIds.entityOfHistoryId(bodyBHistoryId);
            Assert.assertTrue(restoredBodyA >= 0);
            Assert.assertTrue(restoredBodyB >= 0);
            Assert.assertNotEquals(bodyA, restoredBodyA);
            Assert.assertNotEquals(bodyB, restoredBodyB);

            for (int i = 0; i < JOINT_TYPES.length; i++) {
                int restoredJoint = historyIds.entityOfHistoryId(jointHistoryIds[i]);
                Assert.assertTrue(restoredJoint >= 0);
                assertJoint(world, restoredJoint, JOINT_TYPES[i], restoredBodyA, restoredBodyB);
            }

            PhysicsGearJointComponent restoredGear = world.getMapper(PhysicsGearJointComponent.class)
                    .get(historyIds.entityOfHistoryId(jointHistoryIds[JOINT_TYPES.length - 1]));
            Assert.assertEquals(historyIds.entityOfHistoryId(jointHistoryIds[1]), restoredGear.joint1Eid);
            Assert.assertEquals(historyIds.entityOfHistoryId(jointHistoryIds[2]), restoredGear.joint2Eid);

            history.redo();
            world.process();
            Assert.assertEquals(-1, historyIds.entityOfHistoryId(bodyAHistoryId));
            Assert.assertEquals(-1, historyIds.entityOfHistoryId(bodyBHistoryId));
            for (long jointHistoryId : jointHistoryIds) {
                Assert.assertEquals(-1, historyIds.entityOfHistoryId(jointHistoryId));
            }
        }
    }

    @Test
    public void deleteJointKeepsLabelAndDelegatesStableUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        int bodyA = createBody(world, historyIds, 1f);
        int bodyB = createBody(world, historyIds, 2f);
        int joint = createJoint(world, historyIds, PhysicsJointComponent.TYPE_DISTANCE, bodyA, bodyB);
        long jointHistoryId = historyIds.historyIdOfEntity(joint);

        DeleteJointCommand command = new DeleteJointCommand(world, historyIds, joint);
        Assert.assertEquals("Delete Joint", command.label());
        history.execute(command);
        world.process();

        for (int cycle = 0; cycle < 2; cycle++) {
            history.undo();
            world.process();
            int restoredJoint = historyIds.entityOfHistoryId(jointHistoryId);
            assertJoint(world, restoredJoint, PhysicsJointComponent.TYPE_DISTANCE, bodyA, bodyB);

            history.redo();
            world.process();
            Assert.assertEquals(-1, historyIds.entityOfHistoryId(jointHistoryId));
        }
    }

    private static int createBody(World world, HistoryIdRegistry historyIds, float x) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);
        world.getMapper(TransformComponent.class).create(eid).x = x;
        return eid;
    }

    private static int createJoint(World world,
                                   HistoryIdRegistry historyIds,
                                   int type,
                                   int bodyA,
                                   int bodyB) {
        int eid = createBaseJoint(world, historyIds, type, bodyA, bodyB);
        switch (type) {
            case PhysicsJointComponent.TYPE_DISTANCE:
                PhysicsDistanceJointComponent distance =
                        world.getMapper(PhysicsDistanceJointComponent.class).create(eid);
                distance.lengthM = 3.25f;
                distance.frequencyHz = 2.5f;
                distance.dampingRatio = 0.35f;
                break;
            case PhysicsJointComponent.TYPE_REVOLUTE:
                PhysicsRevoluteJointComponent revolute =
                        world.getMapper(PhysicsRevoluteJointComponent.class).create(eid);
                revolute.enableLimit = true;
                revolute.lowerAngleRad = -0.4f;
                revolute.upperAngleRad = 0.7f;
                revolute.enableMotor = true;
                revolute.motorSpeedRad = 3.2f;
                revolute.maxMotorTorque = 9f;
                break;
            case PhysicsJointComponent.TYPE_PRISMATIC:
                PhysicsPrismaticJointComponent prismatic =
                        world.getMapper(PhysicsPrismaticJointComponent.class).create(eid);
                prismatic.axisX = 0.6f;
                prismatic.axisY = 0.8f;
                prismatic.enableLimit = true;
                prismatic.lowerTranslationM = -2f;
                prismatic.upperTranslationM = 4f;
                prismatic.enableMotor = true;
                prismatic.motorSpeedMps = 1.5f;
                prismatic.maxMotorForce = 7f;
                break;
            case PhysicsJointComponent.TYPE_WHEEL:
                PhysicsWheelJointComponent wheel =
                        world.getMapper(PhysicsWheelJointComponent.class).create(eid);
                wheel.axisX = 0.6f;
                wheel.axisY = 0.8f;
                wheel.enableMotor = true;
                wheel.motorSpeedRad = 9.5f;
                wheel.maxMotorTorque = 12.5f;
                wheel.frequencyHz = 4.2f;
                wheel.dampingRatio = 0.55f;
                break;
            case PhysicsJointComponent.TYPE_FRICTION:
                PhysicsFrictionJointComponent friction =
                        world.getMapper(PhysicsFrictionJointComponent.class).create(eid);
                friction.maxForce = 4f;
                friction.maxTorque = 5f;
                break;
            case PhysicsJointComponent.TYPE_MOTOR:
                PhysicsMotorJointComponent motor =
                        world.getMapper(PhysicsMotorJointComponent.class).create(eid);
                motor.linearOffsetX = 1.25f;
                motor.linearOffsetY = -0.75f;
                motor.angularOffsetRad = 0.42f;
                motor.maxForce = 8f;
                motor.maxTorque = 6f;
                motor.correctionFactor = 0.65f;
                break;
            case PhysicsJointComponent.TYPE_WELD:
                PhysicsWeldJointComponent weld =
                        world.getMapper(PhysicsWeldJointComponent.class).create(eid);
                weld.referenceAngleRad = 0.3f;
                weld.frequencyHz = 6f;
                weld.dampingRatio = 0.4f;
                break;
            case PhysicsJointComponent.TYPE_PULLEY:
                PhysicsPulleyJointComponent pulley =
                        world.getMapper(PhysicsPulleyJointComponent.class).create(eid);
                pulley.groundAx = 2f;
                pulley.groundAy = 30f;
                pulley.groundBx = 14f;
                pulley.groundBy = 31f;
                pulley.lengthAM = 6.5f;
                pulley.lengthBM = 8.5f;
                pulley.ratio = 1.6f;
                break;
            default:
                throw new IllegalArgumentException("Unsupported joint type: " + type);
        }
        return eid;
    }

    private static int createGearJoint(World world,
                                       HistoryIdRegistry historyIds,
                                       int bodyA,
                                       int bodyB,
                                       int joint1,
                                       int joint2) {
        int eid = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_GEAR, bodyA, bodyB);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(eid);
        gear.joint1Eid = joint1;
        gear.joint2Eid = joint2;
        gear.ratio = 2.25f;
        return eid;
    }

    private static int createBaseJoint(World world,
                                       HistoryIdRegistry historyIds,
                                       int type,
                                       int bodyA,
                                       int bodyB) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(eid);
        base.type = type;
        base.aEid = bodyA;
        base.bEid = bodyB;
        base.collideConnected = true;
        base.anchorAx = type + 0.1f;
        base.anchorAy = type + 0.2f;
        base.anchorBx = type + 0.3f;
        base.anchorBy = type + 0.4f;
        return eid;
    }

    private static void assertJoint(World world,
                                    int eid,
                                    int type,
                                    int expectedBodyA,
                                    int expectedBodyB) {
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(eid);
        Assert.assertEquals(type, base.type);
        Assert.assertEquals(expectedBodyA, base.aEid);
        Assert.assertEquals(expectedBodyB, base.bEid);
        Assert.assertTrue(base.collideConnected);
        Assert.assertEquals(type + 0.1f, base.anchorAx, 0f);
        Assert.assertEquals(type + 0.2f, base.anchorAy, 0f);
        Assert.assertEquals(type + 0.3f, base.anchorBx, 0f);
        Assert.assertEquals(type + 0.4f, base.anchorBy, 0f);

        switch (type) {
            case PhysicsJointComponent.TYPE_DISTANCE:
                PhysicsDistanceJointComponent distance =
                        world.getMapper(PhysicsDistanceJointComponent.class).get(eid);
                Assert.assertEquals(3.25f, distance.lengthM, 0f);
                Assert.assertEquals(2.5f, distance.frequencyHz, 0f);
                Assert.assertEquals(0.35f, distance.dampingRatio, 0f);
                break;
            case PhysicsJointComponent.TYPE_REVOLUTE:
                PhysicsRevoluteJointComponent revolute =
                        world.getMapper(PhysicsRevoluteJointComponent.class).get(eid);
                Assert.assertTrue(revolute.enableLimit);
                Assert.assertEquals(-0.4f, revolute.lowerAngleRad, 0f);
                Assert.assertEquals(0.7f, revolute.upperAngleRad, 0f);
                Assert.assertTrue(revolute.enableMotor);
                Assert.assertEquals(3.2f, revolute.motorSpeedRad, 0f);
                Assert.assertEquals(9f, revolute.maxMotorTorque, 0f);
                break;
            case PhysicsJointComponent.TYPE_PRISMATIC:
                PhysicsPrismaticJointComponent prismatic =
                        world.getMapper(PhysicsPrismaticJointComponent.class).get(eid);
                Assert.assertEquals(0.6f, prismatic.axisX, 0f);
                Assert.assertEquals(0.8f, prismatic.axisY, 0f);
                Assert.assertTrue(prismatic.enableLimit);
                Assert.assertEquals(-2f, prismatic.lowerTranslationM, 0f);
                Assert.assertEquals(4f, prismatic.upperTranslationM, 0f);
                Assert.assertTrue(prismatic.enableMotor);
                Assert.assertEquals(1.5f, prismatic.motorSpeedMps, 0f);
                Assert.assertEquals(7f, prismatic.maxMotorForce, 0f);
                break;
            case PhysicsJointComponent.TYPE_WHEEL:
                PhysicsWheelJointComponent wheel =
                        world.getMapper(PhysicsWheelJointComponent.class).get(eid);
                Assert.assertEquals(0.6f, wheel.axisX, 0f);
                Assert.assertEquals(0.8f, wheel.axisY, 0f);
                Assert.assertTrue(wheel.enableMotor);
                Assert.assertEquals(9.5f, wheel.motorSpeedRad, 0f);
                Assert.assertEquals(12.5f, wheel.maxMotorTorque, 0f);
                Assert.assertEquals(4.2f, wheel.frequencyHz, 0f);
                Assert.assertEquals(0.55f, wheel.dampingRatio, 0f);
                break;
            case PhysicsJointComponent.TYPE_FRICTION:
                PhysicsFrictionJointComponent friction =
                        world.getMapper(PhysicsFrictionJointComponent.class).get(eid);
                Assert.assertEquals(4f, friction.maxForce, 0f);
                Assert.assertEquals(5f, friction.maxTorque, 0f);
                break;
            case PhysicsJointComponent.TYPE_MOTOR:
                PhysicsMotorJointComponent motor =
                        world.getMapper(PhysicsMotorJointComponent.class).get(eid);
                Assert.assertEquals(1.25f, motor.linearOffsetX, 0f);
                Assert.assertEquals(-0.75f, motor.linearOffsetY, 0f);
                Assert.assertEquals(0.42f, motor.angularOffsetRad, 0f);
                Assert.assertEquals(8f, motor.maxForce, 0f);
                Assert.assertEquals(6f, motor.maxTorque, 0f);
                Assert.assertEquals(0.65f, motor.correctionFactor, 0f);
                break;
            case PhysicsJointComponent.TYPE_WELD:
                PhysicsWeldJointComponent weld =
                        world.getMapper(PhysicsWeldJointComponent.class).get(eid);
                Assert.assertEquals(0.3f, weld.referenceAngleRad, 0f);
                Assert.assertEquals(6f, weld.frequencyHz, 0f);
                Assert.assertEquals(0.4f, weld.dampingRatio, 0f);
                break;
            case PhysicsJointComponent.TYPE_PULLEY:
                PhysicsPulleyJointComponent pulley =
                        world.getMapper(PhysicsPulleyJointComponent.class).get(eid);
                Assert.assertEquals(2f, pulley.groundAx, 0f);
                Assert.assertEquals(30f, pulley.groundAy, 0f);
                Assert.assertEquals(14f, pulley.groundBx, 0f);
                Assert.assertEquals(31f, pulley.groundBy, 0f);
                Assert.assertEquals(6.5f, pulley.lengthAM, 0f);
                Assert.assertEquals(8.5f, pulley.lengthBM, 0f);
                Assert.assertEquals(1.6f, pulley.ratio, 0f);
                break;
            case PhysicsJointComponent.TYPE_GEAR:
                Assert.assertEquals(2.25f,
                        world.getMapper(PhysicsGearJointComponent.class).get(eid).ratio, 0f);
                break;
            default:
                throw new AssertionError("Unexpected joint type: " + type);
        }
    }

    private static void consumeFreedEntityIds(World world, int count) {
        for (int i = 0; i < count; i++) {
            world.create();
        }
    }
}
