package games.pixscape.studio.system;

import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.helper.AuthoredGeometryTransform;
import games.pixscape.studio.helper.HandleLayout;
import games.pixscape.studio.input.InputManipulationContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PickingSystemGizmoTransformMathTest {

    @Test
    public void rotationUsesTransformPositionAsTheAuthoredPivot() {
        TransformComponent transform = new TransformComponent();
        transform.x = 25f;
        transform.y = 30f;
        transform.originX = 10f;
        transform.originY = 5f;

        assertEquals(25f, PickingSystem.transformPivotX(transform), 0f);
        assertEquals(30f, PickingSystem.transformPivotY(transform), 0f);

        float[] vertices = {0f, 0f, 20f, 0f};
        assertEquals(15f, AuthoredGeometryTransform.worldX(transform, vertices[0], vertices[1]), 0.0001f);
        assertEquals(25f, AuthoredGeometryTransform.worldY(transform, vertices[0], vertices[1]), 0.0001f);

        transform.rotationRad += PickingSystem.signedAngleDelta(
                transform.x, transform.y,
                35f, 30f,
                25f, 40f);
        transform.refreshCaches();

        assertEquals((float) (Math.PI * 0.5d), transform.rotationRad, 0.0001f);
        assertEquals(25f, transform.x, 0f);
        assertEquals(30f, transform.y, 0f);
        assertEquals(30f, AuthoredGeometryTransform.worldX(transform, vertices[0], vertices[1]), 0.0001f);
        assertEquals(20f, AuthoredGeometryTransform.worldY(transform, vertices[0], vertices[1]), 0.0001f);
    }

    @Test
    public void rotationDeltaRemainsCorrectFromANonZeroInitialRotation() {
        float initial = (float) (Math.PI * 0.25d);
        float delta = PickingSystem.signedAngleDelta(
                10f, 20f,
                20f, 20f,
                10f, 30f);

        assertEquals((float) (Math.PI * 0.75d), initial + delta, 0.0001f);
    }

    @Test
    public void gameObjectManipulationPivotUsesResolvedBottomLeftPlusOrigin() {
        assertEquals(35f, PickingSystem.gameObjectPivot(25f, 10f), 0f);
        assertEquals(-7f, PickingSystem.gameObjectPivot(-12f, 5f), 0f);
    }

    @Test
    public void rotatedGameObjectUsesDisplayedCornerAndRotationHandlePositions() {
        float c = (float) Math.cos(Math.PI / 4d);
        float s = (float) Math.sin(Math.PI / 4d);
        float[] corners = {
                0f, 0f,
                40f * c, 40f * s,
                40f * c - 20f * s, 40f * s + 20f * c,
                -20f * s, 20f * c
        };

        assertTrue(games.pixscape.studio.helper.HandleHelper.insideSquare(
                HandleLayout.neX(corners), HandleLayout.neY(corners),
                corners[4], corners[5], 0.001f));

        float[] rotate = new float[2];
        HandleLayout.rotateHandle(corners, 12f, rotate);
        assertEquals(InputManipulationContext.Handle.ROTATE,
                PickingSystem.hitTestGameObjectRotateHandle(
                        corners, rotate[0], rotate[1], 1f, 12f, new float[2]));
        assertEquals(InputManipulationContext.Handle.NONE,
                PickingSystem.hitTestGameObjectRotateHandle(
                        corners, 20f, 32f, 1f, 12f, new float[2]));
    }

    @Test
    public void rotatedGameObjectUniformResizeUsesPivotDistanceAndStaysPositive() {
        assertEquals(3f, PickingSystem.uniformScaleFromPointer(
                5f, 7f, 15f, 7f, 25f, 7f, 1.5f), 0.0001f);
        assertEquals(3f, PickingSystem.uniformScaleFromPointer(
                5f, 7f, 5f, 17f, 5f, 27f, 1.5f), 0.0001f);
        assertEquals(0.01f, PickingSystem.uniformScaleFromPointer(
                5f, 7f, 15f, 7f, 5f, 7f, 1.5f), 0f);
        assertTrue(Float.isNaN(PickingSystem.uniformScaleFromPointer(
                5f, 7f, 5f, 7f, 10f, 7f, 1f)));
    }

    @Test
    public void resizeConvertsWorldDeltaIntoTheCorrectRotatedLocalAxis() {
        float cos = 0f;
        float sin = 1f;

        float localX = PickingSystem.worldDeltaToLocalX(0f, 10f, cos, sin);
        float localY = PickingSystem.worldDeltaToLocalY(0f, 10f, cos, sin);

        assertEquals(10f, localX, 0.0001f);
        assertEquals(0f, localY, 0.0001f);
        assertEquals(1.2f, PickingSystem.resizedScale(1f, localX, 50f), 0.0001f);
        assertEquals(1f, PickingSystem.resizedScale(1f, localY, 30f), 0.0001f);
    }

    @Test
    public void degeneratePolylineOnlyResizesItsNonZeroAxis() {
        assertEquals(1.4f, PickingSystem.resizedScale(1f, 20f, 50f), 0.0001f);
        assertEquals(1f, PickingSystem.resizedScale(1f, 20f, 0f), 0f);
        assertEquals(0.6f, PickingSystem.resizedScale(1f, -20f, 50f), 0.0001f);
    }

    @Test
    public void ctrlResizeForHorizontalDegenerateGeometryUsesOnlyX() {
        float scaleX = 2f;
        float scaleY = 17f;

        float east = PickingSystem.resizedScale(scaleX, 10f, 50f);
        float west = PickingSystem.resizedScale(scaleX, -10f, 50f);

        assertEquals(2.2f, east, 0.0001f);
        assertEquals(1.8f, west, 0.0001f);
        assertEquals(east, PickingSystem.uniformScaleReference(
                true, false, east, scaleY, InputManipulationContext.Handle.E), 0f);
        assertEquals(west, PickingSystem.uniformScaleReference(
                true, false, west, scaleY, InputManipulationContext.Handle.W), 0f);
        assertEquals(east, PickingSystem.uniformScaleReference(
                true, false, east, scaleY, InputManipulationContext.Handle.NE), 0f);

        assertTrueNoValidAxis(InputManipulationContext.Handle.N, true, false);
        assertTrueNoValidAxis(InputManipulationContext.Handle.S, true, false);
        assertEquals(scaleY, PickingSystem.resizedScale(scaleY, 10f, 0f), 0f);
    }

    @Test
    public void ctrlResizeForVerticalDegenerateGeometryUsesOnlyY() {
        float scaleX = 19f;
        float scaleY = 2f;

        float north = PickingSystem.resizedScale(scaleY, 10f, 50f);
        float south = PickingSystem.resizedScale(scaleY, -10f, 50f);

        assertEquals(2.2f, north, 0.0001f);
        assertEquals(1.8f, south, 0.0001f);
        assertEquals(north, PickingSystem.uniformScaleReference(
                false, true, scaleX, north, InputManipulationContext.Handle.N), 0f);
        assertEquals(south, PickingSystem.uniformScaleReference(
                false, true, scaleX, south, InputManipulationContext.Handle.S), 0f);
        assertEquals(north, PickingSystem.uniformScaleReference(
                false, true, scaleX, north, InputManipulationContext.Handle.NE), 0f);

        assertTrueNoValidAxis(InputManipulationContext.Handle.E, false, true);
        assertTrueNoValidAxis(InputManipulationContext.Handle.W, false, true);
        assertEquals(scaleX, PickingSystem.resizedScale(scaleX, 10f, 0f), 0f);
    }

    private static void assertTrueNoValidAxis(InputManipulationContext.Handle handle,
                                              boolean canScaleX,
                                              boolean canScaleY) {
        boolean affectsX = canScaleX && PickingSystem.resizeHandleAffectsX(handle);
        boolean affectsY = canScaleY && PickingSystem.resizeHandleAffectsY(handle);
        assertEquals(false, affectsX || affectsY);
    }
}
