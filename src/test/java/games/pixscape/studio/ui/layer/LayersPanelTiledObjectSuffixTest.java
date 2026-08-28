package games.pixscape.studio.ui.layer;

import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LayersPanelTiledObjectSuffixTest {

    @Test
    public void markerTakesPriorityOverClassicAndProjectionSpecificLabels() {
        assertEquals("(Tiled Object)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_CLASSIC, false, true, SceneMetaRuntime.TiledProjection.ORTHO));
        assertEquals("(Tiled orthogonal)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_TILED, false, false, SceneMetaRuntime.TiledProjection.ORTHO));
        assertEquals("(Tiled isometric)", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_TILED, false, false, SceneMetaRuntime.TiledProjection.ISO));
        assertEquals("", LayersPanel.layerTypeSuffix(
                LayerComponent.TYPE_CLASSIC, false, false, SceneMetaRuntime.TiledProjection.ORTHO));
        assertEquals(LayersPanel.layerTypeSuffix(
                        LayerComponent.TYPE_CLASSIC, true, false, SceneMetaRuntime.TiledProjection.ORTHO),
                games.pixscape.studio.service.LayerService.typeSuffixLabel(
                        LayerComponent.TYPE_CLASSIC, true));
    }
}
