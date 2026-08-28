package games.pixscape.studio.service.entitygraph;

import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;

/** Authored-Physics queries for immutable entity graphs. */
public final class EntityGraphPhysicsSupport {

    private EntityGraphPhysicsSupport() {
    }

    public static boolean containsAuthoredPhysics(EntityGraph graph) {
        if (graph == null || graph.isEmpty()) return false;

        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntitySnapshotData snapshot =
                    entry.initializer().toSnapshotData(entry.sourceEntityId());
            if (snapshot.hasPhysicsBody
                    || (snapshot.shapes != null && snapshot.shapes.size > 0)
                    || snapshot.hasJoint
                    || snapshot.hasDistanceJoint
                    || snapshot.hasRevoluteJoint
                    || snapshot.hasPrismaticJoint
                    || snapshot.hasWheelJoint
                    || snapshot.hasFrictionJoint
                    || snapshot.hasMotorJoint
                    || snapshot.hasWeldJoint
                    || snapshot.hasPulleyJoint
                    || snapshot.hasGearJoint) {
                return true;
            }
        }
        return false;
    }
}
