package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.model.EntityKind;
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

    @Test
    public void physicsDisabledBlocksActivationButDormantStateCanBeRemoved() {
        SceneMeta scene = new SceneMeta();
        scene.physicsEnabled = false;
        EntityIndexComponent index = new EntityIndexComponent();
        index.layerIndex = 0;
        LayerComponent layer = new LayerComponent();
        layer.type = LayerComponent.TYPE_CLASSIC;
        layer.spatialEnabled = true;

        boolean eligible = SpatialPhysicsPanel.canActivateSpatialPhysics(
                scene, EntityKind.SPRITE, index, layer);

        Assert.assertFalse(eligible);

        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsService physics = new PhysicsService(world, null, scene);
        int entityId = world.create();
        PhysicsShapeData footprint = new PhysicsShapeData();
        footprint.geometry = new PhysicsGeometryData();
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 0.5f;

        ToggleSpatialActorCommand blockedActivation = new ToggleSpatialActorCommand(
                world,
                history.historyIds(),
                physics,
                entityId,
                true,
                eligible,
                footprint);
        history.execute(blockedActivation);

        Assert.assertTrue(blockedActivation.isNoop());
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(entityId));

        world.getMapper(SpatialHeightComponent.class).create(entityId);
        ToggleSpatialActorCommand removeDormantState = new ToggleSpatialActorCommand(
                world,
                history.historyIds(),
                physics,
                entityId,
                false,
                false,
                null);
        history.execute(removeDormantState);

        Assert.assertFalse(removeDormantState.isNoop());
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(entityId));
        world.dispose();
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
