package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.debug.StudioFrameProfiler;
import games.pixscape.studio.service.atlas.AtlasStudioService;

public final class GpuSnapshotManager {

    public static final String PROPERTY_DIAGNOSTICS = "pixscape.studio.gpuSnapshotDiagnostics";

    private static final String TAG = "GpuSnapshotManager";
    private static final String REASON_UNKNOWN = "unknown";

    private final AtlasStudioService atlasStudioService;
    private final MetricsBatch metricsBatch;
    private final SnapshotSource snapshotSource;
    private final boolean diagnosticsEnabled;
    private final long diagnosticThresholdNs;
    private final LogSink logSink;

    private final ObjectMap<String, AtlasRuntimeService.TextureArrayBundle> activeSnapshots = new ObjectMap<>();
    private final ObjectMap<String, PreparedAtlasPublication> preparedPublications = new ObjectMap<>();
    private final ObjectSet<String> dirtyTags = new ObjectSet<>();
    private final ObjectMap<String, DirtyInfo> dirtyInfos = new ObjectMap<>();
    private final Array<AtlasRuntimeService.TextureArrayBundle> deferredDisposals = new Array<>();
    private final ObjectSet<AtlasRuntimeService.TextureArrayBundle> deferredDisposalSet = new ObjectSet<>();
    private final ObjectMap<String, Long> lastSyncAtNsByScene = new ObjectMap<>();
    private final ObjectMap<String, Integer> rebuildCountByScene = new ObjectMap<>();
    private final StringBuilder report = new StringBuilder(768);

    private long totalSyncAttempts;
    private long skippedNotDirtyCount;
    private long rebuildCount;
    private long lastRebuildNs;

    public GpuSnapshotManager(AtlasStudioService atlasStudioService, MetricsBatch metricsBatch) {
        this(
                atlasStudioService,
                metricsBatch,
                new SnapshotBuilderSource(new SnapshotBuilder(atlasStudioService)),
                diagnosticsEnabledFromProperties(),
                thresholdNsFromProperties(),
                GpuSnapshotManager::logToGdx
        );
    }

    GpuSnapshotManager(AtlasStudioService atlasStudioService,
                       MetricsBatch metricsBatch,
                       SnapshotSource snapshotSource,
                       boolean diagnosticsEnabled,
                       long diagnosticThresholdNs,
                       LogSink logSink) {
        this.atlasStudioService = atlasStudioService;
        this.metricsBatch = metricsBatch;
        this.snapshotSource = snapshotSource;
        this.diagnosticsEnabled = diagnosticsEnabled;
        this.diagnosticThresholdNs = Math.max(0L, diagnosticThresholdNs);
        this.logSink = logSink != null ? logSink : GpuSnapshotManager::logToGdx;
    }

    public void markDirty(String sceneTag) {
        markDirty(sceneTag, REASON_UNKNOWN);
    }

    public void markDirty(String sceneTag, String reason) {
        verifyTag(sceneTag);
        dirtyTags.add(sceneTag);

        DirtyInfo info = dirtyInfos.get(sceneTag);
        if (info == null) {
            info = new DirtyInfo(System.nanoTime());
            dirtyInfos.put(sceneTag, info);
        }
        info.reasons.add(normalizeReason(reason));
    }

    public boolean acceptPreparedPublication(PreparedAtlasPublication candidate, long currentGeneration) {
        if (candidate == null) return false;
        verifyTag(candidate.sceneTag());
        if (candidate.generation() != currentGeneration) {
            candidate.close();
            return false;
        }
        PreparedAtlasPublication previous = preparedPublications.put(candidate.sceneTag(), candidate);
        if (previous != null && previous != candidate) previous.close();
        return true;
    }

    public PreparedAtlasPublication.Uploaded uploadPreparedPublication(String sceneTag,
                                                                       long generation,
                                                                       long currentGeneration,
                                                                       FileHandle publishedImagesDir) {
        return uploadPreparedPublication(
                sceneTag,
                generation,
                currentGeneration,
                publishedImagesDir,
                null,
                null
        );
    }

