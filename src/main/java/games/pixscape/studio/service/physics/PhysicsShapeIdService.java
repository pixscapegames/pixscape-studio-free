package games.pixscape.studio.service.physics;

import games.pixscape.runtime.service.PhysicsService;

/**
 * Studio access point to the active scene's authoritative physics shape allocator.
 */
public final class PhysicsShapeIdService {
    private PhysicsShapeIdService() {
    }

    public static int allocateNewPhysicsShapeId(PhysicsService physicsService) {
        if (physicsService == null) {
            throw new IllegalStateException(
                    "The active scene physics service is required to allocate physicsShapeId.");
        }
        return physicsService.allocateNewPhysicsShapeId();
    }
}
