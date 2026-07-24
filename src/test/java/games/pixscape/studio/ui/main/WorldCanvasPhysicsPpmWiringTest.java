package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import org.junit.Assert;
import org.junit.Test;

public class WorldCanvasPhysicsPpmWiringTest {
    @Test
    public void applyingScenePpmUpdatesBox2dAndSpatialFootprintSyncTogether() {
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2());
        PhysicsSpatialFootprintSyncSystem footprintSync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        World world = new World(new WorldConfigurationBuilder()
                .with(footprintSync)
                .build());
        int entityId = world.create();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        CompiledFixtureData circle = new CompiledFixtureData();
        circle.shapeType = CompiledFixtureData.SHAPE_CIRCLE;
        circle.radius = 0.5f;
        compiled.fixtures.add(circle);
        compiled.generation = 1;
        compiled.valid = true;
        world.process();
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);
        Assert.assertEquals(50f, footprint.radiusPx, 0f);

        WorldCanvas.applyPixelsPerMeter(box2d, footprintSync, 50f);
        world.process();

        Assert.assertEquals(50f, box2d.ppm, 0f);
        Assert.assertEquals(25f, footprint.radiusPx, 0f);
        Assert.assertEquals(1, footprint.physicsGeneration);
        world.dispose();
        box2d.dispose();
    }
}
