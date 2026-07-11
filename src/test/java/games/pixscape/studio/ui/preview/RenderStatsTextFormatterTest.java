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
    public void groupedLinesIncludeOnlyLiveGpuStateCounters() {
        RenderStats stats = new RenderStats();
        stats.textureBinds = 1;
        stats.textureArrayBindSkips = 2;
        stats.shaderBinds = 3;
        stats.projectionUploads = 4;
        stats.framebufferSwitches = 5;
        stats.blendSwitches = 6;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendGpuStateLine(out, stats);

        String line = out.toString();
        Assert.assertTrue(line.contains("texArraySkips=2"));
        Assert.assertTrue(line.contains("shaderBinds=3"));
        Assert.assertTrue(line.contains("projUploads=4"));
        Assert.assertTrue(line.contains("blendSw=6"));
        Assert.assertFalse(line.contains("fbBinds="));
        Assert.assertFalse(line.contains("fbSwitches="));
        Assert.assertFalse(line.contains("blendModeSw="));
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

    @Test
    public void formatterDoesNotShowDormantOrMisleadingCounters() {
        RenderStats stats = new RenderStats();
        stats.culledQuads = 1;
        stats.occludedQuads = 2;
        stats.flushCapacity = 3;
        stats.flushEnd = 4;
        stats.flushStateChanges = 5;
        stats.framebufferBinds = 6;
        stats.framebufferSwitches = 7;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendGeometryLine(out, stats);
        out.append('\n');
        RenderStatsTextFormatter.appendGpuDrawLine(out, stats);
        out.append('\n');
        RenderStatsTextFormatter.appendGpuStateLine(out, stats);
        String formatted = out.toString();

        Assert.assertFalse(formatted.contains("culled="));
        Assert.assertFalse(formatted.contains("occluded="));
        Assert.assertFalse(formatted.contains("capacity="));
        Assert.assertFalse(formatted.contains("end="));
        Assert.assertFalse(formatted.contains("stateChanges="));
        Assert.assertFalse(formatted.contains("fbBinds="));
        Assert.assertFalse(formatted.contains("fbSwitches="));
    }

    @Test
    public void tiledCullingLinesMapToNewRenderStatsFields() {
        RenderStats stats = new RenderStats();
        stats.tiledChunksTested = 11;
        stats.tiledChunksOutside = 12;
        stats.tiledChunksFullyInside = 13;
        stats.tiledChunksPartial = 14;
        stats.tiledRenderableRefsVisible = 21;
        stats.tiledRenderableRefsConsidered = 22;
        stats.tiledRenderableRefsCulled = 23;

        StringBuilder chunks = new StringBuilder();
        RenderStatsTextFormatter.appendTiledChunksLine(chunks, stats);
        Assert.assertEquals("Tiled chunks: tested=11 out=12 full=13 partial=14", chunks.toString());

        StringBuilder refs = new StringBuilder();
        RenderStatsTextFormatter.appendTiledRefsLine(refs, stats);
        Assert.assertEquals("Tiled refs: visible=21 considered=22 narrowCulled=23", refs.toString());
    }

    @Test
    public void buildLineLabelsTiledScanAsVisibleRefs() {
        RenderStats stats = new RenderStats();
        stats.batchesOpaque = 1;
        stats.batchesAlpha = 2;
        stats.buildDrawListScannedEcsSlots = 3;
        stats.buildDrawListScannedTiledSlots = 4;

        StringBuilder out = new StringBuilder();
        RenderStatsTextFormatter.appendBuildLine(out, stats);

        Assert.assertEquals("Build: opaque=1 alpha=2 ecsSlots=3 tiledVisibleRefs=4", out.toString());
    }
}
