package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;

/** Rules for the internal physics representation of Spatial lights. */
public final class SpatialLightPhysicsSupport {
    public static final float FIXED_FOOTPRINT_RADIUS_M = 0.05f;

    private SpatialLightPhysicsSupport() {}

    /** Restores the authored technical geometry once when an existing scene is activated. */
    public static int restoreFixedFootprints(World world) {
        if (world == null) return 0;
        var bodies = world.getMapper(PhysicsBodyComponent.class);
        var shapes = world.getMapper(PhysicsShapesComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsBodyComponent.class, PhysicsShapesComponent.class))
                .getEntities();
        int changed = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entityId = entities.get(i);
            if (!bodies.get(entityId).technicalSpatialLight) continue;
            PhysicsShapesComponent fixtures = shapes.get(entityId);
            for (int j = 0; j < fixtures.shapes.size; j++) {
                PhysicsShapeData fixture = fixtures.shapes.get(j);
                if (fixture == null || !fixture.technicalSpatialLight) continue;
                PhysicsGeometryData geometry = fixture.geometry;
                if (geometry == null || geometry.shapeType != PhysicsGeometryData.SHAPE_CIRCLE) {
                    throw new IllegalStateException("Technical spatial light requires a circular fixture.");
                }
                if (Float.compare(geometry.radius, FIXED_FOOTPRINT_RADIUS_M) == 0
                        && Float.compare(geometry.offsetX, 0f) == 0
                        && Float.compare(geometry.offsetY, 0f) == 0) continue;
                geometry.radius = FIXED_FOOTPRINT_RADIUS_M;
                geometry.offsetX = 0f;
                geometry.offsetY = 0f;
                changed++;
            }
        }
        return changed;
    }

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
