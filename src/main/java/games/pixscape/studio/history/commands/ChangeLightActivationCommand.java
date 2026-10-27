package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.lighting.ActiveLights;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Enabled and visible use the same admission predicate as the Runtime. */
public final class ChangeLightActivationCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final boolean visibility,before,after;
    public ChangeLightActivationCommand(World world,HistoryIdRegistry ids,int entity,boolean visibility,boolean value){
        this.world=world;this.ids=ids;id=ids.ensureForEntity(entity);this.visibility=visibility;after=value;
        VisibilityComponent v=world.getMapper(VisibilityComponent.class).getSafe(entity,null);
        PointLightComponent p=world.getMapper(PointLightComponent.class).getSafe(entity,null);
        ConeLightComponent c=world.getMapper(ConeLightComponent.class).getSafe(entity,null);
        before=visibility?(v==null||v.visible):p!=null?p.enabled:c!=null&&c.enabled;
    }
    @Override public String label(){return visibility?"Light visibility":"Light enabled";}
    @Override public boolean isNoop(){return before==after;}
    @Override public int additionalActiveLights(){
        int e=ids.entityOfHistoryId(id);if(e<0)return 0;
        VisibilityComponent v=world.getMapper(VisibilityComponent.class).getSafe(e,null);
        PointLightComponent p=world.getMapper(PointLightComponent.class).getSafe(e,null);
        ConeLightComponent c=world.getMapper(ConeLightComponent.class).getSafe(e,null);
        boolean active=ActiveLights.isActive(visibility?after:v==null||v.visible,
                p!=null,p!=null&&(visibility?p.enabled:after),c!=null,c!=null&&(visibility?c.enabled:after));
        return (active?1:0)-(ActiveLights.isActive(world,e)?1:0);
    }
    @Override public void redo(){apply(after);}
    @Override public void undo(){apply(before);}
    private void apply(boolean value){
        int e=ids.entityOfHistoryId(id);if(e<0)return;
        if(visibility)world.getMapper(VisibilityComponent.class).create(e).visible=value;
        else{
            PointLightComponent p=world.getMapper(PointLightComponent.class).getSafe(e,null);
            ConeLightComponent c=world.getMapper(ConeLightComponent.class).getSafe(e,null);
            if(p!=null)p.enabled=value;if(c!=null)c.enabled=value;
        }
        games.pixscape.runtime.system.DirtyTrackerSystem dirty=world.getSystem(games.pixscape.runtime.system.DirtyTrackerSystem.class);
        if(dirty!=null){dirty.geometry(e,games.pixscape.runtime.render.GeometryDirty.ALL);dirty.material(e);}
        EventFlow.i().publish(new EventFlow.EntityChanged(e,TransformOp.SCALE,EventFlow.tag(this)));
    }
}
