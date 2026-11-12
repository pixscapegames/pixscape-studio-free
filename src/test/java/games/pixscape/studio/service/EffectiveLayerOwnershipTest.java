package games.pixscape.studio.service;

import games.pixscape.runtime.component.*;
import games.pixscape.studio.history.commands.AddEntityToGameObjectCommand;
import games.pixscape.studio.history.commands.ChangeLayerIndexCommand;
import games.pixscape.studio.history.commands.RemoveEntityFromGameObjectCommand;
import org.junit.Test;

import static org.junit.Assert.*;

public class EffectiveLayerOwnershipTest {
    @Test
    public void brokenParentAndCycleNeverAuthorizeHistoricalLayer() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            int root = f.root(0, 0), child = f.sprite(10, 0, 10, 10);
            f.attach(child, root);
            var member = f.world.getMapper(GameObjectMemberComponent.class).get(child);
            member.parentStableId = Integer.MAX_VALUE;
            assertEquals(-1, f.context.layerService().getEffectiveLayerIndex(child));
            assertTrue(f.context.layerService().describeEffectiveLayer(child).startsWith("Unavailable"));
            assertTrue(f.selection.isLayerLocked(child));
            assertFalse(f.selection.isSelectableInViewport(child));
            f.selection.selectOnly(child, SelectionService.SelectionSource.TREE);
            assertTrue(f.selection.getSelectionSnapshot().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> new RemoveEntityFromGameObjectCommand(
                    f.world, f.history.historyIds(), f.context.identityRegistry(), child));
            assertEquals(0, f.history.getCursor());
            member.parentStableId = f.context.identityRegistry().getStableId(root);
            f.world.getMapper(GameObjectMemberComponent.class).create(root).parentStableId =
                    f.context.identityRegistry().getStableId(root);
            assertEquals(-1, f.context.layerService().getEffectiveLayerIndex(child));
        }
    }

    @Test
    public void ownershipTransitionsLeaveAuthoredPhysicsUntouched() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            f.context.layerService().addLayerTop("Destination");
            int root = f.root(0, 0), child = f.sprite(10, 20, 12, 12);
            int other = f.sprite(30, 20, 12, 12);
            var bodies = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class);
            var body = bodies.create(child);
            body.gravityScale = .5f;
            bodies.create(other);
            var shapes = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsShapesComponent.class).create(child);
            var physics = new games.pixscape.runtime.service.PhysicsService(f.world, null, f.context.sceneMeta());
            shapes.shapes.add(games.pixscape.runtime.service.PhysicsService.createDefaultShape(physics.allocateNewPhysicsShapeId()));
            var joint = f.world.getMapper(games.pixscape.runtime.component.physics.PhysicsJointComponent.class).create(f.world.create());
            joint.aEid = child;
            joint.bEid = other;
            joint.anchorAx = .25f;
            var json = new com.badlogic.gdx.utils.Json();
            String bodyBefore = json.toJson(body), shapesBefore = json.toJson(shapes), jointBefore = json.toJson(joint);
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child, root, 0));
            var move = new ChangeLayerIndexCommand(f.world, f.history.historyIds());
            move.addEntry(f.history.historyIds().ensureForEntity(root), 0, 1);
            f.history.execute(move);
            f.history.execute(new RemoveEntityFromGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child));
            for (int i = 0; i < 3; i++) f.history.undo();
            for (int i = 0; i < 3; i++) f.history.redo();
            assertEquals(bodyBefore, json.toJson(body));
            assertEquals(shapesBefore, json.toJson(shapes));
            assertEquals(jointBefore, json.toJson(joint));
            assertEquals(10, f.t.get(child).x, .001f);
            assertEquals(20, f.t.get(child).y, .001f);
            assertEquals(1, f.context.layerService().getEffectiveLayerIndex(child));
        }
    }

    @Test
    public void attachmentUsesDestinationLockAndVisibilityBeforeAndAfterPublication() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService();
            layers.addLayerTop("Destination");
            f.process();
            int root = f.root(10, 20), child = f.sprite(50, 60, 12, 12);
            f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 1;
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child, root, 0));
            assertEquals(0, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
            for (boolean published : new boolean[]{false, true}) {
                if (published) {
                    f.process();
                    assertEquals(1, layers.getPublishedEffectiveLayerIndex(child));
                    assertEquals(1, f.composition.state().effectiveLayer[child]);
                }
                layers.setLayerLocked(layers.getLayerEntity(1), true);
                f.selection.selectOnly(child, SelectionService.SelectionSource.TREE);
                assertTrue("Destination lock must apply to the child", f.selection.getSelectionSnapshot().isEmpty());
                layers.setLayerLocked(layers.getLayerEntity(1), false);
                layers.setLayerLocked(layers.getLayerEntity(0), true);
                f.selection.selectOnly(child, SelectionService.SelectionSource.TREE);
                assertEquals(child, f.selection.getSelectionSnapshot().first());
                layers.setLayerVisible(layers.getLayerEntity(1), false);
                assertFalse(f.selection.isSelectableInViewport(child));
                // TREE still permits selecting hidden content, as before.
                f.selection.selectOnly(child, SelectionService.SelectionSource.TREE);
                assertEquals(child, f.selection.getSelectionSnapshot().first());
                layers.setLayerVisible(layers.getLayerEntity(1), true);
                layers.setLayerLocked(layers.getLayerEntity(0), false);
                f.selection.clearSelection();
            }
        }
    }

    @Test
    public void rootTransferChangesNestedOwnershipAndDetachKeepsItAcrossUndoRedo() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService();
            layers.addLayerTop("Destination");
            f.process();
            int root = f.root(10, 0), nested = f.root(20, 0), child = f.sprite(50, 0, 10, 10);
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), nested, root, 0));
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child, nested, 0));
            f.process();
            var move = new ChangeLayerIndexCommand(f.world, f.history.historyIds());
            move.addEntry(f.history.historyIds().ensureForEntity(root), 0, 1);
            f.history.execute(move);
            layers.setLayerLocked(layers.getLayerEntity(0), true);
            f.selection.selectOnly(child, SelectionService.SelectionSource.TREE);
            assertEquals(child, f.selection.getSelectionSnapshot().first());
            f.history.execute(new RemoveEntityFromGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child));
            assertEquals(1, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
            assertEquals(50, f.t.get(child).x, .01f);
            for (int i = 0; i < 3; i++) {
                f.history.undo(); // Restore the stored 0, but membership remains effective 1.
                assertEquals(0, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
                assertFalse(f.selection.isLayerLocked(child));
                f.history.undo();
                assertTrue(f.selection.isLayerLocked(child));
                f.history.redo();
                assertFalse(f.selection.isLayerLocked(child));
                f.history.redo();
                assertEquals(1, f.world.getMapper(EntityIndexComponent.class).get(child).layerIndex);
            }
            f.process();
            assertFalse(f.selection.isLayerLocked(child));
        }
    }

    @Test
    public void lockedEffectiveLayerRejectsPlacementWithoutHistoryOrTransformChanges() throws Exception {
        try (var f = new AlignServiceTest.Fixture()) {
            var layers = f.context.layerService();
            layers.addLayerTop("Destination");
            f.process();
            int root = f.root(10, 0), child = f.sprite(50, 0, 10, 10), target = f.sprite(0, 0, 10, 10);
            f.world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 1;
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.context.identityRegistry(), child, root, 0));
            f.select(child, target);
            layers.setLayerLocked(layers.getLayerEntity(1), true);
            int cursor = f.history.getCursor();
            float x = f.t.get(child).x;
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertEquals(cursor, f.history.getCursor());
            assertEquals(x, f.t.get(child).x, 0);
        }
    }
}
