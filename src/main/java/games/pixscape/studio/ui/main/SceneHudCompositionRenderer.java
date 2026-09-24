package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.hud.HudDocumentValidationException;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.HudResourceRequirements;
import games.pixscape.runtime.hud.HudResources;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.HudSelectedResources;
import games.pixscape.runtime.hud.HudSession;
import games.pixscape.runtime.hud.HudTextureProfile;
import games.pixscape.runtime.hud.HudBitmapFontSpec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.hud.HudEditRejectedException;
import games.pixscape.studio.service.runtimeavailability.SceneHudEnvironmentPaths;
import games.pixscape.studio.service.runtimeavailability.SceneHudRuntimePreparationService;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Non-interactive Runtime-style HUD composition drawn over the active Studio Scene canvas. */
public final class SceneHudCompositionRenderer implements AutoCloseable {
    record Association(String projectRoot, String sceneTag, String screenId) {}

    interface PreparationAccess {
        void ensureSceneRequested(String sceneTag);
        long publishedGeneration(String sceneTag);
        default long resourceGeneration(String sceneTag) { return publishedGeneration(sceneTag); }
    }

    interface View extends AutoCloseable {
        void resize(int screenX, int screenY, int width, int height);
        void act(float delta);
        void draw();
        default boolean recompose(Association association) { return false; }
        @Override void close();
    }

    @FunctionalInterface interface AssociationResolver {
        Association resolve(String sceneIdentity);
    }

    @FunctionalInterface interface ViewFactory {
        View create(Association association);
    }

    private final AssociationResolver associationResolver;
    private final PreparationAccess preparation;
    private final ViewFactory viewFactory;
    private final Consumer<RuntimeException> errorHandler;
    private final Set<Association> hiddenAssociations = new HashSet<>();
    private Association association;
    private View activeView;
    private long attemptedGeneration;
    private long attemptedResourceGeneration;
    private int screenX = Integer.MIN_VALUE;
    private int screenY = Integer.MIN_VALUE;
    private int screenWidth = -1;
    private int screenHeight = -1;
    private boolean closed;

    public SceneHudCompositionRenderer(Supplier<FileHandle> projectDir,
                                       Supplier<ProjectConfig> configuration,
                                       EditorDocumentManager documents,
                                       HudDocumentPersistenceService persistence,
                                       SceneHudRuntimePreparationService preparation) {
        Objects.requireNonNull(projectDir, "projectDir");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(documents, "documents");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(preparation, "preparation");
        this.associationResolver = sceneIdentity -> resolveAssociation(
                projectDir.get(), configuration.get(), sceneIdentity);
        this.preparation = new PreparationAccess() {
            @Override public void ensureSceneRequested(String sceneTag) {
                preparation.ensureSceneRequested(sceneTag);
            }

            @Override public long publishedGeneration(String sceneTag) {
                return preparation.compositionGeneration(sceneTag);
            }

            @Override public long resourceGeneration(String sceneTag) {
                return preparation.resourceGeneration(sceneTag);
            }
        };
        this.viewFactory = next -> createRuntimeView(next, documents, persistence);
        this.errorHandler = failure -> {
            if (Gdx.app != null) {
                Gdx.app.error("SceneHudComposition", failure.getMessage(), failure);
            }
        };
    }

