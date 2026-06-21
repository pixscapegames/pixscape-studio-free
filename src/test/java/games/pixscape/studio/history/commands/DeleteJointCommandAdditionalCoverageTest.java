package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class DeleteJointCommandAdditionalCoverageTest {

    @Test
    public void deleteMotorJointUndoRedoRestoresComponentAndHistoryBinding() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 10f, 8f);
        int jointEid = createMotorJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();

        Assert.assertFalse(world.getEntityManager().isActive(jointEid));
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));

        history.undo();
        world.process();

        int restoredJointEid = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(restoredJointEid >= 0);
        Assert.assertTrue(world.getEntityManager().isActive(restoredJointEid));

        PhysicsMotorJointComponent motor = world.getMapper(PhysicsMotorJointComponent.class).get(restoredJointEid);
        Assert.assertEquals(1.25f, motor.linearOffsetX, 0f);
        Assert.assertEquals(-0.75f, motor.linearOffsetY, 0f);
        Assert.assertEquals(0.42f, motor.angularOffsetRad, 0f);
        Assert.assertEquals(8f, motor.maxForce, 0f);
        Assert.assertEquals(6f, motor.maxTorque, 0f);
        Assert.assertEquals(0.65f, motor.correctionFactor, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deletePulleyJointUndoRestoresGroundAnchorsLengthsAndRatio() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 3f, 4f);
        int bodyB = createBody(world, historyIds, 12f, 6f);
        int jointEid = createPulleyJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restoredJointEid = historyIds.entityOfHistoryId(historyId);
        PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class).get(restoredJointEid);
        Assert.assertEquals(2f, pulley.groundAx, 0f);
        Assert.assertEquals(30f, pulley.groundAy, 0f);
        Assert.assertEquals(14f, pulley.groundBx, 0f);
        Assert.assertEquals(30f, pulley.groundBy, 0f);
        Assert.assertEquals(6.5f, pulley.lengthAM, 0f);
        Assert.assertEquals(8.5f, pulley.lengthBM, 0f);
        Assert.assertEquals(1.6f, pulley.ratio, 0f);
    }

    @Test
    public void deleteGearJointUndoRestoresJointReferencesAndDerivedBodies() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 1f, 0f);
        int bodyC = createBody(world, historyIds, 2f, 0f);

        int sourceJoint1 = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_REVOLUTE, bodyA, bodyB);
        int sourceJoint2 = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_PRISMATIC, bodyB, bodyC);

        int gearJoint = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_GEAR, bodyA, bodyC);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(gearJoint);
        gear.joint1Eid = sourceJoint1;
        gear.joint2Eid = sourceJoint2;
        gear.ratio = 2.25f;

        long historyId = historyIds.historyIdOfEntity(gearJoint);

        history.execute(new DeleteJointCommand(world, historyIds, gearJoint));
        world.process();
        history.undo();
        world.process();

        int restoredJointEid = historyIds.entityOfHistoryId(historyId);
        PhysicsGearJointComponent restoredGear = world.getMapper(PhysicsGearJointComponent.class).get(restoredJointEid);
        PhysicsJointComponent restoredBase = world.getMapper(PhysicsJointComponent.class).get(restoredJointEid);

        Assert.assertEquals(sourceJoint1, restoredGear.joint1Eid);
        Assert.assertEquals(sourceJoint2, restoredGear.joint2Eid);
        Assert.assertEquals(2.25f, restoredGear.ratio, 0f);

        Assert.assertEquals(bodyB, restoredBase.aEid);
        Assert.assertEquals(bodyC, restoredBase.bEid);
    }



    @Test
    public void deleteRevoluteJointUndoRedoRestoresSpecificFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 3f, 0f);
        int jointEid = createRevoluteJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restored = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_REVOLUTE, base.type);
        Assert.assertEquals(bodyA, base.aEid);
        Assert.assertEquals(bodyB, base.bEid);
        assertOnlyExpectedJointSpecificComponent(world, restored, PhysicsJointComponent.TYPE_REVOLUTE);

        PhysicsRevoluteJointComponent c = world.getMapper(PhysicsRevoluteJointComponent.class).get(restored);
        Assert.assertTrue(c.enableMotor);
        Assert.assertEquals(3.2f, c.motorSpeedRad, 0f);
        Assert.assertEquals(9f, c.maxMotorTorque, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deletePrismaticJointUndoRedoRestoresSpecificFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 3f, 1f);
        int jointEid = createPrismaticJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restored = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_PRISMATIC, base.type);
        Assert.assertEquals(bodyA, base.aEid);
        Assert.assertEquals(bodyB, base.bEid);
        assertOnlyExpectedJointSpecificComponent(world, restored, PhysicsJointComponent.TYPE_PRISMATIC);
        PhysicsPrismaticJointComponent c = world.getMapper(PhysicsPrismaticJointComponent.class).get(restored);
        Assert.assertEquals(0.6f, c.axisX, 0f);
        Assert.assertEquals(0.8f, c.axisY, 0f);
        Assert.assertTrue(c.enableMotor);
        Assert.assertEquals(7f, c.maxMotorForce, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deleteFrictionJointUndoRedoRestoresSpecificFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 2f, 2f);
        int jointEid = createFrictionJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restored = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_FRICTION, base.type);
        Assert.assertEquals(bodyA, base.aEid);
        Assert.assertEquals(bodyB, base.bEid);
        assertOnlyExpectedJointSpecificComponent(world, restored, PhysicsJointComponent.TYPE_FRICTION);
        PhysicsFrictionJointComponent c = world.getMapper(PhysicsFrictionJointComponent.class).get(restored);
        Assert.assertEquals(4f, c.maxForce, 0f);
        Assert.assertEquals(5f, c.maxTorque, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deleteWeldJointUndoRedoRestoresSpecificFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);
        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 1f, 1f);
        int jointEid = createWeldJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restored = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_WELD, base.type);
        Assert.assertEquals(bodyA, base.aEid);
        Assert.assertEquals(bodyB, base.bEid);
        assertOnlyExpectedJointSpecificComponent(world, restored, PhysicsJointComponent.TYPE_WELD);
        PhysicsWeldJointComponent c = world.getMapper(PhysicsWeldJointComponent.class).get(restored);
        Assert.assertEquals(0.3f, c.referenceAngleRad, 0f);
        Assert.assertEquals(6f, c.frequencyHz, 0f);
        Assert.assertEquals(0.4f, c.dampingRatio, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deleteDistanceJointUndoRedoRestoresBaseAndDistanceFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 0f, 0f);
        int bodyB = createBody(world, historyIds, 5f, 0f);
        int jointEid = createDistanceJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));

        history.undo();
        world.process();
        int restored = historyIds.entityOfHistoryId(historyId);
        Assert.assertTrue(world.getEntityManager().isActive(restored));
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        PhysicsDistanceJointComponent dist = world.getMapper(PhysicsDistanceJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_DISTANCE, base.type);
        Assert.assertEquals(3.25f, dist.lengthM, 0f);
        Assert.assertEquals(2.5f, dist.frequencyHz, 0f);
        Assert.assertEquals(0.35f, dist.dampingRatio, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    @Test
    public void deleteWheelJointUndoRedoRestoresWheelFields() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds, 1f, 1f);
        int bodyB = createBody(world, historyIds, 8f, 1f);
        int jointEid = createWheelJoint(world, historyIds, bodyA, bodyB);
        long historyId = historyIds.historyIdOfEntity(jointEid);

        history.execute(new DeleteJointCommand(world, historyIds, jointEid));
        world.process();
        history.undo();
        world.process();

        int restored = historyIds.entityOfHistoryId(historyId);
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(restored);
        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).get(restored);
        Assert.assertEquals(PhysicsJointComponent.TYPE_WHEEL, base.type);
        Assert.assertEquals(0.6f, wheel.axisX, 0f);
        Assert.assertEquals(0.8f, wheel.axisY, 0f);
        Assert.assertEquals(4.2f, wheel.frequencyHz, 0f);
        Assert.assertEquals(0.55f, wheel.dampingRatio, 0f);
        Assert.assertTrue(wheel.enableMotor);
        Assert.assertEquals(9.5f, wheel.motorSpeedRad, 0f);
        Assert.assertEquals(12.5f, wheel.maxMotorTorque, 0f);

        history.redo();
        world.process();
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(historyId));
    }

    private static int createDistanceJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        int jointEid = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_DISTANCE, bodyA, bodyB);
        PhysicsDistanceJointComponent dist = world.getMapper(PhysicsDistanceJointComponent.class).create(jointEid);
        dist.lengthM = 3.25f;
        dist.frequencyHz = 2.5f;
        dist.dampingRatio = 0.35f;
        return jointEid;
    }

    private static int createWheelJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        int jointEid = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB);
        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).create(jointEid);
        wheel.axisX = 0.6f;
        wheel.axisY = 0.8f;
        wheel.frequencyHz = 4.2f;
        wheel.dampingRatio = 0.55f;
        wheel.enableMotor = true;
        wheel.motorSpeedRad = 9.5f;
        wheel.maxMotorTorque = 12.5f;
        return jointEid;
    }
    private static int createBody(World world, HistoryIdRegistry historyIds, float x, float y) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);
        TransformComponent transform = world.getMapper(TransformComponent.class).create(eid);
        transform.x = x;
        transform.y = y;
        return eid;
    }

    private static int createBaseJoint(World world, HistoryIdRegistry historyIds, int type, int bodyA, int bodyB) {
        int jointEid = world.create();
        historyIds.ensureForEntity(jointEid);
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(jointEid);
        base.type = type;
        base.aEid = bodyA;
        base.bEid = bodyB;
        base.anchorAx = 0.1f;
        base.anchorAy = 0.2f;
        base.anchorBx = 0.3f;
        base.anchorBy = 0.4f;
        return jointEid;
    }

    private static void assertOnlyExpectedJointSpecificComponent(World world, int eid, int expectedType) {
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_DISTANCE, world.getMapper(PhysicsDistanceJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_REVOLUTE, world.getMapper(PhysicsRevoluteJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_PRISMATIC, world.getMapper(PhysicsPrismaticJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_WHEEL, world.getMapper(PhysicsWheelJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_FRICTION, world.getMapper(PhysicsFrictionJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_MOTOR, world.getMapper(PhysicsMotorJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_WELD, world.getMapper(PhysicsWeldJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_PULLEY, world.getMapper(PhysicsPulleyJointComponent.class).has(eid));
        Assert.assertEquals(expectedType == PhysicsJointComponent.TYPE_GEAR, world.getMapper(PhysicsGearJointComponent.class).has(eid));
    }


    private static int createRevoluteJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) { int e = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_REVOLUTE, bodyA, bodyB); PhysicsRevoluteJointComponent c = world.getMapper(PhysicsRevoluteJointComponent.class).create(e); c.enableMotor = true; c.motorSpeedRad = 3.2f; c.maxMotorTorque = 9f; return e; }
    private static int createPrismaticJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) { int e = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_PRISMATIC, bodyA, bodyB); PhysicsPrismaticJointComponent c = world.getMapper(PhysicsPrismaticJointComponent.class).create(e); c.axisX = 0.6f; c.axisY = 0.8f; c.enableMotor = true; c.maxMotorForce = 7f; return e; }
    private static int createFrictionJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) { int e = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_FRICTION, bodyA, bodyB); PhysicsFrictionJointComponent c = world.getMapper(PhysicsFrictionJointComponent.class).create(e); c.maxForce = 4f; c.maxTorque = 5f; return e; }
    private static int createWeldJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) { int e = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_WELD, bodyA, bodyB); PhysicsWeldJointComponent c = world.getMapper(PhysicsWeldJointComponent.class).create(e); c.referenceAngleRad = 0.3f; c.frequencyHz = 6f; c.dampingRatio = 0.4f; return e; }

    private static int createMotorJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        int jointEid = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_MOTOR, bodyA, bodyB);
        PhysicsMotorJointComponent motor = world.getMapper(PhysicsMotorJointComponent.class).create(jointEid);
        motor.linearOffsetX = 1.25f;
        motor.linearOffsetY = -0.75f;
        motor.angularOffsetRad = 0.42f;
        motor.maxForce = 8f;
        motor.maxTorque = 6f;
        motor.correctionFactor = 0.65f;
        return jointEid;
    }

    private static int createPulleyJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        int jointEid = createBaseJoint(world, historyIds, PhysicsJointComponent.TYPE_PULLEY, bodyA, bodyB);
        PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class).create(jointEid);
        pulley.groundAx = 2f;
        pulley.groundAy = 30f;
        pulley.groundBx = 14f;
        pulley.groundBy = 30f;
        pulley.lengthAM = 6.5f;
        pulley.lengthBM = 8.5f;
        pulley.ratio = 1.6f;
        return jointEid;
    }
}
