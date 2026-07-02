package games.pixscape.studio.render;

import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderStatsInstrumentationTest {

    @Test
    public void resetClearsTextureArrayInstrumentationFields() {
        RenderStats stats = new RenderStats();
        stats.textureArrayBindSkips = 1;
        stats.projectionUploads = 2;
        stats.submittedQuads = 3;
        stats.flushedQuads = 4;
        stats.flushedVertices = 5;
        stats.regionResolveCacheHits = 6L;
        stats.regionResolveCacheMisses = 7L;
        stats.frameQueueQuads = 8;
        stats.frameQueuePeakCapacity = 9;
        stats.frameQueueGrowthCount = 10;

        stats.reset();

        Assert.assertEquals(0, stats.textureArrayBindSkips);
        Assert.assertEquals(0, stats.projectionUploads);
        Assert.assertEquals(0, stats.submittedQuads);
        Assert.assertEquals(0, stats.flushedQuads);
        Assert.assertEquals(0, stats.flushedVertices);
        Assert.assertEquals(0L, stats.regionResolveCacheHits);
        Assert.assertEquals(0L, stats.regionResolveCacheMisses);
        Assert.assertEquals(0, stats.frameQueueQuads);
        Assert.assertEquals(0, stats.frameQueuePeakCapacity);
        Assert.assertEquals(0, stats.frameQueueGrowthCount);
    }

    @Test
    public void addAccumulatesTextureArrayInstrumentationFields() {
        RenderStats total = new RenderStats();
        total.textureArrayBindSkips = 1;
        total.projectionUploads = 2;
        total.submittedQuads = 3;
        total.flushedQuads = 4;
        total.flushedVertices = 5;
        total.regionResolveCacheHits = 6L;
        total.regionResolveCacheMisses = 7L;
        total.frameQueueQuads = 8;
        total.frameQueuePeakCapacity = 9;
        total.frameQueueGrowthCount = 10;

        RenderStats frame = new RenderStats();
        frame.textureArrayBindSkips = 10;
        frame.projectionUploads = 20;
        frame.submittedQuads = 30;
        frame.flushedQuads = 40;
        frame.flushedVertices = 50;
        frame.regionResolveCacheHits = 60L;
        frame.regionResolveCacheMisses = 70L;
        frame.frameQueueQuads = 80;
        frame.frameQueuePeakCapacity = 90;
        frame.frameQueueGrowthCount = 100;

        total.add(frame);

        Assert.assertEquals(11, total.textureArrayBindSkips);
        Assert.assertEquals(22, total.projectionUploads);
        Assert.assertEquals(33, total.submittedQuads);
        Assert.assertEquals(44, total.flushedQuads);
        Assert.assertEquals(55, total.flushedVertices);
        Assert.assertEquals(66L, total.regionResolveCacheHits);
        Assert.assertEquals(77L, total.regionResolveCacheMisses);
        Assert.assertEquals(88, total.frameQueueQuads);
        Assert.assertEquals(90, total.frameQueuePeakCapacity);
        Assert.assertEquals(110, total.frameQueueGrowthCount);
    }
}
