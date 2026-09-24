package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.atlas.AsyncAtlasRepackCoordinator;
import games.pixscape.studio.service.hud.HudDocumentEditSession;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Demand-driven Scene-local Runtime preparation, beside (not through) the legacy profile bridge.
 * All public lifecycle/invalidation/update calls belong to the creating Studio thread. This
 * enforces snapshot ownership and serializes live publication, including generation validation.
 * Workers for unrelated Scenes run independently and access only captured data/source files. */
public final class SceneHudRuntimePreparationService implements AutoCloseable {
    private record ProjectIdentity(String root, long epoch) {}
    // A detached, private database clone is read-only after handoff. JSON cloning happens on Studio,
    // not concurrently in workers through AssetMetaDatabase's shared Json serializer.
    private record Snapshot(List<String> roots, List<SceneHudScreenSnapshot> screens, AssetMetaDatabase database) {
        Snapshot { roots = List.copyOf(roots); screens = List.copyOf(screens); }
        SceneHudScreenSnapshot screen(String id) {
            for (var screen : screens) if (screen.screenId().equals(id)) return screen;
            return null;
        }
        Snapshot withScreen(SceneHudScreenSnapshot updated) {
            List<SceneHudScreenSnapshot> next = new ArrayList<>(screens);
            for (int i = 0; i < next.size(); i++) if (next.get(i).screenId().equals(updated.screenId())) {
                next.set(i, updated);
                return new Snapshot(roots, next, database);
            }
            throw new IllegalArgumentException("HUD is not selected by this Scene: " + updated.screenId());
        }
    }
    private record Request(ProjectIdentity project, String sceneTag, long generation, Snapshot snapshot,
                           SceneHudDependencyClosure closure) {}
    private static final class SceneWork {
        volatile Request request;
        AsyncAtlasRepackCoordinator<PreparedSceneHudEnvironment> coordinator;
        volatile long coordinatorEpoch;
        Snapshot currentSnapshot;
        SceneHudDependencyClosure currentClosure;
        String currentPackKey;
        String currentRequestPackKey;
        String publishedPackKey;
        long compositionRevision;
        long publishedCompositionRevision;
        long resourceRevision;
        long publishedResourceRevision;
        long publishedGeneration;
    }

    private final Thread studioThread = Thread.currentThread();
    private final Supplier<ProjectConfig> configuration;
    private final Supplier<AssetMetaDatabase> database;
    private final EditorDocumentManager documents;
    private final HudDocumentPersistenceService persistence;
    private final SceneHudAtlasBuilder builder;
    private final AtomicDirectoryPublication.Publisher publisher;
    private final Function<AsyncAtlasRepackCoordinator.PackRunner<PreparedSceneHudEnvironment>,
            AsyncAtlasRepackCoordinator<PreparedSceneHudEnvironment>> coordinatorFactory;
    private final Map<String, SceneWork> scenes = new LinkedHashMap<>();
    private final Map<HudScreenEditorDocument, HudDocumentEditSession.AuthoredPublicationListener> listeners =
            new IdentityHashMap<>();
    private final EditorDocumentManager.Listener lifecycle = new EditorDocumentManager.Listener() {
        @Override public void documentOpened(OpenEditorDocument document) {
            if (document instanceof HudScreenEditorDocument hud) attach(hud); // No reachability/request.
        }
        @Override public void documentClosed(OpenEditorDocument document) {
            if (document instanceof HudScreenEditorDocument hud) {
                detach(hud);
                // A discarded selected override changes authoritative input back to disk.
                if (hud.isDirty()) invalidateHudScreen(hud.screenId());
            }
        }
    };
    private Consumer<RuntimeException> onError = failure -> {
        if (Gdx.app != null) Gdx.app.error("SceneHudRuntimePreparation", failure.getMessage(), failure);
    };
    private ProjectIdentity project;
    private long nextEpoch;
    private boolean closed;

