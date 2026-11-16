package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ActorSpatialHandlePickingTest {
    @Test
    public void nearestHandleWinsEvenWithLowerTiePriority() {
        Vector2[] points = {new Vector2(0, 0), new Vector2(8, 0)};
        assertEquals(1, ActorSpatialRectangleCanvasEditor.pickHandle(points, 6, 0));
        assertEquals(0, ActorSpatialRectangleCanvasEditor.pickHandle(points, 2, 0));
    }

    @Test
    public void equalDistancesUseStablePriority() {
        Vector2[] points = {new Vector2(0, 0), new Vector2(8, 0)};
        assertEquals(0, ActorSpatialRectangleCanvasEditor.pickHandle(points, 4, 0));
    }

    @Test
    public void movingFirstCoincidentHandleExposesTheNext() {
        Vector2[] points = {new Vector2(), new Vector2(), new Vector2()};
        assertEquals(0, ActorSpatialRectangleCanvasEditor.pickHandle(points, 0, 0));
        points[0].set(25, 0);
        assertEquals(1, ActorSpatialRectangleCanvasEditor.pickHandle(points, 0, 0));
        points[1].set(0, 25);
        assertEquals(2, ActorSpatialRectangleCanvasEditor.pickHandle(points, 0, 0));
    }

    @Test
    public void pickToleranceUsesUiUnitsRatherThanGeometrySize() {
        for (float projectedCenter : new float[]{10, 100, 1000}) {
            Vector2[] points = {new Vector2(projectedCenter, 0)};
            assertEquals(0, ActorSpatialRectangleCanvasEditor.pickHandle(points, projectedCenter + 8, 0));
            assertEquals(-1, ActorSpatialRectangleCanvasEditor.pickHandle(points, projectedCenter + 10, 0));
        }
    }
}
