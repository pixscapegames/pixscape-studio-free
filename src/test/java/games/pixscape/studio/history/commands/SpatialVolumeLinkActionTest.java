package games.pixscape.studio.history.commands;

import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.service.spatial.SpatialVolumeLinkAction;
import org.junit.Test;

import static games.pixscape.studio.history.commands.SpatialBlockLinkCommandTest.wall;
import static org.junit.Assert.*;

public class SpatialVolumeLinkActionTest {
    @Test
    public void directLinkAndUnlinkAreSingleExactHistoryOperations() {
        try (var f = fixture(.3f)) {
            String before = snapshot(f);
            assertNull(apply(f, false));
            String linked = snapshot(f);
            assertEquals("Link volume", f.history.peekUndoLabel());
            assertEquals(90, f.block(2).height, 0);
            assertEquals(.3f, f.block(2).depth, 0);
            f.history.undo();
            assertEquals(before, snapshot(f));
            assertFalse(f.history.canUndo());
            f.history.redo();
            assertEquals(linked, snapshot(f));
            assertNull(apply(f, true));
            String unlinked = snapshot(f);
            assertEquals("Unlink volume", f.history.peekUndoLabel());
            assertEquals(0, f.blocks.links.size);
            f.history.undo();
            assertEquals(linked, snapshot(f));
            assertEquals("Link volume", f.history.peekUndoLabel());
            f.history.redo();
            assertEquals(unlinked, snapshot(f));
        }
    }

    @Test
    public void firstSelectedVolumeRemainsTheReference() {
        try (var f = fixture(.3f)) {
            assertNull(SpatialVolumeLinkAction.apply(f.world, f.history, f.map, 2, 1, false));
            assertEquals(60, f.block(1).height, 0);
            assertEquals(60, f.block(2).height, 0);
            assertEquals(60, f.tiled.defaultTileHeight, 0);
        }
    }