    public SceneHudRuntimePreparationService(Supplier<FileHandle> projectDir, Supplier<ProjectConfig> configuration,
                                             Supplier<AssetMetaDatabase> database, EditorDocumentManager documents,
                                             HudDocumentPersistenceService persistence) {
        this(projectDir, configuration, database, documents, persistence, new SceneHudAtlasBuilder(),
                AtomicDirectoryPublication::move, AsyncAtlasRepackCoordinator::new);
    }

    SceneHudRuntimePreparationService(Supplier<FileHandle> projectDir, Supplier<ProjectConfig> configuration,
                                     Supplier<AssetMetaDatabase> database, EditorDocumentManager documents,
                                     HudDocumentPersistenceService persistence, SceneHudAtlasBuilder builder,
                                     AtomicDirectoryPublication.Publisher publisher,
                                     Function<AsyncAtlasRepackCoordinator.PackRunner<PreparedSceneHudEnvironment>,
                                             AsyncAtlasRepackCoordinator<PreparedSceneHudEnvironment>> coordinatorFactory) {
        // The canonical project identity is captured by bindProject(), which is the project
        // lifecycle boundary. Keep the supplier argument for the existing construction contract,
        // but do not resolve it from the frame/update path.
        Objects.requireNonNull(projectDir);
        this.configuration = Objects.requireNonNull(configuration);
        this.database = Objects.requireNonNull(database);
        this.documents = Objects.requireNonNull(documents);
        this.persistence = Objects.requireNonNull(persistence);
        this.builder = Objects.requireNonNull(builder);
        this.publisher = Objects.requireNonNull(publisher);
        this.coordinatorFactory = Objects.requireNonNull(coordinatorFactory);
        documents.addListener(lifecycle);
    }

    public void setErrorHandler(Consumer<RuntimeException> handler) { checkThread(); onError = Objects.requireNonNull(handler); }

    /** No eager build on binding/opening a project. Even the same root receives a fresh epoch. */
    public void bindProject(FileHandle root) {
        checkThread();
        if (closed) return;
        onProjectChanging();
        project = new ProjectIdentity(canonicalRoot(root), ++nextEpoch);
        for (var document : documents.documents()) if (document instanceof HudScreenEditorDocument hud) attach(hud);
    }

    /** Call before old documents/configuration are cleared. Late completions retain only old private paths. */
    public void onProjectChanging() {
        checkThread();
        project = null; // Block requests before detach/disposal callbacks.
        for (var hud : List.copyOf(listeners.keySet())) detach(hud);
        for (var work : scenes.values()) {
            work.request = null;
            work.coordinator.dispose();
        }
        scenes.clear();
    }

    /** Reusable metadata/dependency invalidation seam; tag comes from ProjectConfig.canonicalSceneTagFor. */
    public void invalidateScene(String sceneTag) {
        invalidateSceneResolved(sceneTag);
    }

    private boolean invalidateSceneResolved(String sceneTag) {
        checkThread();
        if (!currentProject()) return false;
        try {
            String tag = SceneHudEnvironmentPaths.sceneTag(sceneTag);
            SceneMeta scene = findScene(tag);
            if (scene == null) return false;
            Snapshot snapshot = capture(scene);
            SceneWork work = scenes.computeIfAbsent(tag, ignored -> createWork());
            requestIfPhysicalInputsChanged(tag, work, snapshot);
            return true;
        } catch (RuntimeException failure) { report(failure); return false; }
    }

