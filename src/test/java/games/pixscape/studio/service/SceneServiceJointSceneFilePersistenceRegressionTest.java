package games.pixscape.studio.service;

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
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.commands.CreateJointCommand;
import games.pixscape.studio.io.StudioFs;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Regression coverage for multi-scene file persistence.
 * This does not exercise the full editor scene-switch UI/service path.
 * It verifies that Wheel joint data is written to and restored from the expected scene file.
 */
public class SceneServiceJointSceneFilePersistenceRegressionTest {

    @Test
    public void wheelJointSavedToSceneAFileIsNotLostWhenSceneBFileIsSaved() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "JointSceneSwitch";
        cfg.projectFileName = "JointSceneSwitch";
        cfg.exportRootPathDir = new File("build/tmp/scene-service-joint-file-persistence/" + System.nanoTime() + "/export").getAbsolutePath();

        cfg.createSceneMeta("SceneA");
        SceneMeta sceneAMeta = cfg.getCurrentSceneMeta();
        cfg.createSceneMeta("SceneB");
        SceneMeta sceneBMeta = cfg.getCurrentSceneMeta();
        Assert.assertNotNull("SceneA metadata is null", sceneAMeta);
        Assert.assertNotNull("SceneB metadata is null", sceneBMeta);
        Assert.assertNotNull("SceneA file is null", sceneAMeta.getFile());
        Assert.assertNotNull("SceneB file is null", sceneBMeta.getFile());
        Assert.assertFalse("SceneA file is blank", sceneAMeta.getFile().isBlank());
        Assert.assertFalse("SceneB file is blank", sceneBMeta.getFile().isBlank());
        Assert.assertNotEquals("SceneA and SceneB must map to distinct files", sceneAMeta.getFile(), sceneBMeta.getFile());
        cfg.setCurrentSceneByName("SceneA");