    PreparedAtlasPublication.Uploaded uploadPreparedPublication(
            String sceneTag,
            long generation,
            long currentGeneration,
            FileHandle publishedImagesDir,
            PreparedAtlasPublication.PageTextureUploader pageUploader,
            PreparedAtlasPublication.TextureArrayUploader arrayUploader) {
        verifyTag(sceneTag);
        PreparedAtlasPublication candidate = preparedPublications.get(sceneTag);
        if (candidate == null) return null;
        if (candidate.generation() != generation || generation != currentGeneration) {
            preparedPublications.remove(sceneTag);
            candidate.close();
            return null;
        }

        preparedPublications.remove(sceneTag);
        try {
            return pageUploader != null || arrayUploader != null
                    ? candidate.upload(publishedImagesDir, pageUploader, arrayUploader)
                    : candidate.upload(publishedImagesDir);
        } finally {
            candidate.close();
        }
    }

    public boolean publishPreparedSnapshot(String sceneTag,
                                           long generation,
                                           long currentGeneration,
                                           PreparedAtlasPublication.Uploaded uploaded) {
        verifyTag(sceneTag);
        if (uploaded == null) return false;
        if (!sceneTag.equals(uploaded.sceneTag())
                || generation != uploaded.generation()
                || generation != currentGeneration) {
            uploaded.close();
            return false;
        }

        AtlasRuntimeService.TextureArrayBundle next = null;
        try {
            next = uploaded.buildBundle();
            if (metricsBatch != null) metricsBatch.setTextureArrayBundle(next);
            ReplacementResult replacement = replaceActiveSnapshot(sceneTag, next, diagnosticsEnabled);
            next = null;

            dirtyTags.remove(sceneTag);
            dirtyInfos.remove(sceneTag);
            rebuildCount++;
            rebuildCountByScene.put(sceneTag, rebuildCountByScene.get(sceneTag, 0) + 1);
            if (diagnosticsEnabled) {
                logPreparedDiagnostic(sceneTag, uploaded, replacement);
            }
            return true;
        } finally {
            if (next != null && next.textureArray != null) next.textureArray.dispose();
            uploaded.close();
        }
    }

    public void syncIfDirty(String sceneTag) {
        verifyTag(sceneTag);
        totalSyncAttempts++;

        long setupStartNs = diagnosticsEnabled ? System.nanoTime() : 0L;
        if (!dirtyTags.contains(sceneTag)) {
            skippedNotDirtyCount++;
            return;
        }

        DirtyInfo dirtyInfo = dirtyInfos.get(sceneTag);
        boolean existedBefore = activeSnapshots.containsKey(sceneTag);
        Long previousSyncAtNs = lastSyncAtNsByScene.get(sceneTag);
        long dirtyAgeNs = diagnosticsEnabled && dirtyInfo != null
                ? Math.max(0L, setupStartNs - dirtyInfo.firstMarkedAtNs)
                : -1L;
        long sinceLastSyncNs = diagnosticsEnabled && previousSyncAtNs != null
                ? Math.max(0L, setupStartNs - previousSyncAtNs)
                : -1L;
        long setupNs = diagnosticsEnabled ? System.nanoTime() - setupStartNs : 0L;

        long buildStartNs = diagnosticsEnabled ? System.nanoTime() : 0L;
        SnapshotBuilder.BuildResult result =
                snapshotSource.buildSnapshot(sceneTag, diagnosticsEnabled);
        long buildSnapshotNs = diagnosticsEnabled ? System.nanoTime() - buildStartNs : 0L;
        AtlasRuntimeService.TextureArrayBundle next = result.bundle;

        long metricsStartNs = diagnosticsEnabled ? System.nanoTime() : 0L;
        if (metricsBatch != null) {
            metricsBatch.setTextureArrayBundle(next);
        }
        long metricsSetNs = diagnosticsEnabled ? System.nanoTime() - metricsStartNs : 0L;

        ReplacementResult replacement = replaceActiveSnapshot(sceneTag, next, diagnosticsEnabled);
        dirtyTags.remove(sceneTag);
        dirtyInfos.remove(sceneTag);

        long nowNs = diagnosticsEnabled ? System.nanoTime() : 0L;
        if (diagnosticsEnabled) {
            lastSyncAtNsByScene.put(sceneTag, nowNs);
        }
        rebuildCount++;
        lastRebuildNs = buildSnapshotNs + setupNs + metricsSetNs + replacement.swapNs + replacement.deferNs;
        rebuildCountByScene.put(sceneTag, rebuildCountByScene.get(sceneTag, 0) + 1);

        long totalNs = diagnosticsEnabled
                ? Math.max(0L, System.nanoTime() - setupStartNs)
                : 0L;
        if (diagnosticsEnabled && totalNs >= diagnosticThresholdNs) {
            logDiagnostic(
                    sceneTag,
                    dirtyInfo,
                    result,
                    replacement,
                    totalNs,
                    setupNs,
                    buildSnapshotNs,
                    metricsSetNs,
                    existedBefore,
                    dirtyAgeNs,
                    sinceLastSyncNs
            );
        }
    }

