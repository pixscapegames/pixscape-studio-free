package games.pixscape.studio.ui.main;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldCanvasTiledOutsideSelectionTest {

    @Test
    public void orthogonalMapDistinguishesInsideFromOutsideCanvasClicks() {
        TiledMapLayerData map = new TiledMapLayerData(
                4, 3, 32, 16, 1, SceneMetaRuntime.TiledProjection.ORTHO);

        assertTrue(WorldCanvas.tiledMapContainsWorldPoint(map, 16f, 8f));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, -1f, 8f));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, 16f, 49f));
    }

    @Test
    public void isometricMapDistinguishesInsideCellFromOutsideDiamond() {
        TiledMapLayerData map = new TiledMapLayerData(
                4, 3, 32, 16, 1, SceneMetaRuntime.TiledProjection.ISO);
        float insideX = map.tileToWorldX(1, 1) + map.tileWidth * 0.5f;
        float insideY = map.tileToWorldY(1, 1) + map.tileHeight * 0.5f;

        assertTrue(WorldCanvas.tiledMapContainsWorldPoint(map, insideX, insideY));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, insideX - 1000f, insideY));
    }
}
