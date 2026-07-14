package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialCellPickerTest {
    private final SpatialCellPicker.Result result = new SpatialCellPicker.Result();

    @Test
    public void centerAndFourDiamondEdgesPickExactCells() {
        TiledMapLayerData map = isoMap();
        float cx = centerX(map, 2, 2);
        float cy = centerY(map, 2, 2);
        assertPick(map, cx, cy, 2, 2);
        assertToward(map, cx, cy, 1, 2);
        assertToward(map, cx, cy, 2, 1);
        assertToward(map, cx, cy, 3, 2);
        assertToward(map, cx, cy, 2, 3);
    }

    @Test
    public void edgeAndVertexTiesUseLowestRowMajorCell() {
        TiledMapLayerData map = isoMap();
        float[] vertices = new float[8];
        map.tileToCellVertices(2, 2, vertices);
        assertPick(map, (vertices[0] + vertices[2]) * 0.5f,
                (vertices[1] + vertices[3]) * 0.5f, 1, 2);
        assertPick(map, vertices[0], vertices[1], 1, 1);
    }

    @Test
    public void outsideMapIsNotClampedToEdgeCell() {
        TiledMapLayerData map = isoMap();
        Assert.assertFalse(SpatialCellPicker.pick(map, -10000f, -10000f, result));
    }

    @Test
    public void visibleOccupiedSpriteWinsOverEmptyUnderlyingCell() {
        TiledMapLayerData map = isoMap();
        map.setTile(2, 2, 12);
        TiledMapRenderState state = renderState(map);
        float pointerX = centerX(map, 2, 1);
        float pointerY = centerY(map, 2, 1);
        setVisibleQuad(map, state, 2, 2, pointerX, pointerY, 7L);

        Assert.assertTrue(SpatialCellPicker.pickForSpatialSelection(map, state, pointerX, pointerY, result));
        Assert.assertEquals(2, result.gx);
        Assert.assertEquals(2, result.gy);
    }

    @Test
    public void overlappingVisibleSpritesUseHighestDrawSortKeyDeterministically() {
        TiledMapLayerData map = isoMap();
        map.setTile(2, 2, 12);
        map.setTile(3, 2, 13);
        TiledMapRenderState state = renderState(map);
        float pointerX = centerX(map, 2, 1);
        float pointerY = centerY(map, 2, 1);
        setVisibleQuad(map, state, 2, 2, pointerX, pointerY, 7L);
        setVisibleQuad(map, state, 3, 2, pointerX, pointerY, 9L);

        for (int i = 0; i < 5; i++) {
            Assert.assertTrue(SpatialCellPicker.pickForSpatialSelection(map, state, pointerX, pointerY, result));
            Assert.assertEquals(3, result.gx);
            Assert.assertEquals(2, result.gy);
        }
    }

    private void assertToward(TiledMapLayerData map, float cx, float cy, int gx, int gy) {
        float nx = centerX(map, gx, gy);
        float ny = centerY(map, gx, gy);
        assertPick(map, cx * 0.45f + nx * 0.55f, cy * 0.45f + ny * 0.55f, gx, gy);
    }

    private void assertPick(TiledMapLayerData map, float x, float y, int gx, int gy) {
        Assert.assertTrue(SpatialCellPicker.pick(map, x, y, result));
        Assert.assertEquals(gx, result.gx);
        Assert.assertEquals(gy, result.gy);
    }

    private static float centerX(TiledMapLayerData map, int gx, int gy) {
        return map.tileToWorldX(gx, gy) + map.tileWidth * 0.5f;
    }

    private static float centerY(TiledMapLayerData map, int gx, int gy) {
        return map.tileToWorldY(gx, gy) + map.tileHeight * 0.5f;
    }

    private static TiledMapLayerData isoMap() {
        return new TiledMapLayerData(6, 6, 256, 128, 8, SceneMetaRuntime.TiledProjection.ISO);
    }

    private static TiledMapRenderState renderState(TiledMapLayerData map) {
        TiledMapRenderState state = new TiledMapRenderState(64);
        for (TileChunk chunk : map.getChunks()) {
            chunk.renderRefStartIndex = state.registerRefs(chunk.cellCount());
            chunk.renderRefCount = chunk.cellCount();
        }
        map.visualPaddingTop = 512f;
        return state;
    }

    private static void setVisibleQuad(TiledMapLayerData map,
                                       TiledMapRenderState state,
                                       int gx,
                                       int gy,
                                       float pointerX,
                                       float pointerY,
                                       long sortKey) {
        int ref = map.tiledRenderRefForTile(gx, gy);
        state.setRenderDataForRef(ref, 1, 0, 0, 0, 0, 0, sortKey,
                pointerX - 20f, pointerY - 20f,
                pointerX - 20f, pointerY + 20f,
                pointerX + 20f, pointerY + 20f,
                pointerX + 20f, pointerY - 20f,
                0f, 0f, 1f, 1f, 0f, 1f, (byte) 0);
    }
}
