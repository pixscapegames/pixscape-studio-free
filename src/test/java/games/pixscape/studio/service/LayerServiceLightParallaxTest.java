package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LayerServiceLightParallaxTest {

    @Test
    public void parallax_returnsParallaxComponentForLightLayer() {
        World world = new World(new WorldConfiguration());
        LayerService service = new LayerService(world, null, new HistoryIdRegistry());

        int layerEntity = world.create();

        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        layer.type = LayerComponent.TYPE_LIGHT;

        world.getMapper(LayerMetaComponent.class).create(layerEntity);

        LayerParallaxComponent parallax = world.getMapper(LayerParallaxComponent.class).create(layerEntity);
        parallax.factorX = 0.75f;
        parallax.factorY = 1.25f;

        world.process();          // important
        service.rebuildFromWorld();

        LayerParallaxComponent resolved = service.parallax(0);
        assertNotNull(resolved);
        assertEquals(0.75f, resolved.factorX, 0.0001f);
        assertEquals(1.25f, resolved.factorY, 0.0001f);
    }
}
