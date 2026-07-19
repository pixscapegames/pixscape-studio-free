package games.pixscape.studio.debug;

import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.profiling.NanoClock;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StudioFrameProfilerTest {

    @Test
    public void disabledProfilerDoesNotLog() {
        ManualClock clock = new ManualClock();
        ManualStats stats = new ManualStats();
        List<String> logs = new ArrayList<>();
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                false,
                0.0,
                clock,
                stats,
                logs::add
        );

        profiler.beginFrame();
        long phaseStart = profiler.begin(StudioFrameProfiler.EVENT_FLOW_FLUSH);
        clock.advanceMs(50);
        profiler.end(StudioFrameProfiler.EVENT_FLOW_FLUSH, phaseStart);
        profiler.endFrame();

        assertEquals(0, logs.size());
    }

    @Test
    public void enabledProfilerSkipsFramesBelowThreshold() {
        ManualClock clock = new ManualClock();
        ManualStats stats = new ManualStats();
        List<String> logs = new ArrayList<>();
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                true,
                25.0,
                clock,
                stats,
                logs::add
        );

        profiler.beginFrame();
        clock.advanceMs(10);
        profiler.endFrame();

        assertEquals(0, logs.size());
    }

    @Test
    public void enabledProfilerLogsSpikesWithPhaseHeapAndGcDeltas() {
        ManualClock clock = new ManualClock();
        ManualStats stats = new ManualStats();
        stats.heapUsedBytes = 1_024L;
        stats.gcCollections = 2L;
        stats.gcTimeMs = 7L;
        List<String> logs = new ArrayList<>();
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                true,
                25.0,
                clock,
                stats,
                logs::add
        );

        profiler.beginFrame();
        long phaseStart = profiler.begin(StudioFrameProfiler.EVENT_FLOW_FLUSH);
        clock.advanceMs(5);
        profiler.end(StudioFrameProfiler.EVENT_FLOW_FLUSH, phaseStart);
        clock.advanceMs(25);
        stats.heapUsedBytes = 3_072L;
        stats.gcCollections = 3L;
        stats.gcTimeMs = 11L;
        profiler.endFrame();

        assertEquals(1, logs.size());
        String report = logs.get(0);
        assertTrue(report.contains("frame spike 30.0ms"));
        assertTrue(report.contains("eventFlow.flush: 5.0ms"));
        assertTrue(report.contains("heapDelta: +2.0KB"));
        assertTrue(report.contains("gcDelta: 1 collections / 4ms"));
    }

    @Test
    public void disabledProfilerDoesNotCreateSystemProfiler() {
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                false,
                0.0,
                new ManualClock(),
                new ManualStats(),
                ignored -> {
                }
        );

        assertEquals(null, profiler.createSystemProfiler());
    }

    @Test
    public void enabledProfilerCreatesEnabledSystemProfiler() {
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                true,
                0.0,
                new ManualClock(),
                new ManualStats(),
                ignored -> {
                }
        );

        FrameSystemProfiler systemProfiler = profiler.createSystemProfiler();

        assertTrue(systemProfiler != null);
        assertTrue(systemProfiler.enabled());
    }

    @Test
    public void worldProcessReportIncludesEcsBreakdownWhenCollected() {
        ManualClock clock = new ManualClock();
        ManualStats stats = new ManualStats();
        List<String> logs = new ArrayList<>();
        StudioFrameProfiler profiler = new StudioFrameProfiler(
                true,
                0.0,
                clock,
                stats,
                logs::add
        );
        FrameSystemProfiler systemProfiler = new FrameSystemProfiler(clock);
        systemProfiler.setEnabled(true);
        profiler.setSystemProfiler(systemProfiler);

        profiler.beginFrame();
        long worldStart = profiler.begin(StudioFrameProfiler.WORLD_PROCESS);
        systemProfiler.beginFrame();
        long renderTiledSyncStart = systemProfiler.begin(SystemProfilePhases.RENDER_TILED_SYNC);
        clock.advanceMs(8);
        systemProfiler.end(SystemProfilePhases.RENDER_TILED_SYNC, renderTiledSyncStart);
        clock.advanceMs(17);
        profiler.end(StudioFrameProfiler.WORLD_PROCESS, worldStart);
        profiler.endFrame();

        assertEquals(1, logs.size());
        String report = logs.get(0);
        assertTrue(report.contains("world.process: 25.0ms"));
        assertTrue(report.contains("profiledSystemsTotal: 8.0ms"));
        assertTrue(report.contains("unprofiledRemainder: 17.0ms"));
        assertTrue(report.contains("worst: RenderTiledSyncSystem 8.0ms"));
        assertTrue(report.contains("RenderTiledSyncSystem: 8.0ms"));
    }

    private static final class ManualClock implements StudioFrameProfiler.Clock, NanoClock {
        private long nowNs;

        @Override
        public long nanoTime() {
            return nowNs;
        }

        void advanceMs(long ms) {
            nowNs += ms * 1_000_000L;
        }
    }

    private static final class ManualStats implements StudioFrameProfiler.StatsSource {
        private long heapUsedBytes;
        private long gcCollections;
        private long gcTimeMs;

        @Override
        public long heapUsedBytes() {
            return heapUsedBytes;
        }

        @Override
        public long gcCollections() {
            return gcCollections;
        }

        @Override
        public long gcTimeMs() {
            return gcTimeMs;
        }
    }
}
