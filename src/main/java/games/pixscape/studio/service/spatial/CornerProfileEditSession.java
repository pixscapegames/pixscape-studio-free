package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditTileLightingDescriptionsCommand;

/** One detached corner edit. Only the selected compiled receiver is swapped during a gesture. */
public final class CornerProfileEditSession {
    public record Target(int mapId,int gx,int gy,int assetId,int blockId) {}
    private World world;
    private TiledMapRenderState state;
    private HistoryManager history;
    private Target target;
    private TiledLayerComponent tiled;
    private SpatialBlocksComponent blocks;
    private RoundedWallPlacement placement;
    private RoundedWallDescription committed,candidate,gestureStart;
    private RoundedWallSupport support,gestureSupport;
    private MapLightingGeometry geometry;
    private MapLightingGeometry.Preview preview;
    private Array<TileSurfaceDescription> descriptions;
    private final float[] quad=new float[8];
    private int contentRevision,blockRevision,resourceRevision,geometryRevision,renderRef,dragVertex=-1;
    private long historyRevision;
    private String message="";
    private games.pixscape.runtime.tiled.TiledMapLayerData map;
    private float originX,originY,mapAltitude;
    private int tileWidth,tileHeight;
    private float pressX,pressY,vertexX,vertexY,lastX,lastY;
    private boolean active,valid=true;
    public int profileCompilations,geometryCompilations;
    public long lastUpdateNanos,lastCommitNanos;

