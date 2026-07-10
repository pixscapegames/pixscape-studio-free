package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

public final class EditSpatialBlockCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final long layerHistoryId;
    private final int blockId;
    private final SpatialBlockData before;
    private final SpatialBlockData after;
    private final SpatialBlockPhysicsSync.LayerPhysicsState physicsBefore;
    private final boolean noop;

    public EditSpatialBlockCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   SpatialBlockSelectionService selection,
                                   int layerEntityId,
                                   int blockId,
                                   SpatialBlockData before,
                                   SpatialBlockData after) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.blockId = blockId;
        this.before = before != null ? before.copy() : null;
        this.after = after != null ? after.copy() : null;
        this.physicsBefore = shouldSyncPhysics(this.before, this.after)
                ? SpatialBlockPhysicsSync.captureLayerPhysics(world, layerEntityId)
                : null;
        this.noop = world == null
                || historyIds == null
                || layerHistoryId <= 0L
                || blockId <= 0
                || this.before == null
                || this.after == null
                || SpatialBlockCommandSupport.same(this.before, this.after);
    }

    @Override
    public String label() {
        return "Edit Spatial Block";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after, false);
    }

    @Override
    public void undo() {
        apply(before, true);
    }

    private void apply(SpatialBlockData snapshot, boolean restoreCapturedPhysics) {
        if (noop || snapshot == null) return;
        int layerEntityId = resolveLayer();
        if (layerEntityId < 0) return;
        if (!SpatialBlockCommandSupport.validAuthoredActorOccluder(world, layerEntityId, snapshot)) return;
        SpatialBlocksComponent component = SpatialBlockCommandSupport.get(world, layerEntityId);
        SpatialBlockData target = SpatialBlockCommandSupport.find(component, blockId);
        if (target == null) return;
        SpatialBlockCommandSupport.apply(target, snapshot);
        if (selection != null) {
            selection.selectBlock(layerEntityId, blockId);
        }
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
        if (restoreCapturedPhysics && physicsBefore != null) {
            physicsBefore.restore(world, layerEntityId, this);
        } else if (shouldSyncPhysics(before, after)) {
            SpatialBlockPhysicsSync.sync(world, layerEntityId, target, this);
        }
    }

    private int resolveLayer() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId) ? entityId : -1;
    }

    private static boolean shouldSyncPhysics(SpatialBlockData before, SpatialBlockData after) {
        return (before != null && before.physicsCollision) || (after != null && after.physicsCollision);
    }
}
