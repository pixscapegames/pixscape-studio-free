package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ActorShadowRectangleEditingTest {
    @Test
    public void explicitFullImageRepresentationIsNotANoopAndUndoRestoresAbsence() {
        World world = new World();
        try {
            int entity = world.create();
            world.process();
            var history = new games.pixscape.studio.history.HistoryManager(8);
            var command = new EditActorSpatialRectangleCommand(world, history.historyIds(), entity,
                    0, null, new ActorSpatialRectangleData(), null);
            assertFalse(command.isNoop());
            history.execute(command);
            for (int cycle = 0; cycle < 3; cycle++) {
                assertTrue(world.getMapper(SpatialRepresentationComponent.class).has(entity));
                history.undo();
                assertFalse(world.getMapper(SpatialRepresentationComponent.class).has(entity));
                history.redo();
            }
        } finally {
            world.dispose();
        }
    }

    @Test
    public void commandRejectsZeroConfiguredDimensionsWithoutCreatingRepresentation() {
        World world = new World();
        try {
            int entity = world.create();
            world.process();
            HistoryIdRegistry ids = new HistoryIdRegistry();
            for (boolean width : new boolean[]{false, true}) {
                ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
                if (width) {
                    zone.width = 0;
                } else {
                    zone.height = 0;
                }
                assertThrows(IllegalArgumentException.class,
                        () -> new EditActorSpatialRectangleCommand(world, ids, entity, 0, null, zone, null));
                assertFalse(world.getMapper(SpatialRepresentationComponent.class).has(entity));
            }
            ActorSpatialRectangleData outside = new ActorSpatialRectangleData();
            outside.x = 2;
            new EditActorSpatialRectangleCommand(world, ids, entity, 0, null, outside, null).redo();
            assertTrue(outside.same(world.getMapper(SpatialRepresentationComponent.class)
                    .get(entity).rectangles.staticRectangle));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void clipSwitchSelectsItsAuthoredVolumeAndUndoRestoresSelection() {
        World world = new World(new WorldConfiguration());
        try {
            int e = world.create();
            var animation = world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).create(e);
            animation.animationAssetIds.add(8);
            animation.currentClip = "east";
            var asset = world.getMapper(games.pixscape.runtime.component.AssetRefComponent.class).create(e);
            asset.assetId = 8;
            var spatial = world.getMapper(SpatialRepresentationComponent.class).create(e);
            spatial.rectangles = new ActorSpatialRectangles();
            ActorSpatialRectangleData east = new ActorSpatialRectangleData();
            east.pivotX = 45;
            ActorSpatialRectangleData west = new ActorSpatialRectangleData();
            west.pivotX = 90;
            spatial.rectangles.put(8, "east", east);
            spatial.rectangles.put(8, "west", west);
            var before = EditAnimationCommand.Snapshot.capture(animation, asset);
            var after = before.copy();
            after.currentClip = "west";
            var command = new EditAnimationCommand(world, new HistoryIdRegistry(), e, before, after, null, null);
            command.redo();
            assertEquals(90, spatial.rectangles.get(asset.assetId, animation.currentClip).pivotX, 0);
            command.undo();
            assertEquals(45, spatial.rectangles.get(asset.assetId, animation.currentClip).pivotX, 0);
            command.redo();
            animation.frame = 7;
            assertSame(spatial.rectangles.get(8, "west"), spatial.rectangles.get(asset.assetId, animation.currentClip));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void editUndoRedoPreservesOtherClipsAndActorTransform() {
        World world = new World(new WorldConfiguration());
        try {
            int e = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(e);
            transform.x = 37;
            transform.y = 49;
            transform.rotationRad = .5f;
            SpatialRepresentationComponent h = world.getMapper(SpatialRepresentationComponent.class).create(e);
            h.rectangles = new ActorSpatialRectangles();
            h.rectangles.put(8, "east", new ActorSpatialRectangleData());
            h.rectangles.put(8, "west", new ActorSpatialRectangleData());
            HistoryIdRegistry ids = new HistoryIdRegistry();
            ActorSpatialRectangleData edited = h.rectangles.get(8, "east").copy();
            edited.pivotX = 45;
            edited.width = 75;
            EditActorSpatialRectangleCommand command = new EditActorSpatialRectangleCommand(world, ids, e, 8, "east", edited, null);
            for (int i = 0; i < 3; i++) {
                command.redo();
                assertEquals(45, h.rectangles.get(8, "east").pivotX, 0);
                assertEquals(75, h.rectangles.get(8, "east").width, 0);
                assertEquals(0, h.rectangles.get(8, "west").pivotX, 0);
                command.undo();
                assertEquals(0, h.rectangles.get(8, "east").pivotX, 0);
            }
            assertEquals(37, transform.x, 0);
            assertEquals(49, transform.y, 0);
            assertEquals(.5f, transform.rotationRad, 0);
            h.rectangles = new Json().fromJson(ActorSpatialRectangles.class, new Json().toJson(h.rectangles));
            assertEquals(1, h.rectangles.get(8, "east").width, 0);
        } finally {
            world.dispose();
        }
    }
}
