package games.pixscape.studio.ui.main;

import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldCanvasTiledOutsideSelectionTest {

    @Test
    public void orthogonalMapDistinguishesInsideFromOutsideCanvasClicks() {
        TiledMapLayerData map = new TiledMapLayerData(
                4, 3, 32, 16, 1, TiledProjection.ORTHO);

        assertTrue(WorldCanvas.tiledMapContainsWorldPoint(map, 16f, 8f));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, -1f, 8f));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, 16f, 49f));
    }

    @Test
    public void isometricMapDistinguishesInsideCellFromOutsideDiamond() {
        TiledMapLayerData map = new TiledMapLayerData(
                4, 3, 32, 16, 1, TiledProjection.ISO);
        float insideX = map.tileToWorldX(1, 1) + map.tileWidth * 0.5f;
        float insideY = map.tileToWorldY(1, 1) + map.tileHeight * 0.5f;

        assertTrue(WorldCanvas.tiledMapContainsWorldPoint(map, insideX, insideY));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(map, insideX - 1000f, insideY));
    }

    @Test
    public void interactionConversionUsesTheProvidedMapConfiguration() {
        TiledMapLayerData iso = new TiledMapLayerData(
                4, 3, 64, 32, 2, TiledProjection.ISO);
        iso.originX = 100f;
        iso.originY = 50f;
        TiledMapLayerData ortho = new TiledMapLayerData(
                4, 3, 32, 32, 1, TiledProjection.ORTHO);

        float isoCellX = iso.tileToWorldX(1, 1) + iso.tileWidth * 0.5f;
        float isoCellY = iso.tileToWorldY(1, 1) + iso.tileHeight * 0.5f;

        assertTrue(WorldCanvas.tiledMapContainsWorldPoint(iso, isoCellX, isoCellY));
        assertFalse(WorldCanvas.tiledMapContainsWorldPoint(ortho, isoCellX, isoCellY));
    }
}
