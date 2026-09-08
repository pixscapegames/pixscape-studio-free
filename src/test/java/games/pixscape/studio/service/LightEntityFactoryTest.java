package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.Assert;
import org.junit.Test;

public class LightEntityFactoryTest {

    @Test
    public void createPointLightAddsExpectedComponents() {
        World world = new World(new WorldConfiguration());
        createClassicLayer(world, 4);
        int entity = world.create();
        new GenericEntityInitializer(world)
                .configurePointLightProcedural(
                        12f, 24f, 3, 2, 9, 4, "Point",
                        80f, 1.25f, 1.75f, 0.2f, 0.4f, 0.6f)
                .init(entity);

        Assert.assertEquals(4, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        Assert.assertEquals(12f, world.getMapper(TransformComponent.class).get(entity).x, 0f);
        Assert.assertEquals(160f, world.getMapper(DimensionsComponent.class).get(entity).width, 0f);
        Assert.assertEquals(3, world.getMapper(RenderMaterialComponent.class).get(entity).shaderIdx);
        PointLightComponent light = world.getMapper(PointLightComponent.class).get(entity);
        Assert.assertEquals(80f, light.radius, 0f);
        Assert.assertEquals(1.25f, light.intensity, 0f);
        Assert.assertEquals(1.75f, light.falloff, 0f);
        world.dispose();

    }

    @Test
    public void createConeLightAddsExpectedComponents() {
        World world = new World(new WorldConfiguration());
        createClassicLayer(world, 6);
        int entity = world.create();
        new GenericEntityInitializer(world)
                .configureConeLightProcedural(
                        3f, 7f, 0.75f, 5, 4, 11, 6, "Cone",
                        90f, 2f, 55f, 0.3f, 2.25f, 0.7f, 0.5f, 0.1f)
                .init(entity);

        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        Assert.assertEquals(0.75f, world.getMapper(TransformComponent.class).get(entity).rotationRad, 0f);
        Assert.assertEquals(180f, world.getMapper(DimensionsComponent.class).get(entity).height, 0f);
        ConeLightComponent light = world.getMapper(ConeLightComponent.class).get(entity);
        Assert.assertEquals(90f, light.radius, 0f);
        Assert.assertEquals(55f, light.coneAngleDeg, 0f);
        Assert.assertEquals(0.3f, light.softness, 0f);
        world.dispose();
    }

    private static void createClassicLayer(World world, int layerIndex) {
        int layerEntity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = layerIndex;
    }
}
