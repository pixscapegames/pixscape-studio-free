package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/**
 * One authored rectangle edit. Sorting and shadow publication consume it without modifying physics.
 */
public final class EditActorSpatialRectangleCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final int asset;
    private final String clip;
    private final ActorSpatialRectangles before, after;
    private final boolean hadRepresentation, noop;
    private final Runnable save;

    public EditActorSpatialRectangleCommand(World world, HistoryIdRegistry ids, int entity, int asset, String clip,
                                            ActorSpatialRectangleData after, Runnable save) {
        this.world = world;
        this.ids = ids;
        id = ids.ensureForEntity(entity);
        this.asset = asset;
        this.clip = clip;
        this.save = save;
        after.validate();
        SpatialRepresentationComponent spatial = world.getMapper(SpatialRepresentationComponent.class).getSafe(entity, null);
        hadRepresentation = spatial != null;
        before = spatial == null || spatial.rectangles == null ? null : spatial.rectangles.copy();
        ActorSpatialRectangleData current = before == null ? null : before.get(asset, clip);
        noop = current != null && current.same(after);
        this.after = before == null ? new ActorSpatialRectangles() : before.copy();
        this.after.put(asset, clip, after.copy());
    }

    public String label() {
        return "Edit actor spatial rectangle";
    }

    public boolean isNoop() {
        return noop;
    }

    public void redo() {
        apply(after, true);
    }

    public void undo() {
        apply(before, hadRepresentation);
    }

    private void apply(ActorSpatialRectangles value, boolean present) {
        int entity = ids.entityOfHistoryId(id);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) return;
        if (present) {
            world.getMapper(SpatialRepresentationComponent.class).create(entity).rectangles = value == null ? null : value.copy();
        } else {
            world.getMapper(SpatialRepresentationComponent.class).remove(entity);
        }
        games.pixscape.runtime.system.DirtyTrackerSystem dirty = world.getSystem(games.pixscape.runtime.system.DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entity);
        }
        if (save != null) save.run();
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entity, EventFlow.tag(this)));
    }
}
