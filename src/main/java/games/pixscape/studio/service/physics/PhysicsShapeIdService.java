package games.pixscape.studio.service.physics;

import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;

/**
 * Studio access point to the active scene's authoritative physics shape allocator.
 */
public final class PhysicsShapeIdService {
    private PhysicsShapeIdService() {
    }

    public static int allocateNewPhysicsShapeId() {
        SceneMeta sceneMeta = currentSceneMeta();
        return new PhysicsShapeIdAllocator(sceneMeta).allocateNewPhysicsShapeId();
    }

    public static PhysicsShapeIdAllocator currentAllocator() {
        return new PhysicsShapeIdAllocator(currentSceneMeta());
    }

    private static SceneMeta currentSceneMeta() {
        ProjectConfig config = ProjectConfig.getInstance();
        SceneMeta sceneMeta = config != null ? config.getCurrentSceneMeta() : null;
        if (sceneMeta == null) {
            throw new IllegalStateException(
                    "An active scene is required to allocate physicsShapeId.");
        }
        return sceneMeta;
    }
}
