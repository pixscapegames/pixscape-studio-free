package games.pixscape.studio.service;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;

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

}
