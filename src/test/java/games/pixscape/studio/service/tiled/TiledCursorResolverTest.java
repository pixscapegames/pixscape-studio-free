package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledCursorResolverTest {
    private final TiledCursorResolver.Result result = new TiledCursorResolver.Result();

    @Test
    public void resolvesTheSameLogicalCellForOrthoAndIsoMaps() {
        assertResolvesCell(new TiledMapLayerData(
                8, 8, 32, 16, 4, TiledProjection.ORTHO), 3, 5);
        assertResolvesCell(new TiledMapLayerData(
                8, 8, 64, 32, 4, TiledProjection.ISO), 6, 2);
    }

    @Test
    public void marksCoordinatesOutsideTheMapInvalid() {
        TiledMapLayerData map = new TiledMapLayerData(
                8, 8, 32, 16, 4, TiledProjection.ORTHO);

        TiledCursorResolver.resolve(map, -1f, -1f, result);

        Assert.assertFalse(result.valid);
        Assert.assertEquals(map.worldToTileX(-1f, -1f), result.gx);
        Assert.assertEquals(map.worldToTileY(-1f, -1f), result.gy);
    }

    private void assertResolvesCell(TiledMapLayerData map, int expectedGX, int expectedGY) {
        float worldX = map.tileToWorldX(expectedGX, expectedGY) + map.tileWidth * 0.5f;
        float worldY = map.tileToWorldY(expectedGX, expectedGY) + map.tileHeight * 0.5f;

        TiledCursorResolver.resolve(map, worldX, worldY, result);

        Assert.assertTrue(result.valid);
        Assert.assertEquals(map.worldToTileX(worldX, worldY), result.gx);
        Assert.assertEquals(map.worldToTileY(worldX, worldY), result.gy);
    }
}