    @Test
    public void thicknessMatchingIsNeverImplicit() {
        try (var f = fixture(.15f)) {
            assertRejectedWithoutMutation(f, "Use Match thickness before linking these volumes.", false);
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void perpendicularVolumesMayKeepDifferentThicknesses() {
        try (var f = new SpatialBlockLinkCommandTest.Fixture()) {
            f.add(wall(1, 1, 1, 1, 3, .3f, 90));
            f.add(wall(2, 2, 3.8f, 1.3f, .2f, 2, 60));
            assertNull(apply(f, false));
            assertEquals(.3f, f.block(1).depth, 0);
            assertEquals(.2f, f.block(2).width, 0);
        }
    }

    @Test
    public void refusalsPreserveSnapshotsAndRedoStack() {
        try (var f = fixture(.3f)) {
            assertNull(apply(f, false));
            f.history.undo();
            f.block(2).altitude = 10;
            assertRejectedWithoutMutation(f, "The volumes must have the same base altitude.", false);
            assertEquals("Link volume", f.history.peekRedoLabel());
            f.block(2).altitude = 0;
            f.block(2).x = 8;
            assertRejectedWithoutMutation(f, "The volumes must share a clear side contact.", false);
            f.block(2).x = 4;
            assertRejectedWithoutMutation(f, "These volumes are not linked.", true);
            f.history.redo();
            assertRejectedWithoutMutation(f, "These volumes are already linked.", false);
        }
    }

    @Test
    public void removedVolumeAndPhysicsFailureHaveShortAtomicRefusals() {
        try (var f = fixture(.3f)) {
            f.blocks.blocks.removeIndex(1);
            assertRejectedWithoutMutation(f, "Select two volumes in the same map.", false);
        }
        try (var f = fixture(.3f)) {
            PhysicsShapesComponent shapes = f.world.getMapper(PhysicsShapesComponent.class).create(f.map);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = 1;
            shape.spatialBlockId = 2;
            shapes.shapes.add(shape);
            // A linked fixture without its required static body must fail publication atomically.
            String physics = new Json().toJson(shapes);
            assertRejectedWithoutMutation(f, "The change conflicts with the existing geometry or physics shapes.", false);
            assertEquals(physics, new Json().toJson(shapes));
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void matchThicknessHasItsOwnHistoryAndNeverLinksOrMatchesHeight() {
        try (var f = fixture(.15f)) {
            f.add(wall(3, 2, 5.8f, 1, 2, .15f, 60));
            String before = snapshot(f);
            assertNull(SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
            assertEquals("Match thickness", f.history.peekUndoLabel());
            assertEquals(.3f, f.block(2).depth, 0);
            assertEquals(.3f, f.block(3).depth, 0);
            assertEquals(60, f.block(2).height, 0);
            assertEquals(128, f.tiled.defaultTileHeight, 0);
            assertEquals(0, f.blocks.links.size);
            String matched = snapshot(f);
            f.history.undo();
            assertEquals(before, snapshot(f));
            assertFalse(f.history.canUndo());
            f.history.redo();
            assertEquals(matched, snapshot(f));
            assertNull(apply(f, false));
            f.history.undo();
            assertEquals(matched, snapshot(f));
            assertEquals("Match thickness", f.history.peekUndoLabel());
        }
    }

    @Test
    public void matchThicknessRefusesSquaresOrientationChangesAndPhysicsFailure() {
        try (var f = fixture(.3f)) {
            assertEquals("The volumes already have the same thickness.",
                    SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
            f.block(1).depth = 3;
            assertEquals("Square volumes have no clear wall direction.",
                    SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
            f.block(1).depth = 2.5f;
            assertEquals("This thickness would change the wall direction.",
                    SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
            f.block(1).depth = .2f;
            PhysicsShapesComponent shapes = f.world.getMapper(PhysicsShapesComponent.class).create(f.map);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = 1;
            shape.spatialBlockId = 2;
            shapes.shapes.add(shape);
            String before = snapshot(f);
            assertEquals("The change conflicts with the existing geometry or physics shapes.",
                    SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
            assertEquals(before, snapshot(f));
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void matchThicknessUsesTheShortAxisInBothOrientations() {
        for (boolean referenceHorizontal : new boolean[]{false, true}) {
            for (boolean selectedHorizontal : new boolean[]{false, true}) {
                try (var f = new SpatialBlockLinkCommandTest.Fixture()) {
                    f.add(wall(1, 1, 1, 1, referenceHorizontal ? 3 : .3f, referenceHorizontal ? .3f : 3, 90));
                    f.add(wall(2, 2, 6, 6, selectedHorizontal ? 2 : .15f, selectedHorizontal ? .15f : 2, 60));
                    f.add(wall(3, 3, 10, 10, 2, .2f, 45));
                    String reference = new Json().toJson(f.block(1));
                    String unrelated = new Json().toJson(f.block(3));
                    assertNull(SpatialVolumeLinkAction.matchThickness(f.world, f.history, f.map, 1, 2));
                    assertEquals(.3f, selectedHorizontal ? f.block(2).depth : f.block(2).width, 0);
                    assertEquals(2, selectedHorizontal ? f.block(2).width : f.block(2).depth, 0);
                    assertEquals(reference, new Json().toJson(f.block(1)));
                    assertEquals(unrelated, new Json().toJson(f.block(3)));
                    assertEquals(0, f.blocks.links.size);
                }
            }
        }
    }

    private static SpatialBlockLinkCommandTest.Fixture fixture(float thickness) {
        var f = new SpatialBlockLinkCommandTest.Fixture();
        f.tiled.data.defaultTileHeight = f.tiled.defaultTileHeight;
        f.add(wall(1, 1, 1, 1, 3, .3f, 90));
        f.add(wall(2, 2, 4, 1, 2, thickness, 60));
        return f;
    }

    private static String apply(SpatialBlockLinkCommandTest.Fixture f, boolean unlink) {
        return SpatialVolumeLinkAction.apply(f.world, f.history, f.map, 1, 2, unlink);
    }

    private static String snapshot(SpatialBlockLinkCommandTest.Fixture f) {
        // Revisions are publication counters; compare the authored data and both default heights.
        return new Json().toJson(f.blocks.blocks) + new Json().toJson(f.blocks.links)
                + ":" + f.blocks.nextSpatialBlockId + ":" + f.tiled.defaultTileHeight + ":" + f.tiled.data.defaultTileHeight;
    }

    private static void assertRejectedWithoutMutation(SpatialBlockLinkCommandTest.Fixture f, String reason, boolean unlink) {
        String before = new Json().toJson(f.blocks);
        float height = f.tiled.defaultTileHeight;
        String undo = f.history.peekUndoLabel(), redo = f.history.peekRedoLabel();
        assertEquals(reason, apply(f, unlink));
        assertEquals(before, new Json().toJson(f.blocks));
        assertEquals(height, f.tiled.defaultTileHeight, 0);
        assertEquals(undo, f.history.peekUndoLabel());
        assertEquals(redo, f.history.peekRedoLabel());
    }
}
