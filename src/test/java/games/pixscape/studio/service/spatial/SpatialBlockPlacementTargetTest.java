package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.service.tiled.TiledVisualCoverage;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class SpatialBlockPlacementTargetTest {
    @Test
    public void normalTilePlacementTargetMatchesOverlayCell() {
        TiledMapLayerData map = isoMap();
        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                128,
                TileTransformFlags.NONE
        );
        float[] mouse = cellCenter(map, 4, 5);

        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                map,
                7,
                mouse[0],
                mouse[1],
                coverage,
                false
        );

        Assert.assertTrue(target.valid());
        Assert.assertEquals(4, target.targetGx());
        Assert.assertEquals(5, target.targetGy());

        SpatialBlockData block = blockFromTarget(target);
        Assert.assertEquals(target.targetGx(), (int) block.x);
        Assert.assertEquals(target.targetGy(), (int) block.y);
    }

    @Test
    public void tallTilePlacementUsesClickedCellAsBlockOrigin() {
        TiledMapLayerData map = isoMap();
        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                512,
                TileTransformFlags.NONE
        );
        float[] mouse = cellCenter(map, 2, 3);

        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                map,
                7,
                mouse[0],
                mouse[1],
                coverage,
                false
        );

        Assert.assertTrue(target.valid());
        Assert.assertEquals(2, target.targetGx());
        Assert.assertEquals(3, target.targetGy());
        Assert.assertEquals(1, target.coverageMinGx());
        Assert.assertEquals(5, target.coverageMaxGx());
        Assert.assertEquals(1, target.coverageMinGy());
        Assert.assertEquals(5, target.coverageMaxGy());

        SpatialBlockData block = blockFromTarget(target);
        float[] base = new float[8];
        float[] cell = new float[8];
        SpatialBlockProjection.projectBaseFootprint(map, block, base);
        map.tileToCellVertices(2, 3, cell);

        Assert.assertEquals(target.targetGx(), (int) block.x);
        Assert.assertEquals(target.targetGy(), (int) block.y);
        assertPoint(base, 0, cell[0], cell[1]);
        assertPoint(base, 2, cell[6], cell[7]);
        assertPoint(base, 4, cell[4], cell[5]);
        assertPoint(base, 6, cell[2], cell[3]);
    }

    @Test
    public void fallbackPlacementUsesWorldToTileCellWhenNoRenderedTileHitExists() {
        TiledMapLayerData map = isoMap();
        float[] mouse = cellCenter(map, 6, 7);

        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                map,
                7,
                mouse[0],
                mouse[1],
                null,
                true
        );

        Assert.assertTrue(target.valid());
        Assert.assertTrue(target.fallback());
        Assert.assertEquals(6, target.targetGx());
        Assert.assertEquals(7, target.targetGy());
        Assert.assertEquals(6, target.coverageMinGx());
        Assert.assertEquals(6, target.coverageMaxGx());
        Assert.assertEquals(7, target.coverageMinGy());
        Assert.assertEquals(7, target.coverageMaxGy());
    }

    @Test
    public void staleCoverageOutsideMouseCellIsIgnoredForPlacementTarget() {
        TiledMapLayerData map = isoMap();
        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                512,
                TileTransformFlags.NONE
        );
        float[] mouse = cellCenter(map, 10, 10);

        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                map,
                7,
                mouse[0],
                mouse[1],
                coverage,
                false
        );

        Assert.assertTrue(target.valid());
        Assert.assertEquals(10, target.targetGx());
        Assert.assertEquals(10, target.targetGy());
        Assert.assertEquals(10, target.coverageMinGx());
        Assert.assertEquals(10, target.coverageMaxGx());
        Assert.assertEquals(10, target.coverageMinGy());
        Assert.assertEquals(10, target.coverageMaxGy());
    }

    @Test
    public void invalidInputsReturnInvalidTarget() {
        TiledMapLayerData map = isoMap();
        float[] mouse = cellCenter(map, 4, 5);

        Assert.assertFalse(SpatialBlockPlacementTarget.fromWorld(
                null,
                7,
                mouse[0],
                mouse[1],
                null,
                true
        ).valid());
        Assert.assertFalse(SpatialBlockPlacementTarget.fromWorld(
                map,
                -1,
                mouse[0],
                mouse[1],
                null,
                true
        ).valid());
    }

    @Test
    public void placementTargetDoesNotIntroduceTwoToOneConstants() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/spatial/SpatialBlockPlacementTarget.java"
        ));

        Assert.assertFalse(source.contains("2:1"));
        Assert.assertFalse(source.contains("tileWidth * 0.5"));
        Assert.assertFalse(source.contains("tileHeight * 0.5"));
        Assert.assertTrue(source.contains("SpatialCellPicker.pick"));
    }

    private static TiledMapLayerData isoMap() {
        TiledMapLayerData map = new TiledMapLayerData(16, 16, 256, 128, 4, SceneMetaRuntime.TiledProjection.ISO);
        map.originX = 100f;
        map.originY = 50f;
        return map;
    }

    private static float[] cellCenter(TiledMapLayerData map, int gx, int gy) {
        float[] verts = new float[8];
        map.tileToCellVertices(gx, gy, verts);
        return new float[]{
                (verts[0] + verts[2] + verts[4] + verts[6]) / 4f,
                (verts[1] + verts[3] + verts[5] + verts[7]) / 4f
        };
    }

    private static SpatialBlockData blockFromTarget(SpatialBlockPlacementTarget target) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.x = target.targetGx();
        block.y = target.targetGy();
        block.width = 1f;
        block.depth = 1f;
        block.altitude = 0f;
        block.height = SpatialBlockData.DEFAULT_HEIGHT;
        return block;
    }

    private static void assertPoint(float[] verts, int offset, float x, float y) {
        Assert.assertEquals(x, verts[offset], 0.0001f);
        Assert.assertEquals(y, verts[offset + 1], 0.0001f);
    }
}
