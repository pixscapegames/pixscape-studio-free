package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.model.EntityKind;
import org.junit.Assert;
import org.junit.Test;

public class SpatialActorPanelFootprintGeometryTest {
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
    public void physicsDisabledAllowsSpatialActivationAndRemoval() {
        SceneMeta scene = new SceneMeta();
        scene.physicsEnabled = false;
        EntityIndexComponent index = new EntityIndexComponent();
        index.layerIndex = 0;
        LayerComponent layer = new LayerComponent();
        layer.spatialEnabled = true;

        boolean eligible = SpatialActorPanel.canActivateSpatialActor(
                scene, EntityKind.SPRITE, index, layer);

        Assert.assertTrue(eligible);

        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsService physics = new PhysicsService(world, null, scene);
        int entityId = world.create();
        games.pixscape.runtime.component.spatial.SpatialFootprintComponent footprint = new games.pixscape.runtime.component.spatial.SpatialFootprintComponent();
        footprint.radiusPx = 50f;

        ToggleSpatialActorCommand blockedActivation = new ToggleSpatialActorCommand(
                world,
                history.historyIds(),
                entityId,
                true,
                eligible,
                footprint);
        history.execute(blockedActivation);

        Assert.assertFalse(blockedActivation.isNoop());
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(entityId));
        Assert.assertTrue(world.getMapper(SpatialRepresentationComponent.class).has(entityId));

        world.getMapper(SpatialRepresentationComponent.class).create(entityId);
        ToggleSpatialActorCommand removeDormantState = new ToggleSpatialActorCommand(
                world,
                history.historyIds(),
                entityId,
                false,
                false,
                null);
        history.execute(removeDormantState);

        Assert.assertFalse(removeDormantState.isNoop());
        Assert.assertFalse(world.getMapper(SpatialRepresentationComponent.class).has(entityId));
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

        games.pixscape.runtime.component.spatial.SpatialFootprintComponent footprint =
                SpatialActorPanel.createDefaultFootprint(dimensions, transform);

        Assert.assertNotNull(footprint);
        Assert.assertEquals(expectedDiameterM, (footprint.radiusPx / PPM) * 2f, 0.0001f);
        Assert.assertEquals(expectedRadiusM, (footprint.radiusPx / PPM), 0.0001f);
        Assert.assertEquals(expectedCenterXM, (footprint.localOffsetXPx / PPM), 0.0001f);
        Assert.assertEquals(expectedCenterYM, (footprint.localOffsetYPx / PPM), 0.0001f);
        Assert.assertEquals(expectedBottomM,
                (footprint.localOffsetYPx / PPM) - (footprint.radiusPx / PPM), 0.0001f);
    }
}
