package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Activation only owns Spatial state. Existing physics is never changed. */
public final class ToggleSpatialActorCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final boolean enable;
    private final SpatialFootprintComponent before, after;
    private final ActorSpatialRectangles rectanglesBefore;
    private ActorSpatialRectangles rectanglesAfter = new ActorSpatialRectangles();
    private final boolean noop;

    public ToggleSpatialActorCommand(World world, HistoryIdRegistry ids,
            int entity, boolean enable, boolean eligible, SpatialFootprintComponent initial) {
        this.world = world;
        this.ids = ids;
        this.enable = enable;
        boolean valid = world != null && ids != null && entity >= 0 && world.getEntityManager().isActive(entity);
        id = valid ? ids.ensureForEntity(entity) : -1;
        SpatialFootprintComponent current = valid ? world.getMapper(SpatialFootprintComponent.class).getSafe(entity, null) : null;
        before = current == null ? null : current.copy();
        after = before != null ? before.copy() : initial == null ? null : initial.copy();
        SpatialRepresentationComponent representation = valid ? world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null) : null;
        rectanglesBefore = representation == null ? null : representation.rectangles.copy();
        if (rectanglesBefore != null) {
            rectanglesAfter = rectanglesBefore.copy();
        } else {
            rectanglesAfter.staticRectangle = new ActorSpatialRectangleData();
        }
        noop = !valid || enable && (!eligible || after == null || before != null && representation != null)
                || !enable && before == null && representation == null;
        if (after != null) {
            after.validate();
        }
    }

    public ToggleSpatialActorCommand withRectangles(ActorSpatialRectangles rectangles) {
        rectangles.validate();
        rectanglesAfter = rectangles.copy();
        return this;
    }
    @Override
    public String label() {
        return enable ? "Enable Spatial Actor" : "Disable Spatial Actor";
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
        return apply(enable ? after : null, enable ? rectanglesAfter : null);
    }
    @Override
    public CommandOutcome undoOutcome() {
        return apply(before, rectanglesBefore);
    }

    private CommandOutcome apply(SpatialFootprintComponent footprint, ActorSpatialRectangles rectangles) {
        int entity = noop ? -1 : ids.entityOfHistoryId(id);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return CommandOutcome.NO_CHANGE;
        }
        if (footprint == null) {
            world.getMapper(SpatialFootprintComponent.class).remove(entity);
        } else {
            world.getMapper(SpatialFootprintComponent.class).create(entity).set(footprint);
        }
        if (rectangles == null) {
            world.getMapper(SpatialRepresentationComponent.class).remove(entity);
        } else {
            world.getMapper(SpatialRepresentationComponent.class).create(entity).rectangles = rectangles.copy();
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entity);
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entity, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
