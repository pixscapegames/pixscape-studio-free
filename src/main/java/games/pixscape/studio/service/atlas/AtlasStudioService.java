package games.pixscape.studio.service.atlas;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.PreparedAtlasPublication;
import games.pixscape.studio.service.ProjectFileCleanupService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

public final class AtlasStudioService extends AtlasRuntimeService {

    private final WorldCanvas canvas;

    private final AsyncAtlasRepackCoordinator<ScenePrepared> repackCoordinator;
    private volatile boolean disposed = false;
    private final Map<String, SceneAtlasCoverage.Validation> validatedAtlases = new HashMap<>();
    private PackCompletion lastCompletion;

    public record PackCompletion(String sceneTag, long generation, Throwable failure) {}

    private static final String TAG = "AtlasStudioService";

    private StudioAssetVisualResolver assetVisualResolver;

    /** Scene-only worker result: temporary atlas files plus the prepared GPU publication. */
    static final class ScenePrepared implements AutoCloseable {
        private final String sceneTag;
        private final FileHandle outputDir;
        private final FileHandle atlasFile;
        private final SceneAtlasCoverage.Validation validation;
        private final boolean inputsChangedDuringPack;
        private PreparedAtlasPublication preparedPublication;

        ScenePrepared(String sceneTag, FileHandle outputDir, FileHandle atlasFile,
                              SceneAtlasCoverage.Validation validation,
                              PreparedAtlasPublication preparedPublication,
                              boolean inputsChangedDuringPack) {
            this.sceneTag = sceneTag;
            this.outputDir = outputDir;
            this.atlasFile = atlasFile;
            this.validation = validation;
            this.inputsChangedDuringPack = inputsChangedDuringPack;
            this.preparedPublication = preparedPublication;
        }

        private String sceneTag() {
            return sceneTag;
        }

        private FileHandle outputDir() {
            return outputDir;
        }

        private FileHandle atlasFile() {
            return atlasFile;
        }

        private PreparedAtlasPublication takePreparedPublication() {
            PreparedAtlasPublication taken = preparedPublication;
            preparedPublication = null;
            return taken;
        }

