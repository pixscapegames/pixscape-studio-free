package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.initializer.GameObjectRootInitializer;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class MoveItemsCommandTest {
    @Test public void detachmentMeasuresPositionAgainstResolvedRuntimeInsteadOfPreparationHelper() {
        for (float distance : new float[]{1000f, 10000f}) try (F f = new F()) {
            int outer = f.root(0, 2), inner = f.root(0, 1), child = f.sprite(0, 0);
            f.attach(inner, outer, 1); f.attach(child, inner, 0);
            for (int entity : new int[]{outer, inner, child}) {
                f.t(entity).x = f.t(entity).y = f.t(entity).originX = f.t(entity).originY = 0;
            }
            f.t(outer).rotationRad = f.t(inner).rotationRad = .3f;
            f.t(child).x = distance;
            f.publish();
            Vector2 before = f.resolvedPosition(child);
            assertEquals(.825239563f * distance, before.x, .002f);
            assertEquals(.564782776f * distance, before.y, .002f);
            var authoredBefore = ItemMovePreparation.Pose.read(f.t(child));
            int stable = f.stable(child), next = f.meta.nextEntityStableId;
            var plan = f.prepare(1, 0, 0, child);
            for (int i = 0; i < 8; i++) {
                assertTrue(plan.isCurrent());
                assertEquals(authoredBefore, ItemMovePreparation.Pose.read(f.t(child)));
                assertEquals(next, f.meta.nextEntityStableId);
            }
            f.execute(plan);
            var authoredAfter = ItemMovePreparation.Pose.read(f.t(child));
            assertEquals(before, f.resolvedPosition(child)); // No publication is forced by preparation/command.
            f.publish();
            Vector2 after = f.resolvedPosition(child);
            assertEquals(1, f.context.historyManager().getCursor());
            // Characterizes these two .3-radian fixtures, not a general world/pixel error bound.
            // Measured drift: .19170812 at 1000 units; 1.917622 at 10000 units.
            assertTrue("Runtime position drift: " + before.dst(after), before.dst(after) <= distance * .0002f);
            for (int i = 0; i < 4; i++) {
                f.context.historyManager().undo();
                assertEquals(authoredBefore, ItemMovePreparation.Pose.read(f.t(child)));
                assertEquals(f.stable(inner), f.parent(child));
                if (i % 2 == 0) { f.publish(); assertEquals(before, f.resolvedPosition(child)); }
                f.context.historyManager().redo();
                assertEquals(authoredAfter, ItemMovePreparation.Pose.read(f.t(child)));
                assertEquals(0, f.parent(child));
                if (i % 2 == 0) { f.publish(); assertEquals(after, f.resolvedPosition(child)); }
            }
            f.publish(); assertEquals(after, f.resolvedPosition(child));
            assertEquals(stable, f.stable(child)); assertEquals(next, f.meta.nextEntityStableId);
        }
    }

    @Test public void detachedGroupMeasuresDescendantPositionAgainstResolvedRuntime() {
        try (F f = new F()) {
            int outer = f.root(0, 2), group = f.root(0, 1), left = f.sprite(0, 1), right = f.sprite(0, 0);
            f.attach(group, outer, 1); f.attach(left, group, 1); f.attach(right, group, 0);
            for (int entity : new int[]{outer, group, left, right}) {
                f.t(entity).x = f.t(entity).y = f.t(entity).originX = f.t(entity).originY = 0;
            }
            f.t(outer).rotationRad = f.t(group).rotationRad = .3f;
            f.t(left).x = -1000; f.t(right).x = 1000;
            f.publish();
            Vector2 beforeLeft = f.resolvedPosition(left), beforeRight = f.resolvedPosition(right);
            var localLeft = ItemMovePreparation.Pose.read(f.t(left));
            var localRight = ItemMovePreparation.Pose.read(f.t(right));
            f.execute(f.prepare(1, 0, 0, group));
            f.publish();
            Vector2 afterLeft = f.resolvedPosition(left), afterRight = f.resolvedPosition(right);
            assertEquals(new Vector2(), f.resolvedPosition(group));
            // The unchanged pivot does not imply unchanged descendant positions: the two ends
            // drift in opposite directions. Translating only the group cannot correct both.
            assertTrue(beforeLeft.dst(afterLeft) <= .2f);
            assertTrue("Runtime descendant drift: " + beforeRight.dst(afterRight), beforeRight.dst(afterRight) <= .2f);
            assertEquals(localLeft, ItemMovePreparation.Pose.read(f.t(left)));
            assertEquals(localRight, ItemMovePreparation.Pose.read(f.t(right)));
            assertEquals(0, f.index(left).layerIndex); assertEquals(0, f.index(right).layerIndex);
            assertEquals(1f, f.t(group).scaleX, 0f); assertEquals(1f, f.t(group).scaleY, 0f);
            for (int i = 0; i < 3; i++) {
                f.context.historyManager().undo(); f.context.historyManager().redo(); // No intermediate publication.
            }
            f.publish();
            assertEquals(afterLeft, f.resolvedPosition(left)); assertEquals(afterRight, f.resolvedPosition(right));
            f.context.historyManager().undo(); f.publish();
            assertEquals(beforeLeft, f.resolvedPosition(left)); assertEquals(beforeRight, f.resolvedPosition(right));
        }
    }

    @Test public void nontrivialPreparationAndCommandConstructionAreReadOnly() {
        try (F f = new F()) {
            int p = f.root(0, 10), a = f.sprite(0, 25), q = f.root(1, 50);
            f.attach(a, p, 25); f.t(q).rotationRad = .3f; f.t(q).originX = 4;
            var local = ItemMovePreparation.Pose.read(f.t(a));
            int next = f.meta.nextEntityStableId;
            var plan = f.prepare(1, q, 0, a);
            var command = plan.command(); assertNotNull(command);
            assertFalse(plan.isNoop());
            assertEquals(local, ItemMovePreparation.Pose.read(f.t(a)));
            assertEquals(f.stable(p), f.parent(a)); assertEquals(25, f.z(a)); assertEquals(50, f.z(q));
            assertEquals(next, f.meta.nextEntityStableId); assertEquals(0, f.context.historyManager().getCursor());
            assertEquals(-1, f.context.historyManager().historyIds().historyIdOfEntity(a));
            var change = plan.changes().stream().filter(c -> c.before().stableId() == f.stable(a)).findFirst().orElseThrow();
            assertEquals(f.stable(f.context.layerService().getLayerEntity(0)), change.beforeEffectiveLayerStableId());
            assertEquals(f.stable(f.context.layerService().getLayerEntity(1)), change.afterEffectiveLayerStableId());
        }
    }
    @Test public void preparationAndNoopPreserveSparseZIdentitiesSelectionAndEvents() {
        try (F f = new F()) {
            int a = f.sprite(0, 70), b = f.sprite(0, -20);
            f.context.selectionService().selectOnly(a); EventFlow.i().flush();
            int cursor = f.context.historyManager().getCursor(), next = f.meta.nextEntityStableId;
            AtomicInteger events = new AtomicInteger();
            EventFlow.Listener<EventFlow.EntityZOrderChanged> listener = event -> events.incrementAndGet();
            EventFlow.i().subscribe(EventFlow.EntityZOrderChanged.class, listener);
            try {
                var plan = f.prepare(0, 0, 0, a);
                assertTrue(plan.isNoop()); assertTrue(plan.changes().isEmpty());
                f.context.historyManager().execute(plan.command()); EventFlow.i().flush();
                assertEquals(cursor, f.context.historyManager().getCursor());
                assertEquals(0, events.get()); assertEquals(next, f.meta.nextEntityStableId);
                assertEquals(-1, f.context.historyManager().historyIds().historyIdOfEntity(a));
                assertEquals(70, f.z(a)); assertEquals(-20, f.z(b));
                assertEquals(a, f.context.selectionService().getFirstSelectedEntityId());
            } finally { EventFlow.i().unsubscribe(EventFlow.EntityZOrderChanged.class, listener); }
        }
    }

    @Test public void insertsAtBeginningMiddleAndEndAfterSourceRemovalWithExactUndo() {
        for (int slot = 0; slot < 3; slot++) try (F f = new F()) {
            int a = f.sprite(0, 80), b = f.sprite(0, 20), c = f.sprite(0, 20);
            // Root tie-break is the existing reverse ECS order: a,c,b.
            var plan = f.prepare(0, 0, slot, b);
            f.execute(plan);
            List<Integer> expected = new java.util.ArrayList<>(List.of(a, c)); expected.add(slot, b);
            assertEquals(expected, f.order(0));
            if (slot != 2) {
                assertEquals(1, f.context.historyManager().getCursor());
                for (int i = 0; i < 3; i++) {
                    f.context.historyManager().undo(); assertEquals(80, f.z(a)); assertEquals(20, f.z(b)); assertEquals(20, f.z(c));
                    f.context.historyManager().redo(); assertEquals(expected, f.order(0));
                }
                f.publish(); assertEquals(expected, f.order(0));
            } else assertTrue(plan.isNoop());
        }
        try (F f = new F()) {
            int a = f.sprite(0, 9), b = f.sprite(0, 8), c = f.sprite(0, 7), d = f.sprite(0, 6);
            f.execute(f.prepare(0, 0, 2, b, a)); // Two remaining peers: insert at their end.
            assertEquals(List.of(c, d, a, b), f.order(0));
        }
    }

    @Test public void reparentsMultipleParentsInTreeOrderAndPreservesSignedWorldPose() {
        try (F f = new F()) {
            int p = f.root(0, 10), nested = f.root(0, 2), q = f.root(1, 10);
            int a = f.sprite(0, 8), b = f.sprite(0, 2), existing = f.sprite(1, 30);
            f.attach(nested, p, 2); f.attach(a, p, 8); f.attach(b, nested, 2); f.attach(existing, q, 30);
            f.t(p).x = 21; f.t(p).originX = 3; f.t(p).rotationRad = .3f; f.t(p).scaleX = f.t(p).scaleY = 2;
            f.t(q).y = -23; f.t(q).originY = 5; f.t(q).rotationRad = -.4f; f.t(q).scaleX = f.t(q).scaleY = 1.5f;
            f.t(a).originX = 7; f.t(a).scaleX = -2; f.t(a).scaleY = 3;
            f.publish();
            Vector2 runtimeA = f.resolvedPosition(a), runtimeB = f.resolvedPosition(b);
            var aw = f.pose(a); var bw = f.pose(b);
            var plan = f.prepare(1, q, 1, b, a);
            assertEquals(List.of(f.stable(a), f.stable(b)), plan.sources());
            f.execute(plan);
            assertEquals(f.stable(q), f.parent(a)); assertEquals(f.stable(q), f.parent(b));
            assertTrue(aw.closeTo(f.pose(a))); assertTrue(bw.closeTo(f.pose(b)));
            assertEquals(0, f.index(a).layerIndex); assertEquals(0, f.index(b).layerIndex);
            assertTrue(f.z(existing) > f.z(a)); assertTrue(f.z(a) > f.z(b));
            f.publish(); assertTrue(aw.closeTo(f.pose(a)));
            // Independent Runtime oracle, with a fixture-specific world-unit drift allowance.
            assertTrue(runtimeA.dst(f.resolvedPosition(a)) <= .05f);
            assertTrue(runtimeB.dst(f.resolvedPosition(b)) <= .05f);
            f.context.historyManager().undo();
            assertEquals(f.stable(p), f.parent(a)); assertEquals(f.stable(nested), f.parent(b));
            assertEquals(8, f.z(a)); assertEquals(2, f.z(b));
            f.context.historyManager().redo(); assertTrue(bw.closeTo(f.pose(b)));
            f.execute(f.prepare(0, 0, 0, b));
            assertEquals(0, f.parent(b)); assertEquals(0, f.index(b).layerIndex); assertTrue(bw.closeTo(f.pose(b)));
        }
    }

    @Test public void ancestorSelectionMovesItsWholeSubtreeOnceAndSiblingReorderUsesFrontmostFirst() {
        try (F f = new F()) {
            int p = f.root(0, 2), child = f.sprite(0, 40), sibling = f.sprite(0, 40);
            f.attach(child, p, 40); f.attach(sibling, p, 40);
            var plan = f.prepare(1, 0, 0, child, p, sibling);
            assertEquals(List.of(f.stable(p)), plan.sources());
            f.execute(plan); assertEquals(1, f.index(p).layerIndex); assertEquals(0, f.index(child).layerIndex);
            f.execute(f.prepare(1, p, 0, child));
            assertTrue(f.z(child) > f.z(sibling));
            f.context.historyManager().undo(); assertEquals(40, f.z(child)); assertEquals(40, f.z(sibling));
            f.context.historyManager().undo(); assertEquals(0, f.index(p).layerIndex);
        }
    }

    @Test public void rejectsWholeBatchForCyclesLocksUnsupportedMembersAndSpatialMismatch() {
        try (F f = new F()) {
            int p = f.root(0, 0), nested = f.root(0, 0), good = f.sprite(0, 3), bad = f.sprite(0, 2);
            f.attach(nested, p, 0);
            assertFalse(f.result(0, nested, 0, p).accepted());
            f.world.getMapper(ParticleEmitterComponent.class).create(bad);
            assertFalse(f.result(0, p, 0, good, bad).accepted()); assertEquals(0, f.parent(good));
            f.world.getMapper(ParticleEmitterComponent.class).remove(bad);
            f.world.getMapper(TiledLayerComponent.class).create(bad);
            assertFalse(f.result(0, p, 0, good, bad).accepted());
            assertTrue(f.result(1, 0, 0, bad).accepted()); // Map remains an atomic root.
            f.context.layerService().meta(0).locked = true;
            assertFalse(f.result(1, 0, 0, nested).accepted());
            f.context.layerService().meta(0).locked = false;
            f.context.layerService().meta(1).locked = true;
            assertFalse(f.result(1, 0, 0, good).accepted());
            f.context.layerService().meta(1).locked = false;
            f.world.getMapper(SpatialHeightComponent.class).create(good);
            assertFalse(f.result(1, 0, 0, good).accepted());
            assertTrue(f.world.getMapper(SpatialHeightComponent.class).has(good));
            assertEquals(0, f.context.historyManager().getCursor());
        }
    }

    @Test public void refusesStalePreparationWithoutPartialMutationOrHistory() {
        for (int scenario = 0; scenario < 4; scenario++) try (F f = new F()) {
            int a = f.sprite(0, 9), b = f.sprite(0, 3), p = f.root(1, 0);
            var plan = f.prepare(1, p, 0, a, b);
            if (scenario == 0) f.t(b).x++;
            if (scenario == 1) f.context.layerService().meta(1).locked = true;
            if (scenario == 2) { games.pixscape.runtime.service.IdentityRegistry.unindexEntityImmediately(f.world, b); f.world.delete(b); }
            if (scenario == 3) f.sprite(1, 7); // Does not change the parent's child list: change the intended parent instead.
            if (scenario == 3) f.t(p).y++;
            assertFalse(plan.isCurrent()); f.execute(plan);
            assertEquals(0, f.parent(a)); assertEquals(9, f.z(a)); assertEquals(0, f.context.historyManager().getCursor());
        }
    }

    @Test public void currentAttachDetachRestoreThenMoveNeedsNoPublication() {
        try (F f = new F()) {
            int p = f.root(0, 0), a = f.sprite(0, 4);
            f.publish();
            f.execute(f.prepare(0, p, 0, a));
            f.execute(f.prepare(1, 0, 0, a));
            assertEquals(List.of(a), f.order(1));
            long history = f.context.historyManager().historyIds().ensureForEntity(a);
            f.context.historyManager().execute(DeleteEntitiesCommandFactory.create(f.world,
                    f.context.historyManager().historyIds(), IntArray.with(a), null));
            f.publish(); f.context.historyManager().undo();
            int restored = f.context.historyManager().historyIds().entityOfHistoryId(history);
            f.execute(f.prepare(0, p, 0, restored)); assertEquals(f.stable(p), f.parent(restored));
        }
    }

    @Test public void nativeJointsAndGearRemainUnchangedAcrossMovesAndRepeatedHistory() {
        try (F f = new F()) {
            int p = f.root(0, 20), q = f.root(1, 20);
            int a = f.body(0), b = f.body(0), c = f.body(1);
            int internal = f.physics.createRevoluteJoint(a, b, 0, 0);
            int external = f.physics.createRevoluteJoint(a, c, 0, 0);
            int gear = f.physics.createGearJoint(internal, external, 2f);
            assertTrue(gear >= 0); f.publish();
            f.execute(f.prepare(0, p, 0, a, b));
            f.t(q).scaleX = f.t(q).scaleY = 2;
            assertFalse(f.result(1, q, 0, p).accepted());
            f.t(q).scaleX = f.t(q).scaleY = 1;
            f.execute(f.prepare(1, q, 0, p));
            for (int i = 0; i < 4; i++) {
                f.context.historyManager().undo(); f.context.historyManager().redo();
                if (i % 2 == 0) f.publish();
                var joint = f.world.getMapper(PhysicsJointComponent.class).get(external);
                assertEquals(a, joint.aEid); assertEquals(c, joint.bEid);
                var relation = f.world.getMapper(PhysicsGearJointComponent.class).get(gear);
                assertEquals(internal, relation.joint1Eid); assertEquals(external, relation.joint2Eid);
                assertTrue(f.sync.collectJointsForRemoval(IntArray.with(a), new IntArray()).contains(gear));
            }
            f.publish(); assertEquals(3, f.box2d.world.getJointCount());
        }
    }

    @Test public void movedCompositionSurvivesSaveReload() throws Exception {
        var path = Files.createTempFile("moved-items", ".json");
        try (F f = new F(); F loaded = new F(false)) {
            int p = f.root(0, 1), q = f.root(1, 8), a = f.sprite(0, 50);
            f.attach(a, p, 50); f.t(a).originY = 4; f.t(a).scaleX = -1;
            var pose = f.pose(a);
            f.execute(f.prepare(1, q, 0, p)); f.publish();
            SceneService.saveScene(f.world, new FileHandle(path.toFile()), false);
            loaded.meta.nextEntityStableId = f.meta.nextEntityStableId; // Scene metadata is persisted separately from ECS.
            SceneLoader.loadScene(loaded.world, new FileHandle(path.toFile()), true, loaded.meta); loaded.publish();
            loaded.context.identityRegistry().rebuild();
            int restored = loaded.context.identityRegistry().findByStableId(f.stable(a));
            assertEquals(f.stable(p), loaded.parent(restored));
            assertTrue(pose.closeTo(loaded.pose(restored)));
            assertEquals(0, loaded.index(restored).layerIndex);
            assertEquals(1, games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations.layerIndex(loaded.world, restored));
        } finally { Files.deleteIfExists(path); }
    }

    @Test public void historyResolvesRestoredStableIdsAndRejectsAddedSubtreeMembers() {
        try (F f = new F()) {
            int p = f.root(0, 7), a = f.sprite(0, 7), other = f.sprite(0, 7);
            int stable = f.stable(a);
            f.execute(f.prepare(0, p, 0, a)); f.publish();
            f.context.historyManager().execute(DeleteEntitiesCommandFactory.create(f.world,
                    f.context.historyManager().historyIds(), IntArray.with(a), null));
            f.publish();
            for (int i = 0; i < 4; i++) f.world.create();
            f.context.historyManager().undo();
            int restored = f.context.identityRegistry().findByStableId(stable); assertNotEquals(a, restored);
            f.context.historyManager().undo(); assertEquals(0, f.parent(restored)); assertEquals(7, f.z(other));
            f.context.historyManager().redo(); assertEquals(f.stable(p), f.parent(restored));
            var plan = f.prepare(1, 0, 0, p); f.execute(plan);
            int extra = f.sprite(1, 0); f.attach(extra, p, 0);
            assertEquals(CommandOutcome.REJECTED, plan.command().executeOutcome());
            int cursor = f.context.historyManager().getCursor();
            f.context.historyManager().undo(); // Refused atomically: the subtree changed outside history.
            assertEquals(cursor, f.context.historyManager().getCursor()); assertEquals(1, f.index(p).layerIndex);
        }
    }

    @Test public void onlySameSceneDocumentAndGenerationAreAccepted() {
        try (F f = new F(); F other = new F()) {
            int a = f.sprite(0, 0);
            var destination = new ItemMovePreparation.Destination(other.document,
                    other.stable(other.context.layerService().getLayerEntity(0)), 0, 0);
            assertFalse(ItemMovePreparation.prepare(f.document, List.of(f.stable(a)), destination).accepted());
            for (var type : List.of(games.pixscape.studio.document.EditorDocumentType.HUD_SCREEN,
                    games.pixscape.studio.document.EditorDocumentType.GAME_OBJECT)) {
                var excluded = new games.pixscape.studio.document.OpenEditorDocument(
                        new games.pixscape.studio.document.EditorDocumentKey(type, "excluded"), "Excluded") {
                    @Override public boolean closeable() { return true; }
                };
                assertFalse(ItemMovePreparation.prepare(excluded, List.of(f.stable(a)),
                        new ItemMovePreparation.Destination(excluded, 1, 0, 0)).accepted());
            }
            var plan = f.prepare(1, 0, 0, a);
            f.context.resetForSceneReplacement(0);
            assertFalse(plan.isCurrent()); f.execute(plan);
            assertEquals(0, f.index(a).layerIndex); assertEquals(0, f.context.historyManager().getCursor());
        }
    }

    private static final class F implements AutoCloseable {
        final SceneMeta meta = new SceneMeta();
        final SceneEditorContext context = new SceneEditorContext("items", new StudioEditingModeService());
        final SceneEditorDocument document = new SceneEditorDocument("items", "Items", context);
        final Box2dWorldService box2d;
        final Box2dSyncSystem sync;
        final World world;
        final PhysicsService physics;
        F() { this(true); }
        F(boolean createLayers) {
            GdxNativesLoader.load(); box2d = new Box2dWorldService(100f, new Vector2()); sync = new Box2dSyncSystem(box2d);
            world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(32),
                    new GameObjectHierarchySystem(32), sync, new WorldSerializationManager()).build());
            context.initializeWorld(world, new TiledAllocatorService(), meta);
            if (createLayers) { context.layerService().addLayerTop("A"); context.layerService().addLayerTop("B"); }
            physics = new PhysicsService(world, box2d, meta);
        }
        int sprite(int layer, int z) {
            int e = world.create();
            new GenericEntityInitializer(world).configureStandaloneSprite(101, "main", 16, 16, 20, 30, 4, 5, 0, 0, 0, "Sprite", layer).init(e);
            context.identityRegistry().ensureStableId(e); index(e).zIndex = z; return e;
        }
        int root(int layer, int z) {
            int e = world.create(); new GameObjectRootInitializer(world).configure(5, 8, layer).init(e);
            context.identityRegistry().ensureStableId(e); index(e).zIndex = z; return e;
        }
        int body(int layer) { int e = sprite(layer, 0); physics.ensurePhysics(e); world.getMapper(PhysicsBodyComponent.class).get(e).type = PhysicsBodyComponent.DYNAMIC; return e; }
        void attach(int child, int parent, int z) { world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = stable(parent); index(child).zIndex = z; }
        int stable(int entity) { return context.identityRegistry().getStableId(entity); }
        int parent(int entity) { var member = world.getMapper(GameObjectMemberComponent.class).getSafe(entity, null); return member == null ? 0 : member.parentStableId; }
        TransformComponent t(int entity) { return world.getMapper(TransformComponent.class).get(entity); }
        EntityIndexComponent index(int entity) { return world.getMapper(EntityIndexComponent.class).get(entity); }
        int z(int entity) { return index(entity).zIndex; }
        ItemMovePreparation.Pose pose(int entity) { return ItemMovePreparation.Pose.read(GameObjectHierarchyCommandSupport.worldTransform(world, context.identityRegistry(), entity)); }
        ItemMovePreparation.Result result(int layer, int parent, int slot, int... sources) {
            return ItemMovePreparation.prepare(document, Arrays.stream(sources).map(this::stable).boxed().toList(),
                    new ItemMovePreparation.Destination(document, stable(context.layerService().getLayerEntity(layer)), parent == 0 ? 0 : stable(parent), slot));
        }
        ItemMovePreparation.Prepared prepare(int layer, int parent, int slot, int... sources) {
            var result = result(layer, parent, slot, sources); assertTrue(result.refusal(), result.accepted()); return result.move();
        }
        void execute(ItemMovePreparation.Prepared plan) { context.historyManager().execute(plan.command()); }
        List<Integer> order(int layer) { return new LayerLogicalOrderService(world).derive(layer).items().stream().map(LayerLogicalOrderService.LogicalItem::entityId).toList(); }
        void publish() { world.process(); world.process(); }
        Vector2 resolvedPosition(int entity) {
            var resolved = world.getSystem(GameObjectHierarchySystem.class).worldTransforms();
            assertTrue(resolved.isResolved(entity));
            return new Vector2(resolved.x[entity], resolved.y[entity]);
        }
        @Override public void close() { document.close(); box2d.dispose(); }
    }
}
