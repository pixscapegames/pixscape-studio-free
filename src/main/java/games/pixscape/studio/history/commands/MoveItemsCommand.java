package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;

/** Applies a fully validated Items move in place; no intermediate detach or entity recreation. */
public final class MoveItemsCommand implements Command, OutcomeAwareCommand {
    private final ItemMovePreparation.Prepared move;
    private boolean executed;

    MoveItemsCommand(ItemMovePreparation.Prepared move) { this.move = move; }
    @Override public String label() { return "Move Items"; }
    @Override public CommandOutcome executeOutcome() {
        if (!move.isCurrent()) return CommandOutcome.REJECTED;
        CommandOutcome outcome = apply(false);
        executed = outcome == CommandOutcome.APPLIED;
        return outcome;
    }
    @Override public CommandOutcome redoOutcome() { return executed ? apply(false) : executeOutcome(); }
    @Override public CommandOutcome undoOutcome() { return executed ? apply(true) : CommandOutcome.REJECTED; }
    @Override public void redo() { if (redoOutcome() == CommandOutcome.REJECTED) throw new IllegalStateException("Items move is stale or incompatible."); }
    @Override public void undo() { if (undoOutcome() == CommandOutcome.REJECTED) throw new IllegalStateException("Items move undo is stale or incompatible."); }

    private CommandOutcome apply(boolean undo) {
        if (!move.validate(undo)) return CommandOutcome.REJECTED;
        if (move.isNoop()) return CommandOutcome.NO_CHANGE;
        var world = move.world;
        var ids = move.owner.context().identityRegistry();
        var members = world.getMapper(GameObjectMemberComponent.class);
        var indexes = world.getMapper(EntityIndexComponent.class);
        var transforms = world.getMapper(TransformComponent.class);
        var dirty = world.getSystem(DirtyTrackerSystem.class);
        // All targets and complete before/after states were checked above. No callbacks during writes.
        for (var change : move.changes) {
            var target = undo ? change.before() : change.after();
            int entity = ids.findByStableId(target.stableId());
            if (target.parentStableId() == 0) members.remove(entity);
            else members.create(entity).parentStableId = target.parentStableId();
            var index = indexes.get(entity);
            index.layerIndex = target.storedLayer(); index.zIndex = target.z();
            GameObjectHierarchyCommandSupport.apply(transforms.get(entity), target.pose().component());
        }
        for (var change : move.changes) {
            int entity = ids.findByStableId(change.before().stableId());
            if (dirty != null) { dirty.geometry(entity, GeometryDirty.ALL); dirty.layer(entity); dirty.order(entity); }
        }
        // Readers see the complete result. Source and destination layers both need a tree rebuild.
        for (int layer : move.affectedLayerIndices())
            EventFlow.i().publish(new EventFlow.EntityZOrderChanged(layer, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
