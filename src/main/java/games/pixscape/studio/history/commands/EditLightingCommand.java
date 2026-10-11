package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.light.*;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.*;

/** One authored lighting property, with stable history identity and optional-component restoration. */
public final class EditLightingCommand implements Command,HistoryManager.SupportsNoop {
    public enum Property { HEIGHT, QUALITY, RESOLUTION, ALTITUDE, ANCHOR_U, ANCHOR_V,
        DIRECTION_X, DIRECTION_Y, RECEIVE, CAST, ALPHA_THRESHOLD }
    private final World world;private final HistoryIdRegistry ids;private final long historyId;
    private final Property property;private final float before,after;private final boolean hadSurface;
    private final int tag=EventFlow.tag(this);
    public EditLightingCommand(World world,HistoryIdRegistry ids,int entity,Property property,float after){
        this.world=world;this.ids=ids;this.historyId=ids.ensureForEntity(entity);this.property=property;
        this.before=read(world,entity,property);this.after=after;
        hadSurface=world.getMapper(SurfaceLightingComponent.class).has(entity);
        if(!Float.isFinite(after))throw new IllegalArgumentException("Lighting values must be finite");
        if(property==Property.QUALITY && (after!=0 && after!=1 && after!=2))throw new IllegalArgumentException("Unknown shadow quality");
        if(property==Property.RESOLUTION && after!=256 && after!=512 && after!=1024)throw new IllegalArgumentException("Unsupported shadow resolution");
        if((property==Property.ANCHOR_U || property==Property.ANCHOR_V || property==Property.ALPHA_THRESHOLD) && (after<0 || after>1))
            throw new IllegalArgumentException("Anchor and alpha threshold must be in 0..1");
        if(property==Property.DIRECTION_X && Math.abs(after-read(world,entity,Property.DIRECTION_Y))<.000001f
                || property==Property.DIRECTION_Y && Math.abs(read(world,entity,Property.DIRECTION_X)-after)<.000001f)
            throw new IllegalArgumentException("Sheet direction projects to zero width");
    }
    @Override public String label(){return "Edit lighting";}
    @Override public boolean isNoop(){return before==after;}
    @Override public void redo(){apply(after,false);}
    @Override public void undo(){apply(before,true);}
    private void apply(float value,boolean undo){
        int e=ids.entityOfHistoryId(historyId);if(e<0 || !world.getEntityManager().isActive(e))return;
        PointLightComponent p=world.getMapper(PointLightComponent.class).getSafe(e,null);
        ConeLightComponent c=world.getMapper(ConeLightComponent.class).getSafe(e,null);
        if(property==Property.HEIGHT){if(p!=null)p.height=value;if(c!=null)c.height=value;}
        else if(property==Property.QUALITY){if(p!=null)p.shadowQuality=(int)value;if(c!=null)c.shadowQuality=(int)value;}
        else if(property==Property.RESOLUTION){if(p!=null)p.shadowResolution=(int)value;if(c!=null)c.shadowResolution=(int)value;}
        else if(undo && !hadSurface)world.getMapper(SurfaceLightingComponent.class).remove(e);
        else{
            SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).create(e);
            switch(property){case ALTITUDE:s.altitude=value;break;case ANCHOR_U:s.anchorU=value;break;case ANCHOR_V:s.anchorV=value;break;
                case DIRECTION_X:s.directionX=value;break;case DIRECTION_Y:s.directionY=value;break;case RECEIVE:s.receiveLight=value!=0;break;
                case CAST:s.shadowCaster=value!=0;break;case ALPHA_THRESHOLD:s.alphaThreshold=value;break;default:throw new IllegalStateException("Unsupported lighting property");}
        }
        DirtyTrackerSystem dirty=world.getSystem(DirtyTrackerSystem.class);if(dirty!=null){dirty.geometry(e,GeometryDirty.ALL);dirty.material(e);}
        EventFlow.i().publish(new EventFlow.EntityChanged(e,TransformOp.SCALE,tag));
    }
    public static float read(World world,int e,Property property){
        PointLightComponent p=world.getMapper(PointLightComponent.class).getSafe(e,null);
        ConeLightComponent c=world.getMapper(ConeLightComponent.class).getSafe(e,null);
        if(property==Property.HEIGHT)return p!=null?p.height:c!=null?c.height:128;
        if(property==Property.QUALITY)return p!=null?p.shadowQuality:c!=null?c.shadowQuality:1;
        if(property==Property.RESOLUTION)return p!=null?p.shadowResolution:c!=null?c.shadowResolution:512;
        SurfaceLightingComponent s=world.getMapper(SurfaceLightingComponent.class).getSafe(e,null);
        switch(property){case ALTITUDE:return s==null?0:s.altitude;case ANCHOR_U:return s==null?.5f:s.anchorU;case ANCHOR_V:return s==null?0:s.anchorV;
            case DIRECTION_X:return s==null?1:s.directionX;case DIRECTION_Y:return s==null?-1:s.directionY;
            case RECEIVE:return s==null||s.receiveLight?1:0;case CAST:return s!=null&&s.shadowCaster?1:0;
            case ALPHA_THRESHOLD:return s==null?.5f:s.alphaThreshold;default:throw new IllegalArgumentException("Unsupported lighting property");}
    }
}