    ReplacementResult replaceActiveSnapshot(String sceneTag,
                                            AtlasRuntimeService.TextureArrayBundle next) {
        return replaceActiveSnapshot(sceneTag, next, false);
    }

    ReplacementResult replaceActiveSnapshot(String sceneTag,
                                            AtlasRuntimeService.TextureArrayBundle next,
                                            boolean measure) {
        verifyTag(sceneTag);

        long swapStartNs = measure ? System.nanoTime() : 0L;
        AtlasRuntimeService.TextureArrayBundle previous;
        if (next == null) {
            previous = activeSnapshots.remove(sceneTag);
        } else {
            previous = activeSnapshots.put(sceneTag, next);
        }
        long swapNs = measure ? System.nanoTime() - swapStartNs : 0L;

        long deferStartNs = measure ? System.nanoTime() : 0L;
        boolean deferredOld = false;
        if (previous != null && previous != next) {
            deferredOld = deferDispose(previous);
        }
        long deferNs = measure ? System.nanoTime() - deferStartNs : 0L;
        return new ReplacementResult(previous, deferredOld, swapNs, deferNs);
    }

    boolean deferDispose(AtlasRuntimeService.TextureArrayBundle bundle) {
        if (bundle == null) return false;
        if (deferredDisposalSet.add(bundle)) {
            deferredDisposals.add(bundle);
            return true;
        }
        return false;
    }

    public void flushDeferredDisposals() {
        if (deferredDisposals.isEmpty()) return;
        for (AtlasRuntimeService.TextureArrayBundle bundle : deferredDisposals) {
            if (bundle != null && bundle.textureArray != null) {
                bundle.textureArray.dispose();
            }
        }
        deferredDisposals.clear();
        deferredDisposalSet.clear();
    }

    public void disposeAll() {
        for (PreparedAtlasPublication candidate : preparedPublications.values()) {
            if (candidate != null) candidate.close();
        }
        preparedPublications.clear();
        for (AtlasRuntimeService.TextureArrayBundle bundle : activeSnapshots.values()) {
            if (bundle != null && !deferredDisposalSet.contains(bundle) && bundle.textureArray != null) {
                bundle.textureArray.dispose();
            }
        }
        activeSnapshots.clear();
        dirtyTags.clear();
        dirtyInfos.clear();
        lastSyncAtNsByScene.clear();
        rebuildCountByScene.clear();
        flushDeferredDisposals();
    }

    int activeSnapshotCount() {
        return activeSnapshots.size;
    }

    int preparedPublicationCount() {
        return preparedPublications.size;
    }

    AtlasRuntimeService.TextureArrayBundle activeSnapshot(String sceneTag) {
        return activeSnapshots.get(sceneTag);
    }

    int deferredDisposalCount() {
        return deferredDisposals.size;
    }

    int dirtyReasonCount(String sceneTag) {
        DirtyInfo info = dirtyInfos.get(sceneTag);
        return info != null ? info.reasons.size : 0;
    }

    boolean hasDirtyReason(String sceneTag, String reason) {
        DirtyInfo info = dirtyInfos.get(sceneTag);
        return info != null && info.reasons.contains(reason);
    }

    long totalSyncAttempts() {
        return totalSyncAttempts;
    }

    long skippedNotDirtyCount() {
        return skippedNotDirtyCount;
    }

    long rebuildCount() {
        return rebuildCount;
    }

    int rebuildCount(String sceneTag) {
        return rebuildCountByScene.get(sceneTag, 0);
    }

