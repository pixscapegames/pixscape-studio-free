package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialLightPhysicsSupportTest {
    @Test
    public void hidesOnlyTechnicalBodyAndFixtureWithoutHidingUserChildren() {
        World world = new World(new WorldConfiguration());
        int light = world.create();
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(light);
        body.technicalSpatialLight = true;
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(light);
        PhysicsShapeData technical = new PhysicsShapeData();
        technical.physicsShapeId = 1;
        technical.technicalSpatialLight = true;
        shapes.shapes.add(technical);
        world.process();

        Assert.assertTrue(SpatialLightPhysicsSupport.hasOnlyTechnicalBody(world, light));
        Assert.assertTrue(SpatialLightPhysicsSupport.isTechnicalFixture(shapes, 1));

        PhysicsShapeData authored = new PhysicsShapeData();
        authored.physicsShapeId = 2;
        shapes.shapes.add(authored);
        Assert.assertFalse(SpatialLightPhysicsSupport.hasOnlyTechnicalBody(world, light));
        Assert.assertFalse(SpatialLightPhysicsSupport.isTechnicalFixture(shapes, 2));
        shapes.shapes.removeIndex(1);

        int jointEntity = world.create();
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEntity);
        joint.aEid = light;
        world.process();
        Assert.assertFalse(SpatialLightPhysicsSupport.hasOnlyTechnicalBody(world, light));
    }
}
