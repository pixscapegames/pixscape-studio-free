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
public final class DeleteSpatialBlockCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final Array<SpatialBlockData> before;
    private final Array<SpatialBlockData> after;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final boolean noop;

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
        this.physicsBefore = removed != null && removed.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId) : null;
        TiledLayerComponent tiled = world != null
                ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null) : null;
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.delete(
                component, blockId, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        this.noop = world == null || historyIds == null || layerHistoryId <= 0L || !plan.valid;
    }

    @Override public String label() { return "Delete Spatial Wall"; }
    @Override public boolean isNoop() { return noop; }

    @Override
    public void redo() {
        if (noop) return;
        int layer = resolveLayer();
        if (layer < 0) return;
        if (!SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, SpatialBlockCommandSupport.getOrCreate(world, layer), after)) return;
        if (selection != null && selection.getSelectedBlockId() == blockId) selection.enterLayer(layer);
        SpatialBlockPhysicsSync.removeBlockFixture(world, layer, blockId, this);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
    }

    @Override
    public void undo() {
        if (noop) return;
        int layer = resolveLayer();
        if (layer < 0) return;
        if (!SpatialBlockCommandSupport.replaceAllValidated(
                world, layer, SpatialBlockCommandSupport.getOrCreate(world, layer), before)) return;
        if (selection != null) selection.selectBlock(layer, blockId);
        if (physicsBefore != null) physicsBefore.restore(world, layer, this);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
    }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }
}
