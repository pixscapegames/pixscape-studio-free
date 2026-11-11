package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;

import static org.junit.Assert.*;

public class DeletionBeforeEcsPublicationTest {
    @Test public void restoredOrdinarySpriteIsIncludedInNewLayerDeletion() {
        try (Fixture f = new Fixture()) {
            int sprite = f.sprite(0);
            int stable = f.identities.getStableId(sprite);
            long id = f.id(sprite);
            f.publish();
            Command delete = DeleteEntitiesCommandFactory.create(f.world, f.history.historyIds(),
                    IntArray.with(sprite), null);
            assertTrue(delete instanceof DeleteEntitiesCommand);
            f.history.execute(delete); f.publish(); f.occupyReleasedIds(); f.history.undo();
            int restored = f.entity(id);
            assertNotEquals(sprite, restored);
            f.deleteLayer(0);
            assertEquals("The new layer command must capture the restored sprite", -1, f.entity(id));
            assertEquals(-1, f.identities.findByStableId(stable));
            f.history.undo();
            f.assertIdentity(id, stable);
            f.publish();
            f.assertIdentity(id, stable);
            assertEquals(0, f.index(f.entity(id)));
            assertEquals(1, f.world.getAspectSubscriptionManager().get(Aspect.all(
                    games.pixscape.runtime.component.TextureRegionComponent.class)).getEntities().size());
        }
    }

    @Test public void restoredOrdinarySpriteFollowsSurvivingLayerImmediately() {
        try (Fixture f = new Fixture()) {
            int sprite = f.sprite(1);
            int stable = f.identities.getStableId(sprite);
            long id = f.id(sprite), layerB = f.id(f.layers.getLayerEntity(1));
            f.publish();
            f.history.execute(DeleteEntitiesCommandFactory.create(f.world, f.history.historyIds(), IntArray.with(sprite), null));
            f.publish(); f.occupyReleasedIds(); f.history.undo();
            f.deleteLayer(0);
            assertEquals("B moved from index 1 to 0", 0, f.index(f.entity(id)));
            assertEquals(f.entity(layerB), f.layers.getLayerEntity(f.index(f.entity(id))));
            f.assertIdentity(id, stable); f.publish(); f.assertIdentity(id, stable);
            assertEquals(0, f.layers.getEffectiveLayerIndex(f.entity(id)));
            f.history.undo();
            assertEquals(1, f.index(f.entity(id))); f.publish();
            assertEquals(f.entity(layerB), f.layers.getLayerEntity(f.index(f.entity(id))));
        }
    }

    @Test public void newLayerDeletionCapturesRestoredCrossLayerJointBeforePublication() {
        try (Fixture f = new Fixture()) {
            int a = f.body(0), b = f.body(1);
            int joint = f.physics.createDistanceJoint(a, b);
            long ah = f.id(a), bh = f.id(b), jh = f.id(joint);
            f.publish(); assertEquals(1, f.box2d.world.getJointCount());
            f.deleteLayer(1); f.publish(); f.occupyReleasedIds(); f.history.undo();
            int restoredB = f.entity(bh), restoredJoint = f.entity(jh);
            assertNotEquals(b, restoredB); assertNotEquals(joint, restoredJoint);
            f.deleteLayer(0);
            assertEquals("A new capture must see restored joint dependencies", -1, f.entity(jh));
            assertEquals(restoredB, f.entity(bh));
            f.history.undo();
            f.assertJoint(jh, ah, bh);
            assertEquals(restoredB, f.entity(bh));
            f.assertDependencies(f.entity(ah), f.entity(jh));
            f.assertDependencies(f.entity(bh), f.entity(jh));
            f.publish(); f.assertJoint(jh, ah, bh); f.assertJointCount(1);
            f.assertDependencies(f.entity(ah), f.entity(jh));
            f.assertDependencies(f.entity(bh), f.entity(jh));
            assertFalse(f.affected(a).contains(restoredJoint));
            for (int i = 0; i < 3; i++) {
                f.history.redo(); assertEquals(-1, f.entity(jh));
                f.history.undo(); f.assertJoint(jh, ah, bh);
            }
            f.publish(); f.assertJointCount(1); f.assertJoint(jh, ah, bh);
        }
    }

    @Test public void deletedJointIsNotRecapturedOrResurrectedByLayerDeletion() {
        try (Fixture f = new Fixture()) {
            int a = f.body(0), b = f.body(1);
            int joint = f.physics.createDistanceJoint(a, b);
            long jh = f.id(joint);
            f.publish();
            f.history.execute(new DeleteJointCommand(f.world, f.history.historyIds(), joint));
            f.deleteLayer(0); f.history.undo();
            f.publish();
            f.assertJointCount(0);
            assertEquals(-1, f.entity(jh));
            f.history.undo(); f.publish(); f.assertJointCount(1);
        }
    }

