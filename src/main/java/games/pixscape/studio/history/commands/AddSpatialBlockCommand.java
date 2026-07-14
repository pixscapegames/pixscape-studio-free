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
public final class AddSpatialBlockCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final int blockId;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final boolean noop;

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
        this.physicsBefore = block != null && block.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId) : null;
        this.noop = world == null || historyIds == null || layerHistoryId <= 0L || !plan.valid || blockId <= 0;
    }

    @Override public String label() { return "Add Spatial Wall"; }
    @Override public boolean isNoop() { return noop; }

    @Override
    public void redo() {
        if (noop) return;
        int layer = resolveLayer();
        if (layer < 0) return;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.getOrCreate(world, layer);
        if (!SpatialBlockCommandSupport.replaceAllValidated(world, layer, component, after)) return;
        SpatialBlockData added = SpatialBlockCommandSupport.find(component, blockId);
        if (selection != null) selection.selectBlock(layer, blockId);
        if (added != null && added.physicsCollision) SpatialBlockPhysicsSync.sync(world, layer, added, this);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
    }

    @Override
    public void undo() {
        if (noop) return;
        int layer = resolveLayer();
        if (layer < 0) return;
        if (!SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, SpatialBlockCommandSupport.getOrCreate(world, layer), before)) return;
        if (selection != null) selection.enterLayer(layer);
        if (physicsBefore != null) physicsBefore.restore(world, layer, this);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
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
