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

/** Atomic full-layer authored-wall snapshot command for creation and structure merges. */
public final class AddSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final int blockId;
    private final CommandOutcome initialOutcome;
    private boolean identityAllocated;
    private WorldBlockOwnerSnapshot beforeOwner;
    private WorldBlockOwnerSnapshot afterOwner;

    public AddSpatialBlockCommand(World world, HistoryIdRegistry historyIds,
                                  SpatialBlockSelectionService selection, int layerEntityId,
                                  SpatialBlockData block) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        SpatialBlocksComponent component = world != null ? SpatialBlockCommandSupport.get(world, layerEntityId) : null;
        this.before = SpatialBlockCommandSupport.snapshot(component);
        TiledLayerComponent tiled = world != null
                ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null) : null;
        SpatialBlockData prepared = block != null ? block.copy() : null;
        if (prepared != null) {
            prepared.id = component != null ? component.peekNextSpatialBlockId() : 1;
        }
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(
                component, prepared, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        this.blockId = addedBlockId(before, after);
        this.initialOutcome = !plan.valid ? CommandOutcome.REJECTED
                : world == null || historyIds == null || layerHistoryId <= 0L || blockId <= 0
                ? CommandOutcome.NO_CHANGE : CommandOutcome.APPLIED;
    }

    @Override public String label() { return "Add Spatial Wall"; }
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
            SpatialBlocksComponent current = SpatialBlockCommandSupport.get(world, layer);
            int next = current != null ? current.nextSpatialBlockId : 1;
            if (!identityAllocated && next != blockId) throw new IllegalStateException(
                    "Spatial block allocation changed after command preparation.");
            beforeOwner = service.captureOwnerState(identity.stableId);
            service.replaceSpatialBlocks(identity.stableId, identityAllocated ? next : next + 1, after);
            identityAllocated = true;
            afterOwner = service.captureOwnerState(identity.stableId);
            if (selection != null) selection.selectBlock(layer, blockId);
            SpatialBlockCommandSupport.markChanged(world, layer, this);
            return CommandOutcome.APPLIED;
        }
        if (!identityAllocated) {
            SpatialBlocksComponent current = SpatialBlockCommandSupport.get(world, layer);
            int currentNextSpatialBlockId = current != null ? current.peekNextSpatialBlockId() : 1;
            if (currentNextSpatialBlockId != blockId) {
                throw new IllegalStateException(
                        "Spatial block allocation changed after command preparation: expected "
                                + blockId + ", current " + currentNextSpatialBlockId + ".");
            }
        }
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(world, layer, after);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (!identityAllocated) {
            SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layer);
            int allocated = component.allocateNextSpatialBlockId();
            if (allocated != blockId) {
                throw new IllegalStateException(
                        "Spatial block allocation changed after prevalidation: expected "
                                + blockId + ", got " + allocated + ".");
            }
            identityAllocated = true;
        }
        if (selection != null) selection.selectBlock(layer, blockId);
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
                if (selection != null) selection.enterLayer(layer);
                SpatialBlockCommandSupport.markChanged(world, layer, this);
                return CommandOutcome.APPLIED;
            }
        }
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, before);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null) selection.enterLayer(layer);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    public int getBlockId() { return blockId; }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }

    private static int addedBlockId(Array<SpatialBlockData> before, Array<SpatialBlockData> after) {
        for (int i = 0; i < after.size; i++) {
            int id = after.get(i).id;
            boolean found = false;
            for (int j = 0; j < before.size; j++) if (before.get(j).id == id) { found = true; break; }
            if (!found) return id;
        }
        return -1;
    }
}
