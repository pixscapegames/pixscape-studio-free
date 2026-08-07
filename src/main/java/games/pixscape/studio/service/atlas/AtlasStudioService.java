package games.pixscape.studio.service.atlas;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.ProjectFileCleanupService;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.PreparedGpuSnapshot;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.ui.main.WorldCanvas;

public final class AtlasStudioService extends AtlasRuntimeService {

    private final WorldCanvas canvas;

    private final AsyncAtlasRepackCoordinator repackCoordinator;
    private volatile boolean disposed = false;

    private static final String TAG = "AtlasStudioService";

    private StudioAssetVisualResolver assetVisualResolver;

    public AtlasStudioService(WorldCanvas canvas) {
        this.canvas = canvas;
        this.repackCoordinator = new AsyncAtlasRepackCoordinator(this::packAsyncToTemp);
    }

    public void setAssetVisualResolver(StudioAssetVisualResolver assetVisualResolver) {
        this.assetVisualResolver = assetVisualResolver;
    }

    public void requestAsyncPack(String sceneTag) {
        requestAsyncPack(sceneTag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
    }

    public void requestAsyncPack(String sceneTag, AsyncAtlasRepackCoordinator.RepackReason reason) {
        if (disposed) return;
        repackCoordinator.requestAsyncPack(sceneTag, reason);
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

    private AsyncAtlasRepackCoordinator.RepackArtifact packAsyncToTemp(
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

        PreparedGpuSnapshot preparedSnapshot = null;
        try {
            SceneAtlasLoaderService.packSceneAtlasToDirectory(
                    cfg,
                    sceneTag,
                    projectDir,
                    outputDir
            );

            FileHandle atlasFile = outputDir.child(sceneTag + ".atlas");
            FileHandle pngFile = outputDir.child(sceneTag + ".png");

            waitForAtlasFiles(atlasFile, pngFile);
            preparedSnapshot = PreparedGpuSnapshot.prepare(sceneTag, generation, atlasFile);

            return new AsyncAtlasRepackCoordinator.RepackArtifact(
                    sceneTag,
                    generation,
                    outputDir,
                    atlasFile,
                    pngFile,
                    preparedSnapshot
            );
        } catch (RuntimeException failure) {
            if (preparedSnapshot != null) preparedSnapshot.close();
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
        AsyncAtlasRepackCoordinator.RepackArtifact artifact =
                repackCoordinator.pollReadyAsyncPack();

        if (artifact == null) return;

        final String tag = artifact.sceneTag();
        final long generation = artifact.generation();

        Gdx.app.log(TAG, "Applying async pack for scene=" + tag + " gen=" + generation);

        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
        FileHandle finalAtlasFile = atlasesDir.child(tag + ".atlas");

        PreparedGpuSnapshot.Uploaded uploaded = null;
        long applyStarted = System.nanoTime();
        long deleteAndCopyNs = 0L;
        long atlasLoadNs = 0L;
        long publishNs = 0L;
        try {
            GpuSnapshotManager snapshotManager = canvas.getGpuSnapshotManager();
            if (snapshotManager == null) {
                throw new IllegalStateException("GPU snapshot manager is unavailable.");
            }

            PreparedGpuSnapshot preparedSnapshot = artifact.takePreparedSnapshot();
            if (preparedSnapshot == null) {
                throw new IllegalStateException("Atlas artifact has no prepared GPU snapshot.");
            }
            long currentGeneration = repackCoordinator.currentGeneration();
            if (!snapshotManager.acceptPreparedSnapshot(preparedSnapshot, currentGeneration)) return;

            uploaded = snapshotManager.uploadPreparedSnapshot(tag, generation, currentGeneration);
            if (uploaded == null) return;
            if (generation != repackCoordinator.currentGeneration()) return;

            long phaseStarted = System.nanoTime();
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, tag);
            copyAtlasArtifactToFinalDir(artifact, atlasesDir);
            deleteAndCopyNs = System.nanoTime() - phaseStarted;

            phaseStarted = System.nanoTime();
            load(tag, finalAtlasFile);
            atlasLoadNs = System.nanoTime() - phaseStarted;

            TextureAtlas atlas = getAtlas(tag);
            Array<Texture> pageTextures = AtlasRuntimeService.getPageTextures(atlas);
            phaseStarted = System.nanoTime();
            boolean published = snapshotManager.publishPreparedSnapshot(
                    tag,
                    generation,
                    repackCoordinator.currentGeneration(),
                    uploaded,
                    pageTextures
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

            RenderParticleSyncSystem particleSystem =
                    canvas.getEcsWorld().getSystem(RenderParticleSyncSystem.class);
            if (particleSystem != null) {
                particleSystem.invalidateAllEffects();
            }
            canvas.invalidateStudioParticleFallbacks();
        } finally {
            if (uploaded != null) uploaded.close();
            artifact.discard();
            if (Boolean.getBoolean(GpuSnapshotManager.PROPERTY_DIAGNOSTICS)) {
                Gdx.app.log(TAG,
                        "PREPARED_ATLAS_APPLY scene=" + tag
                                + " generation=" + generation
                                + " fileReplaceMs=" + ms(deleteAndCopyNs)
                                + " pageTextureLoadUploadMs=" + ms(atlasLoadNs)
                                + " preparedPublishMs=" + ms(publishNs)
                                + " totalMs=" + ms(System.nanoTime() - applyStarted));
            }
        }
    }

    private static float ms(long ns) {
        return ns / 1_000_000f;
    }

    private static void copyAtlasArtifactToFinalDir(AsyncAtlasRepackCoordinator.RepackArtifact artifact,
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
        super.load(tag, atlasFile);
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAtlasTag(tag);
        }
        requestTiledFallbackValidation();
    }

    @Override
    public void unload(String tag) {
        super.unload(tag);
        if (assetVisualResolver != null) {
            assetVisualResolver.invalidateAtlasTag(tag);
        }
        requestTiledFallbackValidation();
    }

    @Override
    public void unloadAll() {
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