    private void requestIfPhysicalInputsChanged(String tag, SceneWork work, Snapshot snapshot) {
        FileHandle root = new FileHandle(project.root);
        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, snapshot.database, snapshot.roots, snapshot.screens);
        String packKey = packKey(root, snapshot, closure);
        work.currentSnapshot = snapshot;
        work.currentClosure = closure;
        work.currentPackKey = packKey;
        work.compositionRevision++;
        work.resourceRevision++;
        if (work.request != null && packKey.equals(work.currentRequestPackKey)) return;
        if (work.publishedGeneration > 0L && packKey.equals(work.publishedPackKey)
                && publishedAtlasAvailable(root, tag, snapshot)) {
            if (work.request != null) {
                work.request = null;
                work.currentRequestPackKey = null;
                work.coordinator.dispose();
                work.coordinator = coordinatorFor(work);
            }
            work.publishedCompositionRevision = work.compositionRevision;
            work.publishedResourceRevision = work.resourceRevision;
            return;
        }
        long generation = work.coordinator.requestAsyncPack(tag, AsyncAtlasRepackCoordinator.RepackReason.GENERIC);
        work.currentRequestPackKey = packKey;
        work.request = new Request(project, tag, generation, snapshot, closure);
    }

    private static String packKey(FileHandle root, Snapshot snapshot, SceneHudDependencyClosure closure) {
        return snapshot.roots.isEmpty() ? "empty" : SceneHudPackFingerprint.of(root,
                new SceneHudPackInputProjector().project(root, closure));
    }

    private static boolean publishedAtlasAvailable(FileHandle root, String tag, Snapshot snapshot) {
        if (snapshot.roots.isEmpty()) return true; // An empty closure has no atlas artifact.
        FileHandle descriptor = SceneHudEnvironmentPaths.liveDirectory(root, tag)
                .child(SceneHudEnvironmentPaths.ATLAS_FILE);
        return descriptor.exists() && !descriptor.isDirectory();
    }

    /** Requests the current Scene HUD snapshot unless that Scene already has work in flight. */
    public void ensureSceneRequested(String sceneTag) {
        checkThread();
        if (!currentProject()) return;
        String tag;
        try { tag = SceneHudEnvironmentPaths.sceneTag(sceneTag); }
        catch (RuntimeException failure) { report(failure); return; }
        SceneWork work = scenes.get(tag);
        if (work != null && (work.request != null || work.publishedGeneration > 0L)) return;
        invalidateScene(tag);
    }

    /** Last generation successfully published for this Scene, or zero before first publication. */
    public long publishedGeneration(String sceneTag) {
        checkThread();
        if (!currentProject()) return 0L;
        String tag;
        try { tag = SceneHudEnvironmentPaths.sceneTag(sceneTag); }
        catch (RuntimeException failure) { return 0L; }
        SceneWork work = scenes.get(tag);
        return work == null ? 0L : work.publishedGeneration;
    }

    /** Revision of the composition that can use the currently published Scene HUD atlas. */
    public long compositionGeneration(String sceneTag) {
        checkThread();
        if (!currentProject()) return 0L;
        String tag;
        try { tag = SceneHudEnvironmentPaths.sceneTag(sceneTag); }
        catch (RuntimeException failure) { return 0L; }
        SceneWork work = scenes.get(tag);
        return work == null || work.publishedGeneration == 0L ? 0L
                : Objects.equals(work.currentPackKey, work.publishedPackKey)
                ? work.compositionRevision : work.publishedCompositionRevision;
    }

    /** Resource revision usable with the published atlas; authored-only edits leave it intact. */
    public long resourceGeneration(String sceneTag) {
        checkThread();
        if (!currentProject()) return 0L;
        String tag;
        try { tag = SceneHudEnvironmentPaths.sceneTag(sceneTag); }
        catch (RuntimeException failure) { return 0L; }
        SceneWork work = scenes.get(tag);
        return work == null || work.publishedGeneration == 0L ? 0L
                : Objects.equals(work.currentPackKey, work.publishedPackKey)
                ? work.resourceRevision : work.publishedResourceRevision;
    }

    /** Initially O(number of Scenes), direct/default roots only. Open membership is never a root. */
    public void invalidateHudScreen(String screenId) {
        invalidateHudScreen(screenId, null);
    }

    /** A reimport affects Scene roots through Runtime usage, but active authoring also loads an associated Skin. */
    public record ReimportImpact(Set<String> openScreensToReload, Set<String> uncertainOpenScreens,
                                 Set<String> invalidatedScenes, Set<String> uncertainScenes) {
        public ReimportImpact {
            openScreensToReload = Set.copyOf(openScreensToReload);
            uncertainOpenScreens = Set.copyOf(uncertainOpenScreens);
            invalidatedScenes = Set.copyOf(invalidatedScenes);
            uncertainScenes = Set.copyOf(uncertainScenes);
        }
    }

    private record ReimportTarget(Integer fontAssetId, String skinId) {
        boolean runtimeUses(SceneHudDependencyCollector.HudUsage usage) {
            return fontAssetId != null ? usage.fontAssetIds().contains(fontAssetId)
                    : skinId.equals(usage.runtimeSkinId());
        }
        boolean authoringUses(SceneHudDependencyCollector.HudUsage usage) {
            return fontAssetId != null ? usage.fontAssetIds().contains(fontAssetId)
                    : skinId.equals(usage.authoringSkinId());
        }
        boolean runtimeUses(SceneHudDependencyClosure closure) {
            return fontAssetId != null
                    ? closure.fontDependencies().stream().anyMatch(font -> font.assetId() == fontAssetId)
                    : closure.skinDependencies().stream().anyMatch(skin -> skinId.equals(skin.skinId()));
        }
    }

    public ReimportImpact invalidateReimportedFont(int assetId) {
        if (assetId <= 0) throw new IllegalArgumentException("Font Asset ID must be positive.");
        return invalidateReimported(new ReimportTarget(assetId, null));
    }

    public ReimportImpact invalidateReimportedSkin(String skinId) {
        String normalized = SceneHudDependencyCollector.normalizedSkinId(skinId);
        if (normalized == null) throw new IllegalArgumentException("Skin source path is required.");
        return invalidateReimported(new ReimportTarget(null, normalized));
    }

    private ReimportImpact invalidateReimported(ReimportTarget target) {
        checkThread();
        if (!currentProject()) return new ReimportImpact(Set.of(), Set.of(), Set.of(), Set.of());
        FileHandle root = new FileHandle(project.root);
        var collector = new SceneHudDependencyCollector();
        Map<String, SceneHudScreenSnapshot> open = new LinkedHashMap<>();
        Map<String, SceneHudDependencyCollector.HudUsage> resolved = new LinkedHashMap<>();
        Map<String, RuntimeException> failures = new LinkedHashMap<>();
        Set<String> reload = new LinkedHashSet<>(), uncertainOpen = new LinkedHashSet<>();
        for (var document : documents.documents()) if (document instanceof HudScreenEditorDocument hud) {
            try {
                SceneHudScreenSnapshot snapshot = new SceneHudScreenSnapshot(hud.screenId(), hud.asset(), hud.document());
                open.put(hud.screenId(), snapshot);
                var usage = collector.inspectUsage(snapshot);
                resolved.put(hud.screenId(), usage);
                if (target.authoringUses(usage)) reload.add(hud.screenId());
            } catch (RuntimeException failure) {
                failures.put(hud.screenId(), failure);
                uncertainOpen.add(hud.screenId());
                report(failure);
            }
        }
        Set<String> invalidated = new LinkedHashSet<>(), uncertainScenes = new LinkedHashSet<>();
        ProjectConfig cfg = configuration.get();
        for (String name : cfg.getSceneNames()) {
            SceneMeta scene = cfg.getSceneMeta(name);
            String tag = name;
            try {
                tag = cfg.canonicalSceneTagFor(scene);
                List<String> roots = SceneHudRoots.collect(scene);
                if (roots.isEmpty()) continue;
                SceneHudDependencyClosure cached = reusableUsageClosure(
                        scenes.get(tag), roots, open, collector);
                boolean uses = cached != null && target.runtimeUses(cached);
                if (cached == null) for (String id : roots) {
                    RuntimeException previousFailure = failures.get(id);
                    if (previousFailure != null) throw previousFailure;
                    var usage = resolved.get(id);
                    if (usage == null) {
                        var saved = persistence.load(root, id);
                        usage = collector.inspectUsage(new SceneHudScreenSnapshot(id, saved.asset(), saved.document()));
                        resolved.put(id, usage);
                    }
                    uses |= target.runtimeUses(usage);
                }
                if (uses) {
                    if (invalidateSceneResolved(tag)) invalidated.add(tag);
                    else uncertainScenes.add(tag);
                }
            } catch (RuntimeException failure) {
                uncertainScenes.add(tag);
                report(new IllegalStateException("Cannot resolve Scene HUD reimport usage for " + tag, failure));
            }
        }
        return new ReimportImpact(reload, uncertainOpen, invalidated, uncertainScenes);
    }

    private static SceneHudDependencyClosure reusableUsageClosure(SceneWork work, List<String> roots,
            Map<String, SceneHudScreenSnapshot> open, SceneHudDependencyCollector collector) {
        if (work == null || work.currentClosure == null || work.currentSnapshot == null
                || !work.currentSnapshot.roots.equals(roots)) return null;
        for (String id : roots) {
            SceneHudScreenSnapshot current = open.get(id);
            if (current == null || !collector.samePackReferences(work.currentSnapshot.screen(id), current)) return null;
        }
        return work.currentClosure;
    }

    private void invalidateHudScreen(String screenId, HudScreenEditorDocument authored) {
        checkThread();
        if (!currentProject()) return;
        String canonicalId;
        try { canonicalId = HudScreenAssetId.normalize(screenId); }
        catch (RuntimeException failure) { report(failure); return; }
        ProjectConfig cfg = configuration.get();
        for (String name : cfg.getSceneNames()) {
            SceneMeta scene = cfg.getSceneMeta(name);
            try {
                if (SceneHudRoots.collect(scene).contains(canonicalId)) {
                    String tag = cfg.canonicalSceneTagFor(scene);
                    if (authored == null || !reuseResolvedReferences(tag, authored)) invalidateScene(tag);
                }
            } catch (RuntimeException failure) { report(failure); }
        }
    }

    private boolean reuseResolvedReferences(String tag, HudScreenEditorDocument authored) {
        SceneWork work = scenes.get(tag);
        if (work == null || work.currentSnapshot == null) return false;
        SceneHudScreenSnapshot next = new SceneHudScreenSnapshot(authored.screenId(),
                authored.asset(), authored.document());
        SceneHudScreenSnapshot previous = work.currentSnapshot.screen(authored.screenId());
        if (!new SceneHudDependencyCollector().samePackReferences(previous, next)) return false;
        work.currentSnapshot = work.currentSnapshot.withScreen(next);
        work.compositionRevision++;
        return true;
    }

    public void update() {
        checkThread();
        if (!currentProject()) return;
        for (var entry : List.copyOf(scenes.entrySet())) {
            SceneWork work = entry.getValue();
            work.coordinator.update();
            var failure = work.coordinator.pollFailure();
            if (failure != null) {
                Request failed = work.request;
                if (failed != null && failed.generation == failure.generation()
                        && failed.sceneTag.equals(failure.targetKey())) clearRequestIfCurrent(work, failed);
                report(new IllegalStateException("Scene HUD preparation failed for " + entry.getKey(), failure.cause()));
            }
            var ready = work.coordinator.pollReadyAsyncPack();
            if (ready == null) continue;
            Request consumed = work.request;
            try (var payload = ready.takePayload()) {
                apply(work, payload);
            } catch (RuntimeException failureDuringApply) {
                clearRequestIfCurrent(work, consumed);
                report(failureDuringApply);
            }
        }
    }

    private SceneWork createWork() {
        SceneWork work = new SceneWork();
        work.coordinator = coordinatorFor(work);
        return work;
    }

    private AsyncAtlasRepackCoordinator<PreparedSceneHudEnvironment> coordinatorFor(SceneWork work) {
        long coordinatorEpoch = ++work.coordinatorEpoch;
        return coordinatorFactory.apply((tag, generation, reason) -> {
            Request captured = work.request; // Volatile immutable handoff; never read editor state here.
            if (work.coordinatorEpoch != coordinatorEpoch || captured == null
                    || captured.generation != generation || !captured.sceneTag.equals(tag))
                throw new CancellationException("Scene HUD request superseded before worker snapshot handoff");
            return prepare(captured);
        });
    }

    private PreparedSceneHudEnvironment prepare(Request request) {
        FileHandle sourceProject = new FileHandle(request.project.root);
        Snapshot snapshot = request.snapshot;
        AssetMetaDatabase capturedDatabase = snapshot.database;
        var collector = new SceneHudDependencyCollector();
        var closure = request.closure;
        if (snapshot.roots.isEmpty()) return payload(request, SceneHudBuildStamp.of(sourceProject, closure),
                "empty", null, null, null);
        FileHandle workspace = null;
        FileHandle atlas = null;
        Throwable primary = null;
        try {
            workspace = AtomicDirectoryPublication.createCandidate(sourceProject.child(StudioFs.DIR_ATLASES)
                    .child("hud-scene-" + request.sceneTag + "-" + request.generation));
            FileHandle sources = workspace.child("sources");
            SceneHudBuildStamp.copySources(sourceProject, closure, sources);
            closure = collector.collect(sources, capturedDatabase, snapshot.roots, snapshot.screens);
            String stamp = SceneHudBuildStamp.of(sources, closure);
            var plan = new SceneHudPackInputProjector().project(sources, closure);
            String physicalKey = SceneHudPackFingerprint.of(sources, plan);
            var manifest = new SceneHudPackInputMaterializer().materialize(sources, workspace.child("inputs"), plan);
            // Separate owned sibling candidate satisfies AtomicDirectoryPublication's immediate .tmp contract.
            atlas = AtomicDirectoryPublication.createCandidate(SceneHudEnvironmentPaths.liveDirectory(sourceProject, request.sceneTag));
            var result = builder.build(workspace.child("inputs"), manifest, atlas);
            return payload(request, stamp, physicalKey, workspace, atlas, result);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary != null) try { payload(request, "", "", workspace, atlas, null).close(); }
            catch (RuntimeException cleanup) { primary.addSuppressed(cleanup); }
        }
    }

    private static PreparedSceneHudEnvironment payload(Request r, String stamp, String physicalKey, FileHandle workspace,
                                                       FileHandle atlas, SceneHudAtlasBuilder.Result result) {
        return new PreparedSceneHudEnvironment(r.project.root, r.project.epoch, r.sceneTag, r.generation,
                stamp, physicalKey, workspace, atlas, result);
    }

    private void apply(SceneWork work, PreparedSceneHudEnvironment payload) {
        if (!currentProject() || payload.projectEpoch != project.epoch || !payload.projectRoot.equals(project.root)) return;
        Request consumed = work.request;
        if (consumed == null || work.coordinator.currentGeneration() != payload.generation
                || consumed.generation != payload.generation || !consumed.project.equals(project)
                || !consumed.sceneTag.equals(payload.sceneTag)) return;
        SceneMeta scene = findScene(payload.sceneTag);
        if (scene == null) { clearRequestIfCurrent(work, consumed); return; }
        Snapshot current = capture(scene);
        FileHandle root = new FileHandle(project.root);
        var closure = new SceneHudDependencyCollector().collect(root,
                current.database, current.roots, current.screens);
        String currentPackKey = packKey(root, current, closure);
        if (!payload.physicalKey.equals(currentPackKey)) {
            // This ready result consumed the request. Keeping it would deduplicate a needed
            // retry if source bytes changed during copy and then returned to the request key.
            clearRequestIfCurrent(work, consumed);
            invalidateScene(payload.sceneTag); // Same project only; old project never reaches this branch.
            return;
        }
        boolean authoredOrResourcesChanged = !payload.stamp.equals(SceneHudBuildStamp.of(root, closure));
        work.currentSnapshot = current;
        work.currentClosure = closure;
        work.currentPackKey = currentPackKey;
        if (authoredOrResourcesChanged) {
            work.compositionRevision++;
            // A source may have changed without a Studio notification while the worker ran.
            // Refresh logical resources even when its pixels do not change the physical key.
            work.resourceRevision++;
        }
        // No callback can interleave a new generation/project switch: all API calls enforce Studio affinity.
        FileHandle live = SceneHudEnvironmentPaths.liveDirectory(root, payload.sceneTag);
        if (!Objects.equals(work.publishedPackKey, currentPackKey) || work.publishedGeneration == 0L
                || !publishedAtlasAvailable(root, payload.sceneTag, current)) {
            if (payload.empty()) AtomicDirectoryPublication.retire(live);
            else try (var publication = AtomicDirectoryPublication.publish(payload.atlasCandidate, live, publisher)) {
                publication.commit();
            }
        }
        work.publishedPackKey = currentPackKey;
        work.publishedGeneration = payload.generation;
        work.publishedCompositionRevision = work.compositionRevision;
        work.publishedResourceRevision = work.resourceRevision;
        clearRequestIfCurrent(work, consumed);
    }

    private static void clearRequestIfCurrent(SceneWork work, Request expected) {
        if (expected != null && work.request == expected) {
            work.request = null;
            work.currentRequestPackKey = null;
        }
    }

    private Snapshot capture(SceneMeta scene) {
        List<String> roots = SceneHudRoots.collect(scene);
        List<SceneHudScreenSnapshot> selected = new ArrayList<>();
        for (String root : roots) {
            HudScreenEditorDocument open = null;
            for (var document : documents.documents()) if (document instanceof HudScreenEditorDocument hud
                    && hud.screenId().equals(root)) { open = hud; break; }
            if (open != null) selected.add(new SceneHudScreenSnapshot(root, open.asset(), open.document()));
            else {
                var saved = persistence.load(new FileHandle(project.root), root);
                selected.add(new SceneHudScreenSnapshot(root, saved.asset(), saved.document()));
            }
        }
        var detachedDatabase = AssetMetaDatabase.fromSnapshotJson(
                Objects.requireNonNull(database.get(), "Asset metadata unavailable").toSnapshotJson());
        return new Snapshot(roots, selected, detachedDatabase);
    }

    private SceneMeta findScene(String tag) {
        ProjectConfig cfg = configuration.get();
        SceneMeta found = null;
        for (String name : cfg.getSceneNames()) {
            SceneMeta scene = cfg.getSceneMeta(name);
            String candidate = cfg.canonicalSceneTagFor(scene);
            if (candidate != null && candidate.equalsIgnoreCase(tag)) {
                if (found != null) throw new IllegalStateException("Ambiguous Scene HUD output tag: " + tag);
                if (candidate.equals(tag)) found = scene;
                else throw new IllegalStateException("Scene HUD tag differs only by case: " + candidate + " / " + tag);
            }
        }
        return found;
    }

    private void attach(HudScreenEditorDocument hud) {
        checkThread();
        if (closed || project == null || listeners.containsKey(hud)) return;
        HudDocumentEditSession.AuthoredPublicationListener listener = ignored -> invalidateHudScreen(hud.screenId(), hud);
        hud.editSession().addAuthoredPublicationListener(listener);
        listeners.put(hud, listener);
    }
    private void detach(HudScreenEditorDocument hud) {
        var listener = listeners.remove(hud);
        if (listener != null) hud.editSession().removeAuthoredPublicationListener(listener);
    }
    /**
     * Project identity is a session invariant. All supported project changes call
     * onProjectChanging() before configuration/documents change and bindProject() once the new
     * root is authoritative. bindProject() canonicalizes after clearing the old session, so a
     * canonicalization failure cannot leave an old identity active.
     */
    private boolean currentProject() { return !closed && project != null; }
    private static String canonicalRoot(FileHandle root) {
        try { return Objects.requireNonNull(root).file().getCanonicalPath(); }
        catch (IOException failure) { throw new IllegalStateException("Unable to identify Scene HUD project", failure); }
    }
    private void checkThread() {
        if (Thread.currentThread() != studioThread) throw new IllegalStateException("Scene HUD service requires the Studio thread");
    }
    private void report(RuntimeException failure) {
        try { onError.accept(failure); }
        catch (RuntimeException handlerFailure) {
            if (Gdx.app != null) Gdx.app.error("SceneHudRuntimePreparation", "Error handler failed", handlerFailure);
        }
    }
    long generation(String tag) { checkThread(); var work = scenes.get(tag); return work == null ? 0 : work.coordinator.currentGeneration(); }
    boolean isPending(String tag) { checkThread(); var work = scenes.get(tag); return work != null && work.request != null; }

    @Override public void close() {
        checkThread();
        if (closed) return;
        onProjectChanging();
        closed = true;
        documents.removeListener(lifecycle);
    }
}
