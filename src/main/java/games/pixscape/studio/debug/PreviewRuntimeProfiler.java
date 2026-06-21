package games.pixscape.studio.debug;

import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilePhases;

import java.util.Arrays;

public final class PreviewRuntimeProfiler {
    public static final String PROPERTY_ENABLED = "pixscape.studio.profiler.stablePreview";
    public static final String PROPERTY_WINDOW_SECONDS = "pixscape.studio.profiler.windowSeconds";
    public static final String PROPERTY_EXCLUDE_STUDIO_HITCHES = "pixscape.studio.profiler.excludeStudioHitches";

    private static final String TAG = "PreviewRuntimeProfiler";
    private static final double DEFAULT_WINDOW_SECONDS = 5.0;
    private static final long GPU_SNAPSHOT_EXCLUDE_NS = 500_000L;
    private static final long ATLAS_APPLY_EXCLUDE_NS = 500_000L;
    private static final long ATLAS_UPDATE_EXCLUDE_NS = 2_000_000L;
    private static final long EVENT_FLOW_EXCLUDE_NS = 5_000_000L;

    private final boolean enabled;
    private final boolean excludeStudioHitches;
    private final long windowNs;
    private final LogSink logSink;
    private final StringBuilder report = new StringBuilder(768);
    private final SampleSet worldProcess;
    private final SampleSet profiledSystemsTotal;
    private final SampleSet unprofiledRemainder;
    private final SampleSet drawTotal;
    private final SampleSet eventFlowFlush;
    private final SampleSet gpuSnapshotSync;
    private final SampleSet atlasApply;
    private final SampleSet atlasUpdate;
    private final SampleSet previewUpdateTiledPreview;
    private final SampleSet[] systemSamples = new SampleSet[SystemProfilePhases.PHASE_COUNT];

    private long elapsedNs;
    private int frames;
    private int accepted;
    private int excluded;
    private int excludedGpuSnapshot;
    private int excludedAtlasApply;
    private int excludedEventFlow;
    private int excludedSceneNotReady;
    private int excludedPreviewNotReady;
    private int excludedMissingWorldProcess;
    private int excludedOtherStudioWork;
    private long lastFrameNowNs = -1L;

    public static boolean enabledFromSystemProperties(StudioFrameProfiler frameProfiler) {
        return frameProfiler != null
                && frameProfiler.isEnabled()
                && Boolean.getBoolean(PROPERTY_ENABLED);
    }

    public static PreviewRuntimeProfiler fromSystemProperties(StudioFrameProfiler frameProfiler) {
        boolean enabled = enabledFromSystemProperties(frameProfiler);
        if (!enabled) {
            return null;
        }
        double windowSeconds = parsePositiveDouble(
                System.getProperty(PROPERTY_WINDOW_SECONDS),
                DEFAULT_WINDOW_SECONDS
        );
        boolean excludeStudioHitches = parseBoolean(
                System.getProperty(PROPERTY_EXCLUDE_STUDIO_HITCHES),
                true
        );
        return new PreviewRuntimeProfiler(
                enabled,
                windowSeconds,
                excludeStudioHitches,
                PreviewRuntimeProfiler::logToGdx
        );
    }

