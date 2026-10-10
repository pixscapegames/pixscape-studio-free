package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import java.util.*;

/** One detached, cold menu proposal. Reference geometry is never part of the solver variables. */
public final class SpatialLinkProposal {
    public final SpatialBlocksComponent before;
    public final SpatialBlocksComponent after;
    public final int selectedId, referenceId;
    public final boolean unlink;
    public final boolean valid;
    public final String diagnostic;
    public final Set<Integer> affected = new LinkedHashSet<>();
    public final Set<Integer> reference = new LinkedHashSet<>();
    public float defaultHeight;
    public final float beforeDefaultHeight;
    private final TiledMapLayerData map;

    private SpatialLinkProposal(SpatialBlocksComponent source, TiledMapLayerData map, int selectedId, int referenceId, boolean unlink, float defaultHeight) {
        before = copy(source);
        after = copy(source);
        this.selectedId = selectedId;
        this.referenceId = referenceId;
        this.unlink = unlink;
        this.defaultHeight = defaultHeight;
        this.beforeDefaultHeight = defaultHeight;
        this.map = map;
        String error;
        try { error = solve(); }
        catch (IllegalArgumentException failure) { error = failure.getMessage(); }
        valid = error == null;
        diagnostic = error;
    }

    public static SpatialLinkProposal create(SpatialBlocksComponent source, TiledMapLayerData map,
                                             int selected, int reference, boolean unlink, float defaultHeight) {
        SpatialLinkProposal proposal = new SpatialLinkProposal(source, map, selected, reference, unlink, defaultHeight);
        if (!proposal.valid) return proposal;
        SpatialStructureCompilation.Result compilation = SpatialStructureCompilation.tryCompile(proposal.after, map);
        if (!compilation.success()) return proposal.failed(compilation.diagnostic());
        return proposal;
    }

    private SpatialLinkProposal(SpatialLinkProposal proposal, String error) {
        before = proposal.before; after = proposal.after;
        selectedId = proposal.selectedId; referenceId = proposal.referenceId; unlink = proposal.unlink;
        defaultHeight = proposal.defaultHeight; affected.addAll(proposal.affected); reference.addAll(proposal.reference);
        beforeDefaultHeight = proposal.beforeDefaultHeight; map = proposal.map;
        valid = false; diagnostic = error;
    }

    private SpatialLinkProposal failed(String error) { return new SpatialLinkProposal(this, error); }

    public static SpatialBlocksComponent copy(SpatialBlocksComponent source) {
        SpatialBlocksComponent copy = new SpatialBlocksComponent();
        copy.blocks = SpatialStructureTopology.copyWalls(source);
        copy.links = source == null ? new Array<>(SpatialBlockLink.class) : SpatialBlockLinks.copy(source.links);
        copy.nextSpatialBlockId = source == null ? 1 : source.nextSpatialBlockId;
        return copy;
    }

