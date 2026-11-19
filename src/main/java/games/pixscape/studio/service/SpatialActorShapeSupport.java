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

    /** Inspector/command boundary; the selected object's own override is deliberately ignored. */
    public static int ancestorOwner(World world, int entityId) {
        if (entityId < 0) {
            return -1;
        }
        var hierarchy = world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
        var composition = world.getSystem(GameObjectCompositionSystem.class);
        if (hierarchy != null && composition != null) {
            var topology = hierarchy.topology();
            int parent = entityId < topology.getEntityCapacity() ? topology.parentEntityId[entityId] : -1;
            return parent < 0 ? -1 : composition.spatialOwner(parent);
        }
        var identities = games.pixscape.runtime.service.IdentityRegistry.boundTo(world);
        var members = world.getMapper(games.pixscape.runtime.component.GameObjectMemberComponent.class);
        for (int depth = 0; depth < 1024; depth++) {
            var member = members.getSafe(entityId, null);
            entityId = member == null || identities == null ? -1 : identities.findByStableId(member.parentStableId);
            if (entityId < 0) {
                return -1;
            }
            if (world.getMapper(SpatialFootprintComponent.class).has(entityId)) {
                return entityId;
            }
        }
        throw new IllegalArgumentException("Spatial ancestry is cyclic or too deep.");
    }
}
