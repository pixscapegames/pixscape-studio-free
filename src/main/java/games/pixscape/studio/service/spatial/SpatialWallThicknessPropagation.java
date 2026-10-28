package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialBlockLink;
import games.pixscape.runtime.spatial.SpatialBlockLinks;
import games.pixscape.runtime.spatial.SpatialWallGeometry;

import java.util.*;

/** Cold rectangular-wall adjustment: parallel bands and attached perpendicular endpoints. */
public final class SpatialWallThicknessPropagation {
    private SpatialWallThicknessPropagation() { }

    public static String apply(Array<SpatialBlockData> before, Array<SpatialBlockData> after,
                               Array<SpatialBlockLink> links, int seedId) {
        return apply(before, after, links, seedId, prepare(before, links, seedId));
    }

    /** Prepared once at gesture activation; no connectivity discovery in mouse updates. */
    public static final class Prepared {
        final java.util.Set<Long> contacts = new java.util.HashSet<>();
        final java.util.List<int[]> nearby = new java.util.ArrayList<>();
        final com.badlogic.gdx.utils.IntIntMap positions = new com.badlogic.gdx.utils.IntIntMap();
        final java.util.List<int[]> parallel = new java.util.ArrayList<>();
        final java.util.List<int[]> edges = new java.util.ArrayList<>();
        final IntMap<Array<SpatialBlockData>> neighbors = new IntMap<>();
        boolean connected(int a,int b) { return contacts.contains(key(a,b)); }
    }
    private static long key(int a,int b) { return ((long)Math.min(a,b)<<32) | (Math.max(a,b)&0xffffffffL); }
    public static Prepared prepare(Array<SpatialBlockData> before, Array<SpatialBlockLink> links, int seedId) {
        Prepared prepared=new Prepared(); IntMap<SpatialBlockData> index=index(before);
        for(int i=0;i<before.size;i++) for(int j=i+1;j<before.size;j++) {
            SpatialBlockData a=before.get(i),b=before.get(j);
            if(connected(a,b,links)) {
                prepared.contacts.add(key(a.id,b.id));prepared.edges.add(new int[]{i,j});
                if(!prepared.neighbors.containsKey(a.id))prepared.neighbors.put(a.id,new Array<>(SpatialBlockData.class));
                if(!prepared.neighbors.containsKey(b.id))prepared.neighbors.put(b.id,new Array<>(SpatialBlockData.class));
                prepared.neighbors.get(a.id).add(b);prepared.neighbors.get(b.id).add(a);
            }
        }
        SpatialBlockData seed=index.get(seedId); if(seed==null)return prepared;
        for(int i=0;i<before.size;i++)prepared.positions.put(before.get(i).id,i);
        for(int i=0;i<before.size;i++)for(int j=i+1;j<before.size;j++) {
            SpatialBlockData a=before.get(i),b=before.get(j);
            if(a.structureId!=seed.structureId && b.structureId!=seed.structureId)continue;
            SpatialWallGeometry.LinkedCellBounds x=new SpatialWallGeometry.LinkedCellBounds(),y=new SpatialWallGeometry.LinkedCellBounds();
            SpatialWallGeometry.extractLinkedCellBounds(a,x);SpatialWallGeometry.extractLinkedCellBounds(b,y);
            if(x.maxGxExclusive>=y.minGx && y.maxGxExclusive>=x.minGx && x.maxGyExclusive>=y.minGy && y.maxGyExclusive>=x.minGy)
                prepared.nearby.add(new int[]{i,j});
        }
        Set<Integer> visited=new HashSet<>();visited.add(seedId);
        ArrayDeque<Integer> queue=new ArrayDeque<>();queue.add(seedId);
        while(!queue.isEmpty()) {
            int a=queue.remove();
            for(SpatialBlockData b:before) if(!visited.contains(b.id) && b.structureId==seed.structureId
                    && horizontal(b)==horizontal(seed) && prepared.connected(a,b.id)) {
                prepared.parallel.add(new int[]{a,b.id});visited.add(b.id);queue.add(b.id);
            }
        }
        return prepared;
    }

