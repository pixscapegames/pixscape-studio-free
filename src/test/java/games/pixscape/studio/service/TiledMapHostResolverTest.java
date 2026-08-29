package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TiledMapHostResolverTest {
    @Test
    public void mixedWorldAcceptsManyOrdinaryMapsAndOneTransitionalHostMap() {
        World world = new World(new WorldConfiguration());
        int classic = layer(world, 0, LayerComponent.TYPE_CLASSIC);
        map(world, 0);
        map(world, 0);
        int host = layer(world, 1, LayerComponent.TYPE_TILED);
        int hostedMap = map(world, 1);
        world.process();

        TiledMapHostResolver resolver = new TiledMapHostResolver(world);
        resolver.validateWorld();

        assertEquals(-1, resolver.findForHost(classic));
        assertEquals(hostedMap, resolver.requireForHost(host));
    }

    @Test
    public void transitionalHostStillRejectsMultipleMaps() {
        World world = new World(new WorldConfiguration());
        layer(world, 0, LayerComponent.TYPE_TILED);
        map(world, 0);
        map(world, 0);
        world.process();

        assertThrows(IllegalStateException.class,
                () -> new TiledMapHostResolver(world).validateWorld());
    }

    private static int layer(World world, int layerIndex, int type) {
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.layerIndex = layerIndex;
        layer.type = type;
        return entity;
    }

    private static int map(World world, int layerIndex) {
        int entity = world.create();
        world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = layerIndex;
        world.getMapper(TiledLayerComponent.class).create(entity);
        return entity;
    }
}
