package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.service.WorldBlockMutationService;
import games.pixscape.runtime.service.WorldBlockOwnerSnapshot;

/** Atomic full-layer authored-wall snapshot command for deletion and structure splits. */
public final class DeleteSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final CommandOutcome initialOutcome;
    private WorldBlockOwnerSnapshot beforeOwner;
    private WorldBlockOwnerSnapshot afterOwner;

    public DeleteSpatialBlockCommand(World world, HistoryIdRegistry historyIds,
                                     SpatialBlockSelectionService selection,
                                     int layerEntityId, int blockId) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.blockId = blockId;
        SpatialBlocksComponent component = world != null ? SpatialBlockCommandSupport.get(world, layerEntityId) : null;
        this.before = SpatialBlockCommandSupport.snapshot(component);
        TiledLayerComponent tiled = world != null
                ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null) : null;
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.delete(
                component, blockId, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        this.initialOutcome = !plan.valid ? CommandOutcome.REJECTED
                : world == null || historyIds == null || layerHistoryId <= 0L
                ? CommandOutcome.NO_CHANGE : CommandOutcome.APPLIED;
    }

    @Override public String label() { return "Delete Spatial Wall"; }
    @Override public boolean isNoop() { return initialOutcome != CommandOutcome.APPLIED; }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return applyAfter();
    }

    @Override
    public CommandOutcome redoOutcome() {
        if (afterOwner != null) {
            WorldBlockMutationService service = SpatialBlockCommandSupport.mutationService(world);
            if (service != null) {
                service.restoreOwnerState(afterOwner);
                return CommandOutcome.APPLIED;
            }
        }
        return applyAfter();
    }

    private CommandOutcome applyAfter() {
        if (initialOutcome != CommandOutcome.APPLIED) return initialOutcome;
        int layer = resolveLayer();
        if (layer < 0) return CommandOutcome.NO_CHANGE;
        WorldBlockMutationService service = SpatialBlockCommandSupport.mutationService(world);
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .getSafe(layer, null);
        if (service != null && identity != null) {
            beforeOwner = service.captureOwnerState(identity.stableId);
            SpatialBlocksComponent current = SpatialBlockCommandSupport.get(world, layer);
            service.deleteSpatialBlock(identity.stableId, blockId,
                    current != null ? current.nextSpatialBlockId : 1, after);
            afterOwner = service.captureOwnerState(identity.stableId);
            if (selection != null && selection.getSelectedBlockId() == blockId) selection.enterLayer(layer);
            SpatialBlockCommandSupport.markChanged(world, layer, this);
            return CommandOutcome.APPLIED;
        }
        if (identity != null) return CommandOutcome.REJECTED;
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, after);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null && selection.getSelectedBlockId() == blockId) selection.enterLayer(layer);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        if (initialOutcome != CommandOutcome.APPLIED) return initialOutcome;
        int layer = resolveLayer();
        if (layer < 0) return CommandOutcome.NO_CHANGE;
        if (beforeOwner != null) {
            WorldBlockMutationService service = SpatialBlockCommandSupport.mutationService(world);
            if (service != null) {
                service.restoreOwnerState(beforeOwner);
                if (selection != null) selection.selectBlock(layer, blockId);
                SpatialBlockCommandSupport.markChanged(world, layer, this);
                return CommandOutcome.APPLIED;
            }
        }
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, before);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null) selection.selectBlock(layer, blockId);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }
}
