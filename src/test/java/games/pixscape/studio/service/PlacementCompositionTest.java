package games.pixscape.studio.service;

import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.studio.history.commands.AddEntityToGameObjectCommand;
import games.pixscape.studio.history.commands.DeleteGameObjectHierarchyCommand;
import games.pixscape.studio.history.commands.RemoveEntityFromGameObjectCommand;
import org.junit.Test;

import static org.junit.Assert.*;

/** Real command boundaries: deliberately no ECS step between mutation and placement. */
public class PlacementCompositionTest {
    @Test public void unrelatedInvalidRelationStillRejectsCompositionButNotLeafPlacement() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0), child = f.sprite(0, 0, 10, 10);
            int reference = f.sprite(20, 0, 10, 10), leaf = f.sprite(300, 0, 10, 10);
            f.attach(child, root);
            f.select(root, reference);
            int unrelated = f.sprite(500, 0, 10, 10);
            f.world.getMapper(GameObjectMemberComponent.class).create(unrelated).parentStableId = Integer.MAX_VALUE;
            assertFalse(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertEquals(0, f.history.getCursor());
            // Leaf-only preparation must not collect unrelated scene relations.
            f.selection.clearSelection(); f.selection.selectAdd(reference); f.selection.selectAdd(leaf);
            assertTrue(f.align.isPlacementAvailable());
            f.align.alignLeft();
            assertEquals(20, f.t.get(leaf).x, 0.03f);
            f.world.getMapper(GameObjectMemberComponent.class).remove(unrelated);
            f.process();
            assertEquals(f.bounds(reference)[0], f.bounds(leaf)[0], 0.03f);
        }
    }

    @Test public void selectedAncestorMovesOnceWithoutHidingPhysicsFromAttachValidation() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0), body = f.sprite(50, 0, 10, 10);
            int visible = f.sprite(5, 0, 10, 10), reference = f.sprite(0, 0, 10, 10);
            int scaled = f.root(300, 0);
            f.t.get(scaled).scaleX = f.t.get(scaled).scaleY = 2;
            f.attach(body, root); f.attach(visible, root);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.world.getMapper(VisibilityComponent.class).get(body).visible = false;
            f.select(reference, body, root);
            f.align.alignLeft();
            assertEquals(-5, f.t.get(root).x, 0.03f);
            assertEquals(50, f.t.get(body).x, 0);
            assertEquals(5, f.t.get(visible).x, 0);
            assertEquals(1, f.history.getCursor());
            assertThrows(IllegalArgumentException.class, () -> new AddEntityToGameObjectCommand(
                    f.world, f.history.historyIds(), f.context.identityRegistry(), root, scaled, 0));
            assertEquals(1, f.history.getCursor());
            f.process();
            assertEquals(f.bounds(reference)[0], f.bounds(root)[0], 0.03f);
        }
    }

    @Test public void deletedMemberStopsContributingBeforeEcsPublication() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0), distant = f.sprite(100, 0, 10, 10);
            f.attach(f.sprite(0, 0, 10, 10), root); f.attach(distant, root);
            int target = f.sprite(300, 0, 10, 10);
            f.select(root, target);
            f.history.execute(new DeleteGameObjectHierarchyCommand(f.world, f.history.historyIds(),
                    new IntArray(new int[]{distant}), null));
            f.align.alignRight();
            assertEquals(100, f.t.get(target).x, 0.03f);
            f.process();
            assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
        }
    }

    @Test public void newlyIndexedAttachmentIsIncludedWithoutPublishingAnEcsStep() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0), target = f.sprite(300, 0, 10, 10);
            f.attach(f.sprite(0, 0, 10, 10), root);
            f.select(root, target);
            int child = f.sprite(200, 0, 10, 10);
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(),
                    f.context.identityRegistry(), child, root, 1));
            f.align.alignRight();
            assertEquals(200, f.t.get(target).x, 0.03f);
            // Placement must not secretly run the ECS or publish new Runtime geometry.
            assertEquals(110, f.bounds(root)[2], 0.03f);
            f.process();
            assertEquals(210, f.bounds(root)[2], 0.03f);
            assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
        }
    }

    @Test public void publishedAndCommandAdmissionAgreeAfterCompositionChanges() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0), child = f.sprite(0, 0, 10, 10);
            f.attach(child, root);
            f.attach(f.sprite(50, 0, 10, 10), root);
            f.select(root, f.sprite(300, 0, 10, 10));
            assertTrue(f.align.isPlacementAvailableAfterEcsStep());
            f.changeWidth(child, 0); f.process();
            assertFalse(f.align.isPlacementAvailable());
            assertFalse(f.align.isPlacementAvailableAfterEcsStep());
            f.history.undo(); f.process();
            assertTrue(f.align.isPlacementAvailable());
            assertTrue(f.align.isPlacementAvailableAfterEcsStep());
            f.world.getMapper(VisibilityComponent.class).get(child).visible = false;
            f.process();
            assertEquals(f.align.isPlacementAvailable(), f.align.isPlacementAvailableAfterEcsStep());
        }
    }

    @Test public void detachThenPlaceUsesRemainingContent() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0);
            f.attach(f.sprite(0, 0, 10, 10), root);
            int child = f.sprite(100, 0, 10, 10);
            f.attach(child, root);
            int target = f.sprite(300, 0, 10, 10);
            f.select(root, target);
            f.history.execute(new RemoveEntityFromGameObjectCommand(
                    f.world, f.history.historyIds(), f.context.identityRegistry(), child));
            f.align.alignRight();
            assertEquals(100, f.t.get(target).x, 0.03f);
            assertEquals(200, f.t.get(child).x, 0.03f);
            f.process();
            assertEquals(110, f.bounds(root)[2], 0.03f);
            assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
        }
    }

    @Test public void detachAndAttachThenPlaceUsesNewParentContent() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int oldRoot = f.root(100, 0), newRoot = f.root(0, 0);
            f.attach(f.sprite(0, 0, 10, 10), oldRoot);
            f.attach(f.sprite(0, 0, 10, 10), newRoot);
            int child = f.sprite(100, 0, 10, 10);
            f.attach(child, oldRoot);
            int target = f.sprite(300, 0, 10, 10);
            f.select(newRoot, target);
            f.history.execute(new RemoveEntityFromGameObjectCommand(
                    f.world, f.history.historyIds(), f.context.identityRegistry(), child));
            f.history.execute(new AddEntityToGameObjectCommand(
                    f.world, f.history.historyIds(), f.context.identityRegistry(), child, newRoot, 1));
            f.align.alignRight();
            assertEquals(200, f.t.get(target).x, 0.03f);
            f.process();
            assertEquals(210, f.bounds(newRoot)[2], 0.03f);
            assertEquals(f.bounds(newRoot)[2], f.bounds(target)[2], 0.03f);
        }
    }

    @Test public void visibilityEditThenPlaceUsesVisibleContent() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
            int root = f.root(100, 0);
            f.attach(f.sprite(0, 0, 10, 10), root);
            int child = f.sprite(100, 0, 10, 10);
            f.attach(child, root);
            VisibilityComponent visibility = f.world.getMapper(VisibilityComponent.class).create(child);
            int target = f.sprite(300, 0, 10, 10);
            f.select(root, target);
            // EntityProperties' CheckBoxBinder writes this field directly, without an event/history command.
            visibility.visible = false;
            f.align.alignRight();
            assertEquals(100, f.t.get(target).x, 0.03f);
            f.process();
            assertEquals(110, f.bounds(root)[2], 0.03f);
            assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
        }
    }

    @Test public void undoAndRedoCompositionThenPlaceReadCurrentMembership() throws Exception {
        for (boolean redo : new boolean[]{false, true}) {
            try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture()) {
                int root = f.root(100, 0);
                f.attach(f.sprite(0, 0, 10, 10), root);
                int child = f.sprite(100, 0, 10, 10);
                f.attach(child, root);
                int target = f.sprite(300, 0, 10, 10);
                f.select(root, target);
                f.history.execute(new RemoveEntityFromGameObjectCommand(
                        f.world, f.history.historyIds(), f.context.identityRegistry(), child));
                f.process();
                f.history.undo();
                if (redo) { f.process(); f.history.redo(); }
                f.align.alignRight();
                assertEquals(redo ? 100 : 200, f.t.get(target).x, 0.03f);
                f.process();
                assertEquals(redo ? 110 : 210, f.bounds(root)[2], 0.03f);
                assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
                f.history.undo();
                assertEquals(300, f.t.get(target).x, 0.03f);
                f.history.redo();
                assertEquals(redo ? 100 : 200, f.t.get(target).x, 0.03f);
            }
        }
    }
}
