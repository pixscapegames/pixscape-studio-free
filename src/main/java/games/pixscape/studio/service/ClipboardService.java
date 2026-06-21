package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.DeleteEntitiesCommand;
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

    public ClipboardService(WorldCanvas canvas) {
        this.canvas = canvas;
        this.world = canvas.getEcsWorld();
        this.selectionService = canvas.getSelectionService();
        this.historyManager = canvas.getHistoryManager();

        this.identityRegistry = new IdentityRegistry();
        this.identityRegistry.bind(world);
        this.identityRegistry.rebuild();

        this.graphCaptureService = new EntityGraphCaptureService(world);
        this.graphInstantiationService = new EntityGraphInstantiationService(world, historyManager, identityRegistry);

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

        historyManager.execute(new DeleteEntitiesCommand(
                world,
                historyManager.historyIds(),
                supported
        ));
        selectionService.clearSelection();
        pasteCount = 0;
        return true;
    }

    public boolean paste() {
        if (graph.isEmpty()) {
            return false;
        }

        int activeLayerIndex = selectionService.getActiveLayerIndex();
        float dx = (pasteCount + 1) * PASTE_STEP_X;
        float dy = (pasteCount + 1) * PASTE_STEP_Y;

        EntityGraphInstantiationResult result =
                graphInstantiationService.instantiate(graph, activeLayerIndex, dx, dy, "Paste");
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
}
