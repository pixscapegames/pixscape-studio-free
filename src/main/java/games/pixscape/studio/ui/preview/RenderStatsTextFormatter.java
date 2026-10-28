package games.pixscape.studio.ui.preview;

import games.pixscape.runtime.render.batch.performance.RenderStats;

final class RenderStatsTextFormatter {
    private RenderStatsTextFormatter() {
    }

    static void appendGeometryLine(StringBuilder out, RenderStats stats) {
        out.append("Geometry: vertex uploads=").append(stats.bufferUploads)
                .append(" bytes=").append(stats.bufferUploadBytes)
                .append(" uploaded vertices=").append(stats.uploadedVertices)
                .append(" drawn vertices/indices=").append(stats.drawnVertices).append('/').append(stats.drawnIndices)
                .append(" (world meshes; preparation excluded)");
    }

    static void appendCount(StringBuilder out, int value) {
        long magnitude = value;
        if (magnitude < 0) { out.append('-'); magnitude = -magnitude; }
        appendGroups(out, magnitude);
    }

    private static void appendGroups(StringBuilder out, long value) {
        if (value < 1000) { out.append(value); return; }
        appendGroups(out, value / 1000);
        out.append(',');
        long group = value % 1000;
        if (group < 100) out.append('0');
        if (group < 10) out.append('0');
        out.append(group);
    }

    static void appendGpuStateLine(StringBuilder out, RenderStats stats) {
        out.append("GPU state: partial Java calls, NOT GPU totals: texBinds=").append(stats.textureBinds)
                .append(" texArraySkips=").append(stats.textureArrayBindSkips)
                .append(" shaderSw=").append(stats.shaderSwitches)
                .append(" shaderBinds=").append(stats.shaderBinds)
                .append(" projUploads=").append(stats.projectionUploads)
                .append(" blendSw=").append(stats.blendSwitches);
    }

    static void appendTiledChunksLine(StringBuilder out, RenderStats stats) {
        out.append("Tiled chunks: tested=").append(stats.tiledChunksTested)
                .append(" out=").append(stats.tiledChunksOutside)
                .append(" full=").append(stats.tiledChunksFullyInside)
                .append(" partial=").append(stats.tiledChunksPartial);
    }

    static void appendTiledRefsLine(StringBuilder out, RenderStats stats) {
        out.append("Tiled refs: visible=").append(stats.tiledRenderableRefsVisible)
                .append(" considered=").append(stats.tiledRenderableRefsConsidered)
                .append(" narrowCulled=").append(stats.tiledRenderableRefsCulled);
    }

    static void appendRegionCacheLine(StringBuilder out, RenderStats stats) {
        long hits = stats.regionResolveCacheHits;
        long misses = stats.regionResolveCacheMisses;
        out.append("Region cache: ").append(hits)
                .append(" hits / ").append(misses)
                .append(" misses (");
        appendRegionCacheHitRatio(out, hits, misses);
        out.append("%)");
    }

    static void appendFrameQueueLine(StringBuilder out, RenderStats stats) {
        out.append("Frame queue: ").append(stats.frameQueueQuads)
                .append(" quads / peak ").append(stats.frameQueuePeakCapacity)
                .append(" / growths ").append(stats.frameQueueGrowthCount);
    }

    static void appendBuildLine(StringBuilder out, RenderStats stats) {
        out.append("Build: opaque=").append(stats.batchesOpaque).append(" alpha=").append(stats.batchesAlpha)
                .append(" ecsSlots=").append(stats.buildDrawListScannedEcsSlots)
                .append(" tiledVisibleRefs=").append(stats.buildDrawListScannedTiledSlots);
    }

    static void appendDrawStagesLine(StringBuilder out, RenderStats stats) {
        out.append("Passes: ");
        if(stats.forwardLighting)out.append("world=").append(stats.worldDraws);
        else out.append("color=").append(stats.colorDraws).append(" receivers=").append(stats.receiverDraws)
                .append(" accumulation=").append(stats.accumulationDraws).append(" ambient=").append(stats.ambientDraws);
        out.append(" shadows=").append(stats.shadowDraws).append(" present=").append(stats.presentationDraws)
                .append("; replays=").append(stats.replayDraws).append(" (included in draws, not flushes)");
    }

    static void appendRegionCacheHitRatio(StringBuilder out, long hits, long misses) {
        long total = hits + misses;
        float ratio = total == 0L ? 0f : (hits * 100f) / total;
        append1(out, ratio);
    }

    private static void append1(StringBuilder out, float value) {
        long tenths = Math.round(value * 10f);
        out.append(tenths / 10L).append('.').append(Math.abs(tenths % 10L));
    }
}
