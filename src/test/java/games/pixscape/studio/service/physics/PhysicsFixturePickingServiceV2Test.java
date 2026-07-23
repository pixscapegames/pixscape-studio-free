package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsFixturePickingServiceV2Test {
    @Test
    public void concavePickReturnsSourceIdentityAndCompiledPart() {
        GdxNativesLoader.load();
        World world = new World(new WorldConfiguration());
        int bodyEntityId = world.create();
        world.getMapper(TransformComponent.class).create(bodyEntityId);
        world.getMapper(PhysicsBodyComponent.class).create(bodyEntityId);
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(bodyEntityId);
        PhysicsShapeData polygon = new PhysicsShapeData();
        polygon.physicsShapeId = 17;
        polygon.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        polygon.polygonVertices =
                new float[]{0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f};
        polygon.polygonVertexCount = 5;
        shapes.add(polygon);

        PhysicsService physics = new PhysicsService(
                world, new Box2dWorldService(100f, new Vector2()));
        PhysicsFixturePickingService.PickResult result =
                new PhysicsFixturePickingService(physics)
                        .pick(bodyEntityId, 25f, 25f, 0f);

        Assert.assertTrue(result.hit());
        Assert.assertEquals(17, result.physicsShapeId);
        Assert.assertTrue(result.partIndex >= 0);
    }
}