    private void logDiagnostic(String sceneTag,
                               DirtyInfo dirtyInfo,
                               SnapshotBuilder.BuildResult result,
                               ReplacementResult replacement,
                               long totalNs,
                               long setupNs,
                               long buildSnapshotNs,
                               long metricsSetNs,
                               boolean existedBefore,
                               long dirtyAgeNs,
                               long sinceLastSyncNs) {
        TextureArray textureArray = result.bundle != null ? result.bundle.textureArray : null;

        report.setLength(0);
        report.append("GPU SNAPSHOT SYNC ");
        appendMs(report, totalNs);
        report.append("ms scene=").append(sceneTag);
        report.append('\n').append("  reasons: ");
        appendReasons(report, dirtyInfo);
        report.append('\n').append("  existedBefore: ").append(existedBefore);
        report.append('\n').append("  dirtyAge: ");
        appendOptionalMs(report, dirtyAgeNs);
        report.append('\n').append("  sinceLastSync: ");
        appendOptionalMs(report, sinceLastSyncNs);
        report.append('\n').append("  attempts/skipped/rebuilds: ")
                .append(totalSyncAttempts).append('/')
                .append(skippedNotDirtyCount).append('/')
                .append(rebuildCount);
        report.append('\n').append("  sceneRebuilds: ").append(rebuildCount(sceneTag));
        report.append('\n').append("  pages: ").append(result.packedCount);
        report.append('\n').append("  layers: ").append(result.totalLayers)
                .append(" whiteLayer=").append(result.whiteLayer);
        report.append('\n').append("  textureArray: ");
        appendTextureArray(report, textureArray);
        report.append('\n').append("  setup: ");
        appendMs(report, setupNs);
        report.append("ms");
        report.append('\n').append("  buildSnapshot: ");
        appendMs(report, buildSnapshotNs);
        report.append("ms");
        report.append('\n').append("    internalTextureInit: ");
        appendMs(report, result.internalTextureInitNs);
        report.append("ms");
        report.append('\n').append("    atlasLookup: ");
        appendMs(report, result.atlasLookupNs);
        report.append("ms");
        report.append('\n').append("    pageTextureDiscovery: ");
        appendMs(report, result.pageTextureDiscoveryNs);
        report.append("ms");
        report.append('\n').append("    textureArrayBuild(pixmapCopy+upload): ");
        appendMs(report, result.textureArrayBuildNs);
        report.append("ms");
        report.append('\n').append("    whiteLayerLookup: ");
        appendMs(report, result.whiteLayerLookupNs);
        report.append("ms");
        report.append('\n').append("  metricsSetBundle: ");
        appendMs(report, metricsSetNs);
        report.append("ms");
        report.append('\n').append("  swap: ");
        appendMs(report, replacement.swapNs);
        report.append("ms");
        report.append('\n').append("  deferOld: ");
        appendMs(report, replacement.deferNs);
        report.append("ms queued=").append(replacement.deferredOld);
        report.append('\n').append("  deferredPending: ").append(deferredDisposals.size);
        report.append('\n').append("  lastRebuild: ");
        appendMs(report, lastRebuildNs);
        report.append("ms");

        logSink.log(report.toString());
    }

    private void logPreparedDiagnostic(String sceneTag,
                                       PreparedAtlasPublication.Uploaded uploaded,
                                       ReplacementResult replacement) {
        report.setLength(0);
        report.append("GPU SNAPSHOT PREPARED PUBLISH scene=").append(sceneTag);
        report.append(" generation=").append(uploaded.generation());
        report.append('\n').append("  pages: ").append(uploaded.pageCount());
        report.append('\n').append("  cpuCandidate: ").append(uploaded.cpuByteSize()).append(" bytes");
        report.append('\n').append("  backgroundCpuPrepare: ");
        appendMs(report, uploaded.preparationNs());
        report.append("ms");
        report.append('\n').append("    metadataParse: ");
        appendMs(report, uploaded.metadataPreparationNs());
        report.append("ms");
        report.append('\n').append("    pageFileRead: ");
        appendMs(report, uploaded.pageFileReadNs());
        report.append("ms");
        report.append('\n').append("    pageDecodeNormalize: ");
        appendMs(report, uploaded.pageDecodeNormalizeNs());
        report.append("ms");
        report.append('\n').append("  pageTextureGlUpload: ");
        appendMs(report, uploaded.pageTextureUploadNs());
        report.append("ms");
        report.append('\n').append("  textureArrayGlUpload: ");
        appendMs(report, uploaded.textureArrayUploadNs());
        report.append("ms");
        report.append('\n').append("  atlasRegionAssembly: ");
        appendMs(report, uploaded.atlasAssemblyNs());
        report.append("ms");
        report.append('\n').append("  swap: ");
        appendMs(report, replacement.swapNs);
        report.append("ms");
        report.append('\n').append("  deferOld: ");
        appendMs(report, replacement.deferNs);
        report.append("ms queued=").append(replacement.deferredOld);
        logSink.log(report.toString());
    }