        @Override
        public void close() {
            if (preparedPublication != null) {
                preparedPublication.close();
                preparedPublication = null;
            }
            try {
                if (outputDir.exists()) outputDir.deleteDirectory();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public AtlasStudioService(WorldCanvas canvas) {
        this.canvas = canvas;
        this.repackCoordinator = new AsyncAtlasRepackCoordinator(this::packAsyncToTemp);
    }

    AtlasStudioService(WorldCanvas canvas,
                       AsyncAtlasRepackCoordinator.PackRunner<ScenePrepared> runner,
                       ExecutorService executor, LongSupplier clock) {
        this.canvas = canvas;
        this.repackCoordinator = new AsyncAtlasRepackCoordinator<>(
                runner, executor, clock, 0);
    }

    public void setAssetVisualResolver(StudioAssetVisualResolver assetVisualResolver) {
        this.assetVisualResolver = assetVisualResolver;
    }

    public void requestAsyncPack(String sceneTag) {
        requestAsyncPack(sceneTag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
    }

    public long requestAsyncPack(String sceneTag, AsyncAtlasRepackCoordinator.RepackReason reason) {
        if (disposed) {
            if (reason == AsyncAtlasRepackCoordinator.RepackReason.SAVE) {
                throw new IllegalStateException("Atlas service is disposed during save");
            }
            return repackCoordinator.currentGeneration();
        }
        long generation = repackCoordinator.requestAsyncPack(sceneTag, reason);
        markPublicationPending(sceneTag);
        return generation;
    }

    public void markDirty(String sceneTag) {
        requestAsyncPack(sceneTag);
    }

    public void updateAsyncPack() {
        if (disposed) return;
        repackCoordinator.update();
    }

    public boolean isPackInProgress() {
        return repackCoordinator.isAsyncPackRunning();
    }

    public boolean isPackRequested() {
        return repackCoordinator.isAsyncPackRequested();
    }

    public boolean hasAsyncPackQueuedOrRunningFor(String sceneTag) {
        return repackCoordinator.hasQueuedOrRunningFor(sceneTag);
    }

    public long currentPackGeneration() { return repackCoordinator.currentGeneration(); }
    public String currentPackSceneTag() { return repackCoordinator.currentTargetKey(); }
    public PackCompletion lastPackCompletion() { return lastCompletion; }

    /** Reuses the last complete descriptor check while inputs and published files are unchanged. */
    public boolean coversCurrentInput(String tag, FileHandle inputDir, FileHandle atlasFile) {
        try {
            SceneAtlasCoverage.InputSnapshot current = SceneAtlasCoverage.snapshot(inputDir);
            SceneAtlasCoverage.Validation cached = validatedAtlases.get(tag);
            if (cached != null && cached.stillCurrent(current, atlasFile)) return true;
            SceneAtlasCoverage.Validation checked = SceneAtlasCoverage.validate(current, atlasFile);
            validatedAtlases.put(tag, checked);
            return true;
        } catch (RuntimeException invalid) {
            validatedAtlases.remove(tag);
            return false;
        }
    }

    private ScenePrepared packAsyncToTemp(
            String sceneTag,
            long generation,
            AsyncAtlasRepackCoordinator.RepackReason reason
    ) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
        FileHandle tmpRoot = atlasesDir.child(".tmp");
        FileHandle outputDir = tmpRoot.child(sceneTag + "-gen" + generation);

        if (outputDir.exists()) {
            outputDir.deleteDirectory();
        }
        outputDir.mkdirs();

        PreparedAtlasPublication preparedPublication = null;
        try {
            SceneAtlasCoverage.Validation validation = SceneAtlasLoaderService.packSceneAtlasToDirectory(
                    cfg,
                    sceneTag,
                    projectDir,
                    outputDir
            );

            FileHandle atlasFile = outputDir.child(sceneTag + ".atlas");
            FileHandle pngFile = outputDir.child(sceneTag + ".png");

            waitForAtlasFiles(atlasFile, pngFile);
            preparedPublication = PreparedAtlasPublication.prepare(sceneTag, generation, atlasFile);

            return new ScenePrepared(
                    sceneTag,
                    outputDir,
                    atlasFile,
                    validation,
                    preparedPublication,
                    false
            );
        } catch (SceneAtlasLoaderService.InputsChangedDuringPackException changed) {
            return new ScenePrepared(sceneTag, outputDir, outputDir.child(sceneTag + ".atlas"),
                    null, null, true);
        } catch (RuntimeException failure) {
            if (preparedPublication != null) preparedPublication.close();
            outputDir.deleteDirectory();
            throw failure;
        }
    }

    private static void waitForAtlasFiles(FileHandle atlasFile, FileHandle pngFile) {
        long timeout = System.currentTimeMillis() + 5000L;

        while (System.currentTimeMillis() < timeout) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Async pack interrupted");
            }

            if (atlasFile.exists() && pngFile.exists() && pngFile.length() > 0) {
                return;
            }

            try {
                Thread.sleep(10L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Async pack interrupted", ex);
            }
        }

        throw new IllegalStateException("Atlas files not fully written: " + atlasFile.path());
    }

    // ============================================================
    // APPLY ON MAIN THREAD
    // ============================================================

    public void applyIfPackReady() {
        AsyncAtlasRepackCoordinator.PackFailure workerFailure = repackCoordinator.pollFailure();
        if (workerFailure != null) {
            lastCompletion = new PackCompletion(workerFailure.targetKey(), workerFailure.generation(),
                    workerFailure.cause());
        }
        AsyncAtlasRepackCoordinator.Prepared<ScenePrepared> preparedResult =
                repackCoordinator.pollReadyAsyncPack();

        if (preparedResult == null) return;

        final long generation = preparedResult.generation();
        ScenePrepared artifact = preparedResult.takePayload();

        final String tag = artifact.sceneTag();

        Gdx.app.log(TAG, "Applying async pack for scene=" + tag + " gen=" + generation);

        PreparedAtlasPublication.Uploaded uploaded = null;
        long applyStarted = System.nanoTime();
        long deleteAndCopyNs = 0L;
        long atlasPublicationNs = 0L;
        long publishNs = 0L;
        long workerPagePreparationNs = 0L;
        long workerPageFileReadNs = 0L;
        long workerPageDecodeNormalizeNs = 0L;
        long pageTextureUploadNs = 0L;
        long textureArrayUploadNs = 0L;
        long atlasAssemblyNs = 0L;
        try {
            if (artifact.inputsChangedDuringPack) {
                Gdx.app.log(TAG, "Discarding obsolete atlas inputs scene=" + tag + " gen=" + generation);
                requestAsyncPack(tag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
                return;
            }
            if (artifact.validation == null) {
                throw new IllegalStateException("Prepared atlas has no input validation: " + tag);
            }
            ProjectConfig cfg = ProjectConfig.getInstance();
            FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
            FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
            FileHandle currentInput = atlasesDir.child(StudioFs.DIR_INPUT).child(tag);
            if (!artifact.validation.input().sameFiles(
                    SceneAtlasCoverage.snapshot(currentInput))) {
                Gdx.app.log(TAG, "Discarding obsolete atlas inputs scene=" + tag + " gen=" + generation);
                requestAsyncPack(tag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
                return;
            }
            GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
            if (snapshotManager == null) {
                throw new IllegalStateException("GPU snapshot manager is unavailable.");
            }

            PreparedAtlasPublication preparedPublication = artifact.takePreparedPublication();
            if (preparedPublication == null) {
                throw new IllegalStateException("Atlas artifact has no prepared publication candidate.");
            }
            long currentGeneration = repackCoordinator.currentGeneration();
            if (!snapshotManager.acceptPreparedPublication(preparedPublication, currentGeneration)) return;

            uploaded = snapshotManager.uploadPreparedPublication(
                    tag,
                    generation,
                    currentGeneration,
                    atlasesDir
            );
            if (uploaded == null) return;
            workerPagePreparationNs = uploaded.pagePixelsPreparationNs();
            workerPageFileReadNs = uploaded.pageFileReadNs();
            workerPageDecodeNormalizeNs = uploaded.pageDecodeNormalizeNs();
            pageTextureUploadNs = uploaded.pageTextureUploadNs();
            textureArrayUploadNs = uploaded.textureArrayUploadNs();
            atlasAssemblyNs = uploaded.atlasAssemblyNs();
            if (generation != repackCoordinator.currentGeneration()) return;

            long phaseStarted = System.nanoTime();
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, tag);
            copyAtlasArtifactToFinalDir(artifact, atlasesDir);
            deleteAndCopyNs = System.nanoTime() - phaseStarted;
            SceneAtlasCoverage.Validation publishedValidation = artifact.validation.at(
                    atlasesDir.child(tag + ".atlas"));

            phaseStarted = System.nanoTime();
            publishPreparedAtlas(tag, uploaded.takeAtlas());
            atlasPublicationNs = System.nanoTime() - phaseStarted;

            phaseStarted = System.nanoTime();
            boolean published = snapshotManager.publishPreparedSnapshot(
                    tag,
                    generation,
                    repackCoordinator.currentGeneration(),
                    uploaded
            );
            publishNs = System.nanoTime() - phaseStarted;
            uploaded = null;
            if (!published) {
                throw new IllegalStateException("Prepared GPU snapshot became stale during publication.");
            }

            RenderRebindHelper.rebindAfterPreparedSnapshot(
                    canvas,
                    tag,
                    assetVisualResolver
            );
            rebindTiles();

            canvas.requestParticleRuntimeAvailabilityRefresh();
            validatedAtlases.put(tag, publishedValidation);
            lastCompletion = new PackCompletion(tag, generation, null);
        } catch (RuntimeException | Error failure) {
            validatedAtlases.remove(tag);
            lastCompletion = new PackCompletion(tag, generation, failure);
            if (Gdx.app != null) Gdx.app.error(TAG,
                    "Atlas publication failed scene=" + tag + " gen=" + generation, failure);
        } finally {
            if (uploaded != null) uploaded.close();
            artifact.close();
            if (Boolean.getBoolean(GpuSnapshotManager.PROPERTY_DIAGNOSTICS)) {
                Gdx.app.log(TAG,
                        "PREPARED_ATLAS_APPLY scene=" + tag
                                + " generation=" + generation
                                + " fileReplaceMs=" + ms(deleteAndCopyNs)
                                + " workerPageDecodePrepareMs=" + ms(workerPagePreparationNs)
                                + " workerPageFileReadMs=" + ms(workerPageFileReadNs)
                                + " workerPageDecodeNormalizeMs=" + ms(workerPageDecodeNormalizeNs)
                                + " pageTextureGlUploadMs=" + ms(pageTextureUploadNs)
                                + " textureArrayGlUploadMs=" + ms(textureArrayUploadNs)
                                + " atlasRegionAssemblyMs=" + ms(atlasAssemblyNs)
                                + " runtimeAtlasPublicationMs=" + ms(atlasPublicationNs)
                                + " preparedPublishMs=" + ms(publishNs)
                                + " totalMs=" + ms(System.nanoTime() - applyStarted));
            }
        }
    }

    private void publishPreparedAtlas(String tag, TextureAtlas atlas) {
        publishOwnedAtlas(tag, atlas);
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAtlasTag(tag);
        }
        requestTiledFallbackValidation();
    }

    private static float ms(long ns) {
        return ns / 1_000_000f;
    }

    private static void copyAtlasArtifactToFinalDir(ScenePrepared artifact,
                                                    FileHandle atlasesDir) {
        String tag = artifact.sceneTag();

        artifact.atlasFile().copyTo(atlasesDir.child(tag + ".atlas"));

        for (FileHandle child : artifact.outputDir().list()) {
            if (child == null || child.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(child.extension())) continue;

            String name = child.name();
            if (name.equals(tag + ".png") || name.startsWith(tag + "-")) {
                child.copyTo(atlasesDir.child(name));
            }
        }
    }

    private void rebindTiles() {
        World world = canvas.getEcsWorld();
        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();

        for (int i = 0; i < bag.size(); i++) {
            TiledLayerComponent tiled = mTiled.get(data[i]);
            if (tiled != null && tiled.data != null) {
                tiled.data.markAllChunksContentDirty();
            }
        }
    }

    @Override
    public void load(String tag, FileHandle atlasFile) {
        validatedAtlases.remove(tag);
        super.load(tag, atlasFile);
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAtlasTag(tag);
        }
        requestTiledFallbackValidation();
    }

    @Override
    public void unload(String tag) {
        validatedAtlases.remove(tag);
        super.unload(tag);
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAtlasTag(tag);
        }
        requestTiledFallbackValidation();
    }

    @Override
    public void unloadAll() {
        validatedAtlases.clear();
        super.unloadAll();
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAll();
        }
        requestTiledFallbackValidation();
    }

    private void requestTiledFallbackValidation() {
        if (canvas != null) {
            canvas.requestTiledFallbackValidation();
        }
    }

    public synchronized void disposeAsyncPack() {
        if (disposed) return;
        disposed = true;
        repackCoordinator.dispose();
    }
}
