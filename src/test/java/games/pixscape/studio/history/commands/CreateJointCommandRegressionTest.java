package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Consumer;

public class CreateJointCommandRegressionTest {

    @Test
    public void createDistanceJointCommandCreatesExpectedComponentsAndValues() {
        assertJointCreated(
                PhysicsJointComponent.TYPE_DISTANCE,
                d -> {
                    Assert.assertTrue(d.lengthM > 0f);
                    Assert.assertTrue(d.frequencyHz >= 0f);
                    Assert.assertTrue(d.dampingRatio >= 0f && d.dampingRatio <= 1f);
                },
                PhysicsDistanceJointComponent.class
        );
    }
    @Test public void createRevoluteJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_REVOLUTE, r -> {}, PhysicsRevoluteJointComponent.class); }
    @Test public void createPrismaticJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_PRISMATIC, p -> Assert.assertTrue(Math.abs(p.axisX) + Math.abs(p.axisY) > 0f), PhysicsPrismaticJointComponent.class); }
    @Test public void createWheelJointCommandPreservesWheelSpecificFields() { assertJointCreated(PhysicsJointComponent.TYPE_WHEEL, wheel -> { Assert.assertEquals(0f, wheel.axisX, 0f); Assert.assertEquals(1f, wheel.axisY, 0f); Assert.assertEquals(4f, wheel.frequencyHz, 0f); Assert.assertEquals(0.7f, wheel.dampingRatio, 0f); Assert.assertFalse(wheel.enableMotor); Assert.assertEquals(0f, wheel.motorSpeedRad, 0f); Assert.assertEquals(0f, wheel.maxMotorTorque, 0f);}, PhysicsWheelJointComponent.class); }
    @Test public void createFrictionJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_FRICTION, f -> { Assert.assertTrue(f.maxForce >= 0f); Assert.assertTrue(f.maxTorque >= 0f);}, PhysicsFrictionJointComponent.class); }
    @Test public void createMotorJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_MOTOR, m -> { Assert.assertTrue(m.maxForce >= 0f); Assert.assertTrue(m.maxTorque >= 0f); Assert.assertTrue(m.correctionFactor >= 0f && m.correctionFactor <= 1f);}, PhysicsMotorJointComponent.class); }
    @Test public void createWeldJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_WELD, w -> { Assert.assertTrue(w.frequencyHz >= 0f); Assert.assertTrue(w.dampingRatio >= 0f && w.dampingRatio <= 1f);}, PhysicsWeldJointComponent.class); }
    @Test public void createPulleyJointCommandCreatesExpectedComponentsAndValues() { assertJointCreated(PhysicsJointComponent.TYPE_PULLEY, p -> { Assert.assertTrue(p.ratio > 0f); Assert.assertTrue(p.lengthAM > 0f); Assert.assertTrue(p.lengthBM > 0f);}, PhysicsPulleyJointComponent.class); }
    @Test
    public void createWheelJointCommandPreservesSelectedBodyOrder() {
        World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 8f, 0f);
            CreateJointCommand command = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB, 4f, 0f);
            command.redo();
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(command.getCreatedJointEntityId());
            Assert.assertEquals(bodyA, base.aEid);
            Assert.assertEquals(bodyB, base.bEid);
        } finally {
            box2d.dispose();
        }
    }

    @Test
    public void createWheelJointCommandCreatesFiniteReasonableAnchors() {
        World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 10f, 0f);
            CreateJointCommand command = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB, 5f, 0f);
            command.redo();
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(command.getCreatedJointEntityId());
            assertFiniteAndReasonable(base.anchorAx);
            assertFiniteAndReasonable(base.anchorAy);
            assertFiniteAndReasonable(base.anchorBx);
            assertFiniteAndReasonable(base.anchorBy);
        } finally {
            box2d.dispose();
        }
    }

    @Test
    public void createTwoWheelJointsForCarHaveDistinctWheelEndpoints() {
        World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int car = createBody(world, historyIds, 0f, 1f);
            int wheelLeft = createBody(world, historyIds, -2f, 0f);
            int wheelRight = createBody(world, historyIds, 2f, 0f);
            CreateJointCommand left = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, car, wheelLeft, -2f, 0f);
            CreateJointCommand right = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, car, wheelRight, 2f, 0f);
            left.redo();
            right.redo();

            PhysicsJointComponent leftBase = world.getMapper(PhysicsJointComponent.class).get(left.getCreatedJointEntityId());
            PhysicsJointComponent rightBase = world.getMapper(PhysicsJointComponent.class).get(right.getCreatedJointEntityId());
            Assert.assertEquals(car, leftBase.aEid);
            Assert.assertEquals(car, rightBase.aEid);
            Assert.assertEquals(wheelLeft, leftBase.bEid);
            Assert.assertEquals(wheelRight, rightBase.bEid);
            Assert.assertNotEquals(leftBase.bEid, rightBase.bEid);
        } finally {
            box2d.dispose();
        }
    }
    @Test public void wheelJointCreatedByRealPathHasNoEntityIndexAndNoTransform() {
        World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int a = createBody(world, historyIds, 0f, 0f);
            int b = createBody(world, historyIds, 4f, 2f);
            CreateJointCommand command = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, a, b, 1f, 1f);
            command.redo();

            int jointEid = command.getCreatedJointEntityId();
            Assert.assertTrue(world.getMapper(PhysicsJointComponent.class).has(jointEid));
            Assert.assertTrue(world.getMapper(PhysicsWheelJointComponent.class).has(jointEid));
            Assert.assertFalse(world.getMapper(EntityIndexComponent.class).has(jointEid));
            Assert.assertFalse(world.getMapper(TransformComponent.class).has(jointEid));
            Assert.assertFalse(world.getMapper(PixscapeIdentityComponent.class).has(jointEid));
        } finally {
            box2d.dispose();
        }
    }

    private <T extends com.artemis.Component> void assertJointCreated(int type, Consumer<T> typeAssert, Class<T> specificType) {
        World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int a = createBody(world, historyIds, 0f, 0f);
            int b = createBody(world, historyIds, 4f, 2f);
            CreateJointCommand command = new CreateJointCommand(world, physicsService, historyIds, type, a, b, 1f, 1f);
            command.redo();

            int jointEid = command.getCreatedJointEntityId();
            Assert.assertTrue(world.getEntityManager().isActive(jointEid));
            Assert.assertTrue(world.getMapper(PhysicsJointComponent.class).has(jointEid));
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(jointEid);
            Assert.assertEquals(type, base.type);
            Assert.assertEquals(a, base.aEid);
            Assert.assertEquals(b, base.bEid);
            T comp = world.getMapper(specificType).get(jointEid);
            Assert.assertNotNull(comp);
            typeAssert.accept(comp);

            assertOnlyExpectedJointSpecificComponent(world, jointEid, type);
        } finally {
            box2d.dispose();
        }
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
    }

    private static int createBody(World world, HistoryIdRegistry historyIds, float x, float y) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);
        TransformComponent t = world.getMapper(TransformComponent.class).create(eid);
        t.x = x;
        t.y = y;
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(eid);
        PhysicsService.initDefaultBody(body);
        PhysicsFixturesComponent component = world.getMapper(PhysicsFixturesComponent.class).create(eid);
        component.fixtures.add(games.pixscape.studio.FixtureIdentityTestSupport.createFixture(world));
        return eid;
    }

    private static void assertFiniteAndReasonable(float value) {
        Assert.assertTrue(Float.isFinite(value));
        Assert.assertTrue(Math.abs(value) < 1_000_000f);
    }
}
