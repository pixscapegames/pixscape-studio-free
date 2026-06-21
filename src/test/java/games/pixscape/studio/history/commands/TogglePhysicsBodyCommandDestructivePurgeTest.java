package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class TogglePhysicsBodyCommandDestructivePurgeTest {

    @Test
    public void destructivePurgeClassicBodyRemovesBodyFixturesJointsRuntimeAndHistoryBindings() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int bodyA = createBody(world, historyIds);
        int bodyB = createBody(world, historyIds);
        int joint = createRevoluteJoint(world, historyIds, bodyA, bodyB);

        world.getMapper(PhysicsRuntimeBodyComponent.class).create(bodyA);
        long jointHistoryId = historyIds.historyIdOfEntity(joint);

        TogglePhysicsBodyCommand purge = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                bodyA,
                false,
                PhysicsBodyComponent.DYNAMIC,
                true
        );

        purge.redo();
        world.process();

        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsRuntimeBodyComponent.class).has(bodyA));

        Assert.assertFalse(world.getEntityManager().isActive(joint));
        Assert.assertEquals(-1, historyIds.entityOfHistoryId(jointHistoryId));
        assertNoJointReferencesBody(world, bodyA);

        Assert.assertTrue(world.getEntityManager().isActive(bodyB));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(bodyB));
    }

    @Test
    public void destructivePurgeTiledLayerRemovesCollisionPhysicsAndReferencedJoints() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int tiledLayer = createBody(world, historyIds);
        LayerComponent layer = world.getMapper(LayerComponent.class).create(tiledLayer);
        layer.type = LayerComponent.TYPE_TILED;

        int otherBody = createBody(world, historyIds);
        int joint = createRevoluteJoint(world, historyIds, tiledLayer, otherBody);

        TogglePhysicsBodyCommand purge = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                tiledLayer,
                false,
                PhysicsBodyComponent.STATIC,
                false
        );

        purge.redo();
        world.process();

        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(tiledLayer));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(tiledLayer));
        Assert.assertFalse(world.getEntityManager().isActive(joint));
        assertNoJointReferencesBody(world, tiledLayer);
        Assert.assertTrue(world.getEntityManager().isActive(otherBody));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(otherBody));
    }

    @Test
    public void destructivePurgeBodyReferencedByJointDeletesDependentJointAndKeepsOtherBodyValid() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int bodyA = createBody(world, historyIds);
        int bodyB = createBody(world, historyIds);
        int jointAB = createRevoluteJoint(world, historyIds, bodyA, bodyB);

        TogglePhysicsBodyCommand purge = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                bodyA,
                false,
                PhysicsBodyComponent.DYNAMIC,
                true
        );

        purge.redo();
        world.process();

        Assert.assertFalse(world.getEntityManager().isActive(jointAB));
        assertNoJointReferencesBody(world, bodyA);

        Assert.assertTrue(world.getEntityManager().isActive(bodyB));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(bodyB));
        Assert.assertTrue(world.getMapper(PhysicsFixturesComponent.class).has(bodyB));
    }

    @Test
    public void recreateAfterDestructivePurgeStartsFromCleanBaselineWithoutFixtureOrJointDuplication() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int bodyA = createBody(world, historyIds);
        int bodyB = createBody(world, historyIds);
        int joint = createRevoluteJoint(world, historyIds, bodyA, bodyB);

        TogglePhysicsBodyCommand disable = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                bodyA,
                false,
                PhysicsBodyComponent.DYNAMIC,
                true
        );
        disable.redo();
        world.process();

        TogglePhysicsBodyCommand enable = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                bodyA,
                true,
                PhysicsBodyComponent.DYNAMIC,
                true
        );
        enable.redo();
        world.process();

        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).get(bodyA);
        Assert.assertNotNull(fixtures);
        Assert.assertEquals(1, fixtures.fixtures.size);
        assertNoJointReferencesBody(world, bodyA);
        Assert.assertFalse(world.getEntityManager().isActive(joint));
    }

    @Test
    public void repeatedDestructivePurgeOnAlreadyCleanBodyIsSafeAndIdempotent() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();

        int body = createBody(world, historyIds);

        TogglePhysicsBodyCommand first = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                body,
                false,
                PhysicsBodyComponent.DYNAMIC,
                false
        );
        first.redo();
        world.process();

        TogglePhysicsBodyCommand second = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                body,
                false,
                PhysicsBodyComponent.DYNAMIC,
                false
        );
        second.redo();
        world.process();

        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(body));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(body));
        assertNoJointReferencesBody(world, body);
    }

    @Test
    public void destructivePurgeThroughHistoryManager_undoRedoRestoresAndReappliesCleanState() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(16);

        int bodyA = createBody(world, historyIds);
        int bodyB = createBody(world, historyIds);
        int joint = createRevoluteJoint(world, historyIds, bodyA, bodyB);
        world.getMapper(PhysicsRuntimeBodyComponent.class).create(bodyA);

        TogglePhysicsBodyCommand purge = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                bodyA,
                false,
                PhysicsBodyComponent.DYNAMIC,
                true
        );

        history.execute(purge);
        world.process();

        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsRuntimeBodyComponent.class).has(bodyA));
        Assert.assertFalse(world.getEntityManager().isActive(joint));
        assertNoJointReferencesBody(world, bodyA);

        history.undo();
        world.process();

        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(bodyA));
        Assert.assertTrue(world.getMapper(PhysicsFixturesComponent.class).has(bodyA));
        Assert.assertTrue(world.getEntityManager().isActive(joint));
        Assert.assertTrue(world.getMapper(PhysicsJointComponent.class).has(joint));
        assertJointReferencesBody(world, joint, bodyA);

        history.redo();
        world.process();

        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(bodyA));
        Assert.assertFalse(world.getEntityManager().isActive(joint));
        assertNoJointReferencesBody(world, bodyA);
    }

    private static int createBody(World world, HistoryIdRegistry historyIds) {
        int entityId = world.create();
        historyIds.ensureForEntity(entityId);

        world.getMapper(TransformComponent.class).create(entityId);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.type = PhysicsBodyComponent.DYNAMIC;
        body.enabled = true;

        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(entityId);
        FixtureDefData fixture = FixtureCommandSupport.createDefaultFixture();
        fixtures.fixtures.add(fixture);

        return entityId;
    }

    private static int createRevoluteJoint(World world, HistoryIdRegistry historyIds, int bodyA, int bodyB) {
        int jointEntityId = world.create();
        historyIds.ensureForEntity(jointEntityId);

        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEntityId);
        joint.type = PhysicsJointComponent.TYPE_REVOLUTE;
        joint.aEid = bodyA;
        joint.bEid = bodyB;

        world.getMapper(PhysicsRevoluteJointComponent.class).create(jointEntityId);
        return jointEntityId;
    }

    private static void assertNoJointReferencesBody(World world, int bodyEid) {
        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = joints.getData();
        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEid = data[i];
            PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).getSafe(jointEid, null);
            if (joint == null) continue;

            Assert.assertNotEquals("joint still references purged body as A", bodyEid, joint.aEid);
            Assert.assertNotEquals("joint still references purged body as B", bodyEid, joint.bEid);
        }
    }

    private static void assertJointReferencesBody(World world, int jointEid, int bodyEid) {
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).getSafe(jointEid, null);
        Assert.assertNotNull(joint);
        boolean references = joint.aEid == bodyEid || joint.bEid == bodyEid;
        Assert.assertTrue("joint must reference restored body after undo", references);
    }
}
