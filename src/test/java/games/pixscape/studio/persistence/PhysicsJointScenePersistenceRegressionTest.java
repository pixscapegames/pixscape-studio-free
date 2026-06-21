package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.commands.CreateJointCommand;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class PhysicsJointScenePersistenceRegressionTest {

    @Test
    public void distanceJointSurvivesSceneSaveLoadRoundtrip() {
        roundtripJointTypes(
                "distance-joint-roundtrip",
                PhysicsJointComponent.TYPE_DISTANCE
        );
    }

    @Test
    public void wheelJointSurvivesSceneSaveLoadRoundtrip() {
        roundtripJointTypes(
                "wheel-joint-roundtrip",
                PhysicsJointComponent.TYPE_WHEEL
        );
    }


    @Test
    public void wheelJointSceneJsonContainsWheelSpecificComponent() {
        World world = worldWithSerialization();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));

        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 4f, 2f);
            int jointEid = createJoint(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB);
            setNonDefaultJointFields(world, jointEid, PhysicsJointComponent.TYPE_WHEEL);

            world.process();

            FileHandle file = tempSceneFile("wheel-json-contains-wheel-specific-component");
            SceneService.saveScene(world, file, false);

            String json = file.readString(String.valueOf(StandardCharsets.UTF_8));
            Assert.assertTrue("Saved JSON missing base joint component marker", json.contains("PhysicsJointComponent"));
            Assert.assertTrue("Saved JSON missing wheel joint component marker", json.contains("PhysicsWheelJointComponent"));
            Assert.assertTrue("Saved JSON missing axisX", json.contains("axisX"));
            Assert.assertTrue("Saved JSON missing axisY", json.contains("axisY"));
            Assert.assertTrue("Saved JSON missing frequencyHz", json.contains("frequencyHz"));
            Assert.assertTrue("Saved JSON missing dampingRatio", json.contains("dampingRatio"));
            Assert.assertTrue("Saved JSON missing enableMotor", json.contains("enableMotor"));
            Assert.assertTrue("Saved JSON missing motorSpeedRad", json.contains("motorSpeedRad"));
            Assert.assertTrue("Saved JSON missing maxMotorTorque", json.contains("maxMotorTorque"));
        } finally {
            box2d.dispose();
        }
    }

    @Test
    public void wheelJointSurvivesReloadIntoExistingWorldAfterClear() {
        World world = worldWithSerialization();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));

        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 4f, 2f);
            int jointEid = createJoint(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB);
            setNonDefaultJointFields(world, jointEid, PhysicsJointComponent.TYPE_WHEEL);

            world.process();

            FileHandle file = tempSceneFile("wheel-reload-into-existing-world-after-clear");
            SceneService.saveScene(world, file, false);

            clearAllEntities(world);
            SceneLoader.loadScene(world, file, false);
            world.process();

            assertWheelJointPresent(world);
        } finally {
            box2d.dispose();
        }
    }

    @Test
    public void distanceAndWheelJointSurviveSameSceneSaveLoadRoundtrip() {
        roundtripJointTypes(
                "distance-and-wheel-joint-roundtrip",
                PhysicsJointComponent.TYPE_DISTANCE,
                PhysicsJointComponent.TYPE_WHEEL
        );
    }

    private static void roundtripJointTypes(String sceneName, int... jointTypes) {
        World world = worldWithSerialization();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));

        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);

            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 4f, 2f);

            for (int type : jointTypes) {
                int jointEid = createJoint(world, physicsService, historyIds, type, bodyA, bodyB);
                setNonDefaultJointFields(world, jointEid, type);
            }

            world.process();

            FileHandle file = tempSceneFile(sceneName);
            SceneService.saveScene(world, file, false);

            World loaded = worldWithSerialization();
            SceneLoader.loadScene(loaded, file, false);
            loaded.process();

            for (int type : jointTypes) {
                int restoredJoint = assertRestoredJointWithExpectedSpecificComponent(loaded, type);

                PhysicsJointComponent base = loaded.getMapper(PhysicsJointComponent.class).get(restoredJoint);
                Assert.assertTrue(base.aEid >= 0);
                Assert.assertTrue(base.bEid >= 0);

                assertNonDefaultJointFieldsRestored(loaded, restoredJoint, type);
            }
        } finally {
            box2d.dispose();
        }
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }

    private static int createBody(
            World world,
            HistoryIdRegistry historyIds,
            float x,
            float y
    ) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(eid);
        transform.x = x;
        transform.y = y;

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(eid);
        PhysicsService.initDefaultBody(body);

        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(eid);
        fixtures.fixtures.add(PhysicsService.createDefaultFixture());

        return eid;
    }

    private static int createJoint(
            World world,
            PhysicsService physicsService,
            HistoryIdRegistry historyIds,
            int type,
            int bodyA,
            int bodyB
    ) {
        CreateJointCommand command = new CreateJointCommand(
                world,
                physicsService,
                historyIds,
                type,
                bodyA,
                bodyB,
                1f,
                1f
        );

        command.redo();
        return command.getCreatedJointEntityId();
    }

    private static void setNonDefaultJointFields(World world, int jointEid, int type) {
        if (type == PhysicsJointComponent.TYPE_DISTANCE) {
            PhysicsDistanceJointComponent distance =
                    world.getMapper(PhysicsDistanceJointComponent.class).get(jointEid);

            distance.lengthM = 3.25f;
            distance.frequencyHz = 2.5f;
            distance.dampingRatio = 0.3f;
            return;
        }

        if (type == PhysicsJointComponent.TYPE_WHEEL) {
            PhysicsWheelJointComponent wheel =
                    world.getMapper(PhysicsWheelJointComponent.class).get(jointEid);

            wheel.axisX = 0.6f;
            wheel.axisY = 0.8f;
            wheel.frequencyHz = 4.2f;
            wheel.dampingRatio = 0.55f;
            wheel.enableMotor = true;
            wheel.motorSpeedRad = 9.5f;
            wheel.maxMotorTorque = 12.5f;
            return;
        }

        throw new IllegalArgumentException("Unsupported test joint type: " + type);
    }

    private static void assertNonDefaultJointFieldsRestored(World world, int jointEid, int type) {
        if (type == PhysicsJointComponent.TYPE_DISTANCE) {
            PhysicsDistanceJointComponent distance =
                    world.getMapper(PhysicsDistanceJointComponent.class).get(jointEid);

            Assert.assertEquals(3.25f, distance.lengthM, 0.0001f);
            Assert.assertEquals(2.5f, distance.frequencyHz, 0.0001f);
            Assert.assertEquals(0.3f, distance.dampingRatio, 0.0001f);
            return;
        }

        if (type == PhysicsJointComponent.TYPE_WHEEL) {
            PhysicsWheelJointComponent wheel =
                    world.getMapper(PhysicsWheelJointComponent.class).get(jointEid);

            Assert.assertEquals(0.6f, wheel.axisX, 0.0001f);
            Assert.assertEquals(0.8f, wheel.axisY, 0.0001f);
            Assert.assertEquals(4.2f, wheel.frequencyHz, 0.0001f);
            Assert.assertEquals(0.55f, wheel.dampingRatio, 0.0001f);
            Assert.assertTrue(wheel.enableMotor);
            Assert.assertEquals(9.5f, wheel.motorSpeedRad, 0.0001f);
            Assert.assertEquals(12.5f, wheel.maxMotorTorque, 0.0001f);
            return;
        }

        throw new IllegalArgumentException("Unsupported test joint type: " + type);
    }

    private static int assertRestoredJointWithExpectedSpecificComponent(World world, int type) {
        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = joints.getData();

        for (int i = 0; i < joints.size(); i++) {
            int jointEid = data[i];

            if (!world.getEntityManager().isActive(jointEid)) {
                continue;
            }

            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(jointEid);
            if (base.type != type) {
                continue;
            }

            assertOnlyExpectedJointSpecificComponent(world, jointEid, type);
            return jointEid;
        }

        Assert.fail("Joint type not found after scene reload: " + type);
        return -1;
    }

    private static void assertOnlyExpectedJointSpecificComponent(
            World world,
            int eid,
            int expectedType
    ) {
        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_DISTANCE,
                world.getMapper(PhysicsDistanceJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_REVOLUTE,
                world.getMapper(PhysicsRevoluteJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_PRISMATIC,
                world.getMapper(PhysicsPrismaticJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_WHEEL,
                world.getMapper(PhysicsWheelJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_FRICTION,
                world.getMapper(PhysicsFrictionJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_MOTOR,
                world.getMapper(PhysicsMotorJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_WELD,
                world.getMapper(PhysicsWeldJointComponent.class).has(eid)
        );

        Assert.assertEquals(
                expectedType == PhysicsJointComponent.TYPE_PULLEY,
                world.getMapper(PhysicsPulleyJointComponent.class).has(eid)
        );
    }

    private static void clearAllEntities(World world) {
        IntBag allEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();

        int[] data = allEntities.getData();
        int[] ids = new int[allEntities.size()];

        for (int i = 0; i < allEntities.size(); i++) {
            ids[i] = data[i];
        }

        for (int eid : ids) {
            if (world.getEntityManager().isActive(eid)) {
                world.delete(eid);
            }
        }

        world.process();
    }

    private static void assertWheelJointPresent(World world) {
        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = joints.getData();

        for (int i = 0; i < joints.size(); i++) {
            int eid = data[i];

            if (!world.getEntityManager().isActive(eid)) {
                continue;
            }

            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(eid);
            if (base.type != PhysicsJointComponent.TYPE_WHEEL) {
                continue;
            }

            PhysicsWheelJointComponent wheel =
                    world.getMapper(PhysicsWheelJointComponent.class).getSafe(eid, null);

            if (wheel == null) {
                Assert.fail("TYPE_WHEEL joint found on entity " + eid
                        + " but PhysicsWheelJointComponent is missing.");
            }

            assertOnlyExpectedJointSpecificComponent(
                    world,
                    eid,
                    PhysicsJointComponent.TYPE_WHEEL
            );

            Assert.assertEquals("Wheel axisX not restored", 0.6f, wheel.axisX, 0.0001f);
            Assert.assertEquals("Wheel axisY not restored", 0.8f, wheel.axisY, 0.0001f);
            Assert.assertEquals("Wheel frequencyHz not restored", 4.2f, wheel.frequencyHz, 0.0001f);
            Assert.assertEquals("Wheel dampingRatio not restored", 0.55f, wheel.dampingRatio, 0.0001f);
            Assert.assertTrue("Wheel enableMotor not restored", wheel.enableMotor);
            Assert.assertEquals("Wheel motorSpeedRad not restored", 9.5f, wheel.motorSpeedRad, 0.0001f);
            Assert.assertEquals("Wheel maxMotorTorque not restored", 12.5f, wheel.maxMotorTorque, 0.0001f);

            return;
        }

        Assert.fail("No PhysicsJointComponent with TYPE_WHEEL found in world after reload.");
    }


    private static FileHandle tempSceneFile(String name) {
        File file = new File("build/tmp/physics-joint-persistence/" + name + ".json");
        file.getParentFile().mkdirs();
        return new FileHandle(file);
    }
}