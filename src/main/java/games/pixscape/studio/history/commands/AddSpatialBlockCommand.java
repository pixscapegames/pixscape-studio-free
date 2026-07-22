package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialStructureTopology;

/** Atomic full-layer authored-wall snapshot command for creation and structure merges. */
public final class AddSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final int blockId;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private SpatialBlockPhysicsSync.LayerPhysicsState physicsAfter;
    private final CommandOutcome initialOutcome;

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
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(
                component, block, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        this.blockId = addedBlockId(before, after);
        SpatialBlockData plannedAddition = find(after, blockId);
        if (plannedAddition != null) plannedAddition.fixtureId = 0;
        this.physicsBefore = block != null && block.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId) : null;
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
        return applyAfter();
    }

    private CommandOutcome applyAfter() {
        if (initialOutcome != CommandOutcome.APPLIED) return initialOutcome;
        int layer = resolveLayer();
        if (layer < 0) return CommandOutcome.NO_CHANGE;
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(world, layer, after);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layer);
        SpatialBlockData added = SpatialBlockCommandSupport.find(component, blockId);
        if (selection != null) selection.selectBlock(layer, blockId);
        if (physicsAfter != null) {
            physicsAfter.restore(world, layer, this);
        } else if (added != null && added.physicsCollision) {
            SpatialBlockPhysicsSync.sync(world, layer, added, this);
            SpatialBlockData planned = find(after, blockId);
            if (planned != null) planned.fixtureId = added.fixtureId;
            physicsAfter = SpatialBlockPhysicsSync.captureLayerPhysics(world, layer);
        }
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
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, before);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null) selection.enterLayer(layer);
        if (physicsBefore != null) physicsBefore.restore(world, layer, this);
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

    private static SpatialBlockData find(Array<SpatialBlockData> walls, int blockId) {
        for (int i = 0; i < walls.size; i++) {
            SpatialBlockData wall = walls.get(i);
            if (wall != null && wall.id == blockId) return wall;
        }
        return null;
    }
}
