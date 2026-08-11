package games.pixscape.studio.ui.property.entityproperties;

import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialPhysicsPanelFootprintGeometryTest {
    private static final float PPM = 100f;

    @Test
    public void usesHalfVisualWidthAsDiameterAtZeroOriginAndUnitScale() {
        assertFootprint(100f, 60f, 0f, 0f, 1f, 1f,
                0.5f, 0.25f, 0.5f, 0.25f, 0f);
    }

    @Test
    public void remainsCenteredForCenteredOriginAndHorizontalScale() {
        assertFootprint(100f, 60f, 50f, 30f, 2f, 1f,
                1f, 0.5f, 0f, 0.2f, -0.3f);
    }

    @Test
    public void handlesNegativeHorizontalFlip() {
        assertFootprint(100f, 60f, 0f, 0f, -1f, 1f,
                0.5f, 0.25f, -0.5f, 0.25f, 0f);
    }

    @Test
    public void handlesNegativeVerticalFlipAndKeepsCircleTangentToBottom() {
        assertFootprint(100f, 60f, 0f, 0f, 1f, -1f,
                0.5f, 0.25f, 0.5f, -0.35f, -0.6f);
    }

    private static void assertFootprint(
            float width,
            float height,
            float originX,
            float originY,
            float scaleX,
            float scaleY,
            float expectedDiameterM,
            float expectedRadiusM,
            float expectedCenterXM,
            float expectedCenterYM,
            float expectedBottomM) {
        DimensionsComponent dimensions = new DimensionsComponent();
        dimensions.width = width;
        dimensions.height = height;
        TransformComponent transform = new TransformComponent();
        transform.originX = originX;
        transform.originY = originY;
        transform.scaleX = scaleX;
        transform.scaleY = scaleY;

        PhysicsShapeData footprint =
                SpatialPhysicsPanel.createDefaultFootprint(dimensions, transform, PPM);

        Assert.assertNotNull(footprint);
        Assert.assertEquals(expectedDiameterM, footprint.geometry.radius * 2f, 0.0001f);
        Assert.assertEquals(expectedRadiusM, footprint.geometry.radius, 0.0001f);
        Assert.assertEquals(expectedCenterXM, footprint.geometry.offsetX, 0.0001f);
        Assert.assertEquals(expectedCenterYM, footprint.geometry.offsetY, 0.0001f);
        Assert.assertEquals(expectedBottomM,
                footprint.geometry.offsetY - footprint.geometry.radius, 0.0001f);
    }
}
