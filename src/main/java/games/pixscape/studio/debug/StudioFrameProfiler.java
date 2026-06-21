package games.pixscape.studio.debug;

import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.profiling.FrameSystemProfiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

public final class StudioFrameProfiler {

    public static final String PROPERTY_ENABLED = "pixscape.studio.profiler";
    public static final String PROPERTY_THRESHOLD_MS = "pixscape.studio.profiler.thresholdMs";
    public static final double DEFAULT_THRESHOLD_MS = 25.0;

    public static final int ACT_TOTAL = 0;
    public static final int ATLAS_UPDATE_ASYNC_PACK = 1;
    public static final int ATLAS_APPLY_IF_PACK_READY = 2;
    public static final int PREVIEW_UPDATE_TILED_PREVIEW = 3;
    public static final int DRAW_TOTAL = 4;
    public static final int GPU_SNAPSHOT_SYNC_IF_DIRTY = 5;
    public static final int EVENT_FLOW_FLUSH = 6;
    public static final int WORLD_PROCESS = 7;
    public static final int GPU_SNAPSHOT_FLUSH_DEFERRED_DISPOSALS = 8;

    private static final String TAG = "StudioFrameProfiler";
    private static final String[] PHASE_NAMES = {
            "act.total",
            "atlas.updateAsyncPack",
            "atlas.applyIfPackReady",
            "preview.updateTiledPreview",
            "draw.total",
            "gpuSnapshot.syncIfDirty",
            "eventFlow.flush",
            "world.process",
            "gpuSnapshot.flushDeferredDisposals"
    };

    private final boolean enabled;
    private final long thresholdNs;
    private final Clock clock;
    private final StatsSource statsSource;
    private final LogSink logSink;
    private final long[] phaseNs = new long[PHASE_NAMES.length];
    private final StringBuilder report = new StringBuilder(384);
    private FrameSystemProfiler systemProfiler;

    private boolean frameOpen;
    private long frameStartNs;
    private long frameStartHeapBytes;
    private long frameStartGcCollections;
    private long frameStartGcTimeMs;

    public static StudioFrameProfiler fromSystemProperties() {
        boolean enabled = Boolean.getBoolean(PROPERTY_ENABLED);
        double thresholdMs = parseThresholdMs(System.getProperty(PROPERTY_THRESHOLD_MS));
        return new StudioFrameProfiler(
                enabled,
                thresholdMs,
                System::nanoTime,
                enabled ? new JvmStatsSource() : StatsSource.ZERO,
                StudioFrameProfiler::logToGdx
        );
    }

