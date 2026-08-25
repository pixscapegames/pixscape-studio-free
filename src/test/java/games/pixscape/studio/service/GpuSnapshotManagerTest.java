package games.pixscape.studio.service;

import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.debug.StudioFrameProfiler;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GpuSnapshotManagerTest {

    @Test
    public void replacingSnapshotDefersPreviousBundle() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle first = bundle();
        AtlasRuntimeService.TextureArrayBundle second = bundle();

        manager.replaceActiveSnapshot("scene", first);
        manager.replaceActiveSnapshot("scene", second);

        assertEquals(1, manager.activeSnapshotCount());
        assertEquals(1, manager.deferredDisposalCount());
    }

    @Test
    public void replacingWithSameSnapshotDoesNotDefer() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle snapshot = bundle();

        manager.replaceActiveSnapshot("scene", snapshot);
        manager.replaceActiveSnapshot("scene", snapshot);

        assertEquals(1, manager.activeSnapshotCount());
        assertEquals(0, manager.deferredDisposalCount());
    }

    @Test
    public void deferredDisposalIgnoresDuplicatesAndFlushClearsReferences() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle snapshot = bundle();

        manager.deferDispose(snapshot);
        manager.deferDispose(snapshot);
        assertEquals(1, manager.deferredDisposalCount());

        manager.flushDeferredDisposals();
        assertEquals(0, manager.deferredDisposalCount());
    }

    @Test
    public void disposeAllClearsActiveAndDeferredSnapshots() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle active = bundle();
        AtlasRuntimeService.TextureArrayBundle deferred = bundle();

        manager.replaceActiveSnapshot("scene", active);
        manager.deferDispose(deferred);
        manager.disposeAll();

        assertEquals(0, manager.activeSnapshotCount());
        assertEquals(0, manager.deferredDisposalCount());
    }

    @Test
    public void replacingWithNullRemovesAndDefersPreviousSnapshot() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle active = bundle();

        manager.replaceActiveSnapshot("scene", active);
        manager.replaceActiveSnapshot("scene", null);

        assertEquals(0, manager.activeSnapshotCount());
        assertEquals(1, manager.deferredDisposalCount());
    }

    @Test
    public void oldMarkDirtyUsesUnknownReason() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);

        manager.markDirty("scene");

        assertEquals(1, manager.dirtyReasonCount("scene"));
        assertTrue(manager.hasDirtyReason("scene", "unknown"));
    }

    @Test
    public void dirtyReasonsAccumulateAndResetAfterSync() {
        GpuSnapshotManager manager = instrumentedManager(new ArrayList<>());

        manager.markDirty("scene", "atlas-pack-applied");
        manager.markDirty("scene", "scene-atlas-loaded");
        manager.syncIfDirty("scene");

        assertEquals(0, manager.dirtyReasonCount("scene"));
        assertEquals(1, manager.rebuildCount());
        assertEquals(1, manager.rebuildCount("scene"));
    }

    @Test
    public void syncSkippedWhenSceneIsNotDirty() {
        GpuSnapshotManager manager = instrumentedManager(new ArrayList<>());

        manager.syncIfDirty("scene");

        assertEquals(1, manager.totalSyncAttempts());
        assertEquals(1, manager.skippedNotDirtyCount());
        assertEquals(0, manager.rebuildCount());
    }

    @Test
    public void currentScenePublishedHandleDoesNotRequireAnotherRebuild() {
        AtlasRuntimeService.TextureArrayBundle snapshot = bundleWithHandle(17);
        GpuSnapshotManager manager = instrumentedManager(new ArrayList<>(), snapshot);

        manager.markDirty("scene", "initial-publication");
        manager.syncIfDirty("scene");
        assertTrue(manager.isHandlePublishedInCurrentBundle("scene", 17));

        if (!manager.isHandlePublishedInCurrentBundle("scene", 17)) {
            manager.markDirty("scene", "redundant-rebind");
        }
        manager.syncIfDirty("scene");

        assertEquals(1, manager.rebuildCount());
        assertFalse(manager.hasDirtyReason("scene", "redundant-rebind"));
    }

    @Test
    public void absentHandleStillRequiresSnapshotInvalidation() {
        GpuSnapshotManager manager = instrumentedManager(
                new ArrayList<>(), bundleWithHandle(17));

        manager.markDirty("scene", "initial-publication");
        manager.syncIfDirty("scene");

        assertFalse(manager.isHandlePublishedInCurrentBundle("scene", 18));
    }

    @Test
    public void cachedSnapshotFromAnotherSceneIsNotTreatedAsCurrentlyBound() {
        AtlasRuntimeService.TextureArrayBundle first = bundleWithHandle(17);
        AtlasRuntimeService.TextureArrayBundle second = bundleWithHandle(23);
        GpuSnapshotManager manager = new GpuSnapshotManager(
                null,
                metricsBatch(),
                (sceneTag, diagnosticsEnabled) -> new SnapshotBuilder.BuildResult(
                        "first".equals(sceneTag) ? first : second,
                        1, 1, 0, 0L, 0L, 0L, 0L, 0L),
                false,
                0L,
                message -> { }
        );

        manager.markDirty("first", "publish-first");
        manager.syncIfDirty("first");
        manager.markDirty("second", "publish-second");
        manager.syncIfDirty("second");

        assertFalse(manager.isHandlePublishedInCurrentBundle("first", 17));
        assertTrue(manager.isHandlePublishedInCurrentBundle("second", 23));
    }

    @Test
    public void slowDiagnosticLogIncludesReasonsAndBuildBuckets() {
        List<String> logs = new ArrayList<>();
        GpuSnapshotManager manager = instrumentedManager(logs);

        manager.markDirty("scene", "atlas-pack-applied");
        manager.syncIfDirty("scene");

        assertEquals(1, logs.size());
        String log = logs.get(0);
        assertTrue(log.contains("GPU SNAPSHOT SYNC"));
        assertTrue(log.contains("scene=scene"));
        assertTrue(log.contains("reasons: atlas-pack-applied"));
        assertTrue(log.contains("textureArrayBuild(pixmapCopy+upload): 3.0ms"));
        assertTrue(log.contains("attempts/skipped/rebuilds: 1/0/1"));
        assertFalse(manager.hasDirtyReason("scene", "atlas-pack-applied"));
    }

    @Test
    public void generalStudioProfilerDoesNotEnableGpuSnapshotDiagnostics() {
        String oldProfiler = System.getProperty(StudioFrameProfiler.PROPERTY_ENABLED);
        String oldGpuDiagnostics = System.getProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS);
        try {
            System.setProperty(StudioFrameProfiler.PROPERTY_ENABLED, "true");
            System.clearProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS);

            assertFalse(GpuSnapshotManager.diagnosticsEnabledFromProperties());
        } finally {
            restoreProperty(StudioFrameProfiler.PROPERTY_ENABLED, oldProfiler);
            restoreProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS, oldGpuDiagnostics);
        }
    }

    @Test
    public void explicitGpuSnapshotDiagnosticsFlagEnablesDetailedLogs() {
        String oldProfiler = System.getProperty(StudioFrameProfiler.PROPERTY_ENABLED);
        String oldGpuDiagnostics = System.getProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS);
        try {
            System.clearProperty(StudioFrameProfiler.PROPERTY_ENABLED);
            System.setProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS, "true");

            assertTrue(GpuSnapshotManager.diagnosticsEnabledFromProperties());
        } finally {
            restoreProperty(StudioFrameProfiler.PROPERTY_ENABLED, oldProfiler);
            restoreProperty(GpuSnapshotManager.PROPERTY_DIAGNOSTICS, oldGpuDiagnostics);
        }
    }

    private static AtlasRuntimeService.TextureArrayBundle bundle() {
        return new AtlasRuntimeService.TextureArrayBundle(null, new IntIntMap());
    }

    private static AtlasRuntimeService.TextureArrayBundle bundleWithHandle(int handle) {
        IntIntMap handles = new IntIntMap();
        handles.put(handle, 0);
        return new AtlasRuntimeService.TextureArrayBundle(null, handles);
    }

    private static GpuSnapshotManager instrumentedManager(List<String> logs) {
        return instrumentedManager(logs, bundle());
    }

    private static GpuSnapshotManager instrumentedManager(
            List<String> logs,
            AtlasRuntimeService.TextureArrayBundle snapshot) {
        return new GpuSnapshotManager(
                null,
                metricsBatch(),
                (sceneTag, diagnosticsEnabled) -> new SnapshotBuilder.BuildResult(
                        snapshot,
                        2,
                        3,
                        0,
                        1_000_000L,
                        500_000L,
                        2_000_000L,
                        3_000_000L,
                        100_000L
                ),
                true,
                0L,
                logs::add
        );
    }

    private static games.pixscape.runtime.render.batch.MetricsBatch metricsBatch() {
        return (games.pixscape.runtime.render.batch.MetricsBatch) Proxy.newProxyInstance(
                GpuSnapshotManagerTest.class.getClassLoader(),
                new Class<?>[]{games.pixscape.runtime.render.batch.MetricsBatch.class},
                (proxy, method, args) -> null
        );
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