    @Test public void restoredGearSourcesAreVisibleToNewLayerCapture() {
        try (Fixture f = new Fixture()) {
            int groundA = f.body(0), wheelA = f.body(0), groundB = f.body(1), wheelB = f.body(1);
            f.world.getMapper(PhysicsBodyComponent.class).get(groundA).type = PhysicsBodyComponent.STATIC;
            f.world.getMapper(PhysicsBodyComponent.class).get(groundB).type = PhysicsBodyComponent.STATIC;
            int sourceA = f.physics.createRevoluteJoint(groundA, wheelA, 0, 0);
            int sourceB = f.physics.createRevoluteJoint(groundB, wheelB, 100, 0);
            int gear = f.physics.createGearJoint(sourceA, sourceB, 2f);
            assertTrue(gear >= 0);
            long sa = f.id(sourceA), sb = f.id(sourceB), gh = f.id(gear);
            f.publish(); f.assertJointCount(3);
            f.deleteLayer(1); f.publish(); f.occupyReleasedIds(); f.history.undo();
            int retainedSourceB = f.entity(sb);
            f.deleteLayer(0);
            assertEquals(-1, f.entity(gh));
            assertEquals(retainedSourceB, f.entity(sb));
            f.history.undo();
            f.assertGear(gh, sa, sb);
            assertEquals(retainedSourceB, f.entity(sb));
            f.publish(); f.assertGear(gh, sa, sb); f.assertJointCount(3);
            // An explicitly deleted source must withdraw its dependent gear before another capture.
            f.history.execute(new DeleteJointCommand(f.world, f.history.historyIds(), f.entity(sa)));
            assertEquals(-1, f.entity(gh));
            f.deleteLayer(1); f.history.undo(); f.publish();
            f.assertJointCount(1); assertEquals(-1, f.entity(gh));
            f.history.undo(); f.assertGear(gh, sa, sb); f.publish(); f.assertJointCount(3);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Box2dWorldService box2d;
        final Box2dSyncSystem sync;
        final World world;
        final SceneMeta meta = new SceneMeta();
        final IdentityRegistry identities = new IdentityRegistry();
        final HistoryManager history = new HistoryManager(32);
        final LayerService layers;
        final PhysicsService physics;

        Fixture() {
            GdxNativesLoader.load();
            box2d = new Box2dWorldService(100f, new Vector2(0, 0));
            sync = new Box2dSyncSystem(box2d);
            world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(32), sync).build());
            identities.bind(world, meta);
            layers = new LayerService(world, null, history.historyIds(), identities);
            physics = new PhysicsService(world, box2d, meta);
            layers.addLayerTop("A"); layers.addLayerTop("B");
        }
        int sprite(int layer) {
            int entity = world.create();
            new GenericEntityInitializer(world).configureStandaloneSprite(101, "scene1", 16, 16,
                    layer * 100f, 0, 8, 8, 0, 0, 0, "Sprite", layer).init(entity);
            identities.ensureStableId(entity);
            return entity;
        }
        int body(int layer) {
            int entity = sprite(layer);
            physics.ensurePhysics(entity);
            world.getMapper(PhysicsBodyComponent.class).get(entity).type = PhysicsBodyComponent.DYNAMIC;
            return entity;
        }
        long id(int entity) { return history.historyIds().ensureForEntity(entity); }
        int entity(long id) { return history.historyIds().entityOfHistoryId(id); }
        int index(int entity) { return world.getMapper(EntityIndexComponent.class).get(entity).layerIndex; }
        void publish() { world.process(); world.process(); }
        void occupyReleasedIds() { for (int i = 0; i < 12; i++) world.create(); }
        void deleteLayer(int index) { history.execute(new DeleteLayerCommand(layers, layers.getLayerEntity(index), null)); }
        IntArray affected(int entity) { return PhysicsService.collectJointsAffectedByEntityRemoval(world, IntArray.with(entity), new IntArray()); }
        void assertDependencies(int body, int joint) { assertEquals(IntArray.with(joint), affected(body)); }
        void assertIdentity(long id, int stable) {
            int entity = entity(id); assertTrue(entity >= 0);
            assertEquals(stable, world.getMapper(PixscapeIdentityComponent.class).get(entity).stableId);
            assertEquals(entity, identities.findByStableId(stable));
            assertEquals(id, history.historyIds().historyIdOfEntity(entity));
        }
        void assertJoint(long joint, long a, long b) {
            var base = world.getMapper(PhysicsJointComponent.class).get(entity(joint));
            assertEquals(entity(a), base.aEid); assertEquals(entity(b), base.bEid);
        }
        void assertGear(long gear, long a, long b) {
            var relation = world.getMapper(PhysicsGearJointComponent.class).get(entity(gear));
            assertEquals(entity(a), relation.joint1Eid); assertEquals(entity(b), relation.joint2Eid);
            assertTrue(affected(entity(a)).contains(entity(gear)));
            assertTrue(affected(entity(b)).contains(entity(gear)));
        }
        void assertJointCount(int count) {
            assertEquals(count, world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities().size());
            assertEquals(count, box2d.world.getJointCount());
        }
        @Override public void close() { identities.bind(null, null); world.dispose(); box2d.dispose(); }
    }
}
