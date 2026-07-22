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

/** Atomic full-layer authored-wall snapshot command for deletion and structure splits. */
public final class DeleteSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final int removedFixtureId;
    private final CommandOutcome initialOutcome;

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
        SpatialBlockData removed = SpatialBlockCommandSupport.find(component, blockId);
        this.removedFixtureId = removed != null ? removed.fixtureId : 0;
        this.physicsBefore = removed != null && removed.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId) : null;
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
        return applyAfter();
    }

    private CommandOutcome applyAfter() {
        if (initialOutcome != CommandOutcome.APPLIED) return initialOutcome;
        int layer = resolveLayer();
        if (layer < 0) return CommandOutcome.NO_CHANGE;
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, after);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null && selection.getSelectedBlockId() == blockId) selection.enterLayer(layer);
        SpatialBlockPhysicsSync.removeBlockFixture(world, layer, removedFixtureId, this);
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
        if (selection != null) selection.selectBlock(layer, blockId);
        if (physicsBefore != null) physicsBefore.restore(world, layer, this);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }
}
