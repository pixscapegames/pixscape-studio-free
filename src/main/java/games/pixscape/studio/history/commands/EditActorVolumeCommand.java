package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.spatial.ActorVolumeData;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** One authored volume edit. The shadow publisher consumes it without modifying physics. */
public final class EditActorVolumeCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final int asset;
    private final String clip;
    private final ActorVolumeData before,after;
    private final Runnable save;
    public EditActorVolumeCommand(World world,HistoryIdRegistry ids,int entity,int asset,String clip,
                                  ActorVolumeData after,Runnable save) {
        this.world=world;this.ids=ids;id=ids.ensureForEntity(entity);this.asset=asset;this.clip=clip;this.save=save;
        SpatialHeightComponent spatial=world.getMapper(SpatialHeightComponent.class).get(entity);
        before=spatial.volumes.get(asset,clip).copy();this.after=after.copy();this.after.validate();
        this.after.orientation=ActorVolumeData.normalize(after.orientation);
    }
    public String label(){return "Edit actor volume";}
    public boolean isNoop(){return before.same(after);}
    public void redo(){apply(after);}
    public void undo(){apply(before);}
    private void apply(ActorVolumeData value) {
        int entity=ids.entityOfHistoryId(id);
        if(entity<0 || !world.getEntityManager().isActive(entity))return;
        SpatialHeightComponent spatial=world.getMapper(SpatialHeightComponent.class).getSafe(entity,null);
        if(spatial==null || spatial.volumes==null)return;
        spatial.volumes.put(asset,clip,value.copy());
        games.pixscape.runtime.system.DirtyTrackerSystem dirty=world.getSystem(games.pixscape.runtime.system.DirtyTrackerSystem.class);
        if(dirty!=null)dirty.mark(entity,games.pixscape.runtime.render.DirtyBits.GEOMETRY);
        if(save!=null)save.run();
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entity,EventFlow.tag(this)));
    }
}