    private static void appendReasons(StringBuilder out, DirtyInfo dirtyInfo) {
        if (dirtyInfo == null || dirtyInfo.reasons.isEmpty()) {
            out.append(REASON_UNKNOWN);
            return;
        }

        boolean first = true;
        for (String reason : dirtyInfo.reasons) {
            if (!first) {
                out.append(", ");
            }
            out.append(reason);
            first = false;
        }
    }

    private static void appendTextureArray(StringBuilder out, TextureArray textureArray) {
        if (textureArray == null) {
            out.append("<none>");
            return;
        }
        out.append(textureArray.getWidth())
                .append('x')
                .append(textureArray.getHeight())
                .append("x")
                .append(textureArray.getDepth());
    }

    private static void appendOptionalMs(StringBuilder out, long ns) {
        if (ns < 0L) {
            out.append("<first>");
            return;
        }
        appendMs(out, ns);
        out.append("ms");
    }

    private static void appendMs(StringBuilder out, long ns) {
        long tenths = (ns + 50_000L) / 100_000L;
        out.append(tenths / 10L).append('.').append(tenths % 10L);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return REASON_UNKNOWN;
        }
        return reason;
    }

    static boolean diagnosticsEnabledFromProperties() {
        return Boolean.getBoolean(PROPERTY_DIAGNOSTICS);
    }

    private static long thresholdNsFromProperties() {
        String value = System.getProperty(StudioFrameProfiler.PROPERTY_THRESHOLD_MS);
        double thresholdMs = StudioFrameProfiler.DEFAULT_THRESHOLD_MS;
        if (value != null && !value.isBlank()) {
            try {
                thresholdMs = Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                thresholdMs = StudioFrameProfiler.DEFAULT_THRESHOLD_MS;
            }
        }
        return Math.max(0L, (long) (thresholdMs * 1_000_000.0));
    }

    private static void logToGdx(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        } else {
            System.out.println(TAG + ": " + message);
        }
    }

    private void verifyTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalStateException("Scene tag must be non-null and non-empty");
        }
    }

    interface SnapshotSource {
        SnapshotBuilder.BuildResult buildSnapshot(String sceneTag, boolean diagnosticsEnabled);
    }

    interface LogSink {
        void log(String message);
    }

    static final class ReplacementResult {
        final AtlasRuntimeService.TextureArrayBundle previous;
        final boolean deferredOld;
        final long swapNs;
        final long deferNs;

        ReplacementResult(AtlasRuntimeService.TextureArrayBundle previous,
                          boolean deferredOld,
                          long swapNs,
                          long deferNs) {
            this.previous = previous;
            this.deferredOld = deferredOld;
            this.swapNs = swapNs;
            this.deferNs = deferNs;
        }
    }

    private static final class DirtyInfo {
        final long firstMarkedAtNs;
        final ObjectSet<String> reasons = new ObjectSet<>();

        DirtyInfo(long firstMarkedAtNs) {
            this.firstMarkedAtNs = firstMarkedAtNs;
        }
    }

    private static final class SnapshotBuilderSource implements SnapshotSource {
        private final SnapshotBuilder snapshotBuilder;

        SnapshotBuilderSource(SnapshotBuilder snapshotBuilder) {
            this.snapshotBuilder = snapshotBuilder;
        }

        @Override
        public SnapshotBuilder.BuildResult buildSnapshot(String sceneTag, boolean diagnosticsEnabled) {
            return snapshotBuilder.buildSnapshot(sceneTag, diagnosticsEnabled);
        }
    }
}
