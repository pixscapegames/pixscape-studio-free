package games.pixscape.studio.service.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HudTransformGeometryTest {
    private static final float EPSILON = 0.001f;

    @Test public void resizeKeepsTheOppositeEdgeFixedForEveryHandle() {
        HudTransformGeometry.Bounds initial = new HudTransformGeometry.Bounds(10f, 20f, 30f, 40f);

        assertBounds(new HudTransformGeometry.Bounds(10f, 20f, 35f, 40f),
                HudTransformGeometry.resize(initial, HudTransformHandle.E, 5f, 9f));
        assertBounds(new HudTransformGeometry.Bounds(15f, 20f, 25f, 40f),
                HudTransformGeometry.resize(initial, HudTransformHandle.W, 5f, 9f));
        assertBounds(new HudTransformGeometry.Bounds(10f, 20f, 30f, 47f),
                HudTransformGeometry.resize(initial, HudTransformHandle.N, 4f, 7f));
        assertBounds(new HudTransformGeometry.Bounds(10f, 23f, 30f, 37f),
                HudTransformGeometry.resize(initial, HudTransformHandle.S, 4f, 3f));
        assertBounds(new HudTransformGeometry.Bounds(13f, 24f, 27f, 36f),
                HudTransformGeometry.resize(initial, HudTransformHandle.SW, 3f, 4f));
    }

    @Test public void resizeClampsAtAPositiveMinimumWithoutMovingTheFixedEdge() {
        HudTransformGeometry.Bounds initial = new HudTransformGeometry.Bounds(10f, 20f, 30f, 40f);

        assertBounds(new HudTransformGeometry.Bounds(39f, 20f, 1f, 40f),
                HudTransformGeometry.resize(initial, HudTransformHandle.W, 100f, 0f));
        assertBounds(new HudTransformGeometry.Bounds(10f, 20f, 30f, 1f),
                HudTransformGeometry.resize(initial, HudTransformHandle.N, 0f, -100f));
    }

    @Test public void cornerResizesCombineAxesAndPreserveFractionalCoordinates() {
        HudTransformGeometry.Bounds initial = new HudTransformGeometry.Bounds(10.5f, 20.25f, 30.5f, 40.75f);

        assertBounds(new HudTransformGeometry.Bounds(12.5f, 20.25f, 28.5f, 45.75f),
                HudTransformGeometry.resize(initial, HudTransformHandle.NW, 2f, 5f));
        assertBounds(new HudTransformGeometry.Bounds(10.5f, 20.25f, 33.5f, 45.75f),
                HudTransformGeometry.resize(initial, HudTransformHandle.NE, 3f, 5f));
        assertBounds(new HudTransformGeometry.Bounds(12.5f, 24.25f, 28.5f, 36.75f),
                HudTransformGeometry.resize(initial, HudTransformHandle.SW, 2f, 4f));
        assertBounds(new HudTransformGeometry.Bounds(10.5f, 24.25f, 33.5f, 36.75f),
                HudTransformGeometry.resize(initial, HudTransformHandle.SE, 3f, 4f));
        assertBounds(new HudTransformGeometry.Bounds(17.75f, 16.5f, 30.5f, 40.75f),
                HudTransformGeometry.move(initial, 7.25f, -3.75f));
    }

    @Test public void handleHitTestingUsesAllEightCentersAndCornersWinOverlap() {
        HudTransformGeometry.Bounds bounds = new HudTransformGeometry.Bounds(10f, 20f, 30f, 40f);
        float size = 8f;

        assertEquals(HudTransformHandle.NW, HudTransformGeometry.handleAt(bounds, 10f, 60f, size));
        assertEquals(HudTransformHandle.N, HudTransformGeometry.handleAt(bounds, 25f, 60f, size));
        assertEquals(HudTransformHandle.NE, HudTransformGeometry.handleAt(bounds, 40f, 60f, size));
        assertEquals(HudTransformHandle.W, HudTransformGeometry.handleAt(bounds, 10f, 40f, size));
        assertEquals(HudTransformHandle.E, HudTransformGeometry.handleAt(bounds, 40f, 40f, size));
        assertEquals(HudTransformHandle.SW, HudTransformGeometry.handleAt(bounds, 10f, 20f, size));
        assertEquals(HudTransformHandle.S, HudTransformGeometry.handleAt(bounds, 25f, 20f, size));
        assertEquals(HudTransformHandle.SE, HudTransformGeometry.handleAt(bounds, 40f, 20f, size));
        assertEquals(HudTransformHandle.NW, HudTransformGeometry.handleAt(bounds, 13f, 57f, 8f));
        assertNull(HudTransformGeometry.handleAt(bounds, 25f, 40f, size));
    }

    private static void assertBounds(HudTransformGeometry.Bounds expected,
                                     HudTransformGeometry.Bounds actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.width(), actual.width(), EPSILON);
        assertEquals(expected.height(), actual.height(), EPSILON);
    }
}
