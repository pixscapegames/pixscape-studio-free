package games.pixscape.studio.system;

import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickingSystemAuthoredGeometryTest {

    @Test
    public void polygonPickingUsesActualConcaveCapableShapeInsteadOfOnlyItsBounds() {
        TransformComponent transform = identityTransform();
        float[] triangle = {0f, 0f, 10f, 0f, 0f, 10f};

        assertTrue(PickingSystem.isPolygonHit(triangle, transform, 2f, 2f, 0f, 0f, 0f));
        assertFalse(PickingSystem.isPolygonHit(triangle, transform, 9f, 9f, 0f, 0f, 0f));
        assertTrue(PickingSystem.isPolygonHit(triangle, transform, 5f, -1f, 1.1f, 0f, 0f));
    }

    @Test
    public void polylinePickingUsesOnlyOpenSegmentsAndDisplayOffset() {
        TransformComponent transform = identityTransform();
        float[] path = {0f, 0f, 10f, 0f, 10f, 10f};

        assertTrue(PickingSystem.isPolylineHit(path, transform, 5f, 1f, 1.1f, 0f, 0f));
        assertFalse(PickingSystem.isPolylineHit(path, transform, 4f, 4f, 1f, 0f, 0f));
        assertFalse(PickingSystem.isPolylineHit(path, transform, 5f, 5f, 1f, 0f, 0f));
        assertTrue(PickingSystem.isPolylineHit(path, transform, 105f, 1f, 1.1f, 100f, 0f));
        assertFalse(PickingSystem.isPolylineHit(path, transform, 105f, 1f, 1.1f, 0f, 0f));
    }

    @Test
    public void degenerateHorizontalAndVerticalPolylinesStillUsePrecisePicking() {
        TransformComponent transform = identityTransform();
        float[] horizontal = {0f, 0f, 50f, 0f};
        float[] vertical = {0f, 0f, 0f, 50f};

        // A degenerate OBB has no area, while the real line remains selectable.
        assertFalse(PickingSystem.isDisplayedObbHit(
                new float[]{0f, 0f, 50f, 0f, 50f, 0f, 0f, 0f}, 25f, 0f, 1f));
        assertTrue(PickingSystem.isPolylineHit(horizontal, transform, 25f, 0.9f, 1f, 0f, 0f));
        assertFalse(PickingSystem.isPolylineHit(horizontal, transform, 25f, 1.1f, 1f, 0f, 0f));
        assertTrue(PickingSystem.isPolylineHit(horizontal, transform, 125f, 0.9f, 1f, 100f, 0f));
        assertFalse(PickingSystem.isPolylineHit(horizontal, transform, 125f, 0.9f, 1f, 0f, 0f));

        assertTrue(PickingSystem.isPolylineHit(vertical, transform, 0.9f, 25f, 1f, 0f, 0f));
        assertFalse(PickingSystem.isPolylineHit(vertical, transform, 1.1f, 25f, 1f, 0f, 0f));
        assertTrue(PickingSystem.isPolylineHit(vertical, transform, 100.9f, 25f, 1f, 100f, 0f));
        assertFalse(PickingSystem.isPolylineHit(vertical, transform, 100.9f, 25f, 1f, 0f, 0f));
    }

    @Test
    public void authoredObbBroadPhaseSupportsDegenerateBoundsRotationAndDisplayOffset() {
        OrientedBoundsComponent horizontal = bounds(25f, 0f, 1f, 0f, 0f, 1f, 25f, 0f);
        assertTrue(PickingSystem.isAuthoredObbHit(horizontal, 125f, 20.9f, 1f, 100f, 20f));
        assertFalse(PickingSystem.isAuthoredObbHit(horizontal, 125f, 21.1f, 1f, 100f, 20f));

        OrientedBoundsComponent vertical = bounds(0f, 25f, 1f, 0f, 0f, 1f, 0f, 25f);
        assertTrue(PickingSystem.isAuthoredObbHit(vertical, 100.9f, 45f, 1f, 100f, 20f));
        assertFalse(PickingSystem.isAuthoredObbHit(vertical, 101.1f, 45f, 1f, 100f, 20f));

        float diagonal = (float) (Math.sqrt(0.5d));
        OrientedBoundsComponent rotated = bounds(0f, 0f,
                diagonal, diagonal, -diagonal, diagonal, 25f, 0f);
        assertTrue(PickingSystem.isAuthoredObbHit(rotated,
                5f * diagonal - 0.9f * diagonal,
                5f * diagonal + 0.9f * diagonal,
                1f, 0f, 0f));
        assertFalse(PickingSystem.isAuthoredObbHit(rotated,
                5f * diagonal - 1.1f * diagonal,
                5f * diagonal + 1.1f * diagonal,
                1f, 0f, 0f));
    }

    @Test
    public void authoredObbBroadPhaseRejectsFarPolygonBeforePreciseGeometry() {
        OrientedBoundsComponent polygonBounds = bounds(5f, 5f, 1f, 0f, 0f, 1f, 5f, 5f);
        assertFalse(PickingSystem.isAuthoredObbHit(polygonBounds, 100f, 100f, 1f, 0f, 0f));
    }

    private static OrientedBoundsComponent bounds(float cx, float cy,
                                                   float ux, float uy, float vx, float vy,
                                                   float hx, float hy) {
        OrientedBoundsComponent bounds = new OrientedBoundsComponent();
        bounds.cx = cx;
        bounds.cy = cy;
        bounds.ux = ux;
        bounds.uy = uy;
        bounds.vx = vx;
        bounds.vy = vy;
        bounds.hx = hx;
        bounds.hy = hy;
        return bounds;
    }

    private static TransformComponent identityTransform() {
        TransformComponent transform = new TransformComponent();
        transform.refreshCaches();
        return transform;
    }
}
