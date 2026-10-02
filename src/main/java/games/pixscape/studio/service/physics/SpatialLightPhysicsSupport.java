package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;

/** Rules for the internal physics representation of Spatial lights. */
public final class SpatialLightPhysicsSupport {
    public static final float FIXED_FOOTPRINT_RADIUS_M = 0.05f;

    private SpatialLightPhysicsSupport() {}

    public static boolean isTechnicalFixture(PhysicsShapesComponent shapes, int shapeId) {
        if (shapes == null) return false;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.physicsShapeId == shapeId) {
                return shape.technicalSpatialLight;
            }
        }
        return false;
    }

    public static boolean hasOnlyTechnicalBody(World world, int entityId) {
        if (world == null || entityId < 0) return false;
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class)
                .getSafe(entityId, null);
        if (body == null || !body.technicalSpatialLight) return false;
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                .getSafe(entityId, null);
        if (shapes == null || shapes.shapes.size == 0) return false;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape == null || !shape.technicalSpatialLight) return false;
        }
        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        for (int i = 0; i < joints.size(); i++) {
            PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class)
                    .get(joints.get(i));
            if (joint.aEid == entityId || joint.bEid == entityId) return false;
        }
        return true;
    }
}
