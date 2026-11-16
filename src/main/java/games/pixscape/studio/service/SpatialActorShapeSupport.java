package games.pixscape.studio.service;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;

/**
 * Shared authored Spatial Actor state and footprint rules.
 */
public final class SpatialActorShapeSupport {
    private SpatialActorShapeSupport() {
    }

    /** Inspector activation state, independent of current render eligibility or native body presence. */
    public static boolean hasActorState(World world, int entityId) {
        if (world == null || entityId < 0) {
            return false;
        }
        return world.getMapper(SpatialRepresentationComponent.class).has(entityId)
                || world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).has(entityId);
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
            if (shape != null && shape.useSpatialFootprint) {
                return i;
            }
        }
        return -1;
    }
}
