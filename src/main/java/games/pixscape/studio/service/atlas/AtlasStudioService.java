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
    private final PreparedPublisher preparedPublisher;
    private volatile boolean disposed = false;
    private final Map<String, SceneAtlasCoverage.Validation> validatedAtlases = new HashMap<>();
    private PackCompletion lastCompletion;
    private static final String REPACK_REQUIRED_FILE = ".repack-required";

    public record PackCompletion(String sceneTag, long generation, Throwable failure) {}

    @FunctionalInterface
    interface PreparedPublisher {
        void publish(ScenePrepared artifact, FileHandle atlasesDir);
    }

    private static final String TAG = "AtlasStudioService";

    /** Isolated Game Object authoring uses grouped normal pages and full-image color coordinates. */
    public void prepareAuthoringNormals(FileHandle project,World world,java.util.Set<String> required,
            games.pixscape.studio.asset.AssetMetaDatabase db) {
        boolean declared=false;
        for(int i=0;i<db.size();i++)if(db.assetAt(i) instanceof games.pixscape.studio.asset.TileAssetMeta tile
                && tile.normalSourceRelPath!=null && required.contains(tile.sourceRelPath())){declared=true;break;}
        if(!declared){world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class).publishAuthoringNormals(null);return;}
        FileHandle candidate=games.pixscape.studio.io.AtomicDirectoryPublication.createCandidate(project.child(StudioFs.DIR_ATLASES).child("authoring-normals"));
        boolean transferred=false;
        try {
            FileHandle colors=candidate.child("input");colors.mkdirs();
            for(String source:required) {
                FileHandle file=project.child(source);
                if(file.exists() && !file.isDirectory())file.copyTo(colors.child(file.name()));
            }
            NormalAtlasPackingService.sync(project,colors,db,required);
            FileHandle output=candidate.child("output");NormalAtlasPackingService.pack(colors,output,"authoring");
            FileHandle descriptor=output.child("authoring.normals.atlas");
            if(descriptor.exists()){
                world.getSystem(games.pixscape.runtime.system.LightingGeometrySystem.class)
                    .publishAuthoringNormals(new games.pixscape.runtime.service.NormalMapAtlas("authoring",descriptor,null),
                            ()->games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(candidate));
                transferred=true;
            }
        }finally{if(!transferred)games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(candidate);}
    }

    /** Cold Scene activation: prepare newly declared normal resources without repacking color pages. */
    public static void prepareSceneNormals(FileHandle project,String tag,java.util.Set<String> required,
            games.pixscape.studio.asset.AssetMetaDatabase db) {
        FileHandle root=project.child(StudioFs.DIR_ATLASES),colors=root.child(StudioFs.DIR_INPUT).child(tag);
        FileHandle colorDescriptor=root.child(tag+".atlas");
        if(required==null && db!=null && colorDescriptor.exists()){
            required=new java.util.HashSet<>();
            var data=new TextureAtlas.TextureAtlasData(colorDescriptor,root,false);
            for(var region:data.getRegions()){
                int marker=region.name.lastIndexOf("__a");if(marker<0)continue;
                var asset=db.findById(Integer.parseInt(region.name.substring(marker+3)));
                if(asset instanceof games.pixscape.studio.asset.TileAssetMeta tile && tile.normalSourceRelPath!=null){
                    String source=tile.sourceRelPath();
                    if(!colors.child(new FileHandle(source).name()).exists())
                        throw new IllegalStateException("Normal preparation requires scene color input: asset "+tile.id()+" in "+tag);
                    required.add(source);
                }
            }
        }
        FileHandle input=NormalAtlasPackingService.input(colors);
        var oldInputs=ProjectFileCleanupService.snapshotDirectory(input);
        var oldPages=ProjectFileCleanupService.snapshotAtlasPages(root,tag+games.pixscape.runtime.service.NormalMapAtlas.SUFFIX);
        FileHandle candidate=null;boolean publishing=false;
        try {
            var change=NormalAtlasPackingService.sync(project,colors,db,required);
            boolean declared=!input.child("sources.tsv").readString("UTF-8").isBlank();
            FileHandle descriptor=root.child(tag+games.pixscape.runtime.service.NormalMapAtlas.SUFFIX+".atlas");
            if(!change.changed() && (declared==descriptor.exists()))return;
            candidate=games.pixscape.studio.io.AtomicDirectoryPublication.createCandidate(root.child(tag+"-normal-preparation"));
            NormalAtlasPackingService.pack(colors,candidate,tag);
            publishing=true;ProjectFileCleanupService.deleteSceneAtlasFiles(project,tag+games.pixscape.runtime.service.NormalMapAtlas.SUFFIX);
            for(FileHandle file:candidate.list())file.copyTo(root.child(file.name()));
        }catch(RuntimeException failure){
            if(publishing){ProjectFileCleanupService.deleteSceneAtlasFiles(project,tag+games.pixscape.runtime.service.NormalMapAtlas.SUFFIX);ProjectFileCleanupService.restoreAtlasPagesFromSnapshot(root,oldPages);}
            ProjectFileCleanupService.restoreDirectoryFromSnapshot(input,oldInputs);throw failure;
        }finally{if(candidate!=null)games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(candidate);}
    }

    /** Import preflight uses the scene packer policy without publishing any atlas generation. */
    public static void validateTilesetImportCandidate(FileHandle candidate, java.util.List<String> colors) {
        if (colors.isEmpty()) return;
        FileHandle work = games.pixscape.studio.io.AtomicDirectoryPublication.createCandidate(candidate);
        try {
            FileHandle input = work.child("input");
            input.mkdirs();
            for (String path : colors) candidate.child(path).copyTo(input.child(path));
            FileHandle output = work.child("output");
            AtlasPackingService.packScene(input, output, "import-check");
            new TextureAtlas.TextureAtlasData(output.child("import-check.atlas"), output, false);
        } finally {
            games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(work);
        }
    }

    /** One synchronous import boundary: all packs finish before any visible page set is replaced.
     * The caller holds the source/catalog backup until this operation and resource acceptance succeed. */
    public void repackImportedScenes(ProjectConfig cfg, FileHandle project, java.util.Set<String> tags,
                                     Runnable synchronizeInputs, Runnable acceptResources) {
        repackImportedScenes(cfg, project, tags, synchronizeInputs, acceptResources, ignored -> {});
    }

    public void repackImportedScenes(ProjectConfig cfg, FileHandle project, java.util.Set<String> tags,
                                     Runnable synchronizeInputs, Runnable acceptResources,
                                     java.util.function.Consumer<String> progress) {
        if (hasAsyncPackQueuedOrRunningFor(null))
            throw new IllegalStateException("An atlas publication is pending; review the import after it completes.");
        repackImportedScenes(cfg, project, tags, synchronizeInputs, acceptResources,
                (config, tag, root, output) -> {
                    progress.accept("Packing scene " + tag + "...");
                    return SceneAtlasLoaderService.packSceneAtlasToDirectory(config, tag, root, output);
                });
    }

    @FunctionalInterface interface ImportScenePacker {
        SceneAtlasCoverage.Validation pack(ProjectConfig cfg, String tag, FileHandle project, FileHandle output);
    }

    static void repackImportedScenes(ProjectConfig cfg, FileHandle project, java.util.Set<String> tags,
                                    Runnable synchronizeInputs, Runnable acceptResources, ImportScenePacker packer) {
        FileHandle atlasRoot = project.child(StudioFs.DIR_ATLASES);
        Map<String, com.badlogic.gdx.utils.Array<ProjectFileCleanupService.FileSnapshotEntry>> oldPages = new HashMap<>();
        Map<String, com.badlogic.gdx.utils.Array<ProjectFileCleanupService.FileSnapshotEntry>> oldInputs = new HashMap<>();
        Map<String, com.badlogic.gdx.utils.Array<ProjectFileCleanupService.FileSnapshotEntry>> oldNormalInputs = new HashMap<>();
        Map<String, FileHandle> outputs = new HashMap<>();
        boolean publicationStarted = false;
        Throwable operationFailure = null;
        try {
            for (String tag : tags) {
                oldPages.put(tag, ProjectFileCleanupService.snapshotAtlasPages(atlasRoot, tag));
                oldInputs.put(tag, ProjectFileCleanupService.snapshotDirectory(atlasRoot.child(StudioFs.DIR_INPUT).child(tag)));
                oldNormalInputs.put(tag,ProjectFileCleanupService.snapshotDirectory(NormalAtlasPackingService.input(atlasRoot.child(StudioFs.DIR_INPUT).child(tag))));
            }
            synchronizeInputs.run();
            for (String tag : tags) {
                FileHandle output = games.pixscape.studio.io.AtomicDirectoryPublication.createCandidate(atlasRoot.child(tag));
                outputs.put(tag, output);
                packer.pack(cfg, tag, project, output);
            }
            publicationStarted = true;
            for (String tag : tags) {
                ProjectFileCleanupService.deleteSceneAtlasFiles(project, tag);
                for (FileHandle file : outputs.get(tag).list()) file.copyTo(atlasRoot.child(file.name()));
            }
            // Required cleanup still belongs to the reversible part of the import.
            for (FileHandle output : outputs.values()) games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(output);
            outputs.clear();
            acceptResources.run();
            for (String tag : tags) {
                FileHandle marker = atlasRoot.child(StudioFs.DIR_INPUT).child(tag).child(REPACK_REQUIRED_FILE);
                if (marker.exists() && !marker.delete()) throw new IllegalStateException("Cannot clear atlas repack marker: " + tag);
            }
        } catch (RuntimeException | Error failure) {
            operationFailure = failure;
            for (String tag : oldInputs.keySet()) {
                try {
                    if (publicationStarted) {
                        ProjectFileCleanupService.deleteSceneAtlasFiles(project, tag);
                        ProjectFileCleanupService.restoreAtlasPagesFromSnapshot(atlasRoot, oldPages.get(tag));
                    }
                    ProjectFileCleanupService.restoreDirectoryFromSnapshot(atlasRoot.child(StudioFs.DIR_INPUT).child(tag), oldInputs.get(tag));
                    ProjectFileCleanupService.restoreDirectoryFromSnapshot(NormalAtlasPackingService.input(atlasRoot.child(StudioFs.DIR_INPUT).child(tag)),oldNormalInputs.get(tag));
                } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw failure;
        } finally {
            for (FileHandle output : outputs.values()) {
                try { games.pixscape.studio.io.AtomicDirectoryPublication.discardCandidate(output); }
                catch (RuntimeException cleanupFailure) {
                    if (operationFailure != null) operationFailure.addSuppressed(cleanupFailure);
                    else throw cleanupFailure;
                }
            }
        }
    }

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

        FileHandle outputDir() {
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
        this.preparedPublisher = null;
    }

    AtlasStudioService(WorldCanvas canvas,
                       AsyncAtlasRepackCoordinator.PackRunner<ScenePrepared> runner,
                       ExecutorService executor, LongSupplier clock) {
        this(canvas, runner, executor, clock, null);
    }

    AtlasStudioService(WorldCanvas canvas,
                       AsyncAtlasRepackCoordinator.PackRunner<ScenePrepared> runner,
                       ExecutorService executor, LongSupplier clock,
                       PreparedPublisher preparedPublisher) {
        this.canvas = canvas;
        this.repackCoordinator = new AsyncAtlasRepackCoordinator<>(
                runner, executor, clock, 0);
        this.preparedPublisher = preparedPublisher;
    }

    public void setAssetVisualResolver(StudioAssetVisualResolver assetVisualResolver) {
        this.assetVisualResolver = assetVisualResolver;
    }

    public void requestAsyncPack(String sceneTag) {
        requestAsyncPack(sceneTag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
    }

    public long requestAsyncPack(String sceneTag, AsyncAtlasRepackCoordinator.RepackReason reason) {
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new IllegalArgumentException("sceneTag is blank");
        }
        if (disposed) {
            if (reason == AsyncAtlasRepackCoordinator.RepackReason.SAVE) {
                throw new IllegalStateException("Atlas service is disposed during save");
            }
            return repackCoordinator.currentGeneration();
        }
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (canvas != null && cfg != null && cfg.projectDirectoryPath != null
                && !cfg.projectDirectoryPath.isBlank()) {
            markRepackRequired(StudioFs.requireStudioProjectDir(cfg), sceneTag);
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

    /** A marker survives failed work and restart; atlas region names cannot prove pixel freshness. */
    public void markRepackRequired(FileHandle projectDir, String tag) {
        FileHandle marker = repackMarker(projectDir, tag);
        if (!marker.exists()) {
            marker.parent().mkdirs();
            marker.writeString("", false, "UTF-8");
        }
        validatedAtlases.remove(tag);
    }

    public void clearRepackRequired(FileHandle projectDir, String tag) {
        FileHandle marker = repackMarker(projectDir, tag);
        if (marker.exists() && !marker.delete()) {
            throw new IllegalStateException("Unable to clear atlas repack requirement: " + marker.path());
        }
    }

    private static FileHandle repackMarker(FileHandle projectDir, String tag) {
        return projectDir.child(StudioFs.DIR_ATLASES).child(StudioFs.DIR_INPUT)
                .child(tag).child(REPACK_REQUIRED_FILE);
    }

    public static boolean isRepackRequired(FileHandle inputDir) {
        return inputDir != null && inputDir.child(REPACK_REQUIRED_FILE).exists();
    }

    /** Reuses the last complete descriptor check while inputs and published files are unchanged. */
    public boolean coversCurrentInput(String tag, FileHandle inputDir, FileHandle atlasFile) {
        try {
            if (isRepackRequired(inputDir)) return false;
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
        games.pixscape.runtime.service.NormalMapAtlas uploadedNormals=null;
        com.badlogic.gdx.utils.Array<ProjectFileCleanupService.FileSnapshotEntry> previousPageFiles=null;
        FileHandle replacementRoot=null;
        boolean replacingFiles=false,runtimePublished=false;
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
            if (preparedPublisher != null) {
                preparedPublisher.publish(artifact, atlasesDir);
                completePublishedPack(tag, generation, artifact.validation.at(
                        atlasesDir.child(tag + ".atlas")), projectDir);
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
            uploadedNormals=uploaded.takeNormals();

            long phaseStarted = System.nanoTime();
            replacementRoot=atlasesDir;
            previousPageFiles=ProjectFileCleanupService.snapshotAtlasPages(atlasesDir,tag);
            replacingFiles=true;
            ProjectFileCleanupService.deleteSceneAtlasFiles(projectDir, tag);
            copyAtlasArtifactToFinalDir(artifact, atlasesDir);
            deleteAndCopyNs = System.nanoTime() - phaseStarted;
            SceneAtlasCoverage.Validation publishedValidation = artifact.validation.at(
                    atlasesDir.child(tag + ".atlas"));

            phaseStarted = System.nanoTime();
            publishPreparedAtlas(tag, uploaded.takeAtlas(),uploadedNormals);uploadedNormals=null;
            runtimePublished=true;
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

            // Rebind particle pools before the next ECS/render step consumes the new bundle.
            // Deferring until after that step submits particles with the replaced atlas handles.
            canvas.refreshParticleRuntimeAvailability();
            completePublishedPack(tag, generation, publishedValidation, projectDir);
        } catch (RuntimeException | Error failure) {
            if(replacingFiles && !runtimePublished)try{
                ProjectFileCleanupService.deleteSceneAtlasFiles(replacementRoot.parent(),tag);
                ProjectFileCleanupService.restoreAtlasPagesFromSnapshot(replacementRoot,previousPageFiles);
            }catch(RuntimeException rollbackFailure){failure.addSuppressed(rollbackFailure);}
            validatedAtlases.remove(tag);
            lastCompletion = new PackCompletion(tag, generation, failure);
            if (Gdx.app != null) Gdx.app.error(TAG,
                    "Atlas publication failed scene=" + tag + " gen=" + generation, failure);
        } finally {
            if (uploaded != null) uploaded.close();
            if(uploadedNormals!=null)uploadedNormals.dispose();
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

    private void completePublishedPack(String tag, long generation,
                                       SceneAtlasCoverage.Validation validation,
                                       FileHandle projectDir) {
        clearRepackRequired(projectDir, tag);
        validatedAtlases.put(tag, validation);
        lastCompletion = new PackCompletion(tag, generation, null);
    }

    private void publishPreparedAtlas(String tag,TextureAtlas atlas,games.pixscape.runtime.service.NormalMapAtlas normals) {
        publishOwnedAtlas(tag, atlas,normals);
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

        for (FileHandle child : artifact.outputDir().list()) {
            if (child == null || child.isDirectory()) continue;
            String name = child.name();
            if (ProjectFileCleanupService.isSceneAtlasFile(tag,name)) {
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