    StudioFrameProfiler(boolean enabled,
                        double thresholdMs,
                        Clock clock,
                        StatsSource statsSource,
                        LogSink logSink) {
        this.enabled = enabled;
        this.thresholdNs = Math.max(0L, (long) (thresholdMs * 1_000_000.0));
        this.clock = clock != null ? clock : System::nanoTime;
        this.statsSource = statsSource != null ? statsSource : StatsSource.ZERO;
        this.logSink = logSink != null ? logSink : StudioFrameProfiler::logToGdx;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public FrameSystemProfiler createSystemProfiler() {
        if (!enabled) return null;
        FrameSystemProfiler profiler = new FrameSystemProfiler();
        profiler.setEnabled(true);
        return profiler;
    }

    public void setSystemProfiler(FrameSystemProfiler systemProfiler) {
        this.systemProfiler = systemProfiler;
    }

    public void beginFrame() {
        if (!enabled) return;
        Arrays.fill(phaseNs, 0L);
        frameOpen = true;
        frameStartNs = clock.nanoTime();
        frameStartHeapBytes = statsSource.heapUsedBytes();
        frameStartGcCollections = statsSource.gcCollections();
        frameStartGcTimeMs = statsSource.gcTimeMs();
    }

    public long begin(int phaseId) {
        return enabled ? clock.nanoTime() : 0L;
    }

    public void end(int phaseId, long startNs) {
        if (!enabled || phaseId < 0 || phaseId >= phaseNs.length) return;
        phaseNs[phaseId] += Math.max(0L, clock.nanoTime() - startNs);
    }

    public long phaseDurationNs(int phaseId) {
        if (!enabled || phaseId < 0 || phaseId >= phaseNs.length) return 0L;
        return phaseNs[phaseId];
    }

    public long currentFrameElapsedNs() {
        if (!enabled || !frameOpen) return 0L;
        return Math.max(0L, clock.nanoTime() - frameStartNs);
    }

    long nowNs() {
        return enabled ? clock.nanoTime() : 0L;
    }

    public void endFrame() {
        if (!enabled || !frameOpen) return;
        frameOpen = false;

        long frameNs = Math.max(0L, clock.nanoTime() - frameStartNs);
        if (frameNs < thresholdNs) {
            return;
        }

        appendReport(frameNs);
        logSink.log(report.toString());
    }

    private void appendReport(long frameNs) {
        long heapDeltaBytes = statsSource.heapUsedBytes() - frameStartHeapBytes;
        long gcCollectionDelta = statsSource.gcCollections() - frameStartGcCollections;
        long gcTimeDeltaMs = statsSource.gcTimeMs() - frameStartGcTimeMs;

        report.setLength(0);
        report.append("frame spike ");
        appendMs(report, frameNs);
        report.append("ms");

        for (int i = 0; i < phaseNs.length; i++) {
            if (phaseNs[i] == 0L) continue;
            report.append('\n').append("  ").append(PHASE_NAMES[i]).append(": ");
            appendMs(report, phaseNs[i]);
            report.append("ms");
            if (i == WORLD_PROCESS && systemProfiler != null && systemProfiler.enabled()) {
                report.append('\n');
                systemProfiler.appendReport(report, phaseNs[i]);
            }
        }

        report.append('\n').append("  heapDelta: ");
        appendBytes(report, heapDeltaBytes);
        report.append('\n').append("  gcDelta: ")
                .append(gcCollectionDelta)
                .append(" collections / ")
                .append(gcTimeDeltaMs)
                .append("ms");
    }

    private static double parseThresholdMs(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_THRESHOLD_MS;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return DEFAULT_THRESHOLD_MS;
        }
    }

    private static void appendMs(StringBuilder out, long ns) {
        long tenths = (ns + 50_000L) / 100_000L;
        out.append(tenths / 10L).append('.').append(tenths % 10L);
    }

    private static void appendBytes(StringBuilder out, long bytes) {
        if (bytes >= 0L) {
            out.append('+');
        } else {
            out.append('-');
            bytes = -bytes;
        }

        long kbTenths = (bytes * 10L + 512L) / 1024L;
        if (kbTenths < 10_240L) {
            out.append(kbTenths / 10L).append('.').append(kbTenths % 10L).append("KB");
            return;
        }

        long mbTenths = (bytes * 10L + 524_288L) / 1_048_576L;
        out.append(mbTenths / 10L).append('.').append(mbTenths % 10L).append("MB");
    }

    private static void logToGdx(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println(TAG + ": " + message);
        }
    }

    interface Clock {
        long nanoTime();
    }

    interface StatsSource {
        StatsSource ZERO = new StatsSource() {
            @Override
            public long heapUsedBytes() {
                return 0L;
            }

            @Override
            public long gcCollections() {
                return 0L;
            }

            @Override
            public long gcTimeMs() {
                return 0L;
            }
        };

        long heapUsedBytes();

        long gcCollections();

        long gcTimeMs();
    }

    interface LogSink {
        void log(String message);
    }

    private static final class JvmStatsSource implements StatsSource {
        private final Runtime runtime = Runtime.getRuntime();
        private final List<GarbageCollectorMXBean> gcBeans =
                ManagementFactory.getGarbageCollectorMXBeans();

        @Override
        public long heapUsedBytes() {
            return runtime.totalMemory() - runtime.freeMemory();
        }

        @Override
        public long gcCollections() {
            long total = 0L;
            for (GarbageCollectorMXBean bean : gcBeans) {
                long count = bean.getCollectionCount();
                if (count >= 0L) {
                    total += count;
                }
            }
            return total;
        }

        @Override
        public long gcTimeMs() {
            long total = 0L;
            for (GarbageCollectorMXBean bean : gcBeans) {
                long timeMs = bean.getCollectionTime();
                if (timeMs >= 0L) {
                    total += timeMs;
                }
            }
            return total;
        }
    }
}