    private String solve() {
        IntMap<SpatialBlockData> old = index(before), next = index(after);
        SpatialBlockData selected = old.get(selectedId), fixed = old.get(referenceId);
        if (selected == null || fixed == null || selectedId == referenceId) return "Select two volumes in the same map.";
        SpatialBlockLinks.validate(before.blocks, before.links);
        for (SpatialBlockData wall : before.blocks) {
            if (wall.structureId == selected.structureId) affected.add(wall.id);
            if (wall.structureId == fixed.structureId) reference.add(wall.id);
        }
        if (unlink) {
            int edge = edgeIndex(after.links, selectedId, referenceId);
            if (edge < 0) return "There is no voluntary link between these volumes.";
            after.links.removeIndex(edge);
            return normalize(null);
        }
        if (edgeIndex(after.links, selectedId, referenceId) >= 0) return "These volumes already have a voluntary link.";
        if (Float.compare(selected.altitude, fixed.altitude) != 0) return "Different base altitudes: Lier does not change altitude.";
        int face = SpatialBlockLinks.contactFace(selected, fixed);
        if (face < 0 || face == 0 && !SpatialBlockLinks.overlap(selected, fixed)) return "No unambiguous lateral contact between these volumes.";
        if (square(selected) || square(fixed)) return "Square volumes have no unambiguous wall orientation.";
        boolean sameEnsemble = selected.structureId == fixed.structureId;
        if (!sameEnsemble) {
            for (Integer id : affected) next.get(id).height = fixed.height;
            if (Float.compare(selected.height, fixed.height) != 0) defaultHeight = fixed.height;
            if (horizontal(selected) == horizontal(fixed)) {
                float thickness = thickness(fixed);
                SpatialBlockData seed = next.get(selectedId);
                float origin = transverse(fixed);
                int minFace = horizontal(selected) ? SpatialBlockLink.MIN_Y : SpatialBlockLink.MIN_X;
                int maxFace = SpatialBlockLinks.opposite(minFace);
                if (face == minFace) origin += thickness;
                else if (face == maxFace) origin -= thickness;
                setBand(seed, origin, thickness);
                String error = SpatialWallThicknessPropagation.apply(before.blocks, after.blocks, before.links, selectedId);
                if (error != null) return error;
                for (Integer id : affected) {
                    SpatialBlockData previous = old.get(id), adapted = next.get(id);
                    if ((Float.compare(previous.width, adapted.width) != 0 || Float.compare(previous.depth, adapted.depth) != 0)
                            && (square(adapted) || horizontal(previous) != horizontal(adapted)))
                        return "Thickness adaptation would change or make ambiguous the orientation of volume " + id + ".";
                }
            }
        } else if (horizontal(selected) == horizontal(fixed) && !near(thickness(selected), thickness(fixed))) {
            return "Both volumes already belong to the fixed ensemble; thickness adaptation would modify the reference.";
        }
        after.links.add(new SpatialBlockLink(selectedId, referenceId, face, SpatialBlockLinks.opposite(face)));
        String normalized = normalize(fixed.structureId);
        if (normalized != null) return normalized;
        // Recheck all original internal contacts, their coincident corner faces, and accidental overlaps.
        for (int i = 0; i < before.blocks.size; i++) for (int j = i + 1; j < before.blocks.size; j++) {
            SpatialBlockData a = before.blocks.get(i), b = before.blocks.get(j), na = next.get(a.id), nb = next.get(b.id);
            boolean connectedBefore = connected(a, b, before.links);
            if (connectedBefore && !connected(na, nb, after.links)) return "A previous junction would be broken: " + a.id + " / " + b.id + ".";
            if (connectedBefore && !cornersPreserved(a, b, na, nb)) return "Corner faces would lose alignment: " + a.id + " / " + b.id + ".";
            if (a.structureId != b.structureId && !((affected.contains(a.id) && reference.contains(b.id))
                    || (affected.contains(b.id) && reference.contains(a.id))) && !SpatialBlockLinks.overlap(a, b)
                    && SpatialBlockLinks.overlap(na, nb)) return "Adaptation would connect another ensemble: " + a.id + " / " + b.id + ".";
        }
        return null;
    }

    private String normalize(Integer referenceStructure) {
        SpatialStructureTopology.Plan normalized = SpatialStructureTopology.normalize(before.blocks, after.blocks, after.links, map);
        if (!normalized.valid) return normalized.error;
        after.blocks = normalized.walls;
        if (referenceStructure != null) {
            SpatialBlockData seed = index(after).get(referenceId);
            int merged = seed.structureId;
            for (SpatialBlockData wall : after.blocks) if (wall.structureId == merged) wall.structureId = referenceStructure;
            SpatialStructureTopology.Plan validation = SpatialStructureTopology.validate(after, map);
            if (!validation.valid) return validation.error;
        }
        return null;
    }

    private static boolean cornersPreserved(SpatialBlockData a, SpatialBlockData b, SpatialBlockData na, SpatialBlockData nb) {
        for (int axis = 1; axis <= 4; axis *= 4) for (int f : new int[]{axis, axis * 2}) for (int g : new int[]{axis, axis * 2})
            if (near(SpatialBlockLinks.coordinate(a, f), SpatialBlockLinks.coordinate(b, g))
                    && !near(SpatialBlockLinks.coordinate(na, f), SpatialBlockLinks.coordinate(nb, g))) return false;
        return true;
    }

    public static int edgeIndex(Array<SpatialBlockLink> links, int a, int b) {
        if (links != null) for (int i = 0; i < links.size; i++) if (links.get(i).joins(a, b)) return i;
        return -1;
    }

    private static IntMap<SpatialBlockData> index(SpatialBlocksComponent component) {
        IntMap<SpatialBlockData> result = new IntMap<>();
        for (SpatialBlockData wall : component.blocks) result.put(wall.id, wall);
        return result;
    }
    private static boolean connected(SpatialBlockData a, SpatialBlockData b, Array<SpatialBlockLink> links) {
        return SpatialBlockLinks.overlap(a, b) || edgeIndex(links, a.id, b.id) >= 0;
    }
    private static boolean horizontal(SpatialBlockData wall) { return SpatialWallAttachments.determineAxis(wall) == SpatialWallAttachments.Axis.HORIZONTAL; }
    private static boolean square(SpatialBlockData wall) { return near(wall.width, wall.depth); }
    private static float thickness(SpatialBlockData wall) { return horizontal(wall) ? wall.depth : wall.width; }
    private static float transverse(SpatialBlockData wall) { return horizontal(wall) ? wall.y : wall.x; }
    private static void setBand(SpatialBlockData wall, float origin, float thickness) {
        if (horizontal(wall)) { wall.y = origin; wall.depth = thickness; }
        else { wall.x = origin; wall.width = thickness; }
    }
    private static boolean near(float a, float b) { return SpatialBlockLinks.near(a, b); }
}
