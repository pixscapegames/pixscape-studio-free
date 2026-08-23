package games.pixscape.studio.system;

import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TiledObjectOverlaySystemTest {

    @Test
    public void rectangleCornersFollowCurrentTransformOriginRotationAndScale() {
        TransformComponent transform = new TransformComponent();
        transform.x = 10f;
        transform.y = 20f;
        transform.originX = 2f;
        transform.originY = 3f;
        transform.scaleX = -2f;
        transform.scaleY = 0.5f;
        transform.rotationRad = (float) (Math.PI * 0.5d);
        transform.refreshCaches();
        DimensionsComponent dimensions = new DimensionsComponent();
        dimensions.width = 4f;
        dimensions.height = 6f;
        float[] corners = new float[8];

        TiledObjectOverlaySystem.computeRectangleCorners(transform, dimensions, corners);

        assertArrayEquals(new float[]{11.5f, 24f, 11.5f, 16f, 8.5f, 16f, 8.5f, 24f},
                corners, 0.0001f);
        assertEquals(4f, dimensions.width, 0f);
        assertEquals(6f, dimensions.height, 0f);
    }

    @Test
    public void rectangleCornersSupportTiledTopLeftOriginAndZeroDimensions() {
        TransformComponent transform = new TransformComponent();
        transform.x = 10f;
        transform.y = 140f;
        transform.originY = 40f;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        transform.refreshCaches();
        DimensionsComponent dimensions = new DimensionsComponent();
        dimensions.width = 30f;
        dimensions.height = 40f;
        float[] corners = new float[8];

        TiledObjectOverlaySystem.computeRectangleCorners(transform, dimensions, corners);
        assertArrayEquals(new float[]{10f, 100f, 40f, 100f, 40f, 140f, 10f, 140f},
                corners, 0.0001f);

        dimensions.width = 0f;
        dimensions.height = 0f;
        TiledObjectOverlaySystem.computeRectangleCorners(transform, dimensions, corners);
        assertArrayEquals(new float[]{10f, 100f, 10f, 100f, 10f, 100f, 10f, 100f},
                corners, 0.0001f);
    }

    @Test
    public void rectangleCornersSupportArbitraryRotation() {
        TransformComponent transform = new TransformComponent();
        transform.x = 3f;
        transform.y = -4f;
        transform.originX = 1f;
        transform.originY = 2f;
        transform.scaleX = 1.5f;
        transform.scaleY = -0.5f;
        transform.rotationRad = (float) (Math.PI * 0.25d);
        transform.refreshCaches();
        DimensionsComponent dimensions = new DimensionsComponent();
        dimensions.width = 2f;
        dimensions.height = 4f;
        float[] corners = new float[8];

        TiledObjectOverlaySystem.computeRectangleCorners(transform, dimensions, corners);

        assertArrayEquals(new float[]{
                        1.23223f, -4.35355f,
                3.35355f, -2.23223f,
                4.76777f, -3.64645f,
                2.64645f, -5.76777f
                }, corners, 0.001f);
    }

    @Test
    public void pointRadiiRemainScreenSpaceAndVisibilitySkipsUnsupportedShapes() {
        assertEquals(6f, TiledObjectOverlaySystem.pointOuterRadiusWorld(1f), 0f);
        assertEquals(12f, TiledObjectOverlaySystem.pointOuterRadiusWorld(2f), 0f);
        assertEquals(2f, TiledObjectOverlaySystem.pointCenterRadiusWorld(1f), 0f);
        assertEquals(4f, TiledObjectOverlaySystem.pointCenterRadiusWorld(2f), 0f);

        TransformComponent point = new TransformComponent();
        point.x = 12f;
        point.y = -8f;
        point.originX = 99f;
        point.originY = 88f;
        point.scaleX = -3f;
        point.scaleY = 2f;
        point.rotationRad = 1.25f;
        float[] center = new float[2];
        TiledObjectOverlaySystem.pointCenter(point, center);
        assertArrayEquals(new float[]{12f, -8f}, center, 0f);

        assertTrue(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_RECTANGLE, true, true, false));
        assertTrue(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_POINT, true, true, false));
        assertFalse(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.SPRITE, true, true, false));
        assertFalse(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_RECTANGLE, false, true, false));
        assertFalse(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_POINT, true, false, false));
        assertFalse(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.UNKNOWN, true, true, false));
        assertFalse(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_RECTANGLE, true, true, true));
        assertTrue(TiledObjectOverlaySystem.shouldDrawShape(
                EntityKind.TILED_POINT, true, true, true));
    }
}
