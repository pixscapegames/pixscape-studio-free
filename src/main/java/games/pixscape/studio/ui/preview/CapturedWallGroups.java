package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Explicit candidate replay only. Reads construction metadata; never replaces captured geometry. */
public final class CapturedWallGroups {
    public static String apply(FrozenPreviewPose pose,FileHandle sceneFile){
        Json json=new Json();json.setIgnoreUnknownFields(true);
        JsonValue scene=new JsonReader().parse(sceneFile);
        ShadowExclusionIdentities ids=new ShadowExclusionIdentities();WallShadowGroups groups=new WallShadowGroups();
        IntMap<SpatialBlocksComponent> sources=new IntMap<>();IntMap<TiledMapLayerData> maps=new IntMap<>();
        for(JsonValue e:scene.get("entities")){
            JsonValue c=e.get("components"),t=c.get("TiledLayerComponent"),s=c.get("SpatialBlocksComponent");
            if(t==null||s==null)continue;int owner=Integer.parseInt(e.name);
            TiledMapLayerData map=new TiledMapLayerData(t.getInt("mapWidthCells"),t.getInt("mapHeightCells"),t.getInt("tileWidth"),t.getInt("tileHeight"),t.getInt("chunkSize"),TiledProjection.ISO);
            map.originX=t.getFloat("originX",0);map.originY=t.getFloat("originY",0);map.defaultTileAltitude=t.getFloat("defaultTileAltitude",0);
            int count=t.get("tileAssetIds").getInt("size");int[] xs=t.get("tileXs").get("items").asIntArray(),ys=t.get("tileYs").get("items").asIntArray(),assets=t.get("tileAssetIds").get("items").asIntArray();
            for(int i=0;i<count;i++)map.setTile(xs[i],ys[i],assets[i]);
            SpatialBlocksComponent blocks=new SpatialBlocksComponent();for(JsonValue b:s.get("blocks"))blocks.blocks.add(json.readValue(SpatialBlockData.class,b));
            Array<TileSurfaceDescription> descriptions=new Array<>();if(t.has("lightingDescriptions"))for(JsonValue d:t.get("lightingDescriptions"))descriptions.add(json.readValue(TileSurfaceDescription.class,d));
            MapLightingGeometry geometry=new MapLightingGeometry();geometry.descriptions(descriptions);geometry.ensure(map,blocks);
            geometry.registerShadowGroups(owner,ids,groups);sources.put(owner,blocks);maps.put(owner,map);
        }
        groups.connectWalls();StringBuilder report=new StringBuilder("Candidate construction metadata: "+sceneFile.file().getAbsolutePath()+"\n");
        java.util.IdentityHashMap<LightingSurface,Boolean> seen=new java.util.IdentityHashMap<>();
        java.util.IdentityHashMap<LightingSurface,Integer> capturedOwners=new java.util.IdentityHashMap<>();
        // Check actual archived caster coordinates against the metadata used for grouping.
        for(var c:pose.queue.lighting.casters){LightingSurface s=c.surface;int owner=s.ownerEntity;
            if(owner<0&&c.tiledRef>=0&&pose.queue.lighting.tiles.get(c.tiledRef)!=null)owner=pose.queue.lighting.tiles.get(c.tiledRef).ownerEntity;
            if(s.pocSourceBlockId>0&&!s.roundedIdentity){
                SpatialBlocksComponent bs=sources.get(owner);SpatialBlockData b=null;
                if(bs!=null)for(SpatialBlockData candidate:bs.blocks)if(candidate.id==s.pocSourceBlockId)b=candidate;
                if(b==null)throw new IllegalStateException("Missing captured support "+owner+":"+s.pocSourceBlockId);
                float[] min=new float[3],max=new float[3];LightingCoordinates.mapPoint(maps.get(owner),b.x,b.y,b.altitude,min,0);LightingCoordinates.mapPoint(maps.get(owner),b.x+b.width,b.y+b.depth,b.altitude+b.height,max,0);
                for(int i=0;i<s.vertices.length;i+=8)for(int k=0;k<3;k++)if(s.vertices[i+k]<min[k]-.01f||s.vertices[i+k]>max[k]+.01f)throw new IllegalStateException("Captured support differs from metadata "+owner+":"+b.id);
            }
            capturedOwners.put(s,owner);assign(s,owner,ids,groups,seen,report);
        }
        for(var e:pose.queue.lighting.tiles){capturedOwners.put(e.value,e.value.ownerEntity);assign(e.value,e.value.ownerEntity,ids,groups,seen,report);}
        // Frozen-replay diagnostic only: split one non-root support AFTER all unions,
        // including corner connections. Geometry, own exclusion and other groups stay intact.
        String separate=System.getProperty("pixscape.diagnostic.separateSupport");
        if(separate!=null){
            String[] pair=separate.split(":");
            if(pair.length!=2)throw new IllegalArgumentException("Expected owner:block");
            int owner=Integer.parseInt(pair[0]),block=Integer.parseInt(pair[1]),key=ids.idFor(owner,block);
            if(key<=0||groups.group(key)==key)throw new IllegalArgumentException("Expected a grouped non-root support");
            int changed=0;
            for(LightingSurface s:seen.keySet()){
                if(capturedOwners.get(s)==owner){
                    if(!s.roundedIdentity&&s.pocSourceBlockId==block){s.shadowGroupId=key;changed++;}
                    if(s.receiverBlockIds!=null)for(int i=0;i<s.receiverBlockIds.length;i++)
                        if(s.receiverBlockIds[i]==block)s.receiverShadowGroups[i]=key;
                }
            }
            if(changed==0)throw new IllegalArgumentException("Support not present in captured casters");
            report.append("Separated support ").append(separate).append(" group=").append(key).append(" surfaces=").append(changed).append('\n');
        }
        return report.toString();
    }
    private static void assign(LightingSurface s,int owner,ShadowExclusionIdentities ids,WallShadowGroups groups,java.util.IdentityHashMap<LightingSurface,Boolean> seen,StringBuilder report){
        if(seen.put(s,true)!=null)return;
        int key=s.roundedIdentity?ids.idForCorner(owner,s.roundedCellKey):ids.idFor(owner,s.pocSourceBlockId);
        // Zero/provenance-free surfaces retain their existing exclusion policy.
        s.shadowGroupId=key==0?s.selfShadowExclusionId:groups.group(key);
        if(s.receiverBlockIds!=null){s.receiverShadowGroups=new int[s.receiverBlockIds.length];for(int i=0;i<s.receiverBlockIds.length;i++)s.receiverShadowGroups[i]=groups.group(ids.idFor(owner,s.receiverBlockIds[i]));}
        report.append("owner=").append(owner).append(" block=").append(s.pocSourceBlockId).append(" corner=").append(s.roundedIdentity).append(" geometryId=").append(s.selfShadowExclusionId).append(" group=").append(s.shadowGroupId).append('\n');
    }
    public static void main(String[] args){
        FrozenPreviewPose pose=FrozenPreviewPose.codec().fromJson(FrozenPreviewPose.class,new FileHandle(args[0]));
        System.out.print(apply(pose,new FileHandle(args[1])));
    }
}
