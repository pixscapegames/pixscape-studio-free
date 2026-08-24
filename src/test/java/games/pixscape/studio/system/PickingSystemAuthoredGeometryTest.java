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

    private static TransformComponent identityTransform() {
        TransformComponent transform = new TransformComponent();
        transform.refreshCaches();
        return transform;
    }
}
