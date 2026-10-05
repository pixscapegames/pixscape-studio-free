package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.DeleteEntitiesCommandFactory;
import games.pixscape.studio.service.entitygraph.*;
import games.pixscape.studio.ui.main.WorldCanvas;


public final class ClipboardService {

    private static final float PASTE_STEP_X = 16f;
    private static final float PASTE_STEP_Y = -16f;

    private final WorldCanvas canvas;
    private final World world;
    private final games.pixscape.studio.scene.SceneEditorContext editingContext;
    private final ProjectConfig project = ProjectConfig.getInstance();
    private final SelectionService selectionService;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;

    private final EntityGraphCaptureService graphCaptureService;
    private final ClipboardSelectionNormalizer selectionNormalizer;
    private final EntityGraphInstantiationService graphInstantiationService;

    private EntityGraph graph = EntityGraph.empty();
    private int pasteCount = 0;

    public ClipboardService(WorldCanvas canvas, IdentityRegistry identityRegistry) {
        this.canvas = canvas;
        this.world = canvas.getEcsWorld();
        this.selectionService = canvas.getSelectionService();
        this.historyManager = canvas.getHistoryManager();

        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null.");
        }
        this.identityRegistry = identityRegistry;

        this.graphCaptureService = new EntityGraphCaptureService(world);
        this.selectionNormalizer = new ClipboardSelectionNormalizer(world);
        editingContext = canvas.getSceneEditorContext();
        this.graphInstantiationService = new EntityGraphInstantiationService(
                world, historyManager, identityRegistry, canvas.getPhysicsService(),
                () -> editingContext.sceneMeta() != null && editingContext.sceneMeta().physicsEnabled,
                entityId -> canvas.requestParticleRuntimeAvailabilityRefreshIfParticleEntity(editingContext, entityId));

    }

    public boolean hasContent() {
        return belongsToCurrentProject() && !graph.isEmpty();
    }

    public void clear() {
        graph = EntityGraph.empty();
        pasteCount = 0;
    }

    public boolean copySelection() {
        if (!belongsToCurrentProject()) return false;
        IntArray normalized = normalizeClipboardSelection();
        if (normalized == null) return false;
        try {
            graph = graphCaptureService.captureNormalizedClipboard(normalized);
        } catch (IllegalArgumentException ignored) {
            graph = EntityGraph.empty();
            return false;
        }
        if (graph.isEmpty()) {
            return false;
        }
        pasteCount = 0;
        return true;
    }

    public boolean cutSelection() {
        if (!belongsToCurrentProject()) return false;
        IntArray normalized = normalizeClipboardSelection();
        if (normalized == null) return false;
        try {
            graph = graphCaptureService.captureNormalizedClipboard(normalized);
        } catch (IllegalArgumentException ignored) {
            graph = EntityGraph.empty();
            return false;
        }
        if (graph.isEmpty()) {
            return false;
        }

        IntArray cutEntities;
        try {
            cutEntities = graphCaptureService.captureNormalizedClipboardCutEntities(normalized);
        } catch (IllegalArgumentException ignored) {
            graph = EntityGraph.empty();
            return false;
        }
        historyManager.execute(DeleteEntitiesCommandFactory.create(
                world,
                historyManager.historyIds(),
                cutEntities,
                entityId -> canvas.requestParticleRuntimeAvailabilityRefreshIfParticleEntity(
                        editingContext, entityId)));
        selectionService.clearSelection();
        pasteCount = 0;
        return true;
    }

    public boolean paste() {
        if (!belongsToCurrentProject()) return false;
        if (graph.isEmpty()) {
            return false;
        }
        if (!graphInstantiationService.isInstantiationAllowed(graph)) {
            if (Gdx.app != null) {
                Gdx.app.error(
                        "Clipboard",
                        "Cannot paste authored Physics while scene Physics is disabled.");
            }
            return false;
        }

        ResolvedClipboardDestination destination = resolveClipboardDestination();
        if (destination == null) {
            return false;
        }
        if (!graphInstantiationService.isClipboardInstantiationAllowed(
                graph, destination.targetLayer())) {
            if (Gdx.app != null) {
                Gdx.app.error(
                        "Clipboard",
                        "Cannot paste a Game Object hierarchy containing Spatial actor data "
                                + "onto a non-Spatial Layer.");
            }
            return false;
        }
        float dx = (pasteCount + 1) * PASTE_STEP_X;
        float dy = (pasteCount + 1) * PASTE_STEP_Y;

        EntityGraphInstantiationResult result =
                graphInstantiationService.instantiateForClipboard(
                        graph,
                        destination.layerIndex(),
                        dx,
                        dy,
                        "Paste",
                        destination.targetLayer());
        if (result.createdIds().size == 0) {
            return false;
        }

        pasteCount++;
        selectionService.clearSelection();
        selectionService.selectOnly(result.createdRootIds().get(0));
        for (int i = 1; i < result.createdRootIds().size; i++) {
            selectionService.selectAdd(result.createdRootIds().get(i));
        }

        return true;
    }

    private IntArray normalizeClipboardSelection() {
        try {
            return selectionNormalizer.normalize(selectionService.getSelectionSnapshot());
        } catch (IllegalArgumentException ignored) {
            // Preserve existing failed-Copy behavior: the clipboard is cleared, Scene untouched.
            graph = EntityGraph.empty();
            return null;
        }
    }

    private boolean belongsToCurrentProject() {
        if (!editingContext.isDisposed() && ProjectConfig.getInstance() == project) return true;
        clear();
        return false;
    }

    private ResolvedClipboardDestination resolveClipboardDestination() {
        LayerService layers = editingContext.layerService();
        if (layers == null) return null;

        int layerIndex = selectionService.getActiveLayerIndex();
        int layerEntityId = layers.getLayerEntity(layerIndex);
        if (layerEntityId < 0
                || layerEntityId != selectionService.getActivelayerId()
                || !world.getEntityManager().isActive(layerEntityId)) {
            return null;
        }

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null || layer.layerIndex != layerIndex) {
            return null;
        }

        EntityGraphInstantiationService.ClipboardTargetLayer targetLayer =
                LayerService.isSpatialActorLayer(layer)
                        ? EntityGraphInstantiationService.ClipboardTargetLayer.SPATIAL_ENABLED
                        : EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL;
        return new ResolvedClipboardDestination(layerIndex, targetLayer);
    }

    private record ResolvedClipboardDestination(
            int layerIndex,
            EntityGraphInstantiationService.ClipboardTargetLayer targetLayer) {
    }
}
