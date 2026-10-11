package games.pixscape.studio.service.spatial;

import com.artemis.*;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.*;
import java.util.*;

/** Cold, detached vertical adaptation. Search and compilation are never frame work. */
public final class SpatialVerticalJoinProposal {
    public static final class MapState {
        public final int entity;
        public final int stableId;
        public final TiledMapLayerData map;
        public final SpatialBlocksComponent blocks;
        public final float defaultHeight;
        public final int revision;
        public final String physics;
        public final float pixelsPerMeter;
        private final String authored;
        private final float parallaxX,parallaxY;
        private final float originX,originY,altitude;
        private final int width,height,tw,th;
        MapState(World world,int entity) {
            this.entity=entity;
            PixscapeIdentityComponent identity=world.getMapper(PixscapeIdentityComponent.class).getSafe(entity,null);
            stableId=identity!=null?identity.stableId:0;
            TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).get(entity);
            map=tiled.data;blocks=SpatialLinkProposal.copy(world.getMapper(SpatialBlocksComponent.class).get(entity));
            defaultHeight=tiled.defaultTileHeight;revision=map.contentRevision();authored=mapSignature(tiled);
            physics=physicsSignature(world,entity);
            games.pixscape.studio.configuration.ProjectConfig config=games.pixscape.studio.configuration.ProjectConfig.getInstance();
            pixelsPerMeter=config!=null && config.getCurrentSceneMeta()!=null?config.getCurrentSceneMeta().pixelsPerMeter:0;
            originX=map.originX;originY=map.originY;altitude=map.defaultTileAltitude;
            width=map.mapWidth;height=map.mapHeight;tw=map.tileWidth;th=map.tileHeight;
            float[] parallax=parallax(world,entity);parallaxX=parallax[0];parallaxY=parallax[1];
        }
        public boolean matches(World world,int entity,int expectedRevision) {
            TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).getSafe(entity,null);
            if(tiled==null || tiled.data==null || !authored.equals(mapSignature(tiled)))return false;
            TiledMapLayerData current=tiled.data;float[] p=parallax(world,entity);
            return current.contentRevision()==expectedRevision && current.projection==map.projection
                    && current.originX==originX && current.originY==originY && current.defaultTileAltitude==altitude
                    && current.mapWidth==width && current.mapHeight==height && current.tileWidth==tw && current.tileHeight==th
                    && p[0]==parallaxX && p[1]==parallaxY;
        }
        private static String mapSignature(TiledLayerComponent tiled) {
            JsonValue value=new JsonReader().parse(new Json().toJson(tiled));value.remove("defaultTileHeight");
            return value.toJson(JsonWriter.OutputType.json);
        }
        public static String physicsSignature(World world,int entity) {
            Json json=new Json();
            return json.toJson(world.getMapper(PhysicsShapesComponent.class).getSafe(entity,null))
                    +json.toJson(world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).getSafe(entity,null))
                    +json.toJson(world.getMapper(TransformComponent.class).getSafe(entity,null));
        }
    }
    public static final class Candidate {
        public final MapState reference;
        public final int blockId;
        public final boolean referenceAbove;
        public final float longitudinalOverlap,transverseGap,verticalGap;
        Candidate(MapState reference,int blockId,boolean above,float overlap,float transverse,float vertical) {
            this.reference=reference;this.blockId=blockId;referenceAbove=above;
            longitudinalOverlap=overlap;transverseGap=transverse;verticalGap=vertical;
        }
        @Override public String toString() {return "Map "+(reference.stableId>0?reference.stableId:reference.entity)+" / volume "+blockId+(referenceAbove?" (above)":" (below)");}
    }
    public static final class Search {
        public final MapState selected;
        public final Array<Candidate> candidates=new Array<>(Candidate.class);
        public String diagnostic;
        Search(MapState selected) {this.selected=selected;}
    }
    public final MapState selected;
    public final Candidate candidate;
    public final int selectedId;
    public final SpatialBlocksComponent after;
    public final Set<Integer> affected=new LinkedHashSet<>(),referenceMembers=new LinkedHashSet<>();
    public boolean valid;
    public String diagnostic;
    public float defaultHeight;

    /** All maps in the current world with compatible native ISO axes and display parallax. */
    public static Search search(World world,int selectedMap,int selectedId) {
        TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).getSafe(selectedMap,null);
        SpatialBlocksComponent walls=world.getMapper(SpatialBlocksComponent.class).getSafe(selectedMap,null);
        if(tiled==null || tiled.data==null || walls==null) {Search result=new Search(null);result.diagnostic="The selected map is unavailable.";return result;}
        MapState source=new MapState(world,selectedMap);Search result=new Search(source);
        SpatialBlockData seed=find(source.blocks,selectedId);
        if(seed==null || source.map.projection!=TiledProjection.ISO) {result.diagnostic="Vertical volume joining requires ISO maps.";return result;}
        if(ambiguousOrientation(seed)) {result.diagnostic="The selected wall orientation is ambiguous.";return result;}
        boolean horizontal=horizontal(seed);int unsupported=0;
        com.artemis.utils.IntBag maps=world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class,SpatialBlocksComponent.class)).getEntities();
        for(int i=0;i<maps.size();i++) {
            int entity=maps.get(i);TiledLayerComponent other=world.getMapper(TiledLayerComponent.class).get(entity);
            if(other.data==null)continue;
            MapState target=entity==selectedMap?source:new MapState(world,entity);
            if(target.map.projection!=TiledProjection.ISO || source.tw*(double)target.th!=target.tw*(double)source.th
                    || source.parallaxX!=target.parallaxX || source.parallaxY!=target.parallaxY) {unsupported++;continue;}
            for(SpatialBlockData wall:target.blocks.blocks) {
                if(entity==selectedMap && wall.structureId==seed.structureId)continue;
                if(ambiguousOrientation(wall) || horizontal(wall)!=horizontal)continue;
                if(wall.altitude==seed.altitude)continue;
                float[] band=rectangleIn(target.map,wall,source.map);
                float low=horizontal?seed.x:seed.y,high=low+(horizontal?seed.width:seed.depth);
                float rlow=horizontal?band[0]:band[1],rhigh=rlow+(horizontal?band[2]:band[3]);
                float overlap=Math.min(high,rhigh)-Math.max(low,rlow);
                float center=horizontal?seed.y+seed.depth*.5f:seed.x+seed.width*.5f;
                float rcenter=horizontal?band[1]+band[3]*.5f:band[0]+band[2]*.5f;
                float transverse=Math.abs(center-rcenter);
                // A one-cell transverse neighbourhood permits imperfect joins without searching distant walls.
                float reach=Math.max(1f,Math.max(horizontal?seed.depth:seed.width,horizontal?band[3]:band[2]));
                if(overlap<=SpatialWallGeometry.GEOMETRY_EPSILON || transverse>reach)continue;
                boolean above=wall.altitude>seed.altitude;
                result.candidates.add(new Candidate(target,wall.id,above,overlap,transverse,
                        above?wall.altitude-(seed.altitude+seed.height):seed.altitude-(wall.altitude+wall.height)));
            }
        }
        result.candidates.sort(Comparator.comparingDouble((Candidate c)->Math.abs(c.verticalGap))
                .thenComparingDouble(c->c.transverseGap).thenComparingInt(c->c.reference.entity).thenComparingInt(c->c.blockId));
        if(result.candidates.size==0)result.diagnostic="No compatible wall above or below in the one-cell transverse neighbourhood."
                +(unsupported>0?" Some maps have unsupported projection, ISO metric or parallax.":"");
        return result;
    }
    public static SpatialVerticalJoinProposal create(World world,MapState source,int selectedId,Candidate candidate) {
        SpatialVerticalJoinProposal result=new SpatialVerticalJoinProposal(source,selectedId,candidate);
        try {result.diagnostic=result.solve(world);}catch(RuntimeException failure) {result.diagnostic=failure.getMessage()!=null?failure.getMessage():failure.getClass().getSimpleName();}
        result.valid=result.diagnostic==null;return result;
    }
    private SpatialVerticalJoinProposal(MapState source,int selectedId,Candidate candidate) {
        selected=source;this.selectedId=selectedId;this.candidate=candidate;after=SpatialLinkProposal.copy(source.blocks);defaultHeight=source.defaultHeight;
    }
    private String solve(World world) {
        SpatialStructureCompilation.Result initial=SpatialStructureCompilation.tryCompile(selected.blocks,selected.map);
        if(!initial.success())return "Selected map is invalid: "+initial.diagnostic();
        SpatialStructureCompilation.Result reference=SpatialStructureCompilation.tryCompile(candidate.reference.blocks,candidate.reference.map);
        if(!reference.success())return "Reference map is invalid: "+reference.diagnostic();
        SpatialBlockData seed=find(selected.blocks,selectedId),fixed=find(candidate.reference.blocks,candidate.blockId);
        if(seed==null || fixed==null)return "A selected or reference volume is missing.";
        for(SpatialBlockData wall:selected.blocks.blocks)if(wall.structureId==seed.structureId)affected.add(wall.id);
        for(SpatialBlockData wall:candidate.reference.blocks.blocks)if(wall.structureId==fixed.structureId)referenceMembers.add(wall.id);
        if(selected.entity==candidate.reference.entity && seed.structureId==fixed.structureId)return "The reference must be a different ensemble.";
        float base=candidate.referenceAbove?seed.altitude:fixed.altitude+fixed.height;
        float height=candidate.referenceAbove?fixed.altitude-seed.altitude:seed.altitude+seed.height-base;
        if(!Float.isFinite(height) || height<=0 || !Float.isFinite(base))return "The resulting height must be finite and strictly positive.";
        for(SpatialBlockData wall:after.blocks)if(affected.contains(wall.id)) {wall.altitude=base;wall.height=height;}
        SpatialBlockData changed=find(after,selectedId);float[] band=rectangleIn(candidate.reference.map,fixed,selected.map);
        if(horizontal(seed)) {changed.y=band[1];changed.depth=band[3];}
        else {changed.x=band[0];changed.width=band[2];}
        String conflict=SpatialWallThicknessPropagation.apply(selected.blocks.blocks,after.blocks,after.links,selectedId);
        if(conflict!=null)return conflict;
        // Exact topology validation rejects new intersections with disjoint structures, not only broken contacts.
        SpatialStructureCompilation.Result compiled=SpatialStructureCompilation.tryCompile(after,selected.map);
        if(!compiled.success())return compiled.diagnostic();
        for(SpatialBlockData wall:selected.blocks.blocks)if(!affected.contains(wall.id)
                && !new Json().toJson(wall).equals(new Json().toJson(find(after,wall.id))))return "A disjoint volume would change.";
        PhysicsShapesComponent shapes=world.getMapper(PhysicsShapesComponent.class).getSafe(selected.entity,null);
        if(shapes!=null && shapes.shapes.size>0) {
            games.pixscape.studio.configuration.ProjectConfig config=games.pixscape.studio.configuration.ProjectConfig.getInstance();
            if(config==null || config.getCurrentSceneMeta()==null)return "Scene physics metadata is unavailable.";
            PhysicsService.prepareBodyCandidate(world,selected.entity,shapes.shapes,config.getCurrentSceneMeta().pixelsPerMeter,selected.map,after);
        }
        defaultHeight=height;return null;
    }
    /** Affine inverse of the production map conversion; altitude is never inferred from image size. */
    public static float[] rectangleIn(TiledMapLayerData reference,SpatialBlockData wall,TiledMapLayerData owner) {
        float[] low=new float[3],high=new float[3],origin=new float[3],x=new float[3],y=new float[3];
        LightingCoordinates.mapPoint(reference,wall.x,wall.y,wall.altitude,low,0);
        LightingCoordinates.mapPoint(reference,wall.x+wall.width,wall.y+wall.depth,wall.altitude,high,0);
        LightingCoordinates.mapPoint(owner,0,0,0,origin,0);LightingCoordinates.mapPoint(owner,1,0,0,x,0);LightingCoordinates.mapPoint(owner,0,1,0,y,0);
        float ax=x[0]-origin[0],ay=x[1]-origin[1],bx=y[0]-origin[0],by=y[1]-origin[1],det=ax*by-ay*bx;
        if(!Float.isFinite(det) || Math.abs(det)<.000001f)throw new IllegalArgumentException("The map's common spatial basis is singular.");
        float lx=((low[0]-origin[0])*by-(low[1]-origin[1])*bx)/det;
        float ly=((low[1]-origin[1])*ax-(low[0]-origin[0])*ay)/det;
        float hx=((high[0]-origin[0])*by-(high[1]-origin[1])*bx)/det;
        float hy=((high[1]-origin[1])*ax-(high[0]-origin[0])*ay)/det;
        return new float[]{lx,ly,hx-lx,hy-ly};
    }
    public String describe() {
        StringBuilder text=new StringBuilder("Fixed reference: ").append(candidate).append("\nReference ensemble: ").append(referenceMembers)
                .append("\nAdapted ensemble: ").append(affected).append("\n");
        if(!valid)text.append("Conflict: ").append(diagnostic).append("\n");
        for(int id:affected) {
            SpatialBlockData a=find(selected.blocks,id),b=find(after,id);
            text.append("Volume ").append(id).append(": base ").append(a.altitude).append(" -> ").append(b.altitude)
                .append("; top ").append(a.altitude+a.height).append(" -> ").append(b.altitude+b.height)
                .append("; height ").append(a.height).append(" -> ").append(b.height)
                .append("; thickness ").append(horizontal(a)?a.depth:a.width).append(" -> ").append(horizontal(b)?b.depth:b.width)
                .append("; footprint [").append(a.x).append(',').append(a.y).append(',').append(a.width).append(',').append(a.depth)
                .append("] -> [").append(b.x).append(',').append(b.y).append(',').append(b.width).append(',').append(b.depth).append("]\n");
        }
        return text.append("Default height: ").append(selected.defaultHeight).append(" -> ").append(defaultHeight).toString();
    }
    private static boolean ambiguousOrientation(SpatialBlockData wall) {
        SpatialWallGeometry.LinkedCellBounds region=new SpatialWallGeometry.LinkedCellBounds();
        return Math.abs(wall.width-wall.depth)<=SpatialWallGeometry.GEOMETRY_EPSILON
                && (!SpatialWallGeometry.extractLinkedCellBounds(wall,region)
                || region.maxGxExclusive-region.minGx==region.maxGyExclusive-region.minGy);
    }
    private static boolean horizontal(SpatialBlockData wall) {return SpatialWallAttachments.determineAxis(wall)==SpatialWallAttachments.Axis.HORIZONTAL;}
    public static SpatialBlockData find(SpatialBlocksComponent blocks,int id) {for(SpatialBlockData wall:blocks.blocks)if(wall.id==id)return wall;return null;}
    private static float[] parallax(World world,int map) {
        EntityIndexComponent index=world.getMapper(EntityIndexComponent.class).getSafe(map,null);
        int layer=index!=null?index.layerIndex:0;
        com.artemis.utils.IntBag layers=world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class)).getEntities();
        for(int i=0;i<layers.size();i++)if(world.getMapper(LayerComponent.class).get(layers.get(i)).layerIndex==layer) {
            LayerParallaxComponent p=world.getMapper(LayerParallaxComponent.class).getSafe(layers.get(i),null);
            return new float[]{p!=null?p.factorX:1,p!=null?p.factorY:1};
        }
        return new float[]{1,1};
    }
}
