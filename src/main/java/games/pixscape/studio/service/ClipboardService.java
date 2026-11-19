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
import games.pixscape.studio.service.spatial.SpatialActorAdmission;


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
        return copySelection(false);
    }

    public boolean copyVisualSelection() {
        return copySelection(true);
    }

    private boolean copySelection(boolean visualOnly) {
        if (!belongsToCurrentProject()) return false;
        IntArray normalized = normalizeClipboardSelection();
        if (normalized == null) return false;
        try {
            graph = graphCaptureService.captureNormalizedClipboard(normalized, visualOnly);
        } catch (IllegalArgumentException rejected) {
            canvas.showCompositionDiagnostic(rejected.getMessage());
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
        EntityGraph candidate;
        try {
            candidate = graphCaptureService.captureNormalizedClipboard(normalized);
        } catch (IllegalArgumentException rejected) {
            canvas.showCompositionDiagnostic(rejected.getMessage());
            return false;
        }
        if (candidate.isEmpty()) {
            return false;
        }

        IntArray cutEntities;
        try {
            cutEntities = graphCaptureService.captureNormalizedClipboardCutEntities(normalized);
        } catch (IllegalArgumentException rejected) {
            canvas.showCompositionDiagnostic(rejected.getMessage());
            return false;
        }
        historyManager.execute(DeleteEntitiesCommandFactory.create(
                world,
                historyManager.historyIds(),
                cutEntities,
                entityId -> canvas.requestParticleRuntimeAvailabilityRefreshIfParticleEntity(
                        editingContext, entityId)));
        graph = candidate;
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
        if (!graphInstantiationService.isInstantiationAllowed(
                graph)) {
            if (Gdx.app != null) {
                Gdx.app.error(
                        "Clipboard",
                        "Cannot paste physical content into a scene with Physics disabled.");
            }
            return false;
        }
        try {
            SpatialActorAdmission.requireGraphDestination(
                    world, graph, destination.layerIndex());
        } catch (IllegalArgumentException rejected) {
            canvas.showCompositionDiagnostic(rejected.getMessage());
            return false;
        }
        if (!historyManager.prepareLightCreation(graph.activeLightCount(), this::paste)) return false;
        float dx = (pasteCount + 1) * PASTE_STEP_X;
        float dy = (pasteCount + 1) * PASTE_STEP_Y;

        EntityGraphInstantiationResult result =
                graphInstantiationService.instantiate(
                        graph,
                        destination.layerIndex(),
                        dx,
                        dy,
                        "Paste");
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
        if (selectionService.getInspectedFootprintOwner() >= 0) {
            return null;
        }
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

        int layerEntityId = selectionService.getActivelayerId();
        if (layerEntityId < 0
                || !world.getEntityManager().isActive(layerEntityId)) {
            return null;
        }

        LayerComponent layer = world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer == null || layers.getLayerEntity(layer.layerIndex) != layerEntityId) {
            return null;
        }

        return new ResolvedClipboardDestination(layer.layerIndex);
    }

    private record ResolvedClipboardDestination(int layerIndex) {
    }
}
