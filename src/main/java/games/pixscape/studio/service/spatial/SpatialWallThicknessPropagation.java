package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.spatial.*;
import java.util.*;

/** Cold rectangular-wall adjustment: parallel bands and attached perpendicular endpoints. */
public final class SpatialWallThicknessPropagation {
    private SpatialWallThicknessPropagation() { }

    public static String apply(Array<SpatialBlockData> before, Array<SpatialBlockData> after,
                               Array<SpatialBlockLink> links, int seedId) {
        IntMap<SpatialBlockData> old = index(before), next = index(after);
        SpatialBlockData seed = old.get(seedId), changedSeed = next.get(seedId);
        if (seed == null || changedSeed == null) return "Missing thickness propagation seed.";
        boolean axis = horizontal(seed);
        float thickness = axis ? changedSeed.depth : changedSeed.width;
        if (horizontal(changedSeed) != axis) {
            for (SpatialBlockData wall : before) if (wall.id != seedId && connected(seed, wall, links))
                return "Thickness adjustment would change the orientation of connected volume " + seedId + ".";
        }
        Set<Integer> visited = new HashSet<>(); visited.add(seedId);
        ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(seedId);
        while (!queue.isEmpty()) {
            int aId = queue.remove(); SpatialBlockData a = old.get(aId), changed = next.get(aId);
            for (SpatialBlockData b : before) {
                if (visited.contains(b.id) || b.structureId != seed.structureId || horizontal(b) != axis || !connected(a,b,links)) continue;
                float aMin = transverse(a), bMin = transverse(b), aMax = aMin + thickness(a), bMax = bMin + thickness(b);
                float newMin;
                if (near(aMin,bMin)) newMin = transverse(changed);
                else if (near(aMax,bMax)) newMin = transverse(changed);
                else if (near(aMax,bMin)) newMin = transverse(changed) + thickness;
                else if (near(aMin,bMax)) newMin = transverse(changed) - thickness;
                else return "Parallel contact bands are ambiguous at volumes " + aId + " / " + b.id + ".";
                SpatialBlockData target = next.get(b.id);
                if (axis) { target.y = newMin; target.depth = thickness; }
                else { target.x = newMin; target.width = thickness; }
                if (horizontal(target) != axis)
                    return "Thickness adjustment would change the orientation of connected volume " + b.id + ".";
                visited.add(b.id); queue.add(b.id);
            }
        }
        Map<String,Float> endpoints = new HashMap<>();
        for (int ai = 0; ai < before.size; ai++) {
            SpatialBlockData a = before.get(ai);
            if (a.structureId != seed.structureId) continue;
            SpatialBlockData na = next.get(a.id);
            int min = horizontal(a) ? SpatialBlockLink.MIN_X : SpatialBlockLink.MIN_Y;
            int max = SpatialBlockLinks.opposite(min);
            for (int bi = 0; bi < before.size; bi++) {
                SpatialBlockData b = before.get(bi);
                if (horizontal(a) == horizontal(b) || !connected(a,b,links)) continue;
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
        for (int i=0;i<before.size;i++) for (int j=i+1;j<before.size;j++) {
            SpatialBlockData a=before.get(i), b=before.get(j), na=next.get(a.id), nb=next.get(b.id);
            if (!connected(a,b,links)) continue;
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
