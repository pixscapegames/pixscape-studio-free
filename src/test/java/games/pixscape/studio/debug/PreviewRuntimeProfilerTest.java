package games.pixscape.studio.debug;

import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.profiling.NanoClock;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewRuntimeProfilerTest {
    @Test
    public void enabledStableProfilerLogsConfirmationOnce() {
        List<String> logs = new ArrayList<>();

        new PreviewRuntimeProfiler(true, 5.0, true, logs::add);

        assertEquals(1, logs.size());
        assertEquals("enabled window=5.0s excludeStudioHitches=true", logs.get(0));
    }

    @Test
    public void disabledStableProfilerDoesNothing() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(false, 0.001, true, logs::add);
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, null, clock, 2, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true);

        assertEquals(0, logs.size());
    }

    @Test
    public void acceptedFramesContributeToWindowAveragesAndRemainder() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);
        FrameSystemProfiler systemProfiler = new FrameSystemProfiler(clock);
        systemProfiler.setEnabled(true);

        frameProfiler.beginFrame();
        long worldStart = frameProfiler.begin(StudioFrameProfiler.WORLD_PROCESS);
        systemProfiler.beginFrame();
        long spriteStart = systemProfiler.begin(SystemProfilePhases.RENDER_SPRITE_SYNC);
        clock.advanceMs(3);
        systemProfiler.end(SystemProfilePhases.RENDER_SPRITE_SYNC, spriteStart);
        clock.advanceMs(2);
        frameProfiler.end(StudioFrameProfiler.WORLD_PROCESS, worldStart);
        clock.advanceMs(1);

        profiler.onFrame(frameProfiler, systemProfiler, true);

        assertEquals(1, logs.size());
        String report = logs.get(0);
        assertTrue(report.contains("stable window 0.0s frames=1 accepted=1 excluded=0"));
        assertTrue(report.contains("world.process avg=5.0ms p95=5.0ms p99=5.0ms max=5.0ms"));
        assertTrue(report.contains("profiledSystemsTotal avg=3.0ms"));
        assertTrue(report.contains("unprofiledRemainder avg=2.0ms"));
        assertTrue(report.contains("RenderSpriteSyncSystem avg=3.0ms"));
    }

    @Test
    public void excludesGpuSnapshotFrames() {
        assertExclusion(StudioFrameProfiler.GPU_SNAPSHOT_SYNC_IF_DIRTY, 1, "gpuSnapshot=1");
    }

    @Test
    public void excludesAtlasApplyFrames() {
        assertExclusion(StudioFrameProfiler.ATLAS_APPLY_IF_PACK_READY, 1, "atlasApply=1");
    }

    @Test
    public void excludesHighEventFlowFrames() {
        assertExclusion(StudioFrameProfiler.EVENT_FLOW_FLUSH, 6, "eventFlow=1");
    }

    @Test
    public void sceneNotReadyFramesAreExcluded() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, null, clock, 1, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, false);

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("accepted=0 excluded=1"));
        assertTrue(logs.get(0).contains("no accepted frames"));
        assertTrue(logs.get(0).contains("sceneNotReady=1"));
    }

    @Test
    public void previewNotReadyFramesAreExcludedSeparatelyFromSceneReadiness() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, null, clock, 1, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true, false);

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("accepted=0 excluded=1"));
        assertTrue(logs.get(0).contains("previewNotReady=1"));
    }

    @Test
    public void missingWorldProcessFramesAreExcludedAndDiagnosed() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        frameProfiler.beginFrame();
        clock.advanceMs(1);
        profiler.onFrame(frameProfiler, null, true, true);

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("accepted=0 excluded=1"));
        assertTrue(logs.get(0).contains("missingWorldProcess=1"));
    }

    @Test
    public void stableFramesWithNoStudioHitchesAreAccepted() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, null, clock, 1, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true, true);

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("accepted=1 excluded=0"));
        assertTrue(logs.get(0).contains("gpuSnapshot=0"));
        assertTrue(logs.get(0).contains("atlasApply=0"));
        assertTrue(logs.get(0).contains("eventFlow=0"));
    }

    @Test
    public void windowTimingUsesSecondsConvertedToNanoseconds() {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.002, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, null, clock, 1, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true, true);
        assertEquals(0, logs.size());

        recordFrame(frameProfiler, null, clock, 1, 0, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true, true);
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("stable window 0.0s frames=2 accepted=2 excluded=0"));
    }

    private static void assertExclusion(int phaseId, long phaseMs, String expectedCounter) {
        ManualClock clock = new ManualClock();
        List<String> logs = new ArrayList<>();
        PreviewRuntimeProfiler profiler = new PreviewRuntimeProfiler(true, 0.001, true, logs::add);
        logs.clear();
        StudioFrameProfiler frameProfiler = newFrameProfiler(clock);

        recordFrame(frameProfiler, phaseId, clock, 1, phaseMs, 0, 0, 0);
        profiler.onFrame(frameProfiler, null, true);

        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("accepted=0 excluded=1"));
        assertTrue(logs.get(0).contains(expectedCounter));
    }

    private static StudioFrameProfiler newFrameProfiler(ManualClock clock) {
        return new StudioFrameProfiler(
                true,
                10_000.0,
                clock,
                new ManualStats(),
                ignored -> {
                }
        );
    }

    private static void recordFrame(StudioFrameProfiler frameProfiler,
                                    Integer extraPhase,
                                    ManualClock clock,
                                    long worldMs,
                                    long extraMs,
                                    long drawMs,
                                    long atlasUpdateMs,
                                    long previewUpdateMs) {
        frameProfiler.beginFrame();
        if (atlasUpdateMs > 0L) {
            long start = frameProfiler.begin(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK);
            clock.advanceMs(atlasUpdateMs);
            frameProfiler.end(StudioFrameProfiler.ATLAS_UPDATE_ASYNC_PACK, start);
        }
        if (previewUpdateMs > 0L) {
            long start = frameProfiler.begin(StudioFrameProfiler.PREVIEW_UPDATE_TILED_PREVIEW);
            clock.advanceMs(previewUpdateMs);
            frameProfiler.end(StudioFrameProfiler.PREVIEW_UPDATE_TILED_PREVIEW, start);
        }
        if (extraPhase != null && extraMs > 0L) {
            long start = frameProfiler.begin(extraPhase);
            clock.advanceMs(extraMs);
            frameProfiler.end(extraPhase, start);
        }
        long worldStart = frameProfiler.begin(StudioFrameProfiler.WORLD_PROCESS);
        clock.advanceMs(worldMs);
        frameProfiler.end(StudioFrameProfiler.WORLD_PROCESS, worldStart);
        if (drawMs > 0L) {
            long start = frameProfiler.begin(StudioFrameProfiler.DRAW_TOTAL);
            clock.advanceMs(drawMs);
            frameProfiler.end(StudioFrameProfiler.DRAW_TOTAL, start);
        }
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
    }
}
