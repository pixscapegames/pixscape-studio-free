package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.ui.config.EditorOverlayPalette;
import org.junit.Assert;
import org.junit.Test;

public class SpatialWallWireframeTest {

    @Test
    public void liveResizeAlwaysDrawsCompleteCandidateWireframe() {
        Fixture fixture = attachedFixture();
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        float[] before = wireframe(fixture.map, session.candidate());
        Assert.assertTrue(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, 0f, 2.4f));
        float[] duringDrag = wireframe(fixture.map, session.candidate());

        Assert.assertEquals(SpatialWallWireframe.SEGMENT_COUNT, segmentCount(before));
        Assert.assertEquals(SpatialWallWireframe.SEGMENT_COUNT, segmentCount(duringDrag));
        Assert.assertFalse(java.util.Arrays.equals(before, duringDrag));
        assertCompleteSemanticEdgeGroups(fixture.map, session.candidate(), duringDrag);
    }

    @Test
    public void joinedAndLockedSidesNeverRemoveCandidateOutlineSegments() {
        Fixture fixture = attachedFixture();
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));

        Assert.assertTrue(session.attachments().isSideJoined(SpatialWallAttachments.MAX_X));
        Assert.assertFalse(session.isHandleEnabled(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X));
        Assert.assertFalse(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X, 8f, 0f));

        float[] outline = wireframe(fixture.map, session.candidate());
        Assert.assertEquals(SpatialWallWireframe.SEGMENT_COUNT, segmentCount(outline));
        assertCompleteSemanticEdgeGroups(fixture.map, session.candidate(), outline);
    }

    @Test
    public void validAndInvalidPreviewRolesPreserveIdenticalCompleteCoverage() {
        Fixture fixture = attachedFixture();
        SpatialBlockData wall = fixture.walls.blocks.get(0);
        float[] valid = wireframe(fixture.map, wall);
        float[] invalid = wireframe(fixture.map, wall);

        Assert.assertSame(EditorOverlayPalette.VALID_PREVIEW_COLOR,
                EditorOverlayPalette.spatialWallColor(true, false, true, true));
        Assert.assertSame(EditorOverlayPalette.INVALID_PREVIEW_COLOR,
                EditorOverlayPalette.spatialWallColor(true, false, true, false));
        Assert.assertArrayEquals(valid, invalid, 0f);
        Assert.assertEquals(SpatialWallWireframe.SEGMENT_COUNT, segmentCount(invalid));
    }

    @Test
    public void cancelRemovesPreviewAndRestoresCommittedWireframe() {
        Fixture fixture = attachedFixture();
        SpatialBlockData committed = fixture.walls.blocks.get(0);
        float[] before = wireframe(fixture.map, committed);
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, committed.id, fixture.walls, fixture.map));
        Assert.assertTrue(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, 0f, 2.4f));
        Assert.assertFalse(java.util.Arrays.equals(before, wireframe(fixture.map, session.candidate())));

        session.cancel();

        Assert.assertNull(session.candidate());
        Assert.assertArrayEquals(before, wireframe(fixture.map, committed), 0f);
    }

    @Test
    public void commitProducesGeometryEqualToFinalLivePreview() {
        Fixture fixture = attachedFixture();
        SpatialWallEditSession session = new SpatialWallEditSession();
        Assert.assertTrue(session.begin(fixture.layer, 1, fixture.walls, fixture.map));
        Assert.assertTrue(session.updateResize(
                SpatialBlockInteractiveEditSupport.ResizeHandle.MIN_Y, 0f, 2.4f));
        float[] finalPreview = wireframe(fixture.map, session.candidate());

        Assert.assertTrue(session.commit(fixture.world, fixture.history, fixture.selection));

        Assert.assertArrayEquals(finalPreview, wireframe(fixture.map, find(fixture.walls, 1)), 0f);
    }

    private static void assertCompleteSemanticEdgeGroups(TiledMapLayerData map,
                                                         SpatialBlockData wall,
                                                         float[] actual) {
        float[] base = new float[8];
        float[] top = new float[8];
        SpatialBlockProjection.projectBaseFootprint(map, wall, base);
        SpatialBlockProjection.projectTopFootprint(map, wall, top);
        int offset = 0;
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) & 3;
            assertSegment(actual, offset++, base, edge, base, next);
        }
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) & 3;
            assertSegment(actual, offset++, top, edge, top, next);
        }
        for (int corner = 0; corner < 4; corner++) {
            assertSegment(actual, offset++, base, corner, top, corner);
        }
    }

    private static void assertSegment(float[] actual,
                                      int segment,
                                      float[] from,
                                      int fromVertex,
                                      float[] to,
                                      int toVertex) {
        int offset = segment * SpatialWallWireframe.FLOATS_PER_SEGMENT;
        Assert.assertEquals(from[fromVertex * 2], actual[offset], 0f);
        Assert.assertEquals(from[fromVertex * 2 + 1], actual[offset + 1], 0f);
        Assert.assertEquals(to[toVertex * 2], actual[offset + 2], 0f);
        Assert.assertEquals(to[toVertex * 2 + 1], actual[offset + 3], 0f);
    }

    private static float[] wireframe(TiledMapLayerData map, SpatialBlockData wall) {
        float[] base = new float[8];
        float[] top = new float[8];
        float[] out = new float[SpatialWallWireframe.REQUIRED_OUTPUT_FLOATS];
        SpatialBlockProjection.projectBaseFootprint(map, wall, base);
        SpatialBlockProjection.projectTopFootprint(map, wall, top);
        Assert.assertEquals(SpatialWallWireframe.SEGMENT_COUNT, SpatialWallWireframe.write(base, top, out));
        return out;
    }

    private static int segmentCount(float[] segments) {
        return segments.length / SpatialWallWireframe.FLOATS_PER_SEGMENT;
    }

    private static Fixture attachedFixture() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = SpatialWallAuthoringValidatorTest.map(12, 12);
        SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).create(layer);
        walls.blocks.add(wall(1, 2f, 2f, 4f, 1f, 2, 6, 2, 3));
        walls.blocks.add(wall(2, 5f, 1f, 1f, 2f, 5, 6, 1, 4));
        HistoryManager history = new HistoryManager(16);
        history.historyIds().ensureForEntity(layer);
        return new Fixture(world, layer, tiled.data, walls, history, new SpatialBlockSelectionService());
    }

    private static SpatialBlockData wall(int id,
                                         float x,
                                         float y,
                                         float width,
                                         float depth,
                                         int minGx,
                                         int maxGx,
                                         int minGy,
                                         int maxGy) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = 4;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.height = 10f;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy < maxGy; gy++) {
            for (int gx = minGx; gx < maxGx; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static SpatialBlockData find(SpatialBlocksComponent walls, int id) {
        for (int i = 0; i < walls.blocks.size; i++) {
            if (walls.blocks.get(i).id == id) return walls.blocks.get(i);
        }
        return null;
    }

    private record Fixture(World world,
                           int layer,
                           TiledMapLayerData map,
                           SpatialBlocksComponent walls,
                           HistoryManager history,
                           SpatialBlockSelectionService selection) {
    }
}
