package games.pixscape.studio.service;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.GameObjectCompositionSystem;

/** Published footprint ownership shared by authoring consumers. */
public final class SpatialActorShapeSupport {
    private SpatialActorShapeSupport() {
    }

    public static int owner(World world, int entityId) {
        if (world == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        GameObjectCompositionSystem composition = world.getSystem(GameObjectCompositionSystem.class);
        return composition == null ? world.getMapper(SpatialFootprintComponent.class).has(entityId) ? entityId : -1
                : composition.spatialOwner(entityId);
    }

    public static boolean hasActorState(World world, int entityId) {
        return owner(world, entityId) >= 0;
    }
}
