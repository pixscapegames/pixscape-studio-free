package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.render.lighting.ActorShadowProfile;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** One complete calibration gesture, scoped to a static image or one animation asset/clip. */
public final class EditActorShadowCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long historyId;
    private final int assetId;
    private final String clip;
    private final ActorShadowProfile before,after;
    private final boolean hadSurface;
    private final Runnable markSave;
    public EditActorShadowCommand(World world,HistoryIdRegistry ids,int entity,int assetId,String clip,
                                  ActorShadowProfile after,Runnable markSave) {
        this.world=world;this.ids=ids;historyId=ids.ensureForEntity(entity);this.assetId=assetId;this.clip=clip;this.markSave=markSave;
        SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).getSafe(entity,null);
        hadSurface=s!=null;ActorShadowProfile old=s==null?null:s.shadowProfile(assetId,clip);
        before=old==null?null:old.copy();this.after=after==null?null:after.copy();
    }
    @Override public String label(){return "Edit actor shadow contact";}
    @Override public boolean isNoop(){return same(before,after);}
    private static boolean same(ActorShadowProfile a,ActorShadowProfile b){
        return a==b || a!=null && b!=null && a.leftU==b.leftU && a.leftV==b.leftV
                && a.rightU==b.rightU && a.rightV==b.rightV && a.thickness==b.thickness;
    }
    @Override public void redo(){apply(after,false);}
    @Override public void undo(){apply(before,true);}
    private void apply(ActorShadowProfile profile,boolean undo){
        int e=ids.entityOfHistoryId(historyId);if(e<0 || !world.getEntityManager().isActive(e))return;
        preview(world,e,assetId,clip,profile==null?null:profile.copy());
        if(undo && !hadSurface)world.getMapper(SurfaceLightingComponent.class).remove(e);
        if(markSave!=null)markSave.run();
        EventFlow.i().publish(new EventFlow.EntityChanged(e,TransformOp.SCALE,EventFlow.tag(this)));
    }
    /** Transient preview only. The caller restores the original profile before committing history. */
    public static void preview(World world,int e,int assetId,String clip,ActorShadowProfile profile){
        if(e<0 || !world.getEntityManager().isActive(e))return;
        SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).getSafe(e,null);
        if(s==null){if(profile==null)return;s=world.getMapper(SurfaceLightingComponent.class).create(e);}
        if(clip==null)s.actorShadow=profile;
        else{
            if(s.actorShadowClips==null){if(profile==null)return;s.actorShadowClips=new IntMap<>();}
            ObjectMap<String,ActorShadowProfile> clips=s.actorShadowClips.get(assetId);
            if(clips==null){if(profile==null)return;clips=new ObjectMap<>();s.actorShadowClips.put(assetId,clips);}
            if(profile==null){clips.remove(clip);if(clips.size==0)s.actorShadowClips.remove(assetId);if(s.actorShadowClips.size==0)s.actorShadowClips=null;}
            else clips.put(clip,profile);
        }
        DirtyTrackerSystem dirty=world.getSystem(DirtyTrackerSystem.class);if(dirty!=null)dirty.material(e);
    }
}
