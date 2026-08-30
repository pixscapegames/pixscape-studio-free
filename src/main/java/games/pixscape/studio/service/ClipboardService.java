package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.DeleteEntitiesCommandFactory;
import games.pixscape.studio.service.entitygraph.*;
import games.pixscape.studio.ui.main.WorldCanvas;


public final class ClipboardService {

    private static final float PASTE_STEP_X = 16f;
    private static final float PASTE_STEP_Y = -16f;

    private final WorldCanvas canvas;
    private final World world;
    private final SelectionService selectionService;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;

    private final EntityGraphCaptureService graphCaptureService;
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
        this.graphInstantiationService = new EntityGraphInstantiationService(
                world, historyManager, identityRegistry, canvas.getPhysicsService(),
                canvas::isScenePhysicsEnabled,
                canvas::requestParticleRuntimeAvailabilityRefreshIfParticleEntity);

        EventFlow.i().subscribe(EventFlow.CurrentSceneMeta.class, evt -> clear());
    }

    public boolean hasContent() {
        return !graph.isEmpty();
    }

    public void clear() {
        graph = EntityGraph.empty();
        pasteCount = 0;
    }

    public boolean copySelection() {
        graph = graphCaptureService.capture(selectionService.getSelectionSnapshot());
        if (graph.isEmpty()) {
            return false;
        }
        pasteCount = 0;
        return true;
    }

    public boolean cutSelection() {
        graph = graphCaptureService.capture(selectionService.getSelectionSnapshot());
        if (graph.isEmpty()) {
            return false;
        }

        IntArray supported = new IntArray();
        for (EntityGraphEntry entry : graph.entries()) {
            supported.add(entry.sourceEntityId());
        }

        historyManager.execute(DeleteEntitiesCommandFactory.create(
                world,
                historyManager.historyIds(),
                supported,
                canvas::requestParticleRuntimeAvailabilityRefreshIfParticleEntity));
        selectionService.clearSelection();
        pasteCount = 0;
        return true;
    }

    public boolean paste() {
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
        selectionService.selectOnly(result.createdIds().get(0));
        for (int i = 1; i < result.createdIds().size; i++) {
            selectionService.selectAdd(result.createdIds().get(i));
        }

        return true;
    }

    private ResolvedClipboardDestination resolveClipboardDestination() {
        LayerService layers = canvas.getLayerService();
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
