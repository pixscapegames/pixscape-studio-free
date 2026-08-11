package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.physics.*;

public final class ClipboardPhysicsJointGraph {
    private ClipboardPhysicsJointGraph() {
    }

    public static IntArray filterCopyableSelection(World world, IntArray supportedSelection) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        if (supportedSelection == null) throw new IllegalArgumentException("supportedSelection must not be null");

        ComponentMapper<PhysicsJointComponent> mBase = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear = world.getMapper(PhysicsGearJointComponent.class);
        ComponentMapper<PhysicsBodyComponent> mBody = world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsShapesComponent> mFixtures = world.getMapper(PhysicsShapesComponent.class);

        IntSet selected = new IntSet();
        for (int i = 0; i < supportedSelection.size; i++) {
            selected.add(supportedSelection.get(i));
        }

        IntSet accepted = new IntSet();
        IntArray out = new IntArray();

        // 1. Keep selected non-joints first.
        for (int i = 0; i < supportedSelection.size; i++) {
            int eid = supportedSelection.get(i);
            PhysicsJointComponent base = mBase.getSafe(eid, null);

            if (base == null) {
                accepted.add(eid);
                out.add(eid);
            }
        }

        // 2. Add all valid non-gear joints from the world.
        IntArray acceptedNonGearJoints = new IntArray();

        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = joints.getData();

        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEid = data[i];

            if (accepted.contains(jointEid)) {
                continue;
            }

            PhysicsJointComponent base = mBase.getSafe(jointEid, null);
            if (base == null) continue;
            if (base.type == PhysicsJointComponent.TYPE_GEAR) continue;
            if (!isJointSpecificComponentPresent(world, jointEid, base.type)) continue;
            if (!isValidBodyJoint(world, selected, mBody, mFixtures, base)) continue;

            accepted.add(jointEid);
            acceptedNonGearJoints.add(jointEid);
            out.add(jointEid);
        }

        // 3. Add valid gear joints from the world, after source joints are accepted.
        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEid = data[i];

            if (accepted.contains(jointEid)) {
                continue;
            }

            PhysicsJointComponent base = mBase.getSafe(jointEid, null);
            if (base == null || base.type != PhysicsJointComponent.TYPE_GEAR) continue;
            if (!isJointSpecificComponentPresent(world, jointEid, base.type)) continue;
            if (!isValidBodyJoint(world, selected, mBody, mFixtures, base)) continue;

            PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
            if (gear == null) continue;
            if (gear.joint1Eid == gear.joint2Eid) continue;
            if (!accepted.contains(gear.joint1Eid)) continue;
            if (!accepted.contains(gear.joint2Eid)) continue;

            accepted.add(jointEid);
            out.add(jointEid);
        }

        return out;
    }

    public static boolean remapJointReferences(World world, int pastedEntityId, IntIntMap oldToNew) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        if (oldToNew == null) throw new IllegalArgumentException("oldToNew must not be null");
        ComponentMapper<PhysicsJointComponent> mBase = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear = world.getMapper(PhysicsGearJointComponent.class);
        PhysicsJointComponent base = mBase.getSafe(pastedEntityId, null);
        if (base == null) return true;

        if (!oldToNew.containsKey(base.aEid) || !oldToNew.containsKey(base.bEid)) return false;
        int remappedA = oldToNew.get(base.aEid, -1);
        int remappedB = oldToNew.get(base.bEid, -1);
        if (remappedA < 0 || remappedB < 0 || remappedA == remappedB) return false;

        if (base.type == PhysicsJointComponent.TYPE_GEAR) {
            PhysicsGearJointComponent gear = mGear.getSafe(pastedEntityId, null);
            if (gear == null) return false;
            if (!oldToNew.containsKey(gear.joint1Eid) || !oldToNew.containsKey(gear.joint2Eid)) return false;
            int remappedJ1 = oldToNew.get(gear.joint1Eid, -1);
            int remappedJ2 = oldToNew.get(gear.joint2Eid, -1);
            if (remappedJ1 < 0 || remappedJ2 < 0 || remappedJ1 == remappedJ2) return false;

            base.aEid = remappedA;
            base.bEid = remappedB;
            gear.joint1Eid = remappedJ1;
            gear.joint2Eid = remappedJ2;
            return true;
        }

        base.aEid = remappedA;
        base.bEid = remappedB;
        return true;
    }

    private static boolean isValidBodyJoint(World world,
                                            IntSet selected,
                                            ComponentMapper<PhysicsBodyComponent> mBody,
                                            ComponentMapper<PhysicsShapesComponent> mFixtures,
                                            PhysicsJointComponent base) {
        return base.aEid != base.bEid
                && selected.contains(base.aEid)
                && selected.contains(base.bEid)
                && world.getEntityManager().isActive(base.aEid)
                && world.getEntityManager().isActive(base.bEid)
                && hasValidPhysicsBody(base.aEid, mBody, mFixtures)
                && hasValidPhysicsBody(base.bEid, mBody, mFixtures);
    }

    private static boolean hasValidPhysicsBody(int eid,
                                               ComponentMapper<PhysicsBodyComponent> mBody,
                                               ComponentMapper<PhysicsShapesComponent> mFixtures) {
        if (!mBody.has(eid)) return false;
        PhysicsShapesComponent fixtures = mFixtures.getSafe(eid, null);
        return fixtures != null && fixtures.shapes != null && fixtures.shapes.size > 0;
    }

    private static boolean isJointSpecificComponentPresent(World world, int eid, int type) {
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
