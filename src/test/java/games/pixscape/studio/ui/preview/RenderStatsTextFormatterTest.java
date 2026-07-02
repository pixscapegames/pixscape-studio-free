package games.pixscape.studio.ui.preview;

import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderStatsTextFormatterTest {

    @Test
    public void regionCacheRatioIsZeroWhenNoSamplesExist() {
        StringBuilder out = new StringBuilder();

        RenderStatsTextFormatter.appendRegionCacheHitRatio(out, 0L, 0L);

        Assert.assertEquals("0.0", out.toString());
        Assert.assertFalse(out.toString().contains("NaN"));
    }

    @Test
    public void regionCacheLineShowsReadableHitRatio() {
        RenderStats stats = new RenderStats();
        stats.regionResolveCacheHits = 128L;
        stats.regionResolveCacheMisses = 12L;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendRegionCacheLine(out, stats);

        Assert.assertEquals("Region cache: 128 hits / 12 misses (91.4%)", out.toString());
    }

    @Test
    public void groupedLinesIncludeNewTextureArrayCounters() {
        RenderStats stats = new RenderStats();
        stats.textureBinds = 1;
        stats.textureArrayBindSkips = 2;
        stats.shaderBinds = 3;
        stats.projectionUploads = 4;
        stats.framebufferSwitches = 5;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendGpuStateLine(out, stats);

        String line = out.toString();
        Assert.assertTrue(line.contains("texArraySkips=2"));
        Assert.assertTrue(line.contains("shaderBinds=3"));
        Assert.assertTrue(line.contains("projUploads=4"));
        Assert.assertTrue(line.contains("fbSwitches=5"));
    }

    @Test
    public void frameQueueLineShowsPhaseTwoCounters() {
        RenderStats stats = new RenderStats();
        stats.frameQueueQuads = 214;
        stats.frameQueuePeakCapacity = 512;
        stats.frameQueueGrowthCount = 0;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendFrameQueueLine(out, stats);

        Assert.assertEquals("Frame queue: 214 quads / peak 512 / growths 0", out.toString());
    }
}
