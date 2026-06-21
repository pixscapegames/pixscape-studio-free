package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditJointCommandsTest {

    @Test
    public void distanceJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createDistanceJoint(world, historyIds);

        PhysicsDistanceJointComponent joint = world.getMapper(PhysicsDistanceJointComponent.class).get(ctx.jointEid);
        EditDistanceJointCommand.Snapshot before = EditDistanceJointCommand.Snapshot.capture(joint);
        EditDistanceJointCommand.Snapshot after = before.withLengthM(4.25f).withFrequencyHz(7f).withDampingRatio(0.3f);

        EditDistanceJointCommand command = new EditDistanceJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertDistance(joint, 4.25f, 7f, 0.3f);

        command.undo();
        assertDistance(joint, 1.5f, 2f, 0.1f);

        command.redo();
        assertDistance(joint, 4.25f, 7f, 0.3f);
    }

    @Test
    public void revoluteJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createRevoluteJoint(world, historyIds);

        PhysicsRevoluteJointComponent joint = world.getMapper(PhysicsRevoluteJointComponent.class).get(ctx.jointEid);
        EditRevoluteJointCommand.Snapshot before = EditRevoluteJointCommand.Snapshot.capture(joint);
        EditRevoluteJointCommand.Snapshot after = before
                .withEnableLimit(true)
                .withLowerAngleRad(-0.4f)
                .withUpperAngleRad(0.6f)
                .withEnableMotor(true)
                .withMotorSpeedRad(3.2f)
                .withMaxMotorTorque(9f);

        EditRevoluteJointCommand command = new EditRevoluteJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertRevolute(joint, true, -0.4f, 0.6f, true, 3.2f, 9f);

        command.undo();
        assertRevolute(joint, false, -0.1f, 0.2f, false, 0.5f, 2f);

        command.redo();
        assertRevolute(joint, true, -0.4f, 0.6f, true, 3.2f, 9f);
    }

    @Test
    public void prismaticJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createPrismaticJoint(world, historyIds);

        PhysicsPrismaticJointComponent joint = world.getMapper(PhysicsPrismaticJointComponent.class).get(ctx.jointEid);
        EditPrismaticJointCommand.Snapshot before = EditPrismaticJointCommand.Snapshot.capture(joint);
        EditPrismaticJointCommand.Snapshot after = before
                .withAxis(3f, 4f)
                .withEnableLimit(true)
                .withLowerTranslationM(-1f)
                .withUpperTranslationM(2f)
                .withEnableMotor(true)
                .withMotorSpeedMps(6f)
                .withMaxMotorForce(8f);

        EditPrismaticJointCommand command = new EditPrismaticJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertPrismatic(joint, 0.6f, 0.8f, true, -1f, 2f, true, 6f, 8f);

        command.undo();
        assertPrismatic(joint, 1f, 0f, false, -0.2f, 0.2f, false, 0.7f, 3f);

        command.redo();
        assertPrismatic(joint, 0.6f, 0.8f, true, -1f, 2f, true, 6f, 8f);
    }

    @Test
    public void wheelJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createWheelJoint(world, historyIds);

        PhysicsWheelJointComponent joint = world.getMapper(PhysicsWheelJointComponent.class).get(ctx.jointEid);
        EditWheelJointCommand.Snapshot before = EditWheelJointCommand.Snapshot.capture(joint);
        EditWheelJointCommand.Snapshot after = before
                .withAxisX(0.3f)
                .withAxisY(0.7f)
                .withFrequencyHz(5f)
                .withDampingRatio(0.6f)
                .withEnableMotor(true)
                .withMotorSpeedRad(4f)
                .withMaxMotorTorque(11f);

        EditWheelJointCommand command = new EditWheelJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertWheel(joint, 0.3f, 0.7f, 5f, 0.6f, true, 4f, 11f);

        command.undo();
        assertWheel(joint, 1f, 0f, 2f, 0.2f, false, 0.9f, 1.5f);

        command.redo();
        assertWheel(joint, 0.3f, 0.7f, 5f, 0.6f, true, 4f, 11f);
    }

    @Test
    public void frictionJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createFrictionJoint(world, historyIds);

        PhysicsFrictionJointComponent joint = world.getMapper(PhysicsFrictionJointComponent.class).get(ctx.jointEid);
        EditFrictionJointCommand.Snapshot before = EditFrictionJointCommand.Snapshot.capture(joint);
        EditFrictionJointCommand.Snapshot after = before.withMaxForce(8f).withMaxTorque(5f);

        EditFrictionJointCommand command = new EditFrictionJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertFriction(joint, 8f, 5f);

        command.undo();
        assertFriction(joint, 1.3f, 0.7f);

        command.redo();
        assertFriction(joint, 8f, 5f);
    }

    @Test
    public void motorJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createMotorJoint(world, historyIds);

        PhysicsMotorJointComponent joint = world.getMapper(PhysicsMotorJointComponent.class).get(ctx.jointEid);
        EditMotorJointCommand.Snapshot before = EditMotorJointCommand.Snapshot.capture(joint);
        EditMotorJointCommand.Snapshot after = before
                .withLinearOffsetX(4f)
                .withLinearOffsetY(-2f)
                .withAngularOffsetRad(0.8f)
                .withMaxForce(20f)
                .withMaxTorque(12f)
                .withCorrectionFactor(0.35f);

        EditMotorJointCommand command = new EditMotorJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertMotor(joint, 4f, -2f, 0.8f, 20f, 12f, 0.35f);

        command.undo();
        assertMotor(joint, 0.4f, 0.2f, 0.1f, 2f, 3f, 0.9f);

        command.redo();
        assertMotor(joint, 4f, -2f, 0.8f, 20f, 12f, 0.35f);
    }

    @Test
    public void weldJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createWeldJoint(world, historyIds);

        PhysicsWeldJointComponent joint = world.getMapper(PhysicsWeldJointComponent.class).get(ctx.jointEid);
        EditWeldJointCommand.Snapshot before = EditWeldJointCommand.Snapshot.capture(joint);
        EditWeldJointCommand.Snapshot after = before
                .withReferenceAngleRad(0.6f)
                .withFrequencyHz(7f)
                .withDampingRatio(0.4f);

        EditWeldJointCommand command = new EditWeldJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertWeld(joint, 0.6f, 7f, 0.4f);

        command.undo();
        assertWeld(joint, 0.2f, 2f, 0.1f);

        command.redo();
        assertWeld(joint, 0.6f, 7f, 0.4f);
    }

    @Test
    public void pulleyJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createPulleyJoint(world, historyIds);

        PhysicsPulleyJointComponent joint = world.getMapper(PhysicsPulleyJointComponent.class).get(ctx.jointEid);
        EditPulleyJointCommand.Snapshot before = EditPulleyJointCommand.Snapshot.capture(joint);
        EditPulleyJointCommand.Snapshot after = new EditPulleyJointCommand.Snapshot(
                6f, 7f, 8f, 9f, 10f, 11f, 1.75f
        );

        EditPulleyJointCommand command = new EditPulleyJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertPulley(joint, 6f, 7f, 8f, 9f, 10f, 11f, 1.75f);

        command.undo();
        assertPulley(joint, 1f, 2f, 3f, 4f, 2.5f, 3.5f, 1.2f);

        command.redo();
        assertPulley(joint, 6f, 7f, 8f, 9f, 10f, 11f, 1.75f);
    }

    @Test
    public void gearJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createGearJoint(world, historyIds);

        PhysicsGearJointComponent joint = world.getMapper(PhysicsGearJointComponent.class).get(ctx.jointEid);
        EditGearJointCommand.Snapshot before = EditGearJointCommand.Snapshot.capture(joint);
        EditGearJointCommand.Snapshot after = new EditGearJointCommand.Snapshot(ctx.jointRefBEid, ctx.jointRefAEid, 2.5f);

        EditGearJointCommand command = new EditGearJointCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertGear(joint, ctx.jointRefBEid, ctx.jointRefAEid, 2.5f);

        command.undo();
        assertGear(joint, ctx.jointRefAEid, ctx.jointRefBEid, 0.5f);

        command.redo();
        assertGear(joint, ctx.jointRefBEid, ctx.jointRefAEid, 2.5f);
    }

    @Test
    public void baseJointEditUndoRedoRestoresExactValuesAndBodyLinks() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createDistanceJoint(world, historyIds);

        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).get(ctx.jointEid);
        EditJointBaseCommand.Snapshot before = EditJointBaseCommand.Snapshot.capture(joint);
        EditJointBaseCommand.Snapshot after = before
                .withCollideConnected(true)
                .withAnchorAx(2f)
                .withAnchorAy(3f)
                .withAnchorBx(4f)
                .withAnchorBy(5f);

        EditJointBaseCommand command = new EditJointBaseCommand(world, historyIds, ctx.jointEid, before, after);
        command.redo();
        assertBaseJoint(joint, true, 2f, 3f, 4f, 5f, ctx.bodyAEid, ctx.bodyBEid);

        command.undo();
        assertBaseJoint(joint, false, 0.1f, 0.2f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);

        command.redo();
        assertBaseJoint(joint, true, 2f, 3f, 4f, 5f, ctx.bodyAEid, ctx.bodyBEid);
    }

    @Test
    public void noopEditDoesNotCreateMeaningfulHistoryMutation() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        JointContext ctx = createRevoluteJoint(world, historyIds);

        PhysicsRevoluteJointComponent joint = world.getMapper(PhysicsRevoluteJointComponent.class).get(ctx.jointEid);
        EditRevoluteJointCommand.Snapshot before = EditRevoluteJointCommand.Snapshot.capture(joint);
        EditRevoluteJointCommand.Snapshot after = EditRevoluteJointCommand.Snapshot.capture(joint);

        EditRevoluteJointCommand command = new EditRevoluteJointCommand(world, historyIds, ctx.jointEid, before, after);
        Assert.assertTrue(command.isNoop());

        HistoryManager history = new HistoryManager(32);
        executeIfMeaningful(history, command);

        Assert.assertFalse(history.canUndo());
        Assert.assertEquals(0, history.getCursor());
    }

    @Test
    public void mixedChainCreateBodiesCreateJointEditUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(64);

        JointContext ctx = createWheelJoint(world, historyIds);

        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(ctx.jointEid);
        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).get(ctx.jointEid);

        EditJointBaseCommand baseEdit = new EditJointBaseCommand(
                world,
                historyIds,
                ctx.jointEid,
                EditJointBaseCommand.Snapshot.capture(base),
                EditJointBaseCommand.Snapshot.capture(base).withCollideConnected(true).withAnchorAx(8f)
        );
        history.execute(baseEdit);

        EditWheelJointCommand wheelEdit = new EditWheelJointCommand(
                world,
                historyIds,
                ctx.jointEid,
                EditWheelJointCommand.Snapshot.capture(wheel),
                EditWheelJointCommand.Snapshot.capture(wheel)
                        .withEnableMotor(true)
                        .withMotorSpeedRad(12f)
                        .withMaxMotorTorque(13f)
        );
        history.execute(wheelEdit);

        assertBaseJoint(base, true, 8f, 0.2f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);
        assertWheel(wheel, 1f, 0f, 2f, 0.2f, true, 12f, 13f);

        history.undo();
        assertWheel(wheel, 1f, 0f, 2f, 0.2f, false, 0.9f, 1.5f);

        history.undo();
        assertBaseJoint(base, false, 0.1f, 0.2f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);

        history.redo();
        assertBaseJoint(base, true, 8f, 0.2f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);

        history.redo();
        assertWheel(wheel, 1f, 0f, 2f, 0.2f, true, 12f, 13f);
    }

    @Test
    public void newJointEditNoopsAreDetectedWhenBeforeEqualsAfter() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        JointContext frictionCtx = createFrictionJoint(world, historyIds);
        PhysicsFrictionJointComponent friction = world.getMapper(PhysicsFrictionJointComponent.class).get(frictionCtx.jointEid);
        Assert.assertTrue(new EditFrictionJointCommand(
                world,
                historyIds,
                frictionCtx.jointEid,
                EditFrictionJointCommand.Snapshot.capture(friction),
                EditFrictionJointCommand.Snapshot.capture(friction)
        ).isNoop());

        JointContext motorCtx = createMotorJoint(world, historyIds);
        PhysicsMotorJointComponent motor = world.getMapper(PhysicsMotorJointComponent.class).get(motorCtx.jointEid);
        Assert.assertTrue(new EditMotorJointCommand(
                world,
                historyIds,
                motorCtx.jointEid,
                EditMotorJointCommand.Snapshot.capture(motor),
                EditMotorJointCommand.Snapshot.capture(motor)
        ).isNoop());

        JointContext weldCtx = createWeldJoint(world, historyIds);
        PhysicsWeldJointComponent weld = world.getMapper(PhysicsWeldJointComponent.class).get(weldCtx.jointEid);
        Assert.assertTrue(new EditWeldJointCommand(
                world,
                historyIds,
                weldCtx.jointEid,
                EditWeldJointCommand.Snapshot.capture(weld),
                EditWeldJointCommand.Snapshot.capture(weld)
        ).isNoop());

        JointContext pulleyCtx = createPulleyJoint(world, historyIds);
        PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class).get(pulleyCtx.jointEid);
        Assert.assertTrue(new EditPulleyJointCommand(
                world,
                historyIds,
                pulleyCtx.jointEid,
                EditPulleyJointCommand.Snapshot.capture(pulley),
                EditPulleyJointCommand.Snapshot.capture(pulley)
        ).isNoop());

        JointContext gearCtx = createGearJoint(world, historyIds);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(gearCtx.jointEid);
        Assert.assertTrue(new EditGearJointCommand(
                world,
                historyIds,
                gearCtx.jointEid,
                EditGearJointCommand.Snapshot.capture(gear),
                EditGearJointCommand.Snapshot.capture(gear)
        ).isNoop());
    }

    @Test
    public void mixedChainBaseAndFrictionEditsUndoRedoThroughHistoryManager() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(64);
        JointContext ctx = createFrictionJoint(world, historyIds);

        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(ctx.jointEid);
        PhysicsFrictionJointComponent friction = world.getMapper(PhysicsFrictionJointComponent.class).get(ctx.jointEid);

        history.execute(new EditJointBaseCommand(
                world,
                historyIds,
                ctx.jointEid,
                EditJointBaseCommand.Snapshot.capture(base),
                EditJointBaseCommand.Snapshot.capture(base).withAnchorAx(5f).withAnchorAy(6f).withCollideConnected(true)
        ));

        history.execute(new EditFrictionJointCommand(
                world,
                historyIds,
                ctx.jointEid,
                EditFrictionJointCommand.Snapshot.capture(friction),
                EditFrictionJointCommand.Snapshot.capture(friction).withMaxForce(9f).withMaxTorque(10f)
        ));

        assertBaseJoint(base, true, 5f, 6f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);
        assertFriction(friction, 9f, 10f);

        history.undo();
        assertFriction(friction, 1.3f, 0.7f);

        history.undo();
        assertBaseJoint(base, false, 0.1f, 0.2f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);

        history.redo();
        history.redo();
        assertBaseJoint(base, true, 5f, 6f, 0.3f, 0.4f, ctx.bodyAEid, ctx.bodyBEid);
        assertFriction(friction, 9f, 10f);
    }

    private static void executeIfMeaningful(HistoryManager history, Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) return;
        history.execute(command);
    }

    private static JointContext createDistanceJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_DISTANCE);
        PhysicsDistanceJointComponent dist = world.getMapper(PhysicsDistanceJointComponent.class).create(ctx.jointEid);
        dist.lengthM = 1.5f;
        dist.frequencyHz = 2f;
        dist.dampingRatio = 0.1f;
        return ctx;
    }

    private static JointContext createRevoluteJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_REVOLUTE);
        PhysicsRevoluteJointComponent rev = world.getMapper(PhysicsRevoluteJointComponent.class).create(ctx.jointEid);
        rev.enableLimit = false;
        rev.lowerAngleRad = -0.1f;
        rev.upperAngleRad = 0.2f;
        rev.enableMotor = false;
        rev.motorSpeedRad = 0.5f;
        rev.maxMotorTorque = 2f;
        return ctx;
    }

    private static JointContext createPrismaticJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_PRISMATIC);
        PhysicsPrismaticJointComponent prism = world.getMapper(PhysicsPrismaticJointComponent.class).create(ctx.jointEid);
        prism.axisX = 1f;
        prism.axisY = 0f;
        prism.enableLimit = false;
        prism.lowerTranslationM = -0.2f;
        prism.upperTranslationM = 0.2f;
        prism.enableMotor = false;
        prism.motorSpeedMps = 0.7f;
        prism.maxMotorForce = 3f;
        return ctx;
    }

    private static JointContext createWheelJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_WHEEL);
        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).create(ctx.jointEid);
        wheel.axisX = 1f;
        wheel.axisY = 0f;
        wheel.frequencyHz = 2f;
        wheel.dampingRatio = 0.2f;
        wheel.enableMotor = false;
        wheel.motorSpeedRad = 0.9f;
        wheel.maxMotorTorque = 1.5f;
        return ctx;
    }

    private static JointContext createFrictionJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_FRICTION);
        PhysicsFrictionJointComponent friction = world.getMapper(PhysicsFrictionJointComponent.class).create(ctx.jointEid);
        friction.maxForce = 1.3f;
        friction.maxTorque = 0.7f;
        return ctx;
    }

    private static JointContext createMotorJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_MOTOR);
        PhysicsMotorJointComponent motor = world.getMapper(PhysicsMotorJointComponent.class).create(ctx.jointEid);
        motor.linearOffsetX = 0.4f;
        motor.linearOffsetY = 0.2f;
        motor.angularOffsetRad = 0.1f;
        motor.maxForce = 2f;
        motor.maxTorque = 3f;
        motor.correctionFactor = 0.9f;
        return ctx;
    }

    private static JointContext createWeldJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_WELD);
        PhysicsWeldJointComponent weld = world.getMapper(PhysicsWeldJointComponent.class).create(ctx.jointEid);
        weld.referenceAngleRad = 0.2f;
        weld.frequencyHz = 2f;
        weld.dampingRatio = 0.1f;
        return ctx;
    }

    private static JointContext createPulleyJoint(World world, HistoryIdRegistry historyIds) {
        JointContext ctx = createJointBase(world, historyIds, PhysicsJointComponent.TYPE_PULLEY);
        PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class).create(ctx.jointEid);
        pulley.groundAx = 1f;
        pulley.groundAy = 2f;
        pulley.groundBx = 3f;
        pulley.groundBy = 4f;
        pulley.lengthAM = 2.5f;
        pulley.lengthBM = 3.5f;
        pulley.ratio = 1.2f;
        return ctx;
    }

    private static JointContext createGearJoint(World world, HistoryIdRegistry historyIds) {
        int bodyA = world.create();
        int bodyB = world.create();
        int bodyC = world.create();
        historyIds.ensureForEntity(bodyA);
        historyIds.ensureForEntity(bodyB);
        historyIds.ensureForEntity(bodyC);
        world.getMapper(TransformComponent.class).create(bodyA);
        world.getMapper(TransformComponent.class).create(bodyB);
        world.getMapper(TransformComponent.class).create(bodyC);

        int jointRefA = world.create();
        historyIds.ensureForEntity(jointRefA);
        PhysicsJointComponent refA = world.getMapper(PhysicsJointComponent.class).create(jointRefA);
        refA.type = PhysicsJointComponent.TYPE_REVOLUTE;
        refA.aEid = bodyA;
        refA.bEid = bodyB;

        int jointRefB = world.create();
        historyIds.ensureForEntity(jointRefB);
        PhysicsJointComponent refB = world.getMapper(PhysicsJointComponent.class).create(jointRefB);
        refB.type = PhysicsJointComponent.TYPE_PRISMATIC;
        refB.aEid = bodyB;
        refB.bEid = bodyC;

        int jointEid = world.create();
        historyIds.ensureForEntity(jointEid);
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(jointEid);
        base.type = PhysicsJointComponent.TYPE_GEAR;
        base.aEid = bodyA;
        base.bEid = bodyC;

        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(jointEid);
        gear.joint1Eid = jointRefA;
        gear.joint2Eid = jointRefB;
        gear.ratio = 0.5f;

        return new JointContext(jointEid, bodyA, bodyC, jointRefA, jointRefB);
    }

    private static JointContext createJointBase(World world, HistoryIdRegistry historyIds, int type) {
        int bodyA = world.create();
        int bodyB = world.create();
        historyIds.ensureForEntity(bodyA);
        historyIds.ensureForEntity(bodyB);
        world.getMapper(TransformComponent.class).create(bodyA);
        world.getMapper(TransformComponent.class).create(bodyB);

        int jointEid = world.create();
        historyIds.ensureForEntity(jointEid);
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEid);
        joint.type = type;
        joint.aEid = bodyA;
        joint.bEid = bodyB;
        joint.collideConnected = false;
        joint.anchorAx = 0.1f;
        joint.anchorAy = 0.2f;
        joint.anchorBx = 0.3f;
        joint.anchorBy = 0.4f;

        return new JointContext(jointEid, bodyA, bodyB);
    }

    private static void assertDistance(PhysicsDistanceJointComponent dist,
                                       float lengthM,
                                       float frequencyHz,
                                       float dampingRatio) {
        Assert.assertEquals(lengthM, dist.lengthM, 0f);
        Assert.assertEquals(frequencyHz, dist.frequencyHz, 0f);
        Assert.assertEquals(dampingRatio, dist.dampingRatio, 0f);
    }

    private static void assertRevolute(PhysicsRevoluteJointComponent rev,
                                       boolean enableLimit,
                                       float lowerAngle,
                                       float upperAngle,
                                       boolean enableMotor,
                                       float motorSpeed,
                                       float maxTorque) {
        Assert.assertEquals(enableLimit, rev.enableLimit);
        Assert.assertEquals(lowerAngle, rev.lowerAngleRad, 0f);
        Assert.assertEquals(upperAngle, rev.upperAngleRad, 0f);
        Assert.assertEquals(enableMotor, rev.enableMotor);
        Assert.assertEquals(motorSpeed, rev.motorSpeedRad, 0f);
        Assert.assertEquals(maxTorque, rev.maxMotorTorque, 0f);
    }

    private static void assertPrismatic(PhysicsPrismaticJointComponent prism,
                                        float axisX,
                                        float axisY,
                                        boolean enableLimit,
                                        float lowerTranslation,
                                        float upperTranslation,
                                        boolean enableMotor,
                                        float motorSpeed,
                                        float maxMotorForce) {
        Assert.assertEquals(axisX, prism.axisX, 0f);
        Assert.assertEquals(axisY, prism.axisY, 0f);
        Assert.assertEquals(enableLimit, prism.enableLimit);
        Assert.assertEquals(lowerTranslation, prism.lowerTranslationM, 0f);
        Assert.assertEquals(upperTranslation, prism.upperTranslationM, 0f);
        Assert.assertEquals(enableMotor, prism.enableMotor);
        Assert.assertEquals(motorSpeed, prism.motorSpeedMps, 0f);
        Assert.assertEquals(maxMotorForce, prism.maxMotorForce, 0f);
    }

    private static void assertWheel(PhysicsWheelJointComponent wheel,
                                    float axisX,
                                    float axisY,
                                    float frequency,
                                    float damping,
                                    boolean enableMotor,
                                    float motorSpeed,
                                    float maxTorque) {
        Assert.assertEquals(axisX, wheel.axisX, 0f);
        Assert.assertEquals(axisY, wheel.axisY, 0f);
        Assert.assertEquals(frequency, wheel.frequencyHz, 0f);
        Assert.assertEquals(damping, wheel.dampingRatio, 0f);
        Assert.assertEquals(enableMotor, wheel.enableMotor);
        Assert.assertEquals(motorSpeed, wheel.motorSpeedRad, 0f);
        Assert.assertEquals(maxTorque, wheel.maxMotorTorque, 0f);
    }

    private static void assertFriction(PhysicsFrictionJointComponent friction,
                                       float maxForce,
                                       float maxTorque) {
        Assert.assertEquals(maxForce, friction.maxForce, 0f);
        Assert.assertEquals(maxTorque, friction.maxTorque, 0f);
    }

    private static void assertMotor(PhysicsMotorJointComponent motor,
                                    float linearOffsetX,
                                    float linearOffsetY,
                                    float angularOffsetRad,
                                    float maxForce,
                                    float maxTorque,
                                    float correctionFactor) {
        Assert.assertEquals(linearOffsetX, motor.linearOffsetX, 0f);
        Assert.assertEquals(linearOffsetY, motor.linearOffsetY, 0f);
        Assert.assertEquals(angularOffsetRad, motor.angularOffsetRad, 0f);
        Assert.assertEquals(maxForce, motor.maxForce, 0f);
        Assert.assertEquals(maxTorque, motor.maxTorque, 0f);
        Assert.assertEquals(correctionFactor, motor.correctionFactor, 0f);
    }

    private static void assertWeld(PhysicsWeldJointComponent weld,
                                   float referenceAngleRad,
                                   float frequencyHz,
                                   float dampingRatio) {
        Assert.assertEquals(referenceAngleRad, weld.referenceAngleRad, 0f);
        Assert.assertEquals(frequencyHz, weld.frequencyHz, 0f);
        Assert.assertEquals(dampingRatio, weld.dampingRatio, 0f);
    }

    private static void assertPulley(PhysicsPulleyJointComponent pulley,
                                     float groundAx,
                                     float groundAy,
                                     float groundBx,
                                     float groundBy,
                                     float lengthAM,
                                     float lengthBM,
                                     float ratio) {
        Assert.assertEquals(groundAx, pulley.groundAx, 0f);
        Assert.assertEquals(groundAy, pulley.groundAy, 0f);
        Assert.assertEquals(groundBx, pulley.groundBx, 0f);
        Assert.assertEquals(groundBy, pulley.groundBy, 0f);
        Assert.assertEquals(lengthAM, pulley.lengthAM, 0f);
        Assert.assertEquals(lengthBM, pulley.lengthBM, 0f);
        Assert.assertEquals(ratio, pulley.ratio, 0f);
    }

    private static void assertGear(PhysicsGearJointComponent gear,
                                   int joint1Eid,
                                   int joint2Eid,
                                   float ratio) {
        Assert.assertEquals(joint1Eid, gear.joint1Eid);
        Assert.assertEquals(joint2Eid, gear.joint2Eid);
        Assert.assertEquals(ratio, gear.ratio, 0f);
    }

    private static void assertBaseJoint(PhysicsJointComponent joint,
                                        boolean collideConnected,
                                        float anchorAx,
                                        float anchorAy,
                                        float anchorBx,
                                        float anchorBy,
                                        int expectedBodyA,
                                        int expectedBodyB) {
        Assert.assertEquals(collideConnected, joint.collideConnected);
        Assert.assertEquals(anchorAx, joint.anchorAx, 0f);
        Assert.assertEquals(anchorAy, joint.anchorAy, 0f);
        Assert.assertEquals(anchorBx, joint.anchorBx, 0f);
        Assert.assertEquals(anchorBy, joint.anchorBy, 0f);
        Assert.assertEquals(expectedBodyA, joint.aEid);
        Assert.assertEquals(expectedBodyB, joint.bEid);
    }

    private record JointContext(int jointEid, int bodyAEid, int bodyBEid, int jointRefAEid, int jointRefBEid) {
        private JointContext(int jointEid, int bodyAEid, int bodyBEid) {
            this(jointEid, bodyAEid, bodyBEid, -1, -1);
        }
    }
}
