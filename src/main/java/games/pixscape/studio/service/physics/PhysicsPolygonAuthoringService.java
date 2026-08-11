package games.pixscape.studio.service.physics;

import com.artemis.World;
import games.pixscape.runtime.physics.PolygonBuildResult;
import games.pixscape.runtime.physics.PolygonDecomposer;

/** Shared polygon-source validation used by Studio authoring tools. */
public final class PhysicsPolygonAuthoringService {
    public PhysicsPolygonAuthoringService(World world) {
        if (world == null) {
            throw new IllegalArgumentException("world cannot be null");
        }
    }

    public PolygonBuildResult buildFromSource(float[] vertices, int vertexCount) {
        return PolygonDecomposer.build(vertices, vertexCount);
    }
}
