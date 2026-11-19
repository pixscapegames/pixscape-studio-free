package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.hierarchy.GameObjectHierarchyMutation;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;

/**
 * Applies a fully validated Items move in place; no intermediate detach or entity recreation.
 */
public final class MoveItemsCommand implements Command, OutcomeAwareCommand {
    private final ItemMovePreparation.Prepared move;
    private boolean executed;
    private final SpatialRepresentationInitialization representations;

    MoveItemsCommand(ItemMovePreparation.Prepared move) {
        this.move = move;
        representations = new SpatialRepresentationInitialization(move.world, move.owner.context().historyManager().historyIds());
        var identities = move.owner.context().identityRegistry();
        int parent = identities.findByStableId(move.destination.parentStableId());
        if (SpatialRepresentationInitialization.hasFootprintOwner(move.world, parent)) {
            var roots = new com.badlogic.gdx.utils.IntArray();
            for (int stableId : move.sources) {
                roots.add(identities.findByStableId(stableId));
            }
            representations.prepareSubtrees(roots);
        }
    }

    @Override
    public String label() {
        return "Move Items";
    }

    @Override
    public CommandOutcome executeOutcome() {
        if (!move.validateExecution()) {
            return CommandOutcome.REJECTED;
        }
        CommandOutcome outcome = applyValidated(false);
        executed = outcome == CommandOutcome.APPLIED;
        return outcome;
    }

    @Override
    public CommandOutcome redoOutcome() {
        return executed ? apply(false) : executeOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return executed ? apply(true) : CommandOutcome.REJECTED;
    }

    @Override
    public void redo() {
        if (redoOutcome() == CommandOutcome.REJECTED)
            throw new IllegalStateException("Items move is stale or incompatible.");
    }

    @Override
    public void undo() {
        if (undoOutcome() == CommandOutcome.REJECTED)
            throw new IllegalStateException("Items move undo is stale or incompatible.");
    }

    private CommandOutcome apply(boolean undo) {
        if (!move.validate(undo)) return CommandOutcome.REJECTED;
        return applyValidated(undo);
    }

    private CommandOutcome applyValidated(boolean undo) {
        if (move.isNoop()) return CommandOutcome.NO_CHANGE;
        var world = move.world;
        var ids = move.owner.context().identityRegistry();
        var indexes = world.getMapper(EntityIndexComponent.class);
        var dirty = world.getSystem(DirtyTrackerSystem.class);
        representations.apply(undo);
        // All targets and complete before/after states were checked above. No callbacks during writes.
        for (var change : move.changes) {
            var target = undo ? change.before() : change.after();
            int entity = ids.findByStableId(target.stableId());
            if (target.composition()) {
                world.getMapper(GameObjectComponent.class).create(entity);
            } else {
                world.getMapper(GameObjectComponent.class).remove(entity);
            }
            GameObjectHierarchyMutation.applyPreparedLocalPose(
                    world, entity, target.parentStableId(), target.pose().component());
            var index = indexes.get(entity);
            index.layerIndex = target.storedLayer();
            index.zIndex = target.z();
        }
        for (var change : move.changes) {
            int entity = ids.findByStableId(change.before().stableId());
            if (dirty != null) {
                dirty.geometry(entity, GeometryDirty.ALL);
                dirty.layer(entity);
                dirty.order(entity);
            }
            GameObjectHierarchyCommandSupport.notifyTransformChanged(entity,
                    change.before().pose().component(), change.after().pose().component(), EventFlow.tag(this));
        }
        // Readers see the complete result. Source and destination layers both need a tree rebuild.
        for (int layer : move.affectedLayerIndices())
            EventFlow.i().publish(new EventFlow.EntityZOrderChanged(layer, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
