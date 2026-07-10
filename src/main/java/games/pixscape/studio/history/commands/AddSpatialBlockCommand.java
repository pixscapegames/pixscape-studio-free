package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

public final class AddSpatialBlockCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final SpatialBlockData block;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final boolean noop;

    public AddSpatialBlockCommand(World world,
                                  HistoryIdRegistry historyIds,
                                  SpatialBlockSelectionService selection,
                                  int layerEntityId,
                                  SpatialBlockData block) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.block = block != null ? block.copy() : null;
        boolean invalidAuthoring = this.block != null
                && !SpatialBlockCommandSupport.validAuthoredActorOccluder(world, layerEntityId, this.block);
        this.physicsBefore = this.block != null && !invalidAuthoring && this.block.physicsCollision
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId)
                : null;
        this.noop = world == null || historyIds == null || layerHistoryId <= 0L || this.block == null || invalidAuthoring;
    }

    @Override
    public String label() {
        return "Add Spatial Block";
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
        if (!SpatialBlockCommandSupport.validAuthoredActorOccluder(world, layerEntityId, block)) return;

        SpatialBlocksComponent component = SpatialBlockCommandSupport.getOrCreate(world, layerEntityId);
        if (block.id <= 0) {
            block.id = SpatialBlockCommandSupport.allocateId(component);
        }
        if (SpatialBlockCommandSupport.indexOf(component, block.id) < 0) {
            component.blocks.add(block.copy());
        }
        if (selection != null) {
            selection.selectBlock(layerEntityId, block.id);
        }
        if (block.physicsCollision) {
            SpatialBlockData added = SpatialBlockCommandSupport.find(component, block.id);
            SpatialBlockPhysicsSync.sync(world, layerEntityId, added, this);
        }
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
    }

    @Override
    public void undo() {
        if (noop) return;
        int layerEntityId = resolveLayer();
        if (layerEntityId < 0) return;

        SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layerEntityId);
        int index = SpatialBlockCommandSupport.indexOf(component, block.id);
        if (index >= 0) {
            component.blocks.removeIndex(index);
        }
        if (selection != null && selection.getSelectedBlockId() == block.id) {
            selection.enterLayer(layerEntityId);
        }
        if (physicsBefore != null) {
            physicsBefore.restore(world, layerEntityId, this);
        }
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
    }

    public int getBlockId() {
        return block != null ? block.id : -1;
    }

    private int resolveLayer() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId) ? entityId : -1;
    }
}
