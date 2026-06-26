package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.loading.WorldConfigFactory;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.helper.TiledAllocatorHelper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TiledCapacityBudgetTest {

    @Test
    public void twoHundredFiftySixByTwoHundredFiftySixLayerUsesOneSlotPerCell() {
        assertEquals(65_536, slotsForLayer(256, 256));
    }

    @Test
    public void currentStudioBudgetFitsThreeLargeLayersButNotFour() {
        int oneLayer = slotsForLayer(256, 256);
        int threeLayers = oneLayer * 3;
        int fourLayers = oneLayer * 4;

        assertEquals(196_608, threeLayers);
        assertEquals(262_144, fourLayers);
        assertEquals(200_000, WorldConfigFactory.DEFAULT_TILED_BUDGET);

        assertTrue(threeLayers <= WorldConfigFactory.DEFAULT_TILED_BUDGET);
        assertFalse(fourLayers <= WorldConfigFactory.DEFAULT_TILED_BUDGET);
    }

    private static int slotsForLayer(int width, int height) {
        TiledMapLayerData map = new TiledMapLayerData(
                width,
                height,
                16,
                16,
                16,
                SceneMetaRuntime.TiledProjection.ORTHO
        );
        return TiledAllocatorHelper.computeExactSlots(map);
    }
}
