package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class TiledVisualCoverageTest {
    @Test
    public void normalSizedIsoTileCoversOnlyAnchorCell() {
        TiledMapLayerData map = isoMap();

        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                128,
                TileTransformFlags.NONE
        );

        Assert.assertEquals(4, coverage.minGX);
        Assert.assertEquals(4, coverage.maxGX);
        Assert.assertEquals(5, coverage.minGY);
        Assert.assertEquals(5, coverage.maxGY);
        Assert.assertEquals(5, coverage.maxGXExclusive);
        Assert.assertEquals(6, coverage.maxGYExclusive);
        Assert.assertTrue(coverage.isAnchorInside());
    }

    @Test
    public void tallIsoTileCoversMultipleCells() {
        TiledMapLayerData map = isoMap();

        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                512,
                TileTransformFlags.NONE
        );

        Assert.assertTrue(coverage.maxGXExclusive > coverage.minGX + 1);
        Assert.assertTrue(coverage.maxGYExclusive > coverage.minGY + 1);
        Assert.assertTrue(coverage.isAnchorInside());
    }

    @Test
    public void tall256By512IsoTileOn256By128GridUsesExpectedVisualRange() {
        TiledMapLayerData map = isoMap();

        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                512,
                TileTransformFlags.NONE
        );

        Assert.assertEquals(1, coverage.minGX);
        Assert.assertEquals(5, coverage.maxGX);
        Assert.assertEquals(1, coverage.minGY);
        Assert.assertEquals(5, coverage.maxGY);
        Assert.assertEquals(6, coverage.maxGXExclusive);
        Assert.assertEquals(6, coverage.maxGYExclusive);
        Assert.assertEquals(4, coverage.anchorGX);
        Assert.assertEquals(5, coverage.anchorGY);
    }

    @Test
    public void halfOpenMaxOnIntegerBoundaryDoesNotAddExtraCell() {
        TiledMapLayerData map = isoMap();

        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                4,
                5,
                256,
                128,
                TileTransformFlags.NONE
        );

        Assert.assertEquals(4, coverage.minGX);
        Assert.assertEquals(5, coverage.maxGXExclusive);
        Assert.assertEquals(4, coverage.maxGX);
        Assert.assertTrue(coverage.contains(4, 5));
        Assert.assertFalse(coverage.contains(5, 5));
    }

    @Test
    public void coverageClampsToMapBounds() {
        TiledMapLayerData map = isoMap();

        TiledVisualCoverage.Coverage coverage = TiledVisualCoverage.compute(
                map,
                0,
                0,
                256,
                512,
                TileTransformFlags.NONE
        );

        Assert.assertEquals(0, coverage.minGX);
        Assert.assertEquals(0, coverage.minGY);
        Assert.assertTrue(coverage.maxGX < map.mapWidth);
        Assert.assertTrue(coverage.maxGY < map.mapHeight);
    }

    @Test
    public void coverageHelperDoesNotIntroduceTwoToOneConstants() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/tiled/TiledVisualCoverage.java"
        ));

        Assert.assertFalse(source.contains("2:1"));
        Assert.assertFalse(source.contains("tileWidth * 0.5"));
        Assert.assertFalse(source.contains("tileHeight * 0.5"));
    }

    private static TiledMapLayerData isoMap() {
        TiledMapLayerData map = new TiledMapLayerData(16, 16, 256, 128, 4, SceneMetaRuntime.TiledProjection.ISO);
        map.originX = 0f;
        map.originY = 0f;
        return map;
    }
}
