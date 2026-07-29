package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;

/** Adds or removes the authored physics relation for one Spatial Block. */
public final class SetSpatialBlockPhysicsCollisionCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final SpatialBlockSelectionService selection;
    private final PhysicsService physicsService;
    private final long layerHistoryId;
    private final int spatialBlockId;
    private final boolean before;
    private final boolean after;
    private final int originalShapeIndex;
    private PhysicsShapeData linkedShapeSnapshot;

    public SetSpatialBlockPhysicsCollisionCommand(
            World world,
            HistoryIdRegistry historyIds,
            SpatialBlockSelectionService selection,
            PhysicsService physicsService,
            int layerEntityId,
            int spatialBlockId,
            boolean enabled) {
        this.world = world;
        this.historyIds = historyIds;
        this.selection = selection;
        this.physicsService = physicsService;
        this.spatialBlockId = spatialBlockId;
        boolean valid = world != null
                && historyIds != null
                && physicsService != null
                && layerEntityId >= 0
                && world.getEntityManager().isActive(layerEntityId)
                && spatialBlockId > 0;
        this.layerHistoryId = valid ? historyIds.ensureForEntity(layerEntityId) : -1L;
        PhysicsShapesComponent shapes = valid
                ? world.getMapper(PhysicsShapesComponent.class).getSafe(layerEntityId, null)
                : null;
        this.originalShapeIndex = SpatialBlockCommandSupport.indexOfLinkedPhysicsShape(
                shapes, spatialBlockId);
        this.before = originalShapeIndex >= 0;
        this.after = enabled;
        if (before) {
            linkedShapeSnapshot = shapes.shapes.get(originalShapeIndex).copy();
        }
    }

    @Override
    public String label() {
        return after ? "Enable Spatial Block Physics Collision"
                : "Disable Spatial Block Physics Collision";
    }

    @Override
    public boolean isNoop() {
        return layerHistoryId <= 0L || before == after;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return apply(after);
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after);
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(boolean enabled) {
        if (isNoop()) return CommandOutcome.NO_CHANGE;
        int layerEntityId = resolveLayer();
        if (layerEntityId < 0) return CommandOutcome.NO_CHANGE;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class)
                .getSafe(layerEntityId, null);
        SpatialBlocksComponent blocks = SpatialBlockCommandSupport.get(world, layerEntityId);
        if (tiled == null || tiled.data == null
                || SpatialBlockCommandSupport.find(blocks, spatialBlockId) == null) {
            return CommandOutcome.REJECTED;
        }

        PhysicsShapesComponent current = world.getMapper(PhysicsShapesComponent.class)
                .getSafe(layerEntityId, null);
        int currentIndex = SpatialBlockCommandSupport.indexOfLinkedPhysicsShape(
                current, spatialBlockId);
        int linkedCount = SpatialBlockCommandSupport.countLinkedPhysicsShapes(
                current, spatialBlockId);
        if (linkedCount > 1) return CommandOutcome.REJECTED;
        if (enabled == (currentIndex >= 0)) return CommandOutcome.NO_CHANGE;

        Array<PhysicsShapeData> candidate =
                SpatialBlockCommandSupport.copyPhysicsShapes(current);
        if (enabled) {
            if (linkedShapeSnapshot == null) {
                PhysicsShapeData linked = new PhysicsShapeData();
                linked.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
                linked.spatialBlockId = spatialBlockId;
                linked.geometry = null;
                linkedShapeSnapshot = linked.copy();
            }
            int insertionIndex = originalShapeIndex >= 0
                    ? Math.min(originalShapeIndex, candidate.size)
                    : candidate.size;
            candidate.insert(insertionIndex, linkedShapeSnapshot.copy());
        } else {
            if (linkedShapeSnapshot == null) {
                linkedShapeSnapshot = candidate.get(currentIndex).copy();
            }
            candidate.removeIndex(currentIndex);
        }

        PreparedPhysicsBodyCandidate prepared;
        try {
            prepared = SpatialBlockCommandSupport.preparePhysicsCandidateAgainstBlocks(
                    world, layerEntityId, blocks.blocks, candidate);
        } catch (RuntimeException failure) {
            logRejection(layerEntityId, failure);
            return CommandOutcome.REJECTED;
        }

        SpatialBlockCommandSupport.publishStaticTiledPhysicsCandidate(
                world, layerEntityId, candidate, prepared);
        if (selection != null) selection.selectBlock(layerEntityId, spatialBlockId);
        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(
                layerEntityId, EventFlow.tag(this)));
        SpatialBlockCommandSupport.markChanged(world, layerEntityId, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveLayer() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? entityId : -1;
    }

    private static void logRejection(int layerEntityId, RuntimeException failure) {
        if (Gdx.app != null) {
            Gdx.app.error("SpatialBlockPhysicsCollision",
                    "Rejected physics collision mutation for layer " + layerEntityId + ".",
                    failure);
        }
    }
}
