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
    private SpatialBlockPhysicsSync.LayerPhysicsState physicsAfter;
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
        clearFixtureIdsForNewBlocks(before, after);
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
        } else if (physicsAfter != null) {
            physicsAfter.restore(world, layer, this);
        } else {
            for (int i = 0; i < before.size; i++) {
                SpatialBlockData previous = before.get(i);
                if (previous != null && find(after, previous.id) == null && previous.fixtureId > 0) {
                    SpatialBlockPhysicsSync.removeBlockFixture(
                            world, layer, previous.fixtureId, this);
                }
            }
            for (int i = 0; i < component.blocks.size; i++) {
                SpatialBlockData wall = component.blocks.get(i);
                SpatialBlockData previous = find(before, wall.id);
                if (requiresPhysicsSync(previous, wall)) {
                    SpatialBlockPhysicsSync.sync(world, layer, wall, this);
                }
            }
            copyFixtureIds(component.blocks, after);
            physicsAfter = SpatialBlockPhysicsSync.captureLayerPhysics(world, layer);
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

    private static SpatialBlockData find(Array<SpatialBlockData> walls, int blockId) {
        for (int i = 0; i < walls.size; i++) {
            SpatialBlockData wall = walls.get(i);
            if (wall != null && wall.id == blockId) return wall;
        }
        return null;
    }

    private static boolean requiresPhysicsSync(SpatialBlockData before, SpatialBlockData after) {
        if (after == null) return false;
        if (before == null) return after.physicsCollision;
        if (before.physicsCollision != after.physicsCollision) return true;
        if (!after.physicsCollision) return false;
        return Float.compare(before.x, after.x) != 0
                || Float.compare(before.y, after.y) != 0
                || Float.compare(before.width, after.width) != 0
                || Float.compare(before.depth, after.depth) != 0;
    }

    private static boolean sameArrays(Array<SpatialBlockData> a, Array<SpatialBlockData> b) {
        if (a.size != b.size) return false;
        for (int i = 0; i < a.size; i++) {
            if (!SpatialBlockCommandSupport.same(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static void clearFixtureIdsForNewBlocks(Array<SpatialBlockData> before,
                                                    Array<SpatialBlockData> after) {
        for (int i = 0; i < after.size; i++) {
            SpatialBlockData wall = after.get(i);
            if (wall != null && find(before, wall.id) == null) wall.fixtureId = 0;
        }
    }

    private static void copyFixtureIds(Array<SpatialBlockData> source,
                                       Array<SpatialBlockData> target) {
        for (int i = 0; i < target.size; i++) {
            SpatialBlockData targetWall = target.get(i);
            SpatialBlockData sourceWall = targetWall != null ? find(source, targetWall.id) : null;
            if (sourceWall != null) targetWall.fixtureId = sourceWall.fixtureId;
        }
    }
}
