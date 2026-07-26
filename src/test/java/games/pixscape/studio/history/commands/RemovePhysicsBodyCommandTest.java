package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class RemovePhysicsBodyCommandTest {
    @Test
    public void repeatedRemoveUndoRestoresBodyShapeAndJointIdentities() {
        Harness harness = new Harness();
        int bodyA = harness.body(10f);
        int bodyB = harness.body(20f);
        PhysicsShapesComponent originalShapes = harness.world.getMapper(
                PhysicsShapesComponent.class).get(bodyA);
        Array<PhysicsShapeData> expandedShapes =
                FixtureCommandSupport.copyFixtures(harness.world, bodyA);
        expandedShapes.add(PhysicsService.createDefaultShape(
                harness.physics.allocateNewPhysicsShapeId()));
        PreparedPhysicsBodyCandidate expanded =
                PhysicsService.prepareBodyCandidate(expandedShapes);
        PhysicsService.publishPreparedCandidate(
                originalShapes,
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .get(bodyA),
                expanded);
        int joint = harness.physics.createDistanceJoint(bodyA, bodyB);
        PhysicsDistanceJointComponent distance = harness.world.getMapper(
                PhysicsDistanceJointComponent.class).get(joint);
        distance.lengthM = 3.5f;
        long jointHistoryId = harness.historyIds.ensureForEntity(joint);
        int physicsShapeId = originalShapes.shapes.first().physicsShapeId;
        int secondPhysicsShapeId = originalShapes.shapes.get(1).physicsShapeId;
        int highWater = harness.meta.nextPhysicsShapeId;
        TransformComponent ownerTransform = harness.world.getMapper(
                TransformComponent.class).get(bodyA);

        RemovePhysicsBodyCommand command = new RemovePhysicsBodyCommand(
                harness.world, harness.historyIds, harness.physics, bodyA);
        harness.history.execute(command);
        harness.world.process();
        assertRemoved(harness, bodyA, jointHistoryId);
        Assert.assertTrue(harness.world.getEntityManager().isActive(bodyA));
        Assert.assertSame(ownerTransform, harness.world.getMapper(
                TransformComponent.class).get(bodyA));

        for (int cycle = 0; cycle < 2; cycle++) {
            harness.history.undo();
            harness.world.process();
            int restoredJoint = harness.historyIds.entityOfHistoryId(jointHistoryId);
            Assert.assertTrue(restoredJoint >= 0);
            Assert.assertEquals(physicsShapeId,
                    harness.world.getMapper(PhysicsShapesComponent.class)
                            .get(bodyA).shapes.first().physicsShapeId);
            Assert.assertEquals(secondPhysicsShapeId,
                    harness.world.getMapper(PhysicsShapesComponent.class)
                            .get(bodyA).shapes.get(1).physicsShapeId);
            PhysicsJointComponent base = harness.world.getMapper(
                    PhysicsJointComponent.class).get(restoredJoint);
            Assert.assertEquals(bodyA, base.aEid);
            Assert.assertEquals(bodyB, base.bEid);
            Assert.assertEquals(3.5f, harness.world.getMapper(
                    PhysicsDistanceJointComponent.class)
                    .get(restoredJoint).lengthM, 0f);
            Assert.assertTrue(harness.world.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(bodyA).valid);
            Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);

            harness.history.redo();
            harness.world.process();
            assertRemoved(harness, bodyA, jointHistoryId);
        }

        harness.history.undo();
        harness.world.process();
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);
    }

    @Test
    public void indirectGearClosureSurvivesTwoRedoUndoCycles() {
        GdxNativesLoader.load();
        GearHarness harness = new GearHarness();
        try {
            int staticA = harness.body(0f, PhysicsBodyComponent.STATIC);
            int dynamicA = harness.body(100f, PhysicsBodyComponent.DYNAMIC);
            int staticB = harness.body(200f, PhysicsBodyComponent.STATIC);
            int dynamicB = harness.body(300f, PhysicsBodyComponent.DYNAMIC);
            int source1 = harness.physics.createRevoluteJoint(
                    staticA, dynamicA, 50f, 0f);
            int source2 = harness.physics.createPrismaticJoint(
                    staticB, dynamicB, 250f, 0f);
            int gearEntity = harness.physics.createGearJoint(source1, source2, 2f);
            long sourceHistory1 = harness.historyIds.ensureForEntity(source1);
            long sourceHistory2 = harness.historyIds.ensureForEntity(source2);
            long gearHistory = harness.historyIds.ensureForEntity(gearEntity);

            harness.processPhysics();
            Assert.assertEquals(3, harness.box2d.world.getJointCount());

            harness.history.execute(new RemovePhysicsBodyCommand(
                    harness.world, harness.historyIds, harness.physics, staticA));
            harness.processPhysics();
            assertIndirectRemoval(
                    harness, staticA, sourceHistory1, sourceHistory2, gearHistory);
            occupyReleasedEntityIds(harness.world);

            int previousSource1EntityId = source1;
            int previousGearEntityId = gearEntity;
            for (int cycle = 0; cycle < 2; cycle++) {
                harness.history.undo();
                harness.processPhysics();
                assertIndirectRestore(
                        harness, sourceHistory1, sourceHistory2, gearHistory);
                int restoredSource1 =
                        harness.historyIds.entityOfHistoryId(sourceHistory1);
                int restoredGear = harness.historyIds.entityOfHistoryId(gearHistory);
                Assert.assertNotEquals(previousSource1EntityId, restoredSource1);
                Assert.assertNotEquals(previousGearEntityId, restoredGear);
                Assert.assertEquals(
                        source2, harness.historyIds.entityOfHistoryId(sourceHistory2));
                previousSource1EntityId = restoredSource1;
                previousGearEntityId = restoredGear;

                harness.history.redo();
                harness.processPhysics();
                assertIndirectRemoval(
                        harness, staticA, sourceHistory1, sourceHistory2, gearHistory);
                occupyReleasedEntityIds(harness.world);
            }

            harness.history.undo();
            harness.processPhysics();
            assertIndirectRestore(
                    harness, sourceHistory1, sourceHistory2, gearHistory);
            Assert.assertNotEquals(
                    previousSource1EntityId,
                    harness.historyIds.entityOfHistoryId(sourceHistory1));
            Assert.assertNotEquals(
                    previousGearEntityId,
                    harness.historyIds.entityOfHistoryId(gearHistory));
            Assert.assertEquals(
                    source2, harness.historyIds.entityOfHistoryId(sourceHistory2));
        } finally {
            harness.close();
        }
    }

    private static void assertIndirectRemoval(
            GearHarness harness,
            int removedBody,
            long sourceHistory1,
            long sourceHistory2,
            long gearHistory) {
        Assert.assertFalse(harness.world.getMapper(
                PhysicsBodyComponent.class).has(removedBody));
        Assert.assertEquals(-1, harness.historyIds.entityOfHistoryId(sourceHistory1));
        Assert.assertEquals(-1, harness.historyIds.entityOfHistoryId(gearHistory));
        int source2 = harness.historyIds.entityOfHistoryId(sourceHistory2);
        Assert.assertTrue(harness.world.getEntityManager().isActive(source2));
        Assert.assertTrue(harness.world.getMapper(
                PhysicsJointComponent.class).has(source2));
        Assert.assertEquals(1, harness.box2d.world.getJointCount());
        assertAuthoredJointGraphValid(harness.world);
    }

    private static void assertIndirectRestore(
            GearHarness harness,
            long sourceHistory1,
            long sourceHistory2,
            long gearHistory) {
        int restoredSource1 =
                harness.historyIds.entityOfHistoryId(sourceHistory1);
        int restoredSource2 =
                harness.historyIds.entityOfHistoryId(sourceHistory2);
        int restoredGear = harness.historyIds.entityOfHistoryId(gearHistory);
        Assert.assertTrue(restoredSource1 >= 0);
        Assert.assertTrue(restoredSource2 >= 0);
        Assert.assertTrue(restoredGear >= 0);
        PhysicsGearJointComponent restored = harness.world.getMapper(
                PhysicsGearJointComponent.class).get(restoredGear);
        Assert.assertEquals(restoredSource1, restored.joint1Eid);
        Assert.assertEquals(restoredSource2, restored.joint2Eid);
        Assert.assertEquals(2f, restored.ratio, 0f);
        Assert.assertEquals(3, harness.box2d.world.getJointCount());
        assertAuthoredJointGraphValid(harness.world);
    }

    private static void assertAuthoredJointGraphValid(World world) {
        IntBag joints = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsJointComponent.class))
                .getEntities();
        int[] data = joints.getData();
        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEntityId = data[i];
            PhysicsJointComponent joint = world.getMapper(
                    PhysicsJointComponent.class).get(jointEntityId);
            Assert.assertTrue(world.getMapper(
                    PhysicsBodyComponent.class).has(joint.aEid));
            Assert.assertTrue(world.getMapper(
                    PhysicsBodyComponent.class).has(joint.bEid));
            if (joint.type == PhysicsJointComponent.TYPE_GEAR) {
                PhysicsGearJointComponent gear = world.getMapper(
                        PhysicsGearJointComponent.class).get(jointEntityId);
                Assert.assertTrue(world.getMapper(
                        PhysicsJointComponent.class).has(gear.joint1Eid));
                Assert.assertTrue(world.getMapper(
                        PhysicsJointComponent.class).has(gear.joint2Eid));
            }
        }
    }

    private static void occupyReleasedEntityIds(World world) {
        for (int i = 0; i < 8; i++) world.create();
    }

    private static void assertRemoved(
            Harness harness, int bodyEntityId, long jointHistoryId) {
        Assert.assertFalse(harness.world.getMapper(
                PhysicsBodyComponent.class).has(bodyEntityId));
        Assert.assertFalse(harness.world.getMapper(
                PhysicsShapesComponent.class).has(bodyEntityId));
        Assert.assertFalse(harness.world.getMapper(
                PhysicsCompiledFixturesComponent.class).has(bodyEntityId));
        Assert.assertEquals(-1,
                harness.historyIds.entityOfHistoryId(jointHistoryId));
    }

    private static final class Harness {
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        final World world = new World(
                new WorldConfigurationBuilder().with(dirty).build());
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, meta);
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();
        final HistoryManager history = new HistoryManager(16);

        Harness() {
            history.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) -> {
            });
        }

        int body(float x) {
            int entityId = world.create();
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).create(entityId);
            transform.x = x;
            historyIds.ensureForEntity(entityId);
            AddPhysicsBodyCommand add = new AddPhysicsBodyCommand(
                    world,
                    historyIds,
                    physics,
                    entityId,
                    PhysicsBodyComponent.DYNAMIC,
                    true);
            add.redo();
            return entityId;
        }
    }

    private static final class GearHarness {
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        final Box2dWorldService box2d =
                new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        final Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        final World world = new World(
                new WorldConfigurationBuilder().with(dirty, sync).build());
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, box2d, meta);
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();
        final HistoryManager history = new HistoryManager(16);

        GearHarness() {
            history.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) -> {
            });
        }

        int body(float x, int type) {
            int entityId = world.create();
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).create(entityId);
            transform.x = x;
            historyIds.ensureForEntity(entityId);
            physics.ensurePhysics(entityId);
            world.getMapper(PhysicsBodyComponent.class).get(entityId).type = type;
            return entityId;
        }

        void close() {
            world.dispose();
            box2d.dispose();
        }

        void processPhysics() {
            world.process();
            world.process();
        }
    }
}
