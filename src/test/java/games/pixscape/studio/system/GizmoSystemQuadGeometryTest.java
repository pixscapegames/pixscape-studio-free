package games.pixscape.studio.system;

import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class GizmoSystemQuadGeometryTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void loadedQuadDeformationDrivesTheStudioQuadGizmoCorners() {
        OrientedBoundsComponent bounds = new OrientedBoundsComponent();
        bounds.cx = 5f;
        bounds.cy = 5f;
        bounds.ux = 1f;
        bounds.vy = 1f;
        bounds.hx = 5f;
        bounds.hy = 5f;

        TransformComponent transform = new TransformComponent();
        transform.refreshCaches();

        QuadDeformComponent deform = new QuadDeformComponent();
        deform.blX = 1f;
        deform.brY = 2f;
        deform.trX = 3f;
        deform.trY = 4f;
        deform.tlX = -2f;
        deform.tlY = 1f;

        float[] actual = new float[8];
        GizmoSystem.computeQuadEditCorners(bounds, transform, deform, actual);

        assertArrayEquals(
                new float[]{1f, 0f, 10f, 2f, 13f, 14f, -2f, 11f},
                actual,
                EPSILON);
    }

    @Test
    public void gameObjectDerivedBoundsUseStableAxisAlignedCornerLayout() {
        float[] actual = new float[8];
        GizmoSystem.writeAxisAlignedCorners(-2f, 3f, 8f, 11f, actual);
        assertArrayEquals(
                new float[]{-2f, 3f, 8f, 3f, 8f, 11f, -2f, 11f},
                actual, EPSILON);
    }
}