        FileHandle projectDir = new FileHandle(new File("build/tmp/scene-service-joint-file-persistence/" + System.nanoTime() + "/project"));
        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);
        scenesDir.mkdirs();

        World world = worldWithSerialization();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));

        try {
            PhysicsService physicsService = new PhysicsService(world, box2d);
            int bodyA = createBody(world, historyIds, 0f, 0f);
            int bodyB = createBody(world, historyIds, 4f, 2f);
            int jointEid = createWheelJoint(world, physicsService, historyIds, bodyA, bodyB);
            setWheelFields(world, jointEid);
            world.process();

            FileHandle sceneAFile = scenesDir.child(sceneAMeta.getFile());
            SceneSaveTestSupport.save(world, sceneAFile, sceneMeta());
            Assert.assertTrue("SceneA file was not created", sceneAFile.exists());
            String sceneAJson = sceneAFile.readString(StandardCharsets.UTF_8.name());
            Assert.assertTrue("SceneA JSON missing PhysicsWheelJointComponent", sceneAJson.contains("PhysicsWheelJointComponent"));

            clearAllEntities(world);
            cfg.setCurrentSceneByName("SceneB");
            FileHandle sceneBFile = scenesDir.child(sceneBMeta.getFile());
            SceneSaveTestSupport.save(world, sceneBFile, sceneMeta());
            Assert.assertTrue("SceneB file was not created", sceneBFile.exists());
            Assert.assertTrue("SceneA file disappeared after SceneB save", sceneAFile.exists());
            String sceneAJsonAfterSceneBSave = sceneAFile.readString(StandardCharsets.UTF_8.name());
            Assert.assertTrue("SceneA was overwritten while saving SceneB", sceneAJsonAfterSceneBSave.contains("PhysicsWheelJointComponent"));
            String sceneBJson = sceneBFile.readString(StandardCharsets.UTF_8.name());
            Assert.assertFalse("SceneB unexpectedly contains wheel joint data", sceneBJson.contains("PhysicsWheelJointComponent"));

            cfg.setCurrentSceneByName("SceneA");
            clearAllEntities(world);
            SceneLoader.loadScene(
                    world, sceneAFile, false,
                    sceneMeta());
            world.process();

            assertWheelJointPresent(world);
        } finally {
            box2d.dispose();
        }
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private static int createBody(World world, HistoryIdRegistry historyIds, float x, float y) {
        int eid = world.create();
        historyIds.ensureForEntity(eid);
        TransformComponent transform = world.getMapper(TransformComponent.class).create(eid);
        transform.x = x;
        transform.y = y;

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(eid);
        PhysicsService.initDefaultBody(body);
        PhysicsShapesComponent fixtures = world.getMapper(PhysicsShapesComponent.class).create(eid);
        games.pixscape.runtime.physics.PhysicsShapeData shape =
                games.pixscape.runtime.service.PhysicsService.createDefaultShape(eid + 1);
        shape.physicsShapeId = eid + 1;
        fixtures.shapes.add(shape);
        return eid;
    }

    private static games.pixscape.runtime.loading.SceneMetaRuntime sceneMeta() {
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = true;
        meta.nextPhysicsShapeId = 1000;
        return meta;
    }

    private static int createWheelJoint(World world, PhysicsService physicsService, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        CreateJointCommand command = new CreateJointCommand(world, physicsService, historyIds, PhysicsJointComponent.TYPE_WHEEL, bodyA, bodyB, 1f, 1f);
        command.redo();
        return command.getCreatedJointEntityId();
    }

    private static void setWheelFields(World world, int jointEid) {
        PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).get(jointEid);
        wheel.axisX = 0.6f;
        wheel.axisY = 0.8f;
        wheel.frequencyHz = 4.2f;
        wheel.dampingRatio = 0.55f;
        wheel.enableMotor = true;
        wheel.motorSpeedRad = 9.5f;
        wheel.maxMotorTorque = 12.5f;
    }

    private static void clearAllEntities(World world) {
        IntBag all = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        int[] data = all.getData();
        int[] ids = new int[all.size()];
        for (int i = 0; i < all.size(); i++) ids[i] = data[i];
        for (int eid : ids) if (world.getEntityManager().isActive(eid)) world.delete(eid);
        world.process();
    }

    private static void assertWheelJointPresent(World world) {
        IntBag joints = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        int[] data = joints.getData();
        for (int i = 0; i < joints.size(); i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(eid);
            if (base.type != PhysicsJointComponent.TYPE_WHEEL) continue;

            PhysicsWheelJointComponent wheel = world.getMapper(PhysicsWheelJointComponent.class).getSafe(eid, null);
            if (wheel == null) {
                Assert.fail("TYPE_WHEEL found but PhysicsWheelJointComponent missing. Existing joints:\n" + dumpPhysicsJoints(world));
            }
            Assert.assertFalse("Unexpected PhysicsDistanceJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsDistanceJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsRevoluteJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsRevoluteJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsPrismaticJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsPrismaticJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsFrictionJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsFrictionJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsMotorJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsMotorJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsWeldJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsWeldJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsPulleyJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsPulleyJointComponent.class).has(eid));
            Assert.assertFalse("Unexpected PhysicsGearJointComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsGearJointComponent.class).has(eid));

            Assert.assertEquals(0.6f, wheel.axisX, 0.0001f);
            Assert.assertEquals(0.8f, wheel.axisY, 0.0001f);
            Assert.assertEquals(4.2f, wheel.frequencyHz, 0.0001f);
            Assert.assertEquals(0.55f, wheel.dampingRatio, 0.0001f);
            Assert.assertTrue(wheel.enableMotor);
            Assert.assertEquals(9.5f, wheel.motorSpeedRad, 0.0001f);
            Assert.assertEquals(12.5f, wheel.maxMotorTorque, 0.0001f);
            Assert.assertTrue("Wheel body A is inactive. Existing joints:\n" + dumpPhysicsJoints(world), world.getEntityManager().isActive(base.aEid));
            Assert.assertTrue("Wheel body B is inactive. Existing joints:\n" + dumpPhysicsJoints(world), world.getEntityManager().isActive(base.bEid));
            Assert.assertTrue("Wheel body A missing PhysicsBodyComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsBodyComponent.class).has(base.aEid));
            Assert.assertTrue("Wheel body B missing PhysicsBodyComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsBodyComponent.class).has(base.bEid));
            Assert.assertTrue("Wheel body A missing PhysicsShapesComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsShapesComponent.class).has(base.aEid));
            Assert.assertTrue("Wheel body B missing PhysicsShapesComponent. Existing joints:\n" + dumpPhysicsJoints(world), world.getMapper(PhysicsShapesComponent.class).has(base.bEid));
            return;
        }
        Assert.fail("No TYPE_WHEEL joint found after scene switch. Existing joints:\n" + dumpPhysicsJoints(world));
    }

    private static String dumpPhysicsJoints(World world) {
        IntBag joints = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        int[] data = joints.getData();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < joints.size(); i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(eid);
            sb.append("eid=").append(eid)
                    .append(", type=").append(base.type)
                    .append(", aEid=").append(base.aEid)
                    .append(", bEid=").append(base.bEid)
                    .append(", hasDistance=").append(world.getMapper(PhysicsDistanceJointComponent.class).has(eid))
                    .append(", hasWheel=").append(world.getMapper(PhysicsWheelJointComponent.class).has(eid))
                    .append(", hasRevolute=").append(world.getMapper(PhysicsRevoluteJointComponent.class).has(eid))
                    .append(", hasPrismatic=").append(world.getMapper(PhysicsPrismaticJointComponent.class).has(eid))
                    .append(", hasFriction=").append(world.getMapper(PhysicsFrictionJointComponent.class).has(eid))
                    .append(", hasMotor=").append(world.getMapper(PhysicsMotorJointComponent.class).has(eid))
                    .append(", hasWeld=").append(world.getMapper(PhysicsWeldJointComponent.class).has(eid))
                    .append(", hasPulley=").append(world.getMapper(PhysicsPulleyJointComponent.class).has(eid))
                    .append(", hasGear=").append(world.getMapper(PhysicsGearJointComponent.class).has(eid))
                    .append('\n');
        }
        if (sb.length() == 0) return "<no active joint entities>";
        return sb.toString();
    }
}
