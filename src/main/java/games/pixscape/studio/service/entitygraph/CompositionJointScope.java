package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.*;

/** Cold, read-only transfer preflight. Body scope never expands and no joint is silently lost. */
public final class CompositionJointScope {
    private CompositionJointScope() {
    }

    public static IntArray classify(World world, IntSet entities) {
        var bases = world.getMapper(PhysicsJointComponent.class);
        var gears = world.getMapper(PhysicsGearJointComponent.class);
        var bodies = world.getMapper(PhysicsBodyComponent.class);
        var shapes = world.getMapper(PhysicsShapesComponent.class);
        IntSet internal = new IntSet();
        // Copy/export is an explicit cold boundary. Inspect current authoring data, including
        // edits not yet consumed by the native world's dependency publication.
        var joints = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        for (int i = 0; i < joints.size(); i++) {
            int joint = joints.get(i);
            var base = bases.get(joint);
            boolean a = entities.contains(base.aEid), b = entities.contains(base.bEid);
            if (!a && !b && !entities.contains(joint)) {
                continue;
            }
            if (!a || !b) {
                throw rejected(world, joint, "both connected Bodies must be included; use Copy visual only to omit Physics");
            }
            if (base.aEid == base.bEid || base.aEid < 0 || base.bEid < 0
                    || !world.getEntityManager().isActive(base.aEid) || !world.getEntityManager().isActive(base.bEid)
                    || !bodies.has(base.aEid) || !bodies.has(base.bEid)
                    || !shapes.has(base.aEid) || !shapes.has(base.bEid)
                    || shapes.get(base.aEid).shapes == null || shapes.get(base.aEid).shapes.size == 0
                    || shapes.get(base.bEid).shapes == null || shapes.get(base.bEid).shapes.size == 0
                    || !specificPresent(world, joint, base.type)) {
                throw rejected(world, joint, "invalid Body endpoints or missing joint configuration");
            }
            internal.add(joint);
        }
        for (int i = 0; i < joints.size(); i++) {
            int joint = joints.get(i);
            var base = bases.get(joint);
            if (base.type != PhysicsJointComponent.TYPE_GEAR) {
                continue;
            }
            var gear = gears.getSafe(joint, null);
            boolean dependent = gear != null && (internal.contains(gear.joint1Eid) || internal.contains(gear.joint2Eid));
            if (!internal.contains(joint) && !dependent) {
                continue;
            }
            if (gear == null || !internal.contains(joint) || gear.joint1Eid == gear.joint2Eid
                    || !internal.contains(gear.joint1Eid) || !internal.contains(gear.joint2Eid)
                    || !gearSource(bases.getSafe(gear.joint1Eid, null))
                    || !gearSource(bases.getSafe(gear.joint2Eid, null))) {
                throw rejected(world, joint, "the complete Gear dependency group must be included");
            }
        }
        IntArray result = new IntArray(internal.size);
        for (IntSet.IntSetIterator it = internal.iterator(); it.hasNext;) {
            result.add(it.next());
        }
        result.sort();
        return result;
    }

    private static boolean gearSource(PhysicsJointComponent base) {
        return base != null && (base.type == PhysicsJointComponent.TYPE_REVOLUTE
                || base.type == PhysicsJointComponent.TYPE_PRISMATIC);
    }

    private static IllegalArgumentException rejected(World world, int joint, String reason) {
        var id = world.getMapper(PixscapeIdentityComponent.class).getSafe(joint, null);
        return new IllegalArgumentException("Cannot transfer joint #" + (id == null ? joint : id.stableId) + ": " + reason + ".");
    }

    private static boolean specificPresent(World world, int eid, int type) {
        return switch (type) {
            case PhysicsJointComponent.TYPE_DISTANCE -> world.getMapper(PhysicsDistanceJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_REVOLUTE -> world.getMapper(PhysicsRevoluteJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_PRISMATIC -> world.getMapper(PhysicsPrismaticJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_WHEEL -> world.getMapper(PhysicsWheelJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_FRICTION -> world.getMapper(PhysicsFrictionJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_MOTOR -> world.getMapper(PhysicsMotorJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_WELD -> world.getMapper(PhysicsWeldJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_PULLEY -> world.getMapper(PhysicsPulleyJointComponent.class).has(eid);
            case PhysicsJointComponent.TYPE_GEAR -> world.getMapper(PhysicsGearJointComponent.class).has(eid);
            default -> false;
        };
    }
}
