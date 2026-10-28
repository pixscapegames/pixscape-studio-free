package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.lighting.MapLightingGeometry;
import games.pixscape.runtime.render.lighting.RoundedWallDescription;
import games.pixscape.runtime.render.lighting.RoundedWallSupport;
import games.pixscape.runtime.render.lighting.TileSurfaceDescription;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Read-only overview of published corner supports. Rebuilt only at publication/activation boundaries. */
public final class CornerProfileOverlay {
    public record Corner(CornerProfileEditSession.Target target, RoundedWallSupport support,
                         float base, float height, float minX, float minY, float maxX, float maxY) {
        public float x(int vertex) {return support.outerXY[vertex*2]-support.outerXY[vertex*2+1];}
        public float y(int vertex) {return (support.outerXY[vertex*2]+support.outerXY[vertex*2+1])*.5f+base;}
        public boolean contains(float x,float y) {
            for(int i=0;i<support.segments;i++) {
                float ax=x(i),ay=y(i),bx=x(i+1),by=y(i+1);
                if(quadContains(x,y,ax,ay,bx,by,bx,by+height,ax,ay+height))return true;
                float[] inner=support.innerXY;
                float cx=inner[(i+1)*2]-inner[(i+1)*2+1],cy=(inner[(i+1)*2]+inner[(i+1)*2+1])*.5f+base;
                float dx=inner[i*2]-inner[i*2+1],dy=(inner[i*2]+inner[i*2+1])*.5f+base;
                if(quadContains(x,y,ax,ay+height,bx,by+height,cx,cy+height,dx,dy+height)
                        ||quadContains(x,y,dx,dy,cx,cy,cx,cy+height,dx,dy+height))return true;
            }
            return false;
        }
    }
    private static boolean quadContains(float x,float y,float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy) {
        return com.badlogic.gdx.math.Intersector.isPointInTriangle(x,y,ax,ay,bx,by,cx,cy)
                ||com.badlogic.gdx.math.Intersector.isPointInTriangle(x,y,ax,ay,cx,cy,dx,dy);
    }
    private final Array<Corner> corners=new Array<>();
    private final LongMap<Corner> cornersByCell=new LongMap<>();
    private final IntMap<float[]> wires=new IntMap<>();
    private final IntSet replacedWalls=new IntSet();
    private MapLightingGeometry geometry;
    private SpatialBlocksComponent blocks;
    private TiledMapLayerData map;
    private Object descriptions;
    private int mapId=-1, publication=-1, geometryCount=-1, blockRevision=-1, structureRevision=-1;
    private int rebuilds;

    public int size(){return corners.size;}
    public Corner corner(int index){return corners.get(index);}
    public float[] wire(int structureId){return wires.get(structureId);}
    public boolean replacesWall(int wallId){return replacedWalls.contains(wallId);}
    public boolean replacesWall(int requestedMap,int wallId){return requestedMap==mapId&&replacesWall(wallId);}
    public int rebuildCount(){return rebuilds;}

    /** Native sprite picking resolves overlap; the cached support restricts selection to the rounded part. */
    public CornerProfileEditSession.Target pickBody(int requestedMap,float x,float y,
                                                   games.pixscape.runtime.render.TiledMapRenderState state) {
        if(requestedMap!=mapId)return null;
        SpatialCellPicker.Result cell=new SpatialCellPicker.Result();
        if(!SpatialCellPicker.pickTopmostVisibleOccupiedSprite(map,state,x,y,cell))return null;
        Corner corner=cornersByCell.get(((long)cell.gx<<32)^(cell.gy&0xffffffffL));
        return corner!=null&&corner.contains(x,y)?corner.target:null;
    }

    /** Handles are picked before rectangular volumes, using their retained full tile identity. */
    public CornerProfileEditSession.Target pickHandle(int requestedMap,float x,float y,float tolerance,
                                                     games.pixscape.runtime.render.TiledMapRenderState state) {
        if(requestedMap!=mapId)return null;
        Corner hit=null;int bestRef=-1;long bestKey=0;
        for(Corner corner:corners) {
            int ref=map.tiledRenderRefForTile(corner.target.gx(),corner.target.gy());
            if(!state.isRenderableRef(ref))continue;
            for(int i=0;i<=corner.support.segments;i++) {
                float dx=x-corner.x(i),dy=y-corner.y(i);
                if(dx*dx+dy*dy>tolerance*tolerance)continue;
                long key=state.sortKey[ref];
                if(hit==null||Long.compareUnsigned(key,bestKey)>0||key==bestKey&&ref>bestRef) {
                    hit=corner;bestRef=ref;bestKey=key;
                }
                break;
            }
        }
        return hit==null?null:hit.target;
    }

