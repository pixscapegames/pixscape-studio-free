package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialWallGeometry;

/** Derived, non-serialized junction constraints for one committed authored wall. */
public final class SpatialWallAttachments {
    public enum Axis { HORIZONTAL, VERTICAL }

    public static final int MIN_X = 1;
    public static final int MAX_X = 2;
    public static final int MIN_Y = 4;
    public static final int MAX_Y = 8;

    public static final class Attachment {
        public final int neighborBlockId;
        public final float minX;
        public final float maxX;
        public final float minY;
        public final float maxY;
        public final int selectedSides;
        public final boolean longitudinalEnd;
        public final boolean sideJunction;
        public final float minimumOverlap;

        private Attachment(int neighborBlockId,
                           SpatialWallGeometry.Junction junction,
                           int selectedSides,
                           boolean longitudinalEnd,
                           boolean sideJunction) {
            this.neighborBlockId = neighborBlockId;
            this.minX = junction.minX;
            this.maxX = junction.maxX;
            this.minY = junction.minY;
            this.maxY = junction.maxY;
            this.selectedSides = selectedSides;
            this.longitudinalEnd = longitudinalEnd;
            this.sideJunction = sideJunction;
            this.minimumOverlap = SpatialWallGeometry.GEOMETRY_EPSILON;
        }
    }

    private final int wallId;
    private final Axis axis;
    private final Array<Attachment> attachments = new Array<>(Attachment[]::new);
    private boolean minLongitudinalLocked;
    private boolean maxLongitudinalLocked;
    private boolean slideAvailable;
    private Axis slideAxis;
    private float minimumSlideDelta;
    private float maximumSlideDelta;

    private SpatialWallAttachments(int wallId, Axis axis) {
        this.wallId = wallId;
        this.axis = axis;
    }

