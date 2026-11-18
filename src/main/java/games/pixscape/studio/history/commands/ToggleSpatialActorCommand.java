package games.pixscape.studio.history.commands;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Adds or removes only the selected entity footprint; representation settings remain authored. */
public final class ToggleSpatialActorCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final boolean enable;
    private final SpatialFootprintComponent before, after;
    private final boolean noop;

    public ToggleSpatialActorCommand(World world, HistoryIdRegistry ids,
            int entity, boolean enable, boolean eligible, SpatialFootprintComponent initial) {
        this.world = world;
        this.ids = ids;
        this.enable = enable;
        if (enable) {
            TiledEntityRole.requireActorCapability(world, entity, "Footprint");
        }
        boolean valid = world != null && ids != null && entity >= 0 && world.getEntityManager().isActive(entity);
        id = valid ? ids.ensureForEntity(entity) : -1;
        SpatialFootprintComponent current = valid ? world.getMapper(SpatialFootprintComponent.class).getSafe(entity, null) : null;
        before = current == null ? null : current.copy();
        after = before != null ? before.copy() : initial == null ? null : initial.copy();
        noop = !valid || enable && (!eligible || after == null || before != null)
                || !enable && before == null;
        if (after != null) {
            after.validate();
        }
    }

    @Override
    public String label() {
        return enable ? "Add own footprint" : "Remove own footprint";
    }
    @Override
    public boolean isNoop() {
        return noop;
    }
    @Override
    public void redo() {
        redoOutcome();
    }
    @Override
    public void undo() {
        undoOutcome();
    }
    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }
    @Override
    public CommandOutcome redoOutcome() {
        return apply(enable ? after : null);
    }
    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(SpatialFootprintComponent footprint) {
        int entity = noop ? -1 : ids.entityOfHistoryId(id);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return CommandOutcome.NO_CHANGE;
        }
        var hierarchy = world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            hierarchy.topologyForRemoval();
        }
        if (footprint == null) {
            world.getMapper(SpatialFootprintComponent.class).remove(entity);
        } else {
            world.getMapper(SpatialFootprintComponent.class).create(entity).set(footprint);
        }
        var composition = world.getSystem(games.pixscape.runtime.system.GameObjectCompositionSystem.class);
        if (composition != null) {
            composition.refreshSpatialOwners(entity);
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entity);
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entity, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