    SceneHudCompositionRenderer(AssociationResolver associationResolver,
                                PreparationAccess preparation,
                                ViewFactory viewFactory,
                                Consumer<RuntimeException> errorHandler) {
        this.associationResolver = Objects.requireNonNull(associationResolver);
        this.preparation = Objects.requireNonNull(preparation);
        this.viewFactory = Objects.requireNonNull(viewFactory);
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    public void render(String sceneIdentity, Rectangle canvasBounds, float delta) {
        if (closed || canvasBounds == null) return;
        Association next = associationResolver.resolve(sceneIdentity);
        if (!Objects.equals(association, next)) {
            clearView();
            association = next;
            attemptedGeneration = 0L;
            attemptedResourceGeneration = 0L;
            if (next != null) preparation.ensureSceneRequested(next.sceneTag());
        }
        if (association == null) return;

        long publishedGeneration = preparation.publishedGeneration(association.sceneTag());
        if (publishedGeneration > 0L && publishedGeneration != attemptedGeneration) {
            attemptedGeneration = publishedGeneration;
            long resourceGeneration = preparation.resourceGeneration(association.sceneTag());
            if (activeView != null && resourceGeneration == attemptedResourceGeneration) {
                try {
                    if (activeView.recompose(association)) attemptedResourceGeneration = resourceGeneration;
                    else if (installCandidate(association)) attemptedResourceGeneration = resourceGeneration;
                } catch (RuntimeException failure) { errorHandler.accept(failure); }
            } else if (installCandidate(association)) attemptedResourceGeneration = resourceGeneration;
        }
        if (activeView == null || hiddenAssociations.contains(association)) return;

        int x = Math.round(canvasBounds.x);
        int y = Math.round(canvasBounds.y);
        int width = Math.round(canvasBounds.width);
        int height = Math.round(canvasBounds.height);
        if (width <= 0 || height <= 0) return;
        try {
            if (x != screenX || y != screenY || width != screenWidth || height != screenHeight) {
                activeView.resize(x, y, width, height);
                screenX = x;
                screenY = y;
                screenWidth = width;
                screenHeight = height;
            }
            activeView.act(delta);
            activeView.draw();
        } catch (RuntimeException failure) {
            try { clearView(); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            errorHandler.accept(failure);
        }
    }

    public void onProjectChanging() {
        clearView();
        association = null;
        attemptedGeneration = 0L;
        attemptedResourceGeneration = 0L;
        hiddenAssociations.clear();
    }

    public boolean isVisible(String sceneIdentity) {
        Association target = associationResolver.resolve(sceneIdentity);
        return target == null || !hiddenAssociations.contains(target);
    }

    public void setVisible(String sceneIdentity, boolean visible) {
        if (closed) return;
        Association target = associationResolver.resolve(sceneIdentity);
        if (target == null) return;
        if (visible) hiddenAssociations.remove(target);
        else hiddenAssociations.add(target);
    }

    private boolean installCandidate(Association next) {
        View candidate = null;
        try {
            candidate = viewFactory.create(next);
            View previous = activeView;
            activeView = candidate;
            candidate = null;
            resetBounds();
            if (previous != null) previous.close();
            return true;
        } catch (RuntimeException failure) {
            if (candidate != null) candidate.close();
            errorHandler.accept(failure);
            return false;
        }
    }

    private static View createRuntimeView(Association association,
                                          EditorDocumentManager documents,
                                          HudDocumentPersistenceService persistence) {
        FileHandle project = new FileHandle(association.projectRoot());
        HudDocumentPersistenceService.Loaded loaded = loadAuthoritative(
                project, association.screenId(), documents, persistence);
        HudScreenAsset asset = loaded.asset();
        HudDocumentV1 document = loaded.document();
        asset.validate();

        HudDocumentValidator validator = new HudDocumentValidator();
        HudValidationResult structural = validator.validate(document);
        requireValid(HudDocumentValidationException.Phase.STRUCTURAL,
                asset.documentId, structural);
        HudResourceRequirements requirements =
                HudResourceRequirements.from(structural.validatedDocument());
        String skinId = requirements.requiresSkin()
                ? requireSkinId(asset, association.screenId()) : null;

        HudResources resources = null;
        HudSession session = null;
        try {
            if (requirements.bitmapFontAssetIds().isEmpty()) {
                resources = HudResources.prepareEnvironment(
                        project,
                        SceneHudEnvironmentPaths.atlasId(association.sceneTag()),
                        HudTextureProfile.DEFAULT_ID,
                        skinId == null ? List.of() : List.of(skinId));
            } else {
                resources = HudResources.prepareEnvironmentWithFonts(
                    project,
                    SceneHudEnvironmentPaths.atlasId(association.sceneTag()),
                    HudTextureProfile.DEFAULT_ID,
                    skinId == null ? List.of() : List.of(skinId),
                    authoringFontSpecs(project, requirements));
            }
            HudSelectedResources selected = resources.select(skinId);
            HudValidationResult resourceAware = validator.validate(document, selected);
            requireValid(HudDocumentValidationException.Phase.RESOURCE_AWARE,
                    asset.documentId, resourceAware);
            ShaderProgram shader = ShaderRegistry.get(RuntimeFs.HUD_TEXTURE_ARRAY);
            if (shader == null) {
                throw new IllegalStateException("Runtime HUD shader is unavailable.");
            }
            session = HudSession.create(asset, resources, shader);
            session.install(new HudMaterializer().materialize(
                    resourceAware.validatedDocument(), selected));
            return new RuntimeView(resources, session, documents, persistence);
        } catch (RuntimeException failure) {
            if (session != null) session.dispose();
            if (resources != null) resources.dispose();
            throw failure;
        }
    }

    private static List<HudBitmapFontSpec> authoringFontSpecs(
            FileHandle project, HudResourceRequirements requirements) {
        AssetMetaDatabase database = AssetMetaDatabase.load(
                project.child(games.pixscape.studio.io.StudioFs.FILE_ASSETS_JSON));
        java.util.ArrayList<HudBitmapFontSpec> specs = new java.util.ArrayList<>();
        for (Integer assetId : requirements.bitmapFontAssetIds()) {
            var meta = database.findById(assetId);
            if (meta == null || meta.type() != AssetType.FONT) {
                throw new IllegalStateException("Unknown bitmap font Asset " + assetId + ".");
            }
            specs.add(new HudBitmapFontSpec(assetId, meta.sourceRelPath()));
        }
        return List.copyOf(specs);
    }

    static HudDocumentPersistenceService.Loaded loadAuthoritative(
            FileHandle project,
            String screenId,
            EditorDocumentManager documents,
            HudDocumentPersistenceService persistence) {
        HudScreenEditorDocument open = null;
        for (var candidate : documents.documents()) {
            if (candidate instanceof HudScreenEditorDocument hud
                    && screenId.equals(hud.screenId())) {
                open = hud;
                break;
            }
        }
        if (open != null) {
            return new HudDocumentPersistenceService.Loaded(open.asset(), open.document());
        }
        return persistence.load(project, screenId);
    }

    static Association resolveAssociation(FileHandle project,
                                          ProjectConfig configuration,
                                          String sceneIdentity) {
        if (project == null || configuration == null || sceneIdentity == null) return null;
        for (String sceneName : configuration.getSceneNames()) {
            SceneMeta scene = configuration.getSceneMeta(sceneName);
            String sceneTag = configuration.canonicalSceneTagFor(scene);
            if (!Objects.equals(sceneIdentity, sceneTag)) continue;
            String screenId;
            try { screenId = HudScreenAssetId.normalizeOptional(scene.defaultHudScreenId); }
            catch (RuntimeException failure) { return null; }
            return screenId == null ? null
                    : new Association(project.path(), sceneTag, screenId);
        }
        return null;
    }

    private static String requireSkinId(HudScreenAsset asset, String screenId) {
        String skinId = asset.skinId == null || asset.skinId.isBlank()
                ? null : asset.skinId.trim().replace('\\', '/');
        if (skinId == null) {
            throw new IllegalArgumentException("HUD " + screenId + " requires skinId.");
        }
        return skinId;
    }

    private static void requireValid(HudDocumentValidationException.Phase phase,
                                     String documentId,
                                     HudValidationResult validation) {
        if (!validation.isValid()) {
            throw new HudEditRejectedException("HUD document validation failed during "
                    + phase + " for " + documentId + ": " + validation.issues());
        }
    }

    private void clearView() {
        View previous = activeView;
        activeView = null;
        resetBounds();
        if (previous != null) previous.close();
    }

    private void resetBounds() {
        screenX = Integer.MIN_VALUE;
        screenY = Integer.MIN_VALUE;
        screenWidth = -1;
        screenHeight = -1;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        onProjectChanging();
    }

    private static final class RuntimeView implements View {
        private HudResources resources;
        private HudSession session;
        private final EditorDocumentManager documents;
        private final HudDocumentPersistenceService persistence;

        private RuntimeView(HudResources resources, HudSession session,
                             EditorDocumentManager documents, HudDocumentPersistenceService persistence) {
            this.resources = resources;
            this.session = session;
            this.documents = documents;
            this.persistence = persistence;
        }

        @Override public boolean recompose(Association association) {
            var loaded = loadAuthoritative(new FileHandle(association.projectRoot()),
                    association.screenId(), documents, persistence);
            loaded.asset().validate();
            var validator = new HudDocumentValidator();
            var structural = validator.validate(loaded.document());
            requireValid(HudDocumentValidationException.Phase.STRUCTURAL,
                    loaded.asset().documentId, structural);
            var requirements = HudResourceRequirements.from(structural.validatedDocument());
            String skinId = requirements.requiresSkin()
                    ? requireSkinId(loaded.asset(), association.screenId()) : null;
            HudSelectedResources selected = resources.select(skinId);
            var validated = validator.validate(loaded.document(), selected);
            requireValid(HudDocumentValidationException.Phase.RESOURCE_AWARE,
                    loaded.asset().documentId, validated);
            MaterializedHud materialized = new HudMaterializer().materialize(
                    validated.validatedDocument(), selected);
            try { session.install(materialized); }
            catch (RuntimeException failure) {
                // install may have adopted the candidate before old-content disposal failed.
                // Only a still-detached candidate remains ours to release.
                if (materialized.root().getParent() == null) materialized.dispose();
                throw failure;
            }
            return true;
        }

        @Override public void resize(int screenX, int screenY, int width, int height) {
            if (session != null) session.resizeUnscaled(screenX, screenY, width, height);
        }

        @Override public void act(float delta) {
            if (session != null) session.act(delta);
        }

        @Override public void draw() {
            if (session != null) session.draw();
        }

        @Override public void close() {
            RuntimeException failure = null;
            if (session != null) {
                try { session.dispose(); }
                catch (RuntimeException disposalFailure) { failure = disposalFailure; }
            }
            if (resources != null) {
                try { resources.dispose(); }
                catch (RuntimeException disposalFailure) {
                    if (failure == null) failure = disposalFailure;
                }
            }
            session = null;
            resources = null;
            if (failure != null) throw failure;
        }
    }
}
