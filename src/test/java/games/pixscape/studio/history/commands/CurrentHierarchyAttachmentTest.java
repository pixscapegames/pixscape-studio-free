package games.pixscape.studio.history.commands;

import com.artemis.Component;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Command sequences against real published topology, with no intermediate ECS synchronization.
 */
public class CurrentHierarchyAttachmentTest {
    @Test
    public void detachThenAttachToSameOrDifferentParentPreservesPoseAndIdentities() {
        for (boolean sameParent : new boolean[]{true, false}) {
            try (Fixture f = new Fixture()) {
                int first = f.root(2), second = f.root(1.5f), child = f.entity(false, 1);
                f.t(first).x = 8;
                f.t(first).rotationRad = 0.4f;
                f.t(first).originX = 3;
                f.t(second).x = -12;
                f.t(second).rotationRad = -0.3f;
                f.t(second).originY = 7;
                f.t(child).x = 42;
                f.t(child).y = 23;
                f.t(child).rotationRad = 0.7f;
                f.t(child).originX = 4;
                f.t(child).originY = -5;
                f.t(child).scaleY = -1.2f;
                TransformComponent pose = GameObjectHierarchyCommandSupport.copy(f.t(child));
                int stable = f.ids.getStableId(child);
                long historyId = f.history.historyIds().ensureForEntity(child);
                f.attach(child, first);
                f.process();
                for (int i = 0; i < 3; i++) {
                    f.detach(child);
                    f.attach(child, sameParent ? first : second);
                    f.assertPose(child, pose);
                    assertEquals(stable, f.ids.getStableId(child));
                    assertEquals(child, f.ids.findByStableId(stable));
                    assertEquals(historyId, f.history.historyIds().historyIdOfEntity(child));
                    f.process();
                    f.assertPublishedPose(child, pose);
                }
            }
        }
    }

    @Test
    public void detachUndoAndPublishedAttachUndoRedoCanRepeatWithoutAnEcsStep() {
        try (Fixture f = new Fixture()) {
            int parent = f.root(1), child = f.entity(false, 1);
            f.t(child).x = 18;
            f.attach(child, parent);
            f.process();
            TransformComponent pose = GameObjectHierarchyCommandSupport.copy(f.t(child));
            for (int i = 0; i < 3; i++) {
                f.detach(child);
                f.history.undo();
                assertEquals(f.ids.getStableId(parent), f.member(child).parentStableId);
                assertEquals(1, f.history.getCursor());
                f.history.redo();
                f.history.undo();
                f.history.undo();
                f.history.redo();
                f.assertPose(child, pose);
                f.process();
                f.assertPublishedPose(child, pose);
            }
        }
    }

