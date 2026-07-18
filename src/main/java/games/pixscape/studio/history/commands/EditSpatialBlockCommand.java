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

/** Atomic full-layer authored-wall snapshot command for edits, merges, splits, and shared properties. */
public final class EditSpatialBlockCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final CommandOutcome initialOutcome;

    public EditSpatialBlockCommand(World world, HistoryIdRegistry historyIds,
                                   SpatialBlockSelectionService selection, int layerEntityId,
                                   int blockId, SpatialBlockData ignoredBefore,
                                   SpatialBlockData replacement) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.blockId = blockId;
        SpatialBlocksComponent component = world != null ? SpatialBlockCommandSupport.get(world, layerEntityId) : null;
        this.before = SpatialBlockCommandSupport.snapshot(component);
        TiledLayerComponent tiled = world != null
                ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null) : null;
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.edit(
                component, blockId, replacement, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        this.physicsBefore = hasPhysics(before) || hasPhysics(after)
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId) : null;
        this.initialOutcome = !plan.valid ? CommandOutcome.REJECTED
                : world == null || historyIds == null || layerHistoryId <= 0L || sameArrays(before, after)
                ? CommandOutcome.NO_CHANGE : CommandOutcome.APPLIED;
    }

    @Override public String label() { return "Edit Spatial Wall"; }
    @Override public boolean isNoop() { return initialOutcome != CommandOutcome.APPLIED; }
    @Override public void redo() { redoOutcome(); }
    @Override public void undo() { undoOutcome(); }

    @Override public CommandOutcome executeOutcome() { return apply(after, false); }
    @Override public CommandOutcome redoOutcome() { return apply(after, false); }
    @Override public CommandOutcome undoOutcome() { return apply(before, true); }

    private CommandOutcome apply(Array<SpatialBlockData> snapshot, boolean restorePhysics) {
        if (initialOutcome != CommandOutcome.APPLIED) return initialOutcome;
        int layer = resolveLayer();
        if (layer < 0) return CommandOutcome.NO_CHANGE;
        CommandOutcome outcome = SpatialBlockCommandSupport.replaceAllValidated(world, layer, snapshot);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layer);
        if (selection != null) selection.selectBlock(layer, blockId);
        if (restorePhysics && physicsBefore != null) {
            physicsBefore.restore(world, layer, this);
        } else {
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData wall = component.blocks.get(i);
                if (wall.physicsCollision) SpatialBlockPhysicsSync.sync(world, layer, wall, this);
            }
        }
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }

    private static boolean hasPhysics(Array<SpatialBlockData> walls) {
        for (int i = 0; i < walls.size; i++) if (walls.get(i).physicsCollision) return true;
        return false;
    }

    private static boolean sameArrays(Array<SpatialBlockData> a, Array<SpatialBlockData> b) {
        if (a.size != b.size) return false;
        for (int i = 0; i < a.size; i++) {
            if (!SpatialBlockCommandSupport.same(a.get(i), b.get(i))) return false;
        }
        return true;
    }
}
