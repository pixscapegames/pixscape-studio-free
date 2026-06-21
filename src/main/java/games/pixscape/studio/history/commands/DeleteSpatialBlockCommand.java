package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

public final class DeleteSpatialBlockCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final int originalIndex;
    private final SpatialBlockData before;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final boolean noop;

    public DeleteSpatialBlockCommand(World world,
                                     HistoryIdRegistry historyIds,
                                     SpatialBlockSelectionService selection,
                                     int layerEntityId,
                                     int blockId) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.blockId = blockId;

        SpatialBlocksComponent component = world != null ? SpatialBlockCommandSupport.get(world, layerEntityId) : null;
        this.originalIndex = SpatialBlockCommandSupport.indexOf(component, blockId);
        SpatialBlockData current = originalIndex >= 0 ? component.blocks.get(originalIndex) : null;
        this.before = current != null ? current.copy() : null;
        this.physicsBefore = this.before != null && this.before.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId)
                : null;
        this.noop = world == null || historyIds == null || layerHistoryId <= 0L || blockId <= 0 || before == null;
    }

    @Override
    public String label() {
        return "Delete Spatial Block";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        if (noop) return;
        int layerEntityId = resolveLayer();
        if (layerEntityId < 0) return;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layerEntityId);
        int index = SpatialBlockCommandSupport.indexOf(component, blockId);
        if (index >= 0) {
            component.blocks.removeIndex(index);
        }
        if (selection != null && selection.getSelectedBlockId() == blockId) {
            selection.enterLayer(layerEntityId);
        }
        if (before.physicsCollision) {
            SpatialBlockPhysicsSync.removeBlockFixture(world, layerEntityId, blockId, this);
        }
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
    }

    @Override
    public void undo() {
        if (noop) return;
        int layerEntityId = resolveLayer();
        if (layerEntityId < 0) return;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.getOrCreate(world, layerEntityId);
        if (SpatialBlockCommandSupport.indexOf(component, blockId) < 0) {
            int insertIndex = Math.max(0, Math.min(originalIndex, component.blocks.size));
            component.blocks.insert(insertIndex, before.copy());
        }
        if (selection != null) {
            selection.selectBlock(layerEntityId, blockId);
        }
        if (physicsBefore != null) {
            physicsBefore.restore(world, layerEntityId, this);
        } else if (before.physicsCollision) {
            SpatialBlockData restored = SpatialBlockCommandSupport.find(component, blockId);
            SpatialBlockPhysicsSync.sync(world, layerEntityId, restored, this);
        }
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
    }

    private int resolveLayer() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId) ? entityId : -1;
    }
}
