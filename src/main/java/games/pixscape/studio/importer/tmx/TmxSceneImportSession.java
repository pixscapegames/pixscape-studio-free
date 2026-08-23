package games.pixscape.studio.importer.tmx;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.atlas.SceneAtlasLoaderService;

public final class TmxSceneImportSession {

    private enum Phase {
        NEW,
        PREPARED,
        SCENE_CREATED,
        ASSETS_IMPORTED,
        SCENE_MATERIALIZED,
        ATLAS_UPDATED,
        FINISHED,
        ROLLED_BACK
    }

    private final TmxSceneImportService service;
    private final TmxSceneImportRequest request;

    private Phase phase = Phase.NEW;
    private TmxImportPlanResult planResult;
    private TmxImportPlan plan;
    private String sceneName;
    private String createdSceneFileName;
    private String createdSceneTag;
    private TmxSceneImportTransaction transaction;
    private TmxSceneImportService.ImportAssetsResult importedAssets;
    private TmxSceneImportResult result;
    private World temporaryWorld;

    TmxSceneImportSession(TmxSceneImportService service, TmxSceneImportRequest request) {
        this.service = service;
        this.request = request;
    }

    public void prepare() {
        requirePhase(Phase.NEW);
        planResult = service.plan(request);
        if (!planResult.hasPlan()) {
            result = TmxSceneImportResult.rejected(
                    TmxSceneImportStatus.PREFLIGHT_FAILED,
                    planResult,
                    null
            );
            phase = Phase.FINISHED;
            return;
        }

        TmxSceneImportResult rejection = service.validateBeforeMutation(planResult);
        if (rejection != null) {
            result = rejection;
            phase = Phase.FINISHED;
            return;
        }

        plan = planResult.plan();
        sceneName = service.uniqueSceneName(service.sceneName(request, plan));
        phase = Phase.PREPARED;
    }

    public void createScene() {
        requirePhase(Phase.PREPARED);
        ProjectConfig cfg = service.config();
        transaction = new TmxSceneImportTransaction(cfg, service.projectDir(), service.assetDatabase());
        cfg.createSceneMeta(sceneName);
        SceneMeta meta = requireCreatedSceneMeta();
        createdSceneFileName = meta.getFile();
        service.configureSceneMeta(meta, plan.scene());
        createdSceneTag = cfg.canonicalSceneTagFor(meta);
        phase = Phase.SCENE_CREATED;
    }

    public void importAssets() {
        requirePhase(Phase.SCENE_CREATED);
        importedAssets = service.importAssets(plan, requireCreatedSceneMeta());
        phase = Phase.ASSETS_IMPORTED;
    }

    public void materializeAndSaveScene() {
        requirePhase(Phase.ASSETS_IMPORTED);
        FileHandle projectDir = service.projectDir();
        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        FileHandle sceneFile = projectDir.child(StudioFs.DIR_SCENES).child(createdSceneFileName);
        IdentityRegistry identityRegistry = new IdentityRegistry();
        temporaryWorld = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        identityRegistry.bind(temporaryWorld, requireCreatedSceneMeta());
        try {
            service.populateImportedWorld(
                    temporaryWorld,
                    identityRegistry,
                    plan,
                    importedAssets.cellLogicalIdsByTileset(),
                    importedAssets.staticTileAssetIdsByTileset(),
                    importedAssets.imageAssetsBySourceLayer(),
                    createdSceneTag
            );
            SceneService.saveScene(temporaryWorld, sceneFile, false);
            phase = Phase.SCENE_MATERIALIZED;
        } finally {
            identityRegistry.bind(null, null);
            temporaryWorld.dispose();
            temporaryWorld = null;
        }
    }

    public void updateAtlas() {
        requirePhase(Phase.SCENE_MATERIALIZED);
        service.syncAtlasInputs(createdSceneTag, importedAssets.importedAssetIds());
        if (request.packSceneAtlas()) {
            SceneAtlasLoaderService.packSceneAtlas(service.config(), createdSceneTag, service.projectDir());
        }
        phase = Phase.ATLAS_UPDATED;
    }

    public TmxSceneImportResult persistAndFinish() {
        requirePhase(Phase.ATLAS_UPDATED);
        service.assetDatabase().save(service.projectDir().child(StudioFs.FILE_ASSETS_JSON));
        ProjectConfig.ProjectIO.saveProject(
                service.config(),
                StudioFs.requireStudioProjectFile(service.config())
        );
        result = new TmxSceneImportResult(
                TmxSceneImportStatus.IMPORTED,
                planResult,
                sceneName,
                createdSceneFileName,
                createdSceneTag,
                importedAssets.importedTilesetCount(),
                importedAssets.importedTileAssetIds().size(),
                plan.layers().size(),
                plan.scene().nonEmptyTileCount(),
                planResult.preflightReport().diagnostics(),
                null,
                false,
                false,
                new TmxSceneImportRollback(transaction, sceneName, createdSceneFileName, createdSceneTag)
        );
        phase = Phase.FINISHED;
        return result;
    }

    public TmxSceneImportResult rollback(Throwable failure) {
        if (phase == Phase.ROLLED_BACK) return result;
        if (transaction == null) {
            throw new IllegalStateException("TMX import has not started a rollback transaction.");
        }

        RuntimeException importFailure = failure instanceof RuntimeException runtimeFailure
                ? runtimeFailure
                : new RuntimeException("TMX import failed.", failure);
        try {
            transaction.rollback(sceneName, createdSceneFileName, createdSceneTag);
            result = service.failedResult(
                    planResult, sceneName, createdSceneFileName, createdSceneTag, importFailure, true
            );
        } catch (RuntimeException rollbackFailure) {
            importFailure.addSuppressed(rollbackFailure);
            result = service.failedResult(
                    planResult, sceneName, createdSceneFileName, createdSceneTag, importFailure, false
            );
        }
        phase = Phase.ROLLED_BACK;
        return result;
    }

    public boolean finished() {
        return phase == Phase.FINISHED || phase == Phase.ROLLED_BACK;
    }

    public boolean mutationStarted() {
        return transaction != null;
    }

    public TmxSceneImportResult result() {
        if (!finished()) throw new IllegalStateException("TMX import session has not finished.");
        return result;
    }

    boolean hasTemporaryWorld() {
        return temporaryWorld != null;
    }

    private SceneMeta requireCreatedSceneMeta() {
        SceneMeta meta = service.config().getSceneMeta(sceneName);
        if (meta == null) throw new IllegalStateException("Scene metadata was not created: " + sceneName);
        return meta;
    }

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "TMX import phase " + expected + " cannot run while session is " + phase + "."
            );
        }
    }
}