    /** Cold menu inspection; never attaches tiles or mutates the selected volume. */
    public String inspect(World world,TiledMapRenderState state,HistoryManager history,Target target) {
        this.world=world;this.state=state;this.history=history;this.target=target;
        try {
            if(target==null)throw new IllegalArgumentException("Point at a rendered tile linked to the selected volume");
            tiled=world.getMapper(TiledLayerComponent.class).getSafe(target.mapId,null);
            blocks=world.getMapper(SpatialBlocksComponent.class).getSafe(target.mapId,null);
            if(tiled==null||tiled.data==null||blocks==null||tiled.data.getTile(target.gx,target.gy)!=target.assetId)
                throw new IllegalArgumentException("The pointed tile is no longer available");
            Array<SpatialBlockData> owners=new Array<>();boolean selectedLinked=false;
            for(SpatialBlockData b:blocks.blocks) for(SpatialBlockData.LinkedTileRef ref:b.linkedTileRefs) {
                if(ref.gx==target.gx&&ref.gy==target.gy&&ref.tileAssetId==target.assetId) {
                    owners.add(b);selectedLinked|=b.id==target.blockId;break;
                }
            }
            if(!selectedLinked)throw new IllegalArgumentException("This tile is not linked to the selected volume");
            if(tiled.data.getTileTransformFlags(target.gx,target.gy)!=0)
                throw new IllegalArgumentException("Transformed tiles are not supported by this corner editor");
            renderRef=tiled.data.tiledRenderRefForTile(target.gx,target.gy);
            if(!state.isRenderableRef(renderRef))throw new IllegalArgumentException("Native tile image is unavailable");
            readQuad(state,renderRef,quad);
            committed=null;descriptions=tiled.lightingDescriptions;
            for(TileSurfaceDescription d:descriptions) if(d.assetId==target.assetId) {
                if(d.rounded==null)throw new IllegalArgumentException("This asset already has a different explicit lighting support");
                committed=d.rounded.copy();break;
            }
            candidate=committed==null?new RoundedWallDescription():committed.copy();
            if(committed==null&&owners.size>0) {
                candidate.radiusCells=(quad[6]-quad[0])/(2*tiled.data.tileHeight);
                candidate.centerGX=owners.first().x-target.gx+candidate.radiusCells;
                candidate.centerGY=owners.first().y-target.gy+candidate.radiusCells;
            }
            placement=new RoundedWallPlacement(tiled.data,target.gx,target.gy,candidate,owners);
            support=placement.compile(candidate,quad);
            captureRevisions();message="";return null;
        } catch(IllegalArgumentException invalid) {message=invalid.getMessage();return message;}
    }
    public boolean open() {
        if(stale()) {message="The map changed. Open the tile menu again.";return false;}
        if(committed==null&&!publish(candidate))return false;
        active=true;return true;
    }
    /** Selection must never activate or publish a previously unconfigured profile. */
    public boolean openExisting() {return committed!=null&&reason()==null&&open();}
    private void captureRevisions() {
        map=tiled.data;originX=map.originX;originY=map.originY;mapAltitude=map.defaultTileAltitude;tileWidth=map.tileWidth;tileHeight=map.tileHeight;
        descriptions=tiled.lightingDescriptions;contentRevision=tiled.data.contentRevision();blockRevision=blocks.revision;
        historyRevision=history.getMutationRevision();resourceRevision=world.getSystem(RenderTiledSyncSystem.class).lightingResourceRevision(tiled.atlasTag);
        geometry=state.lightingMaps.get(target.mapId);geometryRevision=geometry==null?0:geometry.publicationRevision();
    }
    private boolean stale() {
        return target==null || world.getMapper(TiledLayerComponent.class).getSafe(target.mapId,null)!=tiled
                || world.getMapper(SpatialBlocksComponent.class).getSafe(target.mapId,null)!=blocks
                || map!=tiled.data || originX!=map.originX||originY!=map.originY||mapAltitude!=map.defaultTileAltitude
                ||tileWidth!=map.tileWidth||tileHeight!=map.tileHeight
                || descriptions!=tiled.lightingDescriptions || contentRevision!=tiled.data.contentRevision()
                || blockRevision!=blocks.revision || historyRevision!=history.getMutationRevision()
                || resourceRevision!=world.getSystem(RenderTiledSyncSystem.class).lightingResourceRevision(tiled.atlasTag);
    }
    /** O(1) lifecycle check. Re-inspection only follows an explicit authored/resource publication. */
    public void synchronize() {
        if(!active)return;
        if(stale()||quadChanged()) {
            cancelGesture();
            Target saved=target;
            String error=inspect(world,state,history,saved);
            if(error!=null||committed==null){active=false;return;}
        }
        MapLightingGeometry current=state.lightingMaps.get(target.mapId);
        if(current!=geometry || current!=null&&current.publicationRevision()!=geometryRevision) {
            cancelGesture();geometry=current;geometryRevision=current==null?0:current.publicationRevision();
        }
    }
    private boolean quadChanged() {
        return tiled.data.tiledRenderRefForTile(target.gx,target.gy)!=renderRef || !state.isRenderableRef(renderRef)
                || state.x1[renderRef]!=quad[0]||state.y1[renderRef]!=quad[1]||state.x2[renderRef]!=quad[2]||state.y2[renderRef]!=quad[3]
                ||state.x3[renderRef]!=quad[4]||state.y3[renderRef]!=quad[5]||state.x4[renderRef]!=quad[6]||state.y4[renderRef]!=quad[7];
    }
    public boolean begin(int vertex) {
        synchronize();if(!active||geometry==null)return false;
        float[] points=vertices();
        if(vertex<=0||vertex>=points.length/2-1){message="Endpoint locked to its wall join";return false;}
        try {preview=geometry.beginRoundedPreview(target.gx,target.gy);}
        catch(IllegalArgumentException unavailable){message=unavailable.getMessage();return false;}
        dragVertex=vertex;gestureStart=candidate.copy();gestureSupport=support;valid=true;message="";return true;
    }
    public boolean press(int vertex,float x,float y) {
        if(!begin(vertex))return false;
        pressX=lastX=x;pressY=lastY=y;vertexX=screenX(vertex);vertexY=screenY(vertex);return true;
    }
    public void drag(float x,float y) {if(x!=lastX||y!=lastY){lastX=x;lastY=y;move(vertexX+x-pressX,vertexY+y-pressY);}}
    public void move(float worldX,float worldY) {
        if(dragVertex<0)return;
        RoundedWallDescription next=gestureStart.copy();next.profileXY=new RoundedWallSupport.Profile(gestureStart).vertices();
        int i=dragVertex*2;
        float startX=gestureSupport.outerXY[i]-gestureSupport.outerXY[i+1];
        float startY=(gestureSupport.outerXY[i]+gestureSupport.outerXY[i+1])*.5f+placement.base;
        float dx=worldX-startX,dy=worldY-startY;
        next.profileXY[i]+=(dy+dx*.5f)/placement.radius;
        next.profileXY[i+1]+=(dy-dx*.5f)/placement.radius;
        if(dragVertex==1)next.profileXY[i]=-1;
        if(i==next.profileXY.length-4)next.profileXY[i+1]=-1;
        if(gestureStart.profileXY==null) {
            float[] initial=new RoundedWallSupport.Profile(gestureStart).vertices();
            boolean same=true;for(int k=0;k<initial.length;k++)if(initial[k]!=next.profileXY[k]){same=false;break;}
            if(same)next.profileXY=null;
        }
        if(candidate.sameAs(next))return;
        long start=System.nanoTime();candidate=next;
        try {
            profileCompilations++;RoundedWallSupport compiled=placement.compile(next,quad);geometryCompilations++;
            show(compiled);support=compiled;valid=true;message="";
        } catch(IllegalArgumentException invalid){valid=false;message=invalid.getMessage();}
        lastUpdateNanos=System.nanoTime()-start;
    }
    private void show(RoundedWallSupport compiled) {
        LightingSurface previous=state.lightingSurfaces.get(renderRef);
        compiled.receiver.ownerEntity=target.mapId;
        if(previous!=null)compiled.receiver.normalMap=previous.normalMap;
        preview.show(compiled);state.lightingSurfaces.put(renderRef,compiled.receiver);state.lightingRevision++;
    }
    public void release() {
        if(dragVertex<0)return;
        RoundedWallDescription next=candidate.copy();boolean accepted=valid&&!stale();
        cancelGesture();if(accepted)publish(next);else message="Invalid or stale gesture cancelled";
    }
    public void cancelGesture() {
        if(dragVertex<0)return;
        if(preview!=null&&preview.isCurrent()&&state.lightingMaps.get(target.mapId)==geometry&&geometry.publicationRevision()==geometryRevision) {
            LightingSurface restored=preview.restore();state.lightingSurfaces.put(renderRef,restored);state.lightingRevision++;
        }
        candidate=gestureStart;support=gestureSupport;preview=null;dragVertex=-1;valid=true;message="";
    }
    public void close(){cancelGesture();active=false;}
    public boolean add(int segment) {
        float[] p=vertices();if(p.length>=18){message="Maximum: 8 segments";return false;}
        if(segment<0||segment>=p.length/2-1)return false;
        float[] next=new float[p.length+2];int at=(segment+1)*2;
        System.arraycopy(p,0,next,0,at);next[at]=(p[at-2]+p[at])*.5f;next[at+1]=(p[at-1]+p[at+1])*.5f;
        System.arraycopy(p,at,next,at+2,p.length-at);
        RoundedWallDescription d=candidate.copy();d.profileXY=next;return publish(d);
    }
    public boolean remove(int vertex) {
        float[] p=vertices();if(p.length<=8){message="Minimum: 3 segments";return false;}
        if(vertex<=0||vertex>=p.length/2-1){message="Endpoints cannot be removed";return false;}
        float[] next=new float[p.length-2];int at=vertex*2;System.arraycopy(p,0,next,0,at);
        System.arraycopy(p,at+2,next,at,p.length-at-2);
        RoundedWallDescription d=candidate.copy();d.profileXY=next;return publish(d);
    }
    public boolean reset(){RoundedWallDescription d=candidate.copy();d.profileXY=null;return publish(d);}
    /** Remove the map-local asset declaration, retaining the walls and their tile associations. */
    public boolean removeProfile() {
        cancelGesture();
        if(stale()||committed==null)return false;
        Array<TileSurfaceDescription> values=EditTileLightingDescriptionsCommand.copy(tiled.lightingDescriptions);
        for(int i=values.size-1;i>=0;i--)if(values.get(i).assetId==target.assetId)values.removeIndex(i);
        EditTileLightingDescriptionsCommand command=new EditTileLightingDescriptionsCommand(world,history.historyIds(),target.mapId,values);
        history.execute(command);
        if(command.rejection()!=null){message=command.rejection();return false;}
        close();committed=null;captureRevisions();return true;
    }
    private boolean publish(RoundedWallDescription next) {
        if(stale()){message="The map or resources changed; reopen this profile";return false;}
        if(committed!=null&&committed.sameAs(next))return false;
        long start=System.nanoTime();
        try {
            RoundedWallSupport compiled=placement.compile(next,quad);
            Array<TileSurfaceDescription> values=EditTileLightingDescriptionsCommand.copy(tiled.lightingDescriptions);
            TileSurfaceDescription value=null;for(TileSurfaceDescription d:values)if(d.assetId==target.assetId){value=d;break;}
            if(value==null){value=new TileSurfaceDescription();value.assetId=target.assetId;values.add(value);}
            value.rounded=next.copy();
            EditTileLightingDescriptionsCommand command=new EditTileLightingDescriptionsCommand(world,history.historyIds(),target.mapId,values);
            history.execute(command);
            if(command.rejection()!=null){message=command.rejection();return false;}
            committed=next.copy();candidate=next.copy();support=compiled;captureRevisions();message="";valid=true;return true;
        } catch(IllegalArgumentException invalid){message=invalid.getMessage();return false;}
        finally {lastCommitNanos=System.nanoTime()-start;}
    }
    public boolean active(){return active;}
    public boolean isEditingOccurrence(Target other) {
        return active&&other!=null&&target.mapId==other.mapId&&target.gx==other.gx
                &&target.gy==other.gy&&target.assetId==other.assetId;
    }
    public boolean dragging(){return dragVertex>=0;}
    public boolean valid(){return valid;}
    public Target target(){return target;}
    public String reason(){return message.isEmpty()?null:message;}
    public float[] vertices(){return new RoundedWallSupport.Profile(candidate).vertices();}
    public RoundedWallSupport support(){return support;}
    public float base(){return placement.base;}
    public float height(){return placement.height;}
    public float screenX(int vertex){float[] p=candidate.profileXY;float x=p==null?support.outerXY[vertex*2]:placement.cx+p[vertex*2]*placement.radius;
        float y=p==null?support.outerXY[vertex*2+1]:placement.cy+p[vertex*2+1]*placement.radius;return x-y;}
    public float screenY(int vertex){float[] p=candidate.profileXY;float x=p==null?support.outerXY[vertex*2]:placement.cx+p[vertex*2]*placement.radius;
        float y=p==null?support.outerXY[vertex*2+1]:placement.cy+p[vertex*2+1]*placement.radius;return (x+y)*.5f+placement.base;}
    public int vertexCount(){return candidate.profileXY==null?7:candidate.profileXY.length/2;}
    public int pickVertex(float x,float y,float tolerance) {
        for(int i=0;i<vertexCount();i++){float dx=x-screenX(i),dy=y-screenY(i);if(dx*dx+dy*dy<=tolerance*tolerance)return i;}return -1;
    }
    public int pickSegment(float x,float y,float tolerance) {
        for(int i=0;i<vertexCount()-1;i++) {
            float ax=screenX(i),ay=screenY(i),dx=screenX(i+1)-ax,dy=screenY(i+1)-ay;
            float t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)));
            float ex=x-ax-t*dx,ey=y-ay-t*dy;if(ex*ex+ey*ey<=tolerance*tolerance)return i;
        }return -1;
    }
    private static void readQuad(TiledMapRenderState s,int r,float[] q){q[0]=s.x1[r];q[1]=s.y1[r];q[2]=s.x2[r];q[3]=s.y2[r];q[4]=s.x3[r];q[5]=s.y3[r];q[6]=s.x4[r];q[7]=s.y4[r];}
}