    PreviewRuntimeProfiler(boolean enabled,
                           double windowSeconds,
                           boolean excludeStudioHitches,
                           LogSink logSink) {
        this.enabled = enabled;
        this.excludeStudioHitches = excludeStudioHitches;
        this.windowNs = Math.max(1L, (long) (windowSeconds * 1_000_000_000.0));
        this.logSink = logSink != null ? logSink : PreviewRuntimeProfiler::logToGdx;

        int capacity = sampleCapacity(windowSeconds);
        worldProcess = new SampleSet(capacity);
        profiledSystemsTotal = new SampleSet(capacity);
        unprofiledRemainder = new SampleSet(capacity);
        drawTotal = new SampleSet(capacity);
        eventFlowFlush = new SampleSet(capacity);
        gpuSnapshotSync = new SampleSet(capacity);
        atlasApply = new SampleSet(capacity);
        atlasUpdate = new SampleSet(capacity);
        previewUpdateTiledPreview = new SampleSet(capacity);
        for (int i = 0; i < systemSamples.length; i++) {
            systemSamples[i] = new SampleSet(capacity);
        }
        if (enabled) {
            this.logSink.log("enabled window=" + formatSeconds(windowNs) + "s excludeStudioHitches=" + excludeStudioHitches);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void onFrame(StudioFrameProfiler frameProfiler,
                        FrameSystemProfiler systemProfiler,
                        boolean previewReady) {
        onFrame(frameProfiler, systemProfiler, previewReady, previewReady);
    }

    public void onFrame(StudioFrameProfiler frameProfiler,
                        FrameSystemProfiler systemProfiler,
                        boolean sceneReady,
                        boolean previewReady) {
        if (!enabled || frameProfiler == null) return;

        long frameNs = frameElapsedNs(frameProfiler);
        elapsedNs += frameNs;
        frames++;

        long worldNs = frameProfiler.phaseDurationNs(StudioFrameProfiler.WORLD_PROCESS);
        if (excludeFrame(frameProfiler, sceneReady, previewReady, worldNs)) {
            excluded++;
            maybeReport();
            return;
        }

        accepted++;
        long totalNs = systemProfiler != null && systemProfiler.enabled()
                ? systemProfiler.totalNs()
                : 0L;
        long remainderNs = Math.max(0L, worldNs - totalNs);

        worldProcess.add(worldNs);
        profiledSystemsTotal.add(totalNs);
        unprofiledRemainder.add(remainderNs);
        drawTotal.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.DRAW_TOTAL));
        eventFlowFlush.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.EVENT_FLOW_FLUSH));
        gpuSnapshotSync.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.GPU_SNAPSHOT_SYNC_IF_DIRTY));
        atlasApply.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.ATLAS_APPLY_IF_PACK_READY));
        atlasUpdate.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK));
        previewUpdateTiledPreview.add(frameProfiler.phaseDurationNs(StudioFrameProfiler.PREVIEW_UPDATE_TILED_PREVIEW));

        if (systemProfiler != null && systemProfiler.enabled()) {
            for (int phase = 0; phase < SystemProfilePhases.PHASE_COUNT; phase++) {
                long durationNs = systemProfiler.durationNs(phase);
                if (durationNs > 0L) {
                    systemSamples[phase].add(durationNs);
                }
            }
        }

        maybeReport();
    }

    private long frameElapsedNs(StudioFrameProfiler frameProfiler) {
        long nowNs = frameProfiler.nowNs();
        long frameNs;
        if (lastFrameNowNs < 0L) {
            frameNs = frameProfiler.currentFrameElapsedNs();
        } else {
            frameNs = Math.max(0L, nowNs - lastFrameNowNs);
        }
        lastFrameNowNs = nowNs;
        return frameNs;
    }

    private boolean excludeFrame(StudioFrameProfiler frameProfiler,
                                 boolean sceneReady,
                                 boolean previewReady,
                                 long worldNs) {
        boolean excludedFrame = false;
        if (!sceneReady) {
            excludedSceneNotReady++;
            excludedFrame = true;
        } else if (!previewReady) {
            excludedPreviewNotReady++;
            excludedFrame = true;
        }
        if (worldNs <= 0L) {
            excludedMissingWorldProcess++;
            excludedFrame = true;
        }
        if (!excludeStudioHitches) return excludedFrame;
        if (frameProfiler.phaseDurationNs(StudioFrameProfiler.GPU_SNAPSHOT_SYNC_IF_DIRTY) > GPU_SNAPSHOT_EXCLUDE_NS) {
            excludedGpuSnapshot++;
            excludedFrame = true;
        }
        if (frameProfiler.phaseDurationNs(StudioFrameProfiler.ATLAS_APPLY_IF_PACK_READY) > ATLAS_APPLY_EXCLUDE_NS) {
            excludedAtlasApply++;
            excludedFrame = true;
        }
        if (frameProfiler.phaseDurationNs(StudioFrameProfiler.EVENT_FLOW_FLUSH) > EVENT_FLOW_EXCLUDE_NS) {
            excludedEventFlow++;
            excludedFrame = true;
        }
        if (frameProfiler.phaseDurationNs(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK) > ATLAS_UPDATE_EXCLUDE_NS) {
            excludedOtherStudioWork++;
            excludedFrame = true;
        }
        return excludedFrame;
    }

    private void maybeReport() {
        if (elapsedNs < windowNs) return;
        appendReport();
        logSink.log(report.toString());
        resetWindow();
    }

    private void appendReport() {
        report.setLength(0);
        report.append("stable window ");
        appendSeconds(report, windowNs);
        report.append("s frames=").append(frames)
                .append(" accepted=").append(accepted)
                .append(" excluded=").append(excluded);

        if (accepted == 0) {
            report.append('\n').append("  no accepted frames");
            appendExcludedCounters();
            return;
        }

        appendMetric("world.process", worldProcess);
        appendMetric("profiledSystemsTotal", profiledSystemsTotal);
        appendMetric("unprofiledRemainder", unprofiledRemainder);
        appendMetric("draw.total", drawTotal);
        appendMetric("eventFlow.flush", eventFlowFlush);
        appendMetric("gpuSnapshot.syncIfDirty", gpuSnapshotSync);
        appendMetric("atlas.applyIfPackReady", atlasApply);
        appendMetric("atlas.updateAsyncPack", atlasUpdate);
        appendMetric("preview.updateTiledPreview", previewUpdateTiledPreview);

        report.append('\n').append("  worst avg systems:");
        appendTopSystems(true);
        report.append('\n').append("  worst max systems:");
        appendTopSystems(false);

        appendExcludedCounters();
    }

    private void appendExcludedCounters() {
        report.append('\n').append("  excluded frames:")
                .append('\n').append("    gpuSnapshot=").append(excludedGpuSnapshot)
                .append('\n').append("    atlasApply=").append(excludedAtlasApply)
                .append('\n').append("    eventFlow=").append(excludedEventFlow)
                .append('\n').append("    sceneNotReady=").append(excludedSceneNotReady)
                .append('\n').append("    previewNotReady=").append(excludedPreviewNotReady)
                .append('\n').append("    missingWorldProcess=").append(excludedMissingWorldProcess)
                .append('\n').append("    otherStudioWork=").append(excludedOtherStudioWork);
    }

    private void appendMetric(String label, SampleSet samples) {
        report.append('\n').append("  ").append(label);
        if (samples.count() == 0) {
            report.append(" avg=0.0ms p95=0.0ms p99=0.0ms max=0.0ms");
            return;
        }
        report.append(" avg=");
        appendMs(report, samples.avgNs());
        report.append("ms p95=");
        appendMs(report, samples.percentileNs(0.95f));
        report.append("ms p99=");
        appendMs(report, samples.percentileNs(0.99f));
        report.append("ms max=");
        appendMs(report, samples.maxNs());
        report.append("ms");
    }

    private void appendTopSystems(boolean byAverage) {
        boolean any = false;
        int first = -1;
        int second = -1;
        int third = -1;
        for (int phase = 0; phase < systemSamples.length; phase++) {
            if (systemSamples[phase].count() == 0) continue;
            if (better(phase, first, byAverage)) {
                third = second;
                second = first;
                first = phase;
            } else if (better(phase, second, byAverage)) {
                third = second;
                second = phase;
            } else if (better(phase, third, byAverage)) {
                third = phase;
            }
        }
        any |= appendSystemLine(first);
        any |= appendSystemLine(second);
        any |= appendSystemLine(third);
        if (!any) {
            report.append('\n').append("    none");
        }
    }

    private boolean better(int candidate, int current, boolean byAverage) {
        if (current < 0) return true;
        long candidateValue = byAverage ? systemSamples[candidate].avgNs() : systemSamples[candidate].maxNs();
        long currentValue = byAverage ? systemSamples[current].avgNs() : systemSamples[current].maxNs();
        return candidateValue > currentValue;
    }

    private boolean appendSystemLine(int phase) {
        if (phase < 0) return false;
        SampleSet samples = systemSamples[phase];
        report.append('\n').append("    ")
                .append(SystemProfilePhases.name(phase))
                .append(" avg=");
        appendMs(report, samples.avgNs());
        report.append("ms p95=");
        appendMs(report, samples.percentileNs(0.95f));
        report.append("ms max=");
        appendMs(report, samples.maxNs());
        report.append("ms");
        return true;
    }

    private void resetWindow() {
        elapsedNs = 0L;
        frames = 0;
        accepted = 0;
        excluded = 0;
        excludedGpuSnapshot = 0;
        excludedAtlasApply = 0;
        excludedEventFlow = 0;
        excludedSceneNotReady = 0;
        excludedPreviewNotReady = 0;
        excludedMissingWorldProcess = 0;
        excludedOtherStudioWork = 0;
        worldProcess.clear();
        profiledSystemsTotal.clear();
        unprofiledRemainder.clear();
        drawTotal.clear();
        eventFlowFlush.clear();
        gpuSnapshotSync.clear();
        atlasApply.clear();
        atlasUpdate.clear();
        previewUpdateTiledPreview.clear();
        for (int i = 0; i < systemSamples.length; i++) {
            systemSamples[i].clear();
        }
    }

    private static int sampleCapacity(double windowSeconds) {
        int capacity = (int) Math.ceil(Math.max(1.0, windowSeconds) * 240.0);
        if (capacity < 64) return 64;
        return Math.min(capacity, 4096);
    }

    private static double parsePositiveDouble(String value, double fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value);
    }

    private static void appendMs(StringBuilder out, long ns) {
        long tenths = (ns + 50_000L) / 100_000L;
        out.append(tenths / 10L).append('.').append(tenths % 10L);
    }

    private static void appendSeconds(StringBuilder out, long ns) {
        long tenths = (ns + 50_000_000L) / 100_000_000L;
        out.append(tenths / 10L).append('.').append(tenths % 10L);
    }

    private static String formatSeconds(long ns) {
        StringBuilder out = new StringBuilder(8);
        appendSeconds(out, ns);
        return out.toString();
    }

    private static void logToGdx(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println("[" + TAG + "] " + message);
        }
    }

    interface LogSink {
        void log(String message);
    }

    private static final class SampleSet {
        private final long[] values;
        private final long[] sorted;
        private int count;
        private long sumNs;
        private long maxNs;

        SampleSet(int capacity) {
            values = new long[capacity];
            sorted = new long[capacity];
        }

        void add(long valueNs) {
            if (count == values.length) {
                removeOldest();
            }
            values[count++] = Math.max(0L, valueNs);
            sumNs += values[count - 1];
            if (values[count - 1] > maxNs) {
                maxNs = values[count - 1];
            }
        }

        int count() {
            return count;
        }

        long avgNs() {
            return count > 0 ? sumNs / count : 0L;
        }

        long maxNs() {
            return maxNs;
        }

        long percentileNs(float percentile) {
            if (count == 0) return 0L;
            System.arraycopy(values, 0, sorted, 0, count);
            Arrays.sort(sorted, 0, count);
            int index = (int) Math.ceil(percentile * count) - 1;
            if (index < 0) index = 0;
            if (index >= count) index = count - 1;
            return sorted[index];
        }

        void clear() {
            count = 0;
            sumNs = 0L;
            maxNs = 0L;
        }

        private void removeOldest() {
            long old = values[0];
            System.arraycopy(values, 1, values, 0, values.length - 1);
            count--;
            sumNs -= old;
            if (old == maxNs) {
                maxNs = 0L;
                for (int i = 0; i < count; i++) {
                    if (values[i] > maxNs) {
                        maxNs = values[i];
                    }
                }
            }
        }
    }
}