    @Test
    public void freshIndexedHiddenPhysicsDescendantRejectsScaledAttachmentBeforePublication() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), scaled = f.root(2);
            f.process();
            int body = f.entity(false, 1); // Indexed, not in any published subscription yet.
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.world.getMapper(VisibilityComponent.class).create(body).visible = false;
            f.attach(body, group);
            f.assertRejected(() -> f.attach(group, scaled));
            f.process();
            f.assertRejected(() -> f.attach(group, scaled));
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(group));
            assertEquals(1, f.t(group).scaleX, 0);
        }
    }

    @Test
    public void recentBodyComponentAdditionAndRemovalAreReadWithoutPublication() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), body = f.entity(false, 1), scaled = f.root(2);
            f.attach(body, group);
            f.process();
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.assertRejected(() -> f.attach(group, scaled));
            f.world.getMapper(PhysicsBodyComponent.class).remove(body);
            f.attach(group, scaled);
            f.process();
            assertEquals(f.ids.getStableId(scaled), f.member(group).parentStableId);
        }
    }

    @Test
    public void detachedPhysicsDescendantNoLongerRestrictsItsFormerGroup() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), body = f.entity(false, 1), scaled = f.root(2);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.attach(body, group);
            f.process();
            f.detach(body);
            f.attach(group, scaled);
            f.process();
            assertNull(f.member(body));
            assertEquals(f.ids.getStableId(scaled), f.member(group).parentStableId);
        }
    }

    @Test
    public void studioDeletionExcludesPendingRemovedBodyFromCurrentRelations() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), body = f.entity(false, 1), scaled = f.root(2);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.attach(body, group);
            f.process();
            int bodyStableId = f.ids.getStableId(body);
            f.history.execute(new DeleteGameObjectHierarchyCommand(f.world, f.history.historyIds(),
                    new IntArray(new int[]{body}), null));
            assertEquals(-1, f.ids.findByStableId(bodyStableId));
            assertNull(CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(f.world, f.ids)
                    .get(f.ids.getStableId(group)));
            f.attach(group, scaled);
            f.process();
            assertEquals(f.ids.getStableId(scaled), f.member(group).parentStableId);
        }
    }

    @Test
    public void executeAndRedoRevalidatePhysicsWithoutChangingHistoryOnFailure() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), body = f.entity(false, 1), scaled = f.root(2);
            f.attach(body, group);
            f.process();
            AddEntityToGameObjectCommand prepared = f.command(group, scaled);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.assertRejected(() -> f.history.execute(prepared));
            assertNull(f.member(group));
            assertEquals(1, f.t(group).scaleX, 0);
            f.world.getMapper(PhysicsBodyComponent.class).remove(body);
            f.history.execute(prepared);
            f.process();
            f.history.undo();
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.assertRejected(f.history::redo);
            assertNull(f.member(group));
            assertEquals(1, f.t(group).scaleX, 0);
            f.world.getMapper(PhysicsBodyComponent.class).remove(body);
            f.history.redo();
            f.process();
        }
    }

    @Test
    public void detachUndoRevalidatesAndPreservesUndoEntryOnFailure() {
        try (Fixture f = new Fixture()) {
            int group = f.root(1), body = f.entity(false, 1), scaled = f.root(2);
            f.attach(body, group);
            f.attach(group, scaled);
            f.process();
            f.detach(group);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.assertRejected(f.history::undo);
            assertNull(f.member(group));
            f.world.getMapper(PhysicsBodyComponent.class).remove(body);
            f.history.undo();
            f.process();
            assertEquals(f.ids.getStableId(scaled), f.member(group).parentStableId);
        }
    }

    @Test
    public void currentNonUnitAncestorsAndInternalPhysicsFramesAreRejected() {
        try (Fixture f = new Fixture()) {
            int outer = f.root(2), parent = f.root(1), group = f.root(1), body = f.entity(false, 1);
            f.attach(parent, outer);
            f.world.getMapper(PhysicsBodyComponent.class).create(body);
            f.attach(body, group);
            f.process();
            f.assertRejected(() -> f.attach(group, parent));
            f.t(group).scaleX = f.t(group).scaleY = 2;
            int unitParent = f.root(1);
            f.assertRejected(() -> f.attach(group, unitParent));
            assertNull(f.member(group));
        }
    }

    @Test
    public void invalidAncestorsAndCyclesFailWithControlledRejection() {
        try (Fixture f = new Fixture()) {
            int parent = f.root(1), child = f.root(1), leaf = f.entity(false, 1);
            f.process();
            f.assertRejected(() -> f.attach(parent, parent));
            f.world.getMapper(ParticleEmitterComponent.class).create(leaf);
            f.assertRejected(() -> f.attach(child, leaf));
            f.world.getMapper(ParticleEmitterComponent.class).remove(leaf);
            f.assertRejected(() -> f.attach(child, -1));
            f.world.getMapper(GameObjectMemberComponent.class).create(parent).parentStableId = Integer.MAX_VALUE;
            assertTrue(CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(f.world, f.ids)
                    .get(Integer.MAX_VALUE).contains(parent));
            f.assertRejected(() -> f.attach(child, parent));
            f.member(parent).parentStableId = f.ids.getStableId(leaf);
            f.assertRejected(() -> f.attach(child, parent));
            f.member(parent).parentStableId = f.ids.getStableId(parent);
            f.assertRejected(() -> f.attach(child, parent));
            f.world.getMapper(GameObjectMemberComponent.class).remove(parent);
            f.attach(child, parent);
            f.assertRejected(() -> f.attach(child, parent));
            f.assertRejected(() -> f.attach(parent, child));
            assertNull(f.member(parent));
            f.process();
        }
    }

    @Test
    public void missingCoreAndUnindexedIdentityAreRejectedBeforeMutation() {
        try (Fixture f = new Fixture()) {
            int parent = f.root(1), child = f.entity(false, 1), ancestor = f.root(1);
            f.process();
            IdentityRegistry.unindexEntityImmediately(f.world, child);
            f.assertRejected(() -> f.attach(child, parent));
            f.ids.ensureStableId(child);
            f.world.getMapper(GameObjectMemberComponent.class).create(parent).parentStableId = f.ids.getStableId(ancestor);
            f.world.getMapper(TransformComponent.class).remove(ancestor);
            f.assertRejected(() -> f.attach(child, parent));
            assertNull(f.member(child));
        }
    }

    @Test
    public void existingCompositionAndScaleRestrictionsRemainEnforced() {
        for (Class<? extends Component> type : java.util.List.of(TiledLayerComponent.class,
                ParticleEmitterComponent.class, SpatialBlocksComponent.class, SpatialShapesComponent.class)) {
            try (Fixture f = new Fixture()) {
                int parent = f.root(1), child = f.entity(false, 1);
                f.world.getMapper(type).create(child);
                f.process();
                f.assertRejected(() -> f.attach(child, parent));
                assertNull(f.member(child));
            }
        }
        try (Fixture f = new Fixture()) {
            int parent = f.root(1), child = f.root(1);
            f.process();
            f.t(parent).scaleX = 2;
            f.assertRejected(() -> f.attach(child, parent));
            f.t(parent).scaleX = 1;
            f.t(child).scaleX = f.t(child).scaleY = -1;
            f.assertRejected(() -> f.attach(child, parent));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(16);
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), hierarchy).build());
        final IdentityRegistry ids = new IdentityRegistry();
        final HistoryManager history = new HistoryManager(100);

        Fixture() {
            ids.bind(world, new SceneMetaRuntime());
        }

        int root(float scale) {
            return entity(true, scale);
        }

        int entity(boolean root, float scale) {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity).scaleX = scale;
            t(entity).scaleY = scale;
            world.getMapper(EntityIndexComponent.class).create(entity);
            if (root) world.getMapper(GameObjectComponent.class).create(entity);
            ids.ensureStableId(entity);
            return entity;
        }

        TransformComponent t(int entity) {
            return world.getMapper(TransformComponent.class).get(entity);
        }

        GameObjectMemberComponent member(int entity) {
            return world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
        }

        AddEntityToGameObjectCommand command(int child, int parent) {
            return new AddEntityToGameObjectCommand(world, history.historyIds(), ids, child, parent, 0);
        }

        void attach(int child, int parent) {
            history.execute(command(child, parent));
        }

        void detach(int child) {
            history.execute(new RemoveEntityFromGameObjectCommand(world, history.historyIds(), ids, child));
        }

        void process() {
            world.process();
        }

        void assertRejected(Runnable operation) {
            int cursor = history.getCursor();
            long revision = history.getMutationRevision();
            String undo = history.peekUndoLabel(), redo = history.peekRedoLabel();
            assertThrows(IllegalArgumentException.class, operation::run);
            assertEquals(cursor, history.getCursor());
            assertEquals(revision, history.getMutationRevision());
            assertEquals(undo, history.peekUndoLabel());
            assertEquals(redo, history.peekRedoLabel());
        }

        void assertPose(int entity, TransformComponent expected) {
            TransformComponent actual = GameObjectHierarchyCommandSupport.worldTransform(world, ids, entity);
            assertEquals(expected.x, actual.x, 0.02f);
            assertEquals(expected.y, actual.y, 0.02f);
            assertEquals(expected.rotationRad, actual.rotationRad, 0.0001f);
            assertEquals(expected.scaleX, actual.scaleX, 0.0001f);
            assertEquals(expected.scaleY, actual.scaleY, 0.0001f);
            assertEquals(expected.originX, actual.originX, 0);
            assertEquals(expected.originY, actual.originY, 0);
        }

        void assertPublishedPose(int entity, TransformComponent expected) {
            assertPose(entity, expected);
            var state = hierarchy.worldTransforms();
            assertTrue(state.isResolved(entity));
            assertEquals(expected.x, state.x[entity], 0.02f);
            assertEquals(expected.y, state.y[entity], 0.02f);
            assertEquals(expected.rotationRad, state.rotationRad[entity], 0.0001f);
            assertEquals(expected.scaleX, state.scaleX[entity], 0.0001f);
            assertEquals(expected.scaleY, state.scaleY[entity], 0.0001f);
        }

        @Override
        public void close() {
            ids.bind(null, null);
            world.dispose();
            EventFlow.i().flush();
        }
    }
}
