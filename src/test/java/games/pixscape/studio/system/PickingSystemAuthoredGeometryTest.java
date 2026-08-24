package games.pixscape.studio.system;

import games.pixscape.runtime.component.TransformComponent;
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

    private static TransformComponent identityTransform() {
        TransformComponent transform = new TransformComponent();
        transform.refreshCaches();
        return transform;
    }
}
