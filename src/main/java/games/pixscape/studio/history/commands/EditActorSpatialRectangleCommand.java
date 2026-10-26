package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** One authored rectangle edit. Sorting and shadow publication consume it without modifying physics. */
public final class EditActorSpatialRectangleCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final int asset;
    private final String clip;
    private final ActorSpatialRectangleData before,after;
    private final Runnable save;
    public EditActorSpatialRectangleCommand(World world,HistoryIdRegistry ids,int entity,int asset,String clip,
                                  ActorSpatialRectangleData after,Runnable save) {
        this.world=world;this.ids=ids;id=ids.ensureForEntity(entity);this.asset=asset;this.clip=clip;this.save=save;
        SpatialHeightComponent spatial=world.getMapper(SpatialHeightComponent.class).get(entity);
        before=spatial.rectangles.get(asset,clip).copy();this.after=after.copy();this.after.validate();
        this.after.orientation=ActorSpatialRectangleData.normalize(after.orientation);
    }
    public String label(){return "Edit actor spatial rectangle";}
    public boolean isNoop(){return before.same(after);}
    public void redo(){apply(after);}
    public void undo(){apply(before);}
    private void apply(ActorSpatialRectangleData value) {
        int entity=ids.entityOfHistoryId(id);
        if(entity<0 || !world.getEntityManager().isActive(entity))return;
        SpatialHeightComponent spatial=world.getMapper(SpatialHeightComponent.class).getSafe(entity,null);
        if(spatial==null || spatial.rectangles==null)return;
        spatial.rectangles.put(asset,clip,value.copy());
        games.pixscape.runtime.system.DirtyTrackerSystem dirty=world.getSystem(games.pixscape.runtime.system.DirtyTrackerSystem.class);
        if(dirty!=null){dirty.mark(entity,games.pixscape.runtime.render.DirtyBits.GEOMETRY);dirty.order(entity);}
        if(save!=null)save.run();
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entity,EventFlow.tag(this)));
    }
}
