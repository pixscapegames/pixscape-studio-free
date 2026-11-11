package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.*;
import games.pixscape.studio.service.AlignServiceTest;
import org.junit.Test;

import static org.junit.Assert.*;

public class DeleteLayerCompositionTest {
    @Test public void emptyAndOrdinaryLayersRestoreTheirPropertiesWithoutPublication() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService();
            int emptyIndex = layers.addLayerTop("Empty");
            int empty = layers.getLayerEntity(emptyIndex);
            layers.meta(emptyIndex).description = "Retain me";
            layers.setLayerLocked(empty, true); // Layer deletion never used content-edit locking.
            f.world.getMapper(LayerComponent.class).get(empty).spatialEnabled = true;
            var parallax = f.world.getMapper(LayerParallaxComponent.class).create(empty);
            parallax.factorX = .3f; parallax.factorY = .7f;
            f.history.execute(new DeleteLayerCommand(layers, empty, null));
            f.history.undo();
            assertEquals("Retain me", layers.meta(1).description);
            assertTrue(layers.meta(1).locked);
            assertTrue(f.world.getMapper(LayerComponent.class).get(layers.getLayerEntity(1)).spatialEnabled);
            assertEquals(.3f, f.world.getMapper(LayerParallaxComponent.class).get(layers.getLayerEntity(1)).factorX, 0);
            int ordinary = f.sprite(20, 30, 10, 10);
            int stable = f.context.identityRegistry().getStableId(ordinary);
            f.history.execute(new DeleteLayerCommand(layers, layers.getLayerEntity(0), null));
            assertEquals(-1, f.context.identityRegistry().findByStableId(stable));
            f.history.undo();
            int restored = f.context.identityRegistry().findByStableId(stable);
            assertEquals(20, f.t.get(restored).x, 0);
            assertEquals(30, f.t.get(restored).y, 0);
        }
    }

    @Test public void internalAndCrossLayerJointsRestoreReferencesWithoutDeletingOtherComposition() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService(); layers.addLayerTop("B");
            int root = f.root(0, 0), a = f.sprite(10, 0, 10, 10), b = f.sprite(20, 0, 10, 10);
            int otherRoot = f.root(100, 0), other = f.sprite(5, 0, 10, 10);
            f.attach(a, root); f.attach(b, root); f.attach(other, otherRoot);
            f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 1;
            var bodies = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class);
            bodies.create(a).gravityScale = .4f; bodies.create(b); bodies.create(other);
            var physics = new games.pixscape.runtime.service.PhysicsService(f.world, null, f.context.sceneMeta());
            var shape = games.pixscape.runtime.service.PhysicsService.createDefaultShape(physics.allocateNewPhysicsShapeId());
            f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).create(a).shapes.add(shape);
            f.world.getMapper(CustomPropertiesComponent.class).create(a).properties.putObjectStableId(
                    "other", f.context.identityRegistry().getStableId(other));
            int internal = joint(f, a, b), external = joint(f, a, other);
            f.process();
            long ah = f.history.historyIds().ensureForEntity(a), bh = f.history.historyIds().ensureForEntity(b);
            long ih = f.history.historyIds().ensureForEntity(internal), eh = f.history.historyIds().ensureForEntity(external);
            f.history.execute(new DeleteLayerCommand(layers, layers.getLayerEntity(1), null));
            for (int cycle = 0; cycle < 3; cycle++) {
                assertEquals(-1, f.history.historyIds().entityOfHistoryId(ih));
                assertEquals(-1, f.history.historyIds().entityOfHistoryId(eh));
                assertEquals(other, f.context.identityRegistry().findByStableId(f.context.identityRegistry().getStableId(other)));
                f.history.undo();
                int restoredA = f.history.historyIds().entityOfHistoryId(ah);
                var joints = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsJointComponent.class);
                var inside = joints.get(f.history.historyIds().entityOfHistoryId(ih));
                var outside = joints.get(f.history.historyIds().entityOfHistoryId(eh));
                assertEquals(restoredA, inside.aEid); assertEquals(f.history.historyIds().entityOfHistoryId(bh), inside.bEid);
                assertEquals(restoredA, outside.aEid); assertEquals(other, outside.bEid);
                assertEquals(.4f, bodies.get(restoredA).gravityScale, 0);
                assertTrue(shape.contentEquals(f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class)
                        .get(restoredA).shapes.first()));
                assertEquals(f.context.identityRegistry().getStableId(other), f.world.getMapper(CustomPropertiesComponent.class)
                        .get(restoredA).properties.getObjectStableId("other", -1));
                f.history.redo();
            }
            f.history.undo(); f.process();
            assertEquals(2, f.world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
                    games.pixscape.runtime.component.physics.PhysicsJointComponent.class)).getEntities().size());
        }
    }

    @Test public void brokenRelationRefusesCaptureBeforeDeletingOrRenumberingAnything() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService(); layers.addLayerTop("B");
            int root = f.root(0, 0), child = f.sprite(10, 20, 10, 10);
            f.attach(child, root); f.process();
            f.world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId = Integer.MAX_VALUE;
            int cursor = f.history.getCursor(), stable = f.context.identityRegistry().getStableId(root);
            assertThrows(IllegalArgumentException.class, () -> f.history.execute(
                    new DeleteLayerCommand(layers, layers.getLayerEntity(0), null)));
            assertEquals(cursor, f.history.getCursor()); assertEquals(2, layers.count());
            assertEquals(root, f.context.identityRegistry().findByStableId(stable));
            assertEquals(0, f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex);
        }
    }

    private static int joint(AlignServiceTest.Fixture f, int a, int b) {
        int entity = f.world.create();
        var joint = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsJointComponent.class).create(entity);
        joint.aEid = a; joint.bEid = b;
        f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent.class).create(entity);
        f.history.historyIds().ensureForEntity(entity);
        return entity;
    }

    @Test public void invalidJointEndpointRefusesDeletionBeforeMutation() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            int a = f.sprite(0, 0, 10, 10), b = f.sprite(20, 0, 10, 10);
            var bodies = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class);
            bodies.create(a); bodies.create(b);
            int joint = joint(f, a, b);
            f.process();
            f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsJointComponent.class).get(joint).bEid = -1;
            var layers = f.context.layerService();
            int cursor = f.history.getCursor();
            assertThrows(IllegalArgumentException.class, () -> f.history.execute(
                    new DeleteLayerCommand(layers, layers.getLayerEntity(0), null)));
            assertEquals(cursor, f.history.getCursor());
            assertEquals(1, layers.count());
            assertEquals(a, f.context.identityRegistry().findByStableId(f.context.identityRegistry().getStableId(a)));
            assertTrue(f.history.historyIds().historyIdOfEntity(joint) > 0);
        }
    }

    @Test public void deletingHistoricalLayerPreservesTransferredCompositionAndIndices() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            f.context.layerService().addLayerTop("B");
            int root = f.root(10, 20), nested = f.root(3, 4), child = f.sprite(7, 8, 10, 10);
            f.attach(nested, root); f.attach(child, nested); f.process();
            f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 1;
            int stable = f.context.identityRegistry().getStableId(child);
            var layers = f.context.layerService();
            f.history.execute(new DeleteLayerCommand(layers, layers.getLayerEntity(0), null));
            assertEquals(child, f.context.identityRegistry().findByStableId(stable));
            assertEquals(0, layers.getEffectiveLayerIndex(child));
            f.history.undo();
            assertEquals(1, layers.getEffectiveLayerIndex(child));
            assertEquals(0, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
            f.process();
            assertEquals(child, f.context.identityRegistry().findByStableId(stable));
        }
    }

    @Test public void deletingEffectiveLayerRestoresNestedCompositionThroughImmediateCycles() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService(); layers.addLayerTop("B");
            int root = f.root(10, 20), nested = f.root(3, 4), child = f.sprite(7, 8, 10, 10);
            f.attach(nested, root); f.attach(child, nested); f.process();
            f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 1;
            int rootStable = f.context.identityRegistry().getStableId(root);
            int nestedStable = f.context.identityRegistry().getStableId(nested);
            int childStable = f.context.identityRegistry().getStableId(child);
            f.t.get(child).originX = 2; f.t.get(child).originY = 6;
            f.t.get(child).rotationRad = .4f; f.t.get(child).scaleX = -2; f.t.get(child).scaleY = 3;
            f.world.getMapper(EntityIndexComponent.class).get(root).zIndex = 42;
            f.world.getMapper(EntityIndexComponent.class).get(nested).zIndex = 5;
            f.world.getMapper(EntityIndexComponent.class).get(child).zIndex = 9;
            f.history.execute(new DeleteLayerCommand(layers, layers.getLayerEntity(1), null));
            for (int cycle = 0; cycle < 3; cycle++) {
                assertEquals(-1, f.context.identityRegistry().findByStableId(rootStable));
                assertEquals(-1, f.context.identityRegistry().findByStableId(childStable));
                f.history.undo();
                root = f.context.identityRegistry().findByStableId(rootStable);
                nested = f.context.identityRegistry().findByStableId(nestedStable);
                child = f.context.identityRegistry().findByStableId(childStable);
                assertTrue(f.world.getMapper(GameObjectComponent.class).has(root));
                assertEquals(rootStable, f.world.getMapper(GameObjectMemberComponent.class).get(nested).parentStableId);
                assertEquals(nestedStable, f.world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId);
                assertEquals(0, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
                assertEquals(1, layers.getEffectiveLayerIndex(child));
                assertEquals(7, f.t.get(child).x, 0);
                assertEquals(8, f.t.get(child).y, 0);
                assertEquals(2, f.t.get(child).originX, 0);
                assertEquals(6, f.t.get(child).originY, 0);
                assertEquals(.4f, f.t.get(child).rotationRad, 0);
                assertEquals(-2, f.t.get(child).scaleX, 0);
                assertEquals(3, f.t.get(child).scaleY, 0);
                assertEquals(42, f.world.getMapper(EntityIndexComponent.class).get(root).zIndex);
                assertEquals(5, f.world.getMapper(EntityIndexComponent.class).get(nested).zIndex);
                assertEquals(9, f.world.getMapper(EntityIndexComponent.class).get(child).zIndex);
                f.history.redo();
            }
            f.history.undo(); f.process();
            assertEquals(1, layers.getEffectiveLayerIndex(f.context.identityRegistry().findByStableId(childStable)));
        }
    }
}
