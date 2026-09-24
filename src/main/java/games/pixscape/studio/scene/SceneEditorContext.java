package games.pixscape.studio.scene;

import com.artemis.World;
import com.badlogic.gdx.utils.Disposable;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.physics.PhysicsSelectionReconciler;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

import java.util.Objects;

/**
 * Owns the mutable editor state for one open Scene document.
 *
 * <p>The context remains alive while its document tab is open. Activation only attaches its
 * state to the shared WorldCanvas; it never reloads or disposes the World.</p>
 */
public final class SceneEditorContext implements Disposable {
    public static final int DEFAULT_HISTORY_CAPACITY = 1024;

    private final HistoryManager historyManager;
    /** Non-owning compatibility projection; HUD/document activation remains application-owned. */
    private final StudioEditingModeService editingModeService;
    private final PhysicsSelectionService physicsSelectionService;
    private final PhysicsSelectionReconciler physicsSelectionReconciler;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final EventFlow.SubscriptionScope activeEventScope;

    private World world;
    private IdentityRegistry identityRegistry;
    private LayerService layerService;
    private SelectionService selectionService;
    private String sceneIdentity;
    private StudioEditingMode sceneSubmode = StudioEditingMode.NORMAL;
    private float cameraX;
    private float cameraY;
    private float cameraZoom = 1f;
    private boolean viewStateInitialized;
    private boolean explicitSaveRequired;
    private boolean active;
    private int attachCount;
    private int detachCount;
    private long processedFrameCount;
    private Runnable dirtyStateListener;
    private long generation;
    private int resetCount;
    private int disposeCount;
    private boolean disposed;

    public SceneEditorContext(String initialSceneIdentity,
                              StudioEditingModeService editingModeService) {
        this.editingModeService = Objects.requireNonNull(editingModeService, "editingModeService");
        activeEventScope = EventFlow.i().newSubscriptionScope(this::isActive);
        historyManager = new HistoryManager(DEFAULT_HISTORY_CAPACITY);
        physicsSelectionService = new PhysicsSelectionService(editingModeService);
        physicsSelectionReconciler = new PhysicsSelectionReconciler(physicsSelectionService);
        spatialBlockSelectionService = new SpatialBlockSelectionService(editingModeService);
        spatialTileSelectionService = new SpatialTileSelectionService();
        sceneIdentity = normalizeIdentity(initialSceneIdentity);
    }

    /** Completes the single context after WorldCanvas has assembled the Runtime World. */
    public SelectionService initializeWorld(World ownedWorld,
                                            TiledAllocatorService tiledAllocatorService,
                                            SceneMeta sceneMeta) {
        ensureUsable();
        if (world != null) throw new IllegalStateException("Scene context already owns a World.");
        world = Objects.requireNonNull(ownedWorld, "ownedWorld");
        identityRegistry = new IdentityRegistry();
        identityRegistry.bind(world, sceneMeta);
        identityRegistry.rebuild();
        layerService = new LayerService(
                world,
                Objects.requireNonNull(tiledAllocatorService, "tiledAllocatorService"),
                historyManager.historyIds(),
                identityRegistry);
        selectionService = new SelectionService(world, layerService, editingModeService);
        physicsSelectionReconciler.bindWorld(world);
        physicsSelectionService.setContextActive(false);
        spatialBlockSelectionService.setContextActive(false);
        spatialTileSelectionService.setContextActive(false);
        return selectionService;
    }

    /** Clears per-Scene editor state at the existing single-Scene replacement boundary. */
    public void resetForSceneReplacement(int sourceTag) {
        ensureInitialized();
        boolean replacingMaterializedScene = sceneIdentity != null;
        selectionService.clearTiledMapEditingTarget();
        selectionService.clearSelection();
        physicsSelectionService.clear();
        spatialBlockSelectionService.clear();
        spatialTileSelectionService.clear();
        editingModeService.reset(sourceTag);
        sceneSubmode = StudioEditingMode.NORMAL;
        historyManager.clear();
        historyManager.historyIds().clear();
        sceneIdentity = null;
        if (replacingMaterializedScene) {
            generation++;
            resetCount++;
        }
    }

    /** Binds the canonical identity of the Scene loaded into this single live context. */
    public void bindSceneIdentity(String canonicalSceneIdentity) {
        ensureInitialized();
        sceneIdentity = Objects.requireNonNull(
                normalizeIdentity(canonicalSceneIdentity), "canonicalSceneIdentity");
    }

