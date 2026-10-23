package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;

/** Explicit map-local profile declaration. Validate a detached candidate before replacing authored data. */
public final class EditTileLightingDescriptionsCommand implements Command, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long mapId;
    private final Array<TileSurfaceDescription> before,after;
    private String rejection;
    public EditTileLightingDescriptionsCommand(World world,HistoryIdRegistry ids,int entity,Array<TileSurfaceDescription> after){
        this.world=world;this.ids=ids;mapId=ids.ensureForEntity(entity);
        before=copy(world.getMapper(TiledLayerComponent.class).get(entity).lightingDescriptions);this.after=copy(after);
    }
    public static Array<TileSurfaceDescription> copy(Array<TileSurfaceDescription> values){
        if(values==null)throw new IllegalArgumentException("Lighting descriptions cannot be null");
        Array<TileSurfaceDescription> result=new Array<>(TileSurfaceDescription.class);
        for(TileSurfaceDescription value:values){if(value==null)throw new IllegalArgumentException("Null lighting description");result.add(value.copy());}return result;
    }
    public static void validate(World world,TiledLayerComponent tiled,SpatialBlocksComponent blocks,Array<TileSurfaceDescription> values){
        if(tiled==null||tiled.data==null)throw new IllegalArgumentException("Map must be loaded before editing lighting descriptions");
        games.pixscape.runtime.system.RenderTiledSyncSystem sync=world.getSystem(games.pixscape.runtime.system.RenderTiledSyncSystem.class);
        if(sync==null)throw new IllegalArgumentException("Native tile resources are unavailable; lighting candidate cannot be validated");
        sync.validateLighting(tiled,blocks,values);
    }
    public String rejection(){return rejection;}
    @Override public String label(){return "Edit Tile Lighting Descriptions";}
    @Override public void redo(){redoOutcome();}
    @Override public void undo(){undoOutcome();}
    @Override public CommandOutcome executeOutcome(){return apply(after);}
    @Override public CommandOutcome redoOutcome(){return apply(after);}
    @Override public CommandOutcome undoOutcome(){return apply(before);}
    private CommandOutcome apply(Array<TileSurfaceDescription> snapshot){
        int entity=ids.entityOfHistoryId(mapId);if(entity<0||!world.getEntityManager().isActive(entity))return CommandOutcome.NO_CHANGE;
        TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).getSafe(entity,null);if(tiled==null)return CommandOutcome.NO_CHANGE;
        Array<TileSurfaceDescription> next=copy(snapshot);
        try{validate(world,tiled,world.getMapper(SpatialBlocksComponent.class).getSafe(entity,null),next);}
        catch(IllegalArgumentException invalid){rejection=invalid.getMessage();return CommandOutcome.REJECTED;}
        rejection=null;tiled.lightingDescriptions=next;
        DirtyTrackerSystem dirty=world.getSystem(DirtyTrackerSystem.class);if(dirty!=null)dirty.geometry(entity,GeometryDirty.ALL);
        return CommandOutcome.APPLIED;
    }
}