    public static SpatialWallAttachments derive(SpatialBlocksComponent component, SpatialBlockData selected) {
        SpatialWallAttachments result = new SpatialWallAttachments(
                selected != null ? selected.id : -1, determineAxis(selected));
        if (component == null || component.blocks == null || selected == null) return result;

        SpatialWallGeometry.Bounds selectedBounds = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Bounds neighborBounds = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();
        if (!SpatialWallGeometry.extractBounds(selected, selectedBounds)) return result;

        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData neighbor = component.blocks.get(i);
            if (neighbor == null || neighbor.id == selected.id) continue;
            SpatialWallGeometry.JunctionClassification classification = SpatialWallGeometry.classifyJunction(
                    selected, neighbor, selectedBounds, neighborBounds, junction);
            if (classification != SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION) continue;

            int sides = sidesAt(selectedBounds, junction);
            boolean minEnd = result.axis == Axis.HORIZONTAL
                    ? (sides & MIN_X) != 0 : (sides & MIN_Y) != 0;
            boolean maxEnd = result.axis == Axis.HORIZONTAL
                    ? (sides & MAX_X) != 0 : (sides & MAX_Y) != 0;
            boolean longitudinal = minEnd || maxEnd;
            if (minEnd) result.minLongitudinalLocked = true;
            if (maxEnd) result.maxLongitudinalLocked = true;
            result.attachments.add(new Attachment(
                    neighbor.id, junction, sides, longitudinal, !longitudinal));
        }
        result.sortByNeighborId();
        result.computeSlideConstraint(component, selected);
        return result;
    }

    public int wallId() { return wallId; }
    public Axis axis() { return axis; }
    public boolean isHorizontal() { return axis == Axis.HORIZONTAL; }
    public boolean isAttached() { return attachments.size > 0; }
    public boolean minLongitudinalLocked() { return minLongitudinalLocked; }
    public boolean maxLongitudinalLocked() { return maxLongitudinalLocked; }
    public int size() { return attachments.size; }
    public Attachment get(int index) { return attachments.get(index); }
    public boolean isSideJoined(int side) {
        for (int i = 0; i < attachments.size; i++) {
            if ((attachments.get(i).selectedSides & side) != 0) return true;
        }
        return false;
    }
    public boolean canSlide() { return slideAvailable; }
    public Axis slideAxis() { return slideAxis; }
    public float clampSlideDelta(float delta) {
        return Math.max(minimumSlideDelta, Math.min(maximumSlideDelta, delta));
    }
    public float minimumSlideDelta() { return minimumSlideDelta; }
    public float maximumSlideDelta() { return maximumSlideDelta; }

    public boolean preservesAll(SpatialBlockData candidate, SpatialBlocksComponent committed) {
        if (candidate == null || committed == null) return false;
        SpatialWallGeometry.Bounds candidateBounds = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Bounds neighborBounds = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();
        for (int i = 0; i < attachments.size; i++) {
            Attachment attachment = attachments.get(i);
            SpatialBlockData neighbor = find(committed, attachment.neighborBlockId);
            if (neighbor == null
                    || Float.compare(candidate.altitude, neighbor.altitude) != 0
                    || Float.compare(candidate.height, neighbor.height) != 0) return false;
            SpatialWallGeometry.JunctionClassification classification = SpatialWallGeometry.classifyJunction(
                    candidate, neighbor, candidateBounds, neighborBounds, junction);
            if (classification != SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION) return false;
            if (junction.maxX - junction.minX <= attachment.minimumOverlap
                    || junction.maxY - junction.minY <= attachment.minimumOverlap) return false;
        }
        return true;
    }

    public static Axis determineAxis(SpatialBlockData wall) {
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        if (wall != null && SpatialWallGeometry.extractLinkedCellBounds(wall, linked)) {
            int spanX = linked.maxGxExclusive - linked.minGx;
            int spanY = linked.maxGyExclusive - linked.minGy;
            if (spanX > spanY) return Axis.HORIZONTAL;
            if (spanY > spanX) return Axis.VERTICAL;
        }
        if (wall != null) {
            int precise = Float.compare(wall.width, wall.depth);
            if (precise > 0) return Axis.HORIZONTAL;
            if (precise < 0) return Axis.VERTICAL;
        }
        // Square linked and precise footprints use a stable horizontal tie rule.
        return Axis.HORIZONTAL;
    }

    private static int sidesAt(SpatialWallGeometry.Bounds selected, SpatialWallGeometry.Junction junction) {
        int result = 0;
        float epsilon = SpatialWallGeometry.GEOMETRY_EPSILON;
        if (Math.abs(junction.minX - selected.minX) <= epsilon) result |= MIN_X;
        if (Math.abs(junction.maxX - selected.maxX) <= epsilon) result |= MAX_X;
        if (Math.abs(junction.minY - selected.minY) <= epsilon) result |= MIN_Y;
        if (Math.abs(junction.maxY - selected.maxY) <= epsilon) result |= MAX_Y;
        return result;
    }

    private static SpatialBlockData find(SpatialBlocksComponent component, int id) {
        for (int i = 0; i < component.blocks.size; i++) {
            SpatialBlockData wall = component.blocks.get(i);
            if (wall != null && wall.id == id) return wall;
        }
        return null;
    }

    private void computeSlideConstraint(SpatialBlocksComponent component, SpatialBlockData selected) {
        if (attachments.size == 0 || component == null || selected == null) return;
        Axis commonAxis = null;
        float minDelta;
        float maxDelta;
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        if (!SpatialWallGeometry.extractLinkedCellBounds(selected, linked)) return;

        minDelta = Float.NEGATIVE_INFINITY;
        maxDelta = Float.POSITIVE_INFINITY;
        float margin = SpatialWallGeometry.GEOMETRY_EPSILON * 2f;
        for (int i = 0; i < attachments.size; i++) {
            SpatialBlockData neighbor = find(component, attachments.get(i).neighborBlockId);
            if (neighbor == null) return;
            Axis neighborAxis = determineAxis(neighbor);
            if (commonAxis == null) commonAxis = neighborAxis;
            else if (commonAxis != neighborAxis) return;

            if (commonAxis == Axis.HORIZONTAL) {
                minDelta = Math.max(minDelta, neighbor.x - (selected.x + selected.width) + margin);
                maxDelta = Math.min(maxDelta, neighbor.x + neighbor.width - selected.x - margin);
            } else {
                minDelta = Math.max(minDelta, neighbor.y - (selected.y + selected.depth) + margin);
                maxDelta = Math.min(maxDelta, neighbor.y + neighbor.depth - selected.y - margin);
            }
        }

        if (commonAxis == Axis.HORIZONTAL) {
            minDelta = Math.max(minDelta, linked.minGx - selected.x);
            maxDelta = Math.min(maxDelta, linked.maxGxExclusive - selected.x - selected.width);
        } else {
            minDelta = Math.max(minDelta, linked.minGy - selected.y);
            maxDelta = Math.min(maxDelta, linked.maxGyExclusive - selected.y - selected.depth);
        }
        if (minDelta > maxDelta) return;
        slideAxis = commonAxis;
        minimumSlideDelta = minDelta;
        maximumSlideDelta = maxDelta;
        slideAvailable = minDelta < -SpatialWallGeometry.GEOMETRY_EPSILON
                || maxDelta > SpatialWallGeometry.GEOMETRY_EPSILON;
    }

    private void sortByNeighborId() {
        for (int i = 1; i < attachments.size; i++) {
            Attachment value = attachments.get(i);
            int j = i - 1;
            while (j >= 0 && attachments.get(j).neighborBlockId > value.neighborBlockId) {
                attachments.set(j + 1, attachments.get(j));
                j--;
            }
            attachments.set(j + 1, value);
        }
    }
}
