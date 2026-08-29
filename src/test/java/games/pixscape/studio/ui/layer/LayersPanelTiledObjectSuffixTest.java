package games.pixscape.studio.ui.layer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LayersPanelTiledObjectSuffixTest {

    @Test
    public void markerTakesPriorityOverClassicAndProjectionSpecificLabels() {
        assertEquals("(Tiled Object)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_CLASSIC, false, true, TiledProjection.ORTHO));
        assertEquals("(Tiled orthogonal)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_TILED, false, false, TiledProjection.ORTHO));
        assertEquals("(Tiled isometric)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_TILED, false, false, TiledProjection.ISO));
        assertEquals("", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_CLASSIC, false, false, TiledProjection.ORTHO));
        assertEquals(LayersPanel.layerTypeSuffix(
                        LayerComponent.TYPE_CLASSIC, true, false, TiledProjection.ORTHO),
                games.pixscape.studio.service.LayerService.typeSuffixLabel(
                        LayerComponent.TYPE_CLASSIC, true));
    }

    @Test
    public void ordinaryLayerWithoutTiledMapStillBuildsItsSuffix() {
        World world = new World(new WorldConfigurationBuilder().build());
        try {
            ComponentMapper<TiledLayerComponent> tiledMapper =
                    world.getMapper(TiledLayerComponent.class);

            TiledProjection projection =
                    LayersPanel.tiledProjectionForMapEntity(tiledMapper, -1);

            assertNull(projection);
            assertEquals("", LayersPanel.layerTypeSuffix(
                    LayerComponent.TYPE_CLASSIC, false, false, projection));
        } finally {
            world.dispose();
        }
    }
}
