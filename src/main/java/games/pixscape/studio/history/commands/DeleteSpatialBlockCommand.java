package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.spatial.SpatialBlockData;
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
    private final Array<PhysicsShapeData> physicsBefore;
    private final Array<PhysicsShapeData> physicsAfter;
    private final boolean removesLinkedCollision;
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
        TiledLayerComponent tiled = world != null
                ? world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null) : null;
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.delete(
                component, blockId, tiled != null ? tiled.data : null);
        this.after = plan.walls;
        PhysicsShapesComponent shapes = world != null
                ? world.getMapper(PhysicsShapesComponent.class).getSafe(layerEntityId, null)
                : null;
        this.physicsBefore = SpatialBlockCommandSupport.copyPhysicsShapes(shapes);
        int linkedIndex = SpatialBlockCommandSupport.indexOfLinkedPhysicsShape(shapes, blockId);
        this.removesLinkedCollision = linkedIndex >= 0;
        this.physicsAfter = copyShapes(physicsBefore);
        if (linkedIndex >= 0) physicsAfter.removeIndex(linkedIndex);
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
        CommandOutcome outcome = applySnapshot(
                layer, after, physicsAfter, removesLinkedCollision);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null && selection.getSelectedBlockId() == blockId) selection.enterLayer(layer);
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
        CommandOutcome outcome = applySnapshot(
                layer, before, physicsBefore, removesLinkedCollision);
        if (outcome != CommandOutcome.APPLIED) return outcome;
        if (selection != null) selection.selectBlock(layer, blockId);
        SpatialBlockCommandSupport.markChanged(world, layer, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveLayer() {
        int entity = historyIds.entityOfHistoryId(layerHistoryId);
        return entity >= 0 && world.getEntityManager().isActive(entity) ? entity : -1;
    }

    private CommandOutcome applySnapshot(
            int layerEntityId,
            Array<SpatialBlockData> blockSnapshot,
            Array<PhysicsShapeData> shapeSnapshot,
            boolean publishPhysics) {
        if (!publishPhysics) {
            return SpatialBlockCommandSupport.replaceAllValidated(
                    world, layerEntityId, blockSnapshot);
        }
        if (SpatialBlockCommandSupport.validateBlocks(
                world, layerEntityId, blockSnapshot) != CommandOutcome.APPLIED) {
            return CommandOutcome.REJECTED;
        }
        Array<SpatialBlockData> replacement = copyBlocks(blockSnapshot);
        Array<PhysicsShapeData> candidateShapes = copyShapes(shapeSnapshot);
        PreparedPhysicsBodyCandidate prepared;
        try {
            prepared = SpatialBlockCommandSupport.preparePhysicsCandidateAgainstBlocks(
                    world, layerEntityId, replacement, candidateShapes);
        } catch (RuntimeException failure) {
            if (Gdx.app != null) {
                Gdx.app.error("DeleteSpatialBlockCommand",
                        "Rejected atomic block and physics mutation for layer "
                                + layerEntityId + ".", failure);
            }
            return CommandOutcome.REJECTED;
        }

        SpatialBlocksComponent component =
                SpatialBlockCommandSupport.getOrCreate(world, layerEntityId);
        component.blocks = replacement;
        component.revision++;
        SpatialBlockCommandSupport.publishStaticTiledPhysicsCandidate(
                world, layerEntityId, candidateShapes, prepared);
        games.pixscape.studio.event.EventFlow.i().publish(
                new games.pixscape.studio.event.EventFlow.PhysicsBodyStructureChanged(
                        layerEntityId,
                        games.pixscape.studio.event.EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private static Array<SpatialBlockData> copyBlocks(Array<SpatialBlockData> source) {
        Array<SpatialBlockData> copy = new Array<>(SpatialBlockData[]::new);
        if (source != null) {
            for (int i = 0; i < source.size; i++) copy.add(source.get(i).copy());
        }
        return copy;
    }

    private static Array<PhysicsShapeData> copyShapes(Array<PhysicsShapeData> source) {
        Array<PhysicsShapeData> copy = new Array<>(
                true, source != null ? source.size : 0, PhysicsShapeData.class);
        if (source != null) {
            for (int i = 0; i < source.size; i++) {
                PhysicsShapeData shape = source.get(i);
                copy.add(shape != null ? shape.copy() : null);
            }
        }
        return copy;
    }
}