    public String sceneIdentity() { return sceneIdentity; }
    public long generation() { return generation; }
    public int resetCount() { return resetCount; }
    public int disposeCount() { return disposeCount; }
    public boolean isDisposed() { return disposed; }
    public boolean isInitialized() { return world != null && !disposed; }
    public boolean isActive() { return active && !disposed; }
    public int attachCount() { return attachCount; }
    public int detachCount() { return detachCount; }
    public long processedFrameCount() { return processedFrameCount; }

    public World world() { ensureInitialized(); return world; }
    public HistoryManager historyManager() { ensureUsable(); return historyManager; }
    public SelectionService selectionService() { ensureInitialized(); return selectionService; }
    public LayerService layerService() { ensureInitialized(); return layerService; }
    public IdentityRegistry identityRegistry() { ensureInitialized(); return identityRegistry; }
    public PhysicsSelectionService physicsSelectionService() {
        ensureUsable();
        return physicsSelectionService;
    }
    public PhysicsSelectionReconciler physicsSelectionReconciler() {
        ensureUsable();
        return physicsSelectionReconciler;
    }
    public SpatialBlockSelectionService spatialBlockSelectionService() {
        ensureUsable();
        return spatialBlockSelectionService;
    }
    public SpatialTileSelectionService spatialTileSelectionService() {
        ensureUsable();
        return spatialTileSelectionService;
    }

    public StudioEditingMode sceneSubmode() { return sceneSubmode; }

    public void rememberSceneSubmode(StudioEditingMode mode) {
        if (mode != null && mode != StudioEditingMode.HUD) sceneSubmode = mode;
    }

    public void captureView(float x, float y, float zoom) {
        ensureUsable();
        cameraX = x;
        cameraY = y;
        cameraZoom = zoom > 0f ? zoom : 1f;
        viewStateInitialized = true;
    }

    public boolean hasViewState() { return viewStateInitialized; }
    public float cameraX() { return cameraX; }
    public float cameraY() { return cameraY; }
    public float cameraZoom() { return cameraZoom; }

    public void markExplicitSaveRequired() {
        ensureUsable();
        explicitSaveRequired = true;
        notifyDirtyStateChanged();
    }

    public void markSaved() {
        ensureUsable();
        historyManager.markSaved();
        explicitSaveRequired = false;
        notifyDirtyStateChanged();
    }

    public boolean isDirty() {
        ensureUsable();
        return historyManager.isDirty() || explicitSaveRequired;
    }

    public void setDirtyStateListener(Runnable listener) {
        dirtyStateListener = listener;
        historyManager.setListener((undoSize, redoSize, undoLabel, redoLabel, dirty) ->
                notifyDirtyStateChanged());
    }

    private void notifyDirtyStateChanged() {
        if (dirtyStateListener != null) dirtyStateListener.run();
    }

    /** Lifecycle hook called only by the shared canvas attachment seam. */
    public void attached() {
        ensureInitialized();
        if (active) return;
        active = true;
        attachCount++;
        physicsSelectionService.setContextActive(true);
        spatialBlockSelectionService.setContextActive(true);
        spatialTileSelectionService.setContextActive(true);
    }

    /** Suspension preserves all Scene-owned state. */
    public void detached() {
        if (!active) return;
        active = false;
        detachCount++;
        physicsSelectionService.setContextActive(false);
        spatialBlockSelectionService.setContextActive(false);
        spatialTileSelectionService.setContextActive(false);
    }

    public void recordProcessedFrame() {
        ensureInitialized();
        if (!active) throw new IllegalStateException("Inactive Scene context cannot process.");
        processedFrameCount++;
    }

    public void collectActiveUiSubscriptions(Runnable registration) {
        activeEventScope.run(registration);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        disposeCount++;
        detached();
        physicsSelectionReconciler.bindWorld(null);
        physicsSelectionService.dispose();
        spatialBlockSelectionService.dispose();
        spatialTileSelectionService.dispose();
        activeEventScope.close();
        if (identityRegistry != null) identityRegistry.bind(null, null);
        if (world != null) world.dispose();
        world = null;
        selectionService = null;
        layerService = null;
        identityRegistry = null;
        sceneIdentity = null;
    }

    private void ensureInitialized() {
        ensureUsable();
        if (world == null) throw new IllegalStateException("Scene context has no World yet.");
    }

    private void ensureUsable() {
        if (disposed) throw new IllegalStateException("Scene context is disposed.");
    }

    private static String normalizeIdentity(String identity) {
        if (identity == null) return null;
        String normalized = identity.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