    /** O(1) unchanged check; cold work visits linked memberships and affected structure boundaries only. */
    public void synchronize(int id,TiledLayerComponent tiled,SpatialBlocksComponent source,
                            MapLightingGeometry published,SpatialStructureGeometryCache structures) {
        int revision=published==null?-1:published.publicationRevision();
        int compiled=published==null?-1:published.roundedGeometryCompilationCount();
        if(mapId==id&&map==tiled.data&&blocks==source&&descriptions==tiled.lightingDescriptions
                &&geometry==published&&publication==revision&&geometryCount==compiled
                &&blockRevision==source.revision&&structureRevision==structures.publishedRevision())return;
        mapId=id;map=tiled.data;blocks=source;descriptions=tiled.lightingDescriptions;
        geometry=published;publication=revision;geometryCount=compiled;blockRevision=source.revision;
        structureRevision=structures.publishedRevision();rebuilds++;
        corners.clear();cornersByCell.clear();wires.clear();replacedWalls.clear();
        if(published==null)return;
        IntMap<RoundedWallDescription> profiles=new IntMap<>();
        for(TileSurfaceDescription d:tiled.lightingDescriptions)
            // Legacy center/radius descriptions already have compiled vertices too.
            if(d.rounded!=null)profiles.put(d.assetId,d.rounded);
        if(profiles.size==0)return;
        LongMap<Corner> cells=new LongMap<>();
        IntMap<LongMap<Corner>> byStructure=new IntMap<>();
        for(SpatialBlockData wall:source.blocks)for(SpatialBlockData.LinkedTileRef ref:wall.linkedTileRefs) {
            RoundedWallDescription d=profiles.get(ref.tileAssetId);
            if(d==null||map.getTile(ref.gx,ref.gy)!=ref.tileAssetId)continue;
            RoundedWallSupport support=published.roundedSupport(ref.gx,ref.gy);
            if(support==null)continue;
            replacedWalls.add(wall.id);
            long key=((long)ref.gx<<32)^(ref.gy&0xffffffffL);
            Corner corner=cells.get(key);
            if(corner==null) {
                float cx=ref.gx+d.centerGX,cy=ref.gy+d.centerGY;
                corner=new Corner(new CornerProfileEditSession.Target(id,ref.gx,ref.gy,ref.tileAssetId,wall.id),
                        support,wall.altitude,wall.height,cx-d.radiusCells,cy-d.radiusCells,cx,cy);
                cells.put(key,corner);cornersByCell.put(key,corner);corners.add(corner);
            }
            LongMap<Corner> cuts=byStructure.get(wall.structureId);
            if(cuts==null){cuts=new LongMap<>();byStructure.put(wall.structureId,cuts);}
            cuts.put(key,corner);
        }
        for(int i=0;i<structures.structureCount();i++) {
            CompiledSpatialStructure structure=structures.structure(i);
            LongMap<Corner> cuts=byStructure.get(structure.structureId());
            if(cuts!=null)wires.put(structure.structureId(),clippedWire(map,structure,cuts.values().toArray()));
        }
    }

    /** Subtract the same corner square replaced by the renderer; retain straight wall continuations. */
    private static float[] clippedWire(TiledMapLayerData map,CompiledSpatialStructure structure,Array<Corner> cuts) {
        FloatArray result=new FloatArray();float[] projected=new float[8];LongMap<Boolean> verticals=new LongMap<>();
        for(int i=0;i<structure.segmentCount();i++) {
            FloatArray pieces=new FloatArray(new float[]{structure.startX(i),structure.startY(i),structure.endX(i),structure.endY(i)});
            // Geometric subtraction is restricted to corners of this structure, never all map objects.
            for(Corner cut:cuts) {
                FloatArray remaining=new FloatArray();
                for(int j=0;j<pieces.size;j+=4)subtract(pieces.items,j,cut,remaining);
                pieces=remaining;
            }
            for(int j=0;j<pieces.size;j+=4) {
                float ax=pieces.items[j],ay=pieces.items[j+1],bx=pieces.items[j+2],by=pieces.items[j+3];
                SpatialBlockProjection.projectStructurePoint(map,ax,ay,structure.lowerZ(),projected,0);
                SpatialBlockProjection.projectStructurePoint(map,bx,by,structure.lowerZ(),projected,2);
                SpatialBlockProjection.projectStructurePoint(map,ax,ay,structure.upperZ(),projected,4);
                SpatialBlockProjection.projectStructurePoint(map,bx,by,structure.upperZ(),projected,6);
                result.addAll(projected,0,4);result.addAll(projected,4,4);
                appendVertical(result,verticals,ax,ay,projected,0);
                appendVertical(result,verticals,bx,by,projected,2);
            }
        }
        return result.toArray();
    }
    private static void appendVertical(FloatArray out,LongMap<Boolean> seen,float x,float y,float[] p,int offset) {
        long key=((long)Float.floatToIntBits(x)<<32)^(Float.floatToIntBits(y)&0xffffffffL);
        if(seen.containsKey(key))return;seen.put(key,true);
        out.add(p[offset],p[offset+1],p[offset+4],p[offset+5]);
    }
    static void subtract(float[] p,int offset,Corner c,FloatArray out) {
        float ax=p[offset],ay=p[offset+1],bx=p[offset+2],by=p[offset+3];
        boolean horizontal=ay==by;
        float constant=horizontal?ay:ax,lo=horizontal?c.minY:c.minX,hi=horizontal?c.maxY:c.maxX;
        if(constant<lo||constant>=hi){out.add(ax,ay,bx,by);return;}
        float start=horizontal?ax:ay,end=horizontal?bx:by;
        float from=horizontal?c.minX:c.minY,to=horizontal?c.maxX:c.maxY;
        float low=Math.min(start,end),high=Math.max(start,end);
        if(high<=from||low>=to){out.add(ax,ay,bx,by);return;}
        if(low<from)appendAxis(out,horizontal,constant,low,from);
        if(high>to)appendAxis(out,horizontal,constant,to,high);
    }
    private static void appendAxis(FloatArray out,boolean horizontal,float constant,float from,float to) {
        if(horizontal)out.add(from,constant,to,constant);else out.add(constant,from,constant,to);
    }
}
