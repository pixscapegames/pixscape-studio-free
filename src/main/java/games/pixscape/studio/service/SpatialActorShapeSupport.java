package games.pixscape.studio.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.PhysicsShapeData;

/** Shared authored-shape rule for disabling Spatial Actor state. */
public final class SpatialActorShapeSupport {
    private SpatialActorShapeSupport() {
    }

    /**
     * Removes the single Spatial Actor footprint, if present.
     *
     * @return false when the input contains contradictory multiple footprints
     */
    public static boolean removeFootprint(Array<PhysicsShapeData> shapes) {
        int markedIndex = findFootprint(shapes, 0);
        if (markedIndex < 0) return true;
        if (findFootprint(shapes, markedIndex + 1) >= 0) return false;
        shapes.removeIndex(markedIndex);
        return true;
    }

    public static int findFootprint(Array<PhysicsShapeData> shapes, int start) {
        for (int i = start; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.spatialFootprint) return i;
        }
        return -1;
    }
}
