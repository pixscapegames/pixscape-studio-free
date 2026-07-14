package games.pixscape.studio.service.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialWallGeometry;

/** Deterministic precise-thickness inheritance for a newly connected authored wall. */
public final class SpatialWallThicknessInheritance {
    public static final class Result {
        public final boolean valid;
        public final String error;
        public final SpatialBlockData wall;

        private Result(boolean valid, String error, SpatialBlockData wall) {
            this.valid = valid;
            this.error = error;
            this.wall = wall;
        }
    }

    private SpatialWallThicknessInheritance() {
    }

    public static Result apply(SpatialBlockData source, SpatialBlocksComponent existing) {
        return apply(source, existing, null);
    }

    public static Result apply(SpatialBlockData source,
                               SpatialBlocksComponent existing,
                               SpatialWallAttachments.Axis gestureAxis) {
        if (source == null) return new Result(false, "New wall candidate is missing.", null);
        SpatialBlockData candidate = source.copy();
        if (existing == null || existing.blocks == null || existing.blocks.size == 0) {
            return new Result(true, null, candidate);
        }

        Array<SpatialBlockData> touched = touchingNeighbors(candidate, existing);
        if (touched.size == 0) return new Result(true, null, candidate);
        sortById(touched);

        float inherited = thickness(touched.first());
        for (int i = 1; i < touched.size; i++) {
            if (Math.abs(thickness(touched.get(i)) - inherited) > SpatialWallGeometry.GEOMETRY_EPSILON) {
                return new Result(false,
                        "Connected neighbors have incompatible precise wall thicknesses.", null);
            }
        }

        SpatialBlockData canonical = touched.first();
        SpatialWallAttachments.Axis neighborAxis = SpatialWallAttachments.determineAxis(canonical);
        SpatialWallGeometry.LinkedCellBounds linked = new SpatialWallGeometry.LinkedCellBounds();
        if (!SpatialWallGeometry.extractLinkedCellBounds(candidate, linked)) {
            return new Result(false, "New wall linked-cell rectangle is invalid.", null);
        }
        int spanX = linked.maxGxExclusive - linked.minGx;
        int spanY = linked.maxGyExclusive - linked.minGy;
        SpatialWallAttachments.Axis candidateAxis;
        if (spanX > spanY) candidateAxis = SpatialWallAttachments.Axis.HORIZONTAL;
        else if (spanY > spanX) candidateAxis = SpatialWallAttachments.Axis.VERTICAL;
        else if (gestureAxis != null) candidateAxis = gestureAxis;
        else if (touched.size == 1) candidateAxis = neighborAxis == SpatialWallAttachments.Axis.HORIZONTAL
                ? SpatialWallAttachments.Axis.VERTICAL : SpatialWallAttachments.Axis.HORIZONTAL;
        else candidateAxis = SpatialWallAttachments.Axis.HORIZONTAL;
        if (candidateAxis == SpatialWallAttachments.Axis.HORIZONTAL) {
            candidate.depth = inherited;
            float preferred = neighborAxis == candidateAxis ? canonical.y
                    : candidate.y + (source.depth - inherited) * 0.5f;
            candidate.y = clamp(preferred, linked.minGy, linked.maxGyExclusive - inherited);
        } else {
            candidate.width = inherited;
            float preferred = neighborAxis == candidateAxis ? canonical.x
                    : candidate.x + (source.width - inherited) * 0.5f;
            candidate.x = clamp(preferred, linked.minGx, linked.maxGxExclusive - inherited);
        }

        SpatialWallGeometry.Bounds a = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Bounds b = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();
        for (int i = 0; i < touched.size; i++) {
            if (SpatialWallGeometry.classifyJunction(candidate, touched.get(i), a, b, junction)
                    != SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION) {
                return new Result(false,
                        "Inherited thickness cannot preserve every candidate wall junction.", null);
            }
        }
        return new Result(true, null, candidate);
    }

    private static Array<SpatialBlockData> touchingNeighbors(SpatialBlockData candidate,
                                                              SpatialBlocksComponent existing) {
        Array<SpatialBlockData> result = new Array<>(SpatialBlockData[]::new);
        SpatialWallGeometry.Bounds a = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Bounds b = new SpatialWallGeometry.Bounds();
        SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();
        for (int i = 0; i < existing.blocks.size; i++) {
            SpatialBlockData wall = existing.blocks.get(i);
            if (wall != null && SpatialWallGeometry.classifyJunction(candidate, wall, a, b, junction)
                    == SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION) {
                result.add(wall);
            }
        }
        return result;
    }

    private static float thickness(SpatialBlockData wall) {
        return SpatialWallAttachments.determineAxis(wall) == SpatialWallAttachments.Axis.HORIZONTAL
                ? wall.depth : wall.width;
    }

    private static void sortById(Array<SpatialBlockData> walls) {
        for (int i = 1; i < walls.size; i++) {
            SpatialBlockData value = walls.get(i);
            int j = i - 1;
            while (j >= 0 && walls.get(j).id > value.id) {
                walls.set(j + 1, walls.get(j));
                j--;
            }
            walls.set(j + 1, value);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