    public static String apply(Array<SpatialBlockData> before, Array<SpatialBlockData> after,
                               Array<SpatialBlockLink> links, int seedId, Prepared prepared) {
        IntMap<SpatialBlockData> old = index(before), next = index(after);
        SpatialBlockData seed = old.get(seedId), changedSeed = next.get(seedId);
        if (seed == null || changedSeed == null) return "Missing thickness propagation seed.";
        boolean axis = horizontal(seed);
        float thickness = axis ? changedSeed.depth : changedSeed.width;
        if (horizontal(changedSeed) != axis) {
            for (SpatialBlockData wall : before) if (wall.id != seedId && prepared.connected(seed.id, wall.id))
                return "Thickness adjustment would change the orientation of connected volume " + seedId + ".";
        }
        for (int[] edge : prepared.parallel) {
            SpatialBlockData a = old.get(edge[0]), b = old.get(edge[1]), changed = next.get(a.id);
            float aMin=transverse(a), bMin=transverse(b), aMax=aMin+thickness(a), bMax=bMin+thickness(b);
            float newMin;
            if(near(aMin,bMin) || near(aMax,bMax)) newMin=transverse(changed);
            else if(near(aMax,bMin)) newMin=transverse(changed)+thickness;
            else if(near(aMin,bMax)) newMin=transverse(changed)-thickness;
            else return "Parallel contact bands are ambiguous at volumes " + a.id + " / " + b.id + ".";
            SpatialBlockData target=next.get(b.id);
            if(axis) {target.y=newMin;target.depth=thickness;} else {target.x=newMin;target.width=thickness;}
            if(horizontal(target)!=axis) return "Thickness adjustment changes a connected wall orientation.";
        }
        Map<String,Float> endpoints = new HashMap<>();
        for (int ai = 0; ai < before.size; ai++) {
            SpatialBlockData a = before.get(ai);
            if (a.structureId != seed.structureId) continue;
            SpatialBlockData na = next.get(a.id);
            int min = horizontal(a) ? SpatialBlockLink.MIN_X : SpatialBlockLink.MIN_Y;
            int max = SpatialBlockLinks.opposite(min);
            Array<SpatialBlockData> neighbors=prepared.neighbors.get(a.id);
            for (int bi = 0; neighbors!=null && bi < neighbors.size; bi++) {
                SpatialBlockData b = neighbors.get(bi);
                if (horizontal(a) == horizontal(b)) continue;
                SpatialBlockData nb = next.get(b.id);
                for (int endpoint : new int[]{min,max}) for (int side : new int[]{min,max}) {
                    if (!near(SpatialBlockLinks.coordinate(a,endpoint),SpatialBlockLinks.coordinate(b,side))) continue;
                    float value = SpatialBlockLinks.coordinate(nb,side);
                    String key = a.id + ":" + endpoint;
                    Float previous = endpoints.put(key,value);
                    if (previous != null && !near(previous,value)) return "Conflicting corner constraints on volume " + a.id + ".";
                }
            }
            float low = endpoints.getOrDefault(a.id + ":" + min,SpatialBlockLinks.coordinate(na,min));
            float high = endpoints.getOrDefault(a.id + ":" + max,SpatialBlockLinks.coordinate(na,max));
            if (!near(low, SpatialBlockLinks.coordinate(na,min)) || !near(high, SpatialBlockLinks.coordinate(na,max))) {
                if (horizontal(a)) { na.x = low; na.width = high-low; }
                else { na.y = low; na.depth = high-low; }
            }
        }
        for (int[] edge:prepared.edges) {
            SpatialBlockData a=before.get(edge[0]), b=before.get(edge[1]), na=next.get(a.id), nb=next.get(b.id);
            if (!connected(na,nb,links)) return "A previous junction would be broken: " + a.id + " / " + b.id + ".";
            for (int axisFace : new int[]{1,4}) for (int f : new int[]{axisFace,axisFace*2}) for (int g : new int[]{axisFace,axisFace*2}) {
                if (near(SpatialBlockLinks.coordinate(a,f),SpatialBlockLinks.coordinate(b,g))
                        && !near(SpatialBlockLinks.coordinate(na,f),SpatialBlockLinks.coordinate(nb,g)))
                    return "Corner faces would lose alignment: " + a.id + " / " + b.id + ".";
            }
        }
        return null;
    }

    private static IntMap<SpatialBlockData> index(Array<SpatialBlockData> walls) { IntMap<SpatialBlockData> index=new IntMap<>(); for(SpatialBlockData wall:walls)index.put(wall.id,wall); return index; }
    private static boolean connected(SpatialBlockData a,SpatialBlockData b,Array<SpatialBlockLink> links) {
        if(SpatialBlockLinks.overlap(a,b))return true;
        for(SpatialBlockLink link:links)if(link.joins(a.id,b.id))return SpatialBlockLinks.contact(a,b,link.firstBlockId==a.id?link.firstFace:link.secondFace,link.firstBlockId==a.id?link.secondFace:link.firstFace);
        return false;
    }
    private static boolean horizontal(SpatialBlockData wall) { return SpatialWallAttachments.determineAxis(wall)==SpatialWallAttachments.Axis.HORIZONTAL; }
    private static float thickness(SpatialBlockData wall) { return horizontal(wall)?wall.depth:wall.width; }
    private static float transverse(SpatialBlockData wall) { return horizontal(wall)?wall.y:wall.x; }
    private static boolean near(float a,float b) { return SpatialBlockLinks.near(a,b); }
}
