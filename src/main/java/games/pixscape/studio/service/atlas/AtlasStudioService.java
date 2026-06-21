package games.pixscape.studio.service.atlas;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.ProjectFileCleanupService;
import games.pixscape.studio.ui.main.WorldCanvas;

public final class AtlasStudioService extends AtlasRuntimeService {

    private final WorldCanvas canvas;

    private final AsyncAtlasRepackCoordinator repackCoordinator;
    private volatile boolean disposed = false;

    private static final String TAG = "AtlasStudioService";

    private final ObjectMap<String, IntSet> packedIdsBySceneTag = new ObjectMap<>();

    public AtlasStudioService(WorldCanvas canvas) {
        this.canvas = canvas;
        this.repackCoordinator = new AsyncAtlasRepackCoordinator(this::packAsyncToTemp);
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

        SceneAtlasLoaderService.packSceneAtlasToDirectory(
                cfg,
                sceneTag,
                projectDir,
                outputDir
        );

        FileHandle atlasFile = outputDir.child(sceneTag + ".atlas");
        FileHandle pngFile = outputDir.child(sceneTag + ".png");

        waitForAtlasFiles(atlasFile, pngFile);

        return new AsyncAtlasRepackCoordinator.RepackArtifact(
                sceneTag,
                generation,
                outputDir,
                atlasFile,
                pngFile
        );
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

        Gdx.app.log(TAG, "Applying async pack for scene=" + tag + " gen=" + artifact.generation());

        ProjectConfig cfg = ProjectConfig.getInstance();
        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
        FileHandle finalAtlasFile = atlasesDir.child(tag + ".atlas");

        try {
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, tag);
            copyAtlasArtifactToFinalDir(artifact, atlasesDir);

            load(tag, finalAtlasFile);

            RenderRebindHelper.rebindAfterAtlasChange(canvas, tag, this, "atlas-pack-applied");
            rebindTiles();

            RenderParticleSyncSystem particleSystem =
                    canvas.getEcsWorld().getSystem(RenderParticleSyncSystem.class);
            if (particleSystem != null) {
                particleSystem.invalidateAllEffects();
                canvas.invalidateStudioParticleFallbacks();
            }
        } finally {
            artifact.outputDir().deleteDirectory();
        }
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

    public Array<TextureAtlas.AtlasRegion> list(String tag, String regionName) {
        TextureAtlas a = atlases.get(tag);
        return a == null ? new Array<>() : a.findRegions(regionName);
    }


    private void rebuildPackedIds(String sceneTag) {
        if (sceneTag == null || sceneTag.isBlank()) return;

        TextureAtlas atlas = getAtlas(sceneTag);
        if (atlas == null) {
            packedIdsBySceneTag.remove(sceneTag);
            return;
        }

        IntSet packedIds = new IntSet();
        for (TextureAtlas.AtlasRegion region : atlas.getRegions()) {
            if (region == null || region.name == null) continue;
            int pos = region.name.lastIndexOf("__a");
            if (pos < 0) continue;
            String idPart = region.name.substring(pos +
                    3);
            try {
                packedIds.add(Integer.parseInt(idPart));
            } catch (NumberFormatException ignored) {
                // non asset-suffixed regions are ignored
            }
        }

        packedIdsBySceneTag.put(sceneTag, packedIds);
    }

    public boolean isPacked(int assetId, String sceneTag) {
        if (assetId < 0 || sceneTag == null || sceneTag.isBlank()) return false;

        IntSet packedIds = packedIdsBySceneTag.get(sceneTag);
        if (packedIds == null) {
            rebuildPackedIds(sceneTag);
            packedIds = packedIdsBySceneTag.get(sceneTag);
        }

        return packedIds != null && packedIds.contains(assetId);
    }

    @Override
    public void load(String tag, FileHandle atlasFile) {
        super.load(tag, atlasFile);
        rebuildPackedIds(tag);
    }

    @Override
    public void unload(String tag) {
        super.unload(tag);
        packedIdsBySceneTag.remove(tag);
    }

    @Override
    public void unloadAll() {
        super.unloadAll();
        packedIdsBySceneTag.clear();
    }

    public synchronized void disposeAsyncPack() {
        if (disposed) return;
        disposed = true;
        repackCoordinator.dispose();
    }
}
