package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.SpatialActorPose;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class EditSpatialFootprintCommandTest {
    @Test
    public void compensatesInheritedStaticAndEveryClipInProjectedSpaceWithOneHistoryEntry() {
        try (Fixture f = new Fixture()) {
            int parent = f.entity(-1, false);
            f.pose(parent).rotationRad = .71f;
            f.pose(parent).scaleX = f.pose(parent).scaleY = 2f;
            int owner = f.entity(parent, true);
            f.pose(owner).rotationRad = -.23f;
            int child = f.entity(owner, false);
            f.pose(child).rotationRad = 1.4f;
            int independent = f.entity(owner, true);
            int independentChild = f.entity(independent, false);
            f.publish();
            String original = f.data(owner, child, independent, independentChild);
            float[] pivots = f.pivots(owner, owner, child);
            String untouched = f.data(independent, independentChild);
            String transform = new Json().toJson(f.pose(owner));
            var before = f.snapshot(owner);
            f.history.execute(f.edit(owner, before.withOffsetX(26).withOffsetY(-19)));
            assertEquals(1, f.history.getCursor());
            assertArrayEquals(pivots, f.pivots(owner, owner, child), .0001f);
            assertEquals(untouched, f.data(independent, independentChild));
            assertEquals(transform, new Json().toJson(f.pose(owner)));
            String changed = f.data(owner, child, independent, independentChild);
            for (int cycle = 0; cycle < 3; cycle++) {
                f.history.undo();
                assertEquals(original, f.data(owner, child, independent, independentChild));
                f.history.redo();
                assertEquals(changed, f.data(owner, child, independent, independentChild));
                assertArrayEquals(pivots, f.pivots(owner, owner, child), .0001f);
            }
        }
    }

    @Test
    public void radiusAndPivotEditsDoNotMoveEachOther() {
        try (Fixture f = new Fixture()) {
            int owner = f.entity(-1, true);
            f.publish();
            var rectangles = f.rectangles(owner).copy();
            float[] pivots = f.pivots(owner, owner);
            f.history.execute(f.edit(owner, f.snapshot(owner).withRadius(80)));
            assertEquals(new Json().toJson(rectangles), new Json().toJson(f.rectangles(owner)));
            assertArrayEquals(pivots, f.pivots(owner, owner), 0);
            var footprint = f.snapshot(owner);
            var pivot = f.rectangles(owner).get(1, "idle").copy();
            pivot.pivotX += 20;
            f.history.execute(new EditActorSpatialRectangleCommand(f.world, f.history.historyIds(), owner,
                    1, "idle", pivot, null));
            assertEquals(footprint, f.snapshot(owner));
        }
    }

    @Test
    public void previewCancellationRestoresExactFootprintAndAllClipOffsets() {
        try (Fixture f = new Fixture()) {
            int owner = f.entity(-1, true);
            int child = f.entity(owner, false);
            f.publish();
            String original = f.data(owner, child);
            float[] pivots = f.pivots(owner, owner, child);
            var preview = new EditSpatialFootprintCommand.Preview(f.world, f.history.historyIds(), owner);
            for (int i = 1; i <= 20; i++) {
                preview.update(f.snapshot(owner).withOffsetX(i * 3.125f).withOffsetY(-i * 1.75f));
                assertArrayEquals(pivots, f.pivots(owner, owner, child), .0001f);
                assertEquals(0, f.history.getCursor());
            }
            preview.cancel();
            assertEquals(original, f.data(owner, child));
        }
    }

    @Test
    public void previewCommitAndReloadRetainCompensationAcrossClips() {
        try (Fixture f = new Fixture()) {
            int owner = f.entity(-1, true);
            int child = f.entity(owner, false);
            f.publish();
            var preview = new EditSpatialFootprintCommand.Preview(f.world, f.history.historyIds(), owner);
            var initial = f.snapshot(owner);
            String original = f.data(owner, child);
            float[] pivots = f.pivots(owner, owner, child);
            preview.update(initial.withOffsetX(71).withOffsetY(-26));
            var command = preview.finish();
            assertEquals(original, f.data(owner, child));
            f.history.execute(command);
            String changed = f.data(owner, child);
            var json = new Json();
            for (int e : new int[]{owner, child}) {
                var value = f.world.getMapper(SpatialRepresentationComponent.class).get(e);
                value.rectangles = json.fromJson(ActorSpatialRectangles.class, json.toJson(value.rectangles));
            }
            var footprint = f.world.getMapper(SpatialFootprintComponent.class).get(owner);
            footprint.set(json.fromJson(SpatialFootprintComponent.class, json.toJson(footprint)));
            assertEquals(changed, f.data(owner, child));
            assertArrayEquals(pivots, f.pivots(owner, owner, child), .0001f);
            f.history.undo();
            assertEquals(original, f.data(owner, child));
            f.history.redo();
            assertEquals(changed, f.data(owner, child));
        }
    }

    @Test
    public void movingTheActorStillMovesBothAnchorsTogether() {
        try (Fixture f = new Fixture()) {
            int owner = f.entity(-1, true);
            f.publish();
            f.history.execute(f.edit(owner, f.snapshot(owner).withOffsetX(25)));
            var circle = f.circle(owner);
            var rectangle = f.rectangles(owner).staticRectangle;
            float x = circle.footX + rectangle.pivotX, y = circle.footY + rectangle.pivotY;
            f.pose(owner).x += 30;
            f.pose(owner).y -= 40;
            f.world.getSystem(DirtyTrackerSystem.class).geometry(owner, games.pixscape.runtime.render.GeometryDirty.ALL);
            f.publish();
            var moved = f.circle(owner);
            assertEquals(30, moved.footX - circle.footX, .0001f);
            assertEquals(-40, moved.footY - circle.footY, .0001f);
            assertEquals(x + 30, moved.footX + rectangle.pivotX, .0001f);
            assertEquals(y - 40, moved.footY + rectangle.pivotY, .0001f);
        }
    }

    @Test
    public void stalePreviewDoesNotRestoreOverReplacementRepresentation() {
        try (Fixture f = new Fixture()) {
            int owner = f.entity(-1, true);
            f.publish();
            var before = f.snapshot(owner);
            var preview = new EditSpatialFootprintCommand.Preview(f.world, f.history.historyIds(), owner);
            preview.update(before.withOffsetX(45));
            var replacement = f.rectangles(owner).copy();
            replacement.staticRectangle.pivotX = 123;
            f.world.getMapper(SpatialRepresentationComponent.class).get(owner).rectangles = replacement;
            assertFalse(preview.isCurrent());
            preview.cancel();
            assertSame(replacement, f.rectangles(owner));
            assertEquals(123, f.rectangles(owner).staticRectangle.pivotX, 0);
            assertEquals(before, f.snapshot(owner));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(32),
                new GameObjectHierarchySystem(), new GameObjectCompositionSystem(null)).build());
        final IdentityRegistry identities = new IdentityRegistry();
        final HistoryManager history = new HistoryManager(20);

        Fixture() {
            identities.bind(world, new SceneMetaRuntime());
        }

        int entity(int parent, boolean ownFootprint) {
            int e = world.create();
            identities.ensureStableId(e);
            world.getMapper(GameObjectComponent.class).create(e);
            world.getMapper(EntityIndexComponent.class).create(e);
            var t = world.getMapper(TransformComponent.class).create(e);
            t.x = 57;
            t.y = 89;
            if (parent >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(e).parentStableId = identities.getStableId(parent);
            }
            if (ownFootprint) {
                var foot = world.getMapper(SpatialFootprintComponent.class).create(e);
                foot.radiusPx = 12;
                foot.altitude = 35;
            }
            var rectangles = new ActorSpatialRectangles();
            rectangles.staticRectangle = new ActorSpatialRectangleData();
            rectangles.put(1, "idle", new ActorSpatialRectangleData());
            var walk = new ActorSpatialRectangleData();
            walk.pivotX = -9;
            walk.pivotY = 17;
            rectangles.put(2, "walk", walk);
            world.getMapper(SpatialRepresentationComponent.class).create(e).rectangles = rectangles;
            return e;
        }

        TransformComponent pose(int e) {
            return world.getMapper(TransformComponent.class).get(e);
        }

        ActorSpatialRectangles rectangles(int e) {
            return world.getMapper(SpatialRepresentationComponent.class).get(e).rectangles;
        }

        EditSpatialFootprintCommand.Snapshot snapshot(int e) {
            return EditSpatialFootprintCommand.Snapshot.capture(world.getMapper(SpatialFootprintComponent.class).get(e));
        }

        EditSpatialFootprintCommand edit(int e, EditSpatialFootprintCommand.Snapshot after) {
            return new EditSpatialFootprintCommand(world, history.historyIds(), e, snapshot(e), after);
        }

        SpatialActorGeometry.Footprint circle(int e) {
            var result = new SpatialActorGeometry.Footprint();
            assertTrue(SpatialActorPose.resolve(e, pose(e), world.getMapper(SpatialFootprintComponent.class).get(e),
                    world.getSystem(GameObjectHierarchySystem.class).worldTransforms(), result));
            return result;
        }

        float[] pivots(int owner, int... visuals) {
            var circle = circle(owner);
            float[] result = new float[visuals.length * 6];
            int i = 0;
            for (int e : visuals) {
                var rectangles = rectangles(e);
                for (var rectangle : new ActorSpatialRectangleData[]{rectangles.staticRectangle, rectangles.get(1, "idle"), rectangles.get(2, "walk")}) {
                    float x = circle.footX + rectangle.pivotX, y = circle.footY + rectangle.pivotY;
                    result[i++] = LightingCoordinates.worldX(x, y, circle.bottom);
                    result[i++] = LightingCoordinates.worldY(x, y, circle.bottom);
                }
            }
            return result;
        }

        String data(int... entities) {
            var result = new StringBuilder();
            var json = new Json();
            for (int e : entities) {
                result.append(json.toJson(rectangles(e)));
                result.append(json.toJson(world.getMapper(SpatialFootprintComponent.class).getSafe(e, null)));
            }
            return result.toString();
        }

        void publish() {
            world.process();
            identities.rebuild();
        }

        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
            EventFlow.i().discardPending();
        }
    }
}
