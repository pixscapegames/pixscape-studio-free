package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
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

        IntSet selected = new IntSet();
        IntArray result = new IntArray(supportedSelection);
        for (int i = 0; i < supportedSelection.size; i++) {
            selected.add(supportedSelection.get(i));
        }
        var joints = games.pixscape.studio.service.entitygraph.CompositionJointScope.classify(world, selected);
        for (int i = 0; i < joints.size; i++) {
            if (selected.add(joints.get(i))) {
                result.add(joints.get(i));
            }
        }
        return result;
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

}
