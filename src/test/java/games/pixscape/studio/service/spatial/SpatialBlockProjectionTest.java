package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SpatialBlockProjectionTest {
    @Test
    public void tileCellBaseProjectsToOverlayCellVertices() {
        TiledMapLayerData map = isoMap();
        SpatialBlockData block = block(2f, 3f, 1f, 1f, 0f, 0f);
        float[] out = new float[8];
        float[] cell = new float[8];

        SpatialBlockProjection.projectBaseFootprint(map, block, out);
        map.tileToCellVertices(2, 3, cell);

        assertPoint(out, 0, cell[0], cell[1]);
        assertPoint(out, 2, cell[6], cell[7]);
        assertPoint(out, 4, cell[4], cell[5]);
        assertPoint(out, 6, cell[2], cell[3]);
    }

    @Test
    public void heightMovesTopFootprintUpFromBaseFootprint() {
        TiledMapLayerData map = isoMap();
        SpatialBlockData block = block(2f, 3f, 1f, 1f, 0f, 237f);
        float[] base = new float[8];
        float[] top = new float[8];

        SpatialBlockProjection.projectBaseFootprint(map, block, base);
        SpatialBlockProjection.projectTopFootprint(map, block, top);

        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(base[i * 2], top[i * 2], 0.0001f);
            Assert.assertEquals(base[i * 2 + 1] + 237f, top[i * 2 + 1], 0.0001f);
        }
    }

    @Test
    public void altitudeOffsetsBothBaseAndTop() {
        TiledMapLayerData map = isoMap();
        SpatialBlockData block = block(2f, 3f, 1f, 1f, 12f, 40f);
        SpatialBlockData ground = block(2f, 3f, 1f, 1f, 0f, 0f);
        float[] groundBase = new float[8];
        float[] base = new float[8];
        float[] top = new float[8];

        SpatialBlockProjection.projectBaseFootprint(map, ground, groundBase);
        SpatialBlockProjection.projectBaseFootprint(map, block, base);
        SpatialBlockProjection.projectTopFootprint(map, block, top);

        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(groundBase[i * 2 + 1] + 12f, base[i * 2 + 1], 0.0001f);
            Assert.assertEquals(groundBase[i * 2 + 1] + 52f, top[i * 2 + 1], 0.0001f);
        }
    }

    @Test
    public void footprintWorldToTileLocalInvertsProjectedBaseCorners() {
        TiledMapLayerData map = isoMap();
        SpatialBlockData block = block(2f, 3f, 1.75f, 2.25f, 12f, 40f);
        float[] base = new float[8];
        Vector2 out = new Vector2();

        SpatialBlockProjection.projectBaseFootprint(map, block, base);

        SpatialBlockProjection.footprintWorldToTileLocal(map, base[0], base[1], block.altitude, out);
        Assert.assertEquals(block.x, out.x, 0.0001f);
        Assert.assertEquals(block.y, out.y, 0.0001f);

        SpatialBlockProjection.footprintWorldToTileLocal(map, base[4], base[5], block.altitude, out);
        Assert.assertEquals(block.x + block.width, out.x, 0.0001f);
        Assert.assertEquals(block.y + block.depth, out.y, 0.0001f);
    }

    @Test
    public void addAtMouseSnapsToContainingCell() {
        TiledMapLayerData map = isoMap();
        float centerX = map.tileToWorldX(4, 5) + map.tileWidth * 0.5f;
        float centerY = map.tileToWorldY(4, 5) + map.tileHeight * 0.5f;

        Assert.assertEquals(4, SpatialBlockProjection.snapWorldToTileCellX(map, centerX, centerY));
        Assert.assertEquals(5, SpatialBlockProjection.snapWorldToTileCellY(map, centerX, centerY));
    }

    @Test
    public void projectionHelperDoesNotDuplicateIsoRatioMath() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/spatial/SpatialBlockProjection.java"
        ));

        Assert.assertFalse(source.contains("2:1"));
        Assert.assertFalse(source.contains("tileWidth * 0.5"));
        Assert.assertFalse(source.contains("tileHeight * 0.5"));
        Assert.assertTrue(source.contains("map.projectSpatialPoint"));
        Assert.assertFalse(source.contains("tileToWorldX(1, 0)"));
        Assert.assertFalse(source.contains("tileToWorldX(0, 1)"));
    }

    private static TiledMapLayerData isoMap() {
        TiledMapLayerData map = new TiledMapLayerData(16, 16, 256, 128, 4, SceneMetaRuntime.TiledProjection.ISO);
        map.originX = 100f;
        map.originY = 50f;
        return map;
    }

    private static SpatialBlockData block(float x, float y, float width, float depth, float altitude, float height) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.altitude = altitude;
        block.height = height;
        return block;
    }

    private static void assertPoint(float[] verts, int offset, float x, float y) {
        Assert.assertEquals(x, verts[offset], 0.0001f);
        Assert.assertEquals(y, verts[offset + 1], 0.0001f);
    }
}
