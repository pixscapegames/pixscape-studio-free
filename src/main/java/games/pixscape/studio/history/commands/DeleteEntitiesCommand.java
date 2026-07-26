package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.LongArray;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Delete entities with stable undo/redo.
 * <p>
 * - During construction: for each entityId, capture a GenericEntityInitializer + its historyId.
 * - redo() : deletes entities (resolved through HistoryIdRegistry).
 * - undo() : recreates entities with the same historyId and captured initializer.
 * <p>
 * NOTE IMPORTANT:
 * - No fallback scan: resolution goes through the in-memory registry.
 */
public final class DeleteEntitiesCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;

    /**
     * historyId of each deleted entity, in the same order as inits.
     */
    private final LongArray historyIdsToDelete = new LongArray();

    /**
     * Initializers capturing full state of deleted entities.
     */
    private final List<GenericEntityInitializer> inits = new ArrayList<>();
    private final List<JointSnapshot> jointSnapshots = new ArrayList<>();
    private final IntConsumer onRestoredEntity;

    public DeleteEntitiesCommand(World world, HistoryIdRegistry historyIds, IntArray entityIdsToDelete) {
        this(world, historyIds, entityIdsToDelete, null);
    }

    public DeleteEntitiesCommand(World world, HistoryIdRegistry historyIds, IntArray entityIdsToDelete, IntConsumer onRestoredEntity) {

        this.world = world;
        this.historyIds = historyIds;
        this.onRestoredEntity = onRestoredEntity;

        var em = world.getEntityManager();

        IntArray expanded = expandWithDependentJoints(entityIdsToDelete);
        var mJoint = world.getMapper(PhysicsJointComponent.class);

        for (int i = 0; i < expanded.size; i++) {
            int e = expanded.get(i);
            if (!em.isActive(e)) continue;

            long id = historyIds.ensureForEntity(e);

            GenericEntityInitializer init = new GenericEntityInitializer(world);
            init.syncFrom(e); // snapshot complet (Transform, Dimensions, TR, Meta, etc.)

            inits.add(init);
            historyIdsToDelete.add(id);

            if (mJoint.has(e)) {
                jointSnapshots.add(JointSnapshot.capture(world, historyIds, e));
            } else {
                jointSnapshots.add(null);
            }
        }
    }

    @Override
    public String label() {
        return "Delete Entities";
    }

    @Override
    public void redo() {
        var em = world.getEntityManager();
        for (int i = 0; i < historyIdsToDelete.size; i++) {
            long historyId = historyIdsToDelete.get(i);
            int e = historyIds.entityOfHistoryId(historyId);
            if (e != -1 && em.isActive(e)) {
                IdentityRegistry.unindexEntityImmediately(world, e);
                world.delete(e);
            }
            historyIds.unbindHistoryId(historyId);
        }
    }

    @Override
    public void undo() {
        IntArray recreated = new IntArray(false, inits.size());

        for (int i = 0; i < inits.size(); i++) {
            GenericEntityInitializer init = inits.get(i);
            long historyId = historyIdsToDelete.get(i);

            int e = world.create();

            // Reconstruit tous les composants via l'initializer
            init.init(e);
            historyIds.bind(e, historyId);

            if (onRestoredEntity != null) {
                onRestoredEntity.accept(e);
            }
            recreated.add(e);
        }

        for (int i = 0; i < jointSnapshots.size(); i++) {
            JointSnapshot snap = jointSnapshots.get(i);
            if (snap == null) continue;

            int jointEid = recreated.get(i);
            snap.restore(world, historyIds, jointEid);
        }
    }

    private IntArray expandWithDependentJoints(IntArray requested) {
        IntSet selected = new IntSet();
        IntArray out = new IntArray(false, requested.size + 8);

        var em = world.getEntityManager();
        for (int i = 0; i < requested.size; i++) {
            int e = requested.get(i);
            if (e < 0 || !em.isActive(e)) continue;
            if (!selected.add(e)) continue;
            out.add(e);
        }

        var mJoint = world.getMapper(PhysicsJointComponent.class);
        IntBag joints = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = joints.getData();
        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEid = data[i];
            if (!em.isActive(jointEid)) continue;

            PhysicsJointComponent joint = mJoint.getSafe(jointEid, null);
            if (joint == null) continue;

            if (selected.contains(jointEid)
                    || selected.contains(joint.aEid)
                    || selected.contains(joint.bEid)) {
                if (selected.add(jointEid)) {
                    out.add(jointEid);
                }
            }
        }

        return out;
    }

    private static final class JointSnapshot {
        long aHistoryId, bHistoryId;
        boolean hasGear;
        long gearJoint1HistoryId, gearJoint2HistoryId;

        static JointSnapshot capture(World world, HistoryIdRegistry historyIds, int jointEid) {
            var mJoint = world.getMapper(PhysicsJointComponent.class);
            PhysicsJointComponent base = mJoint.getSafe(jointEid, null);
            if (base == null) return null;

            JointSnapshot snap = new JointSnapshot();
            snap.aHistoryId = (base.aEid >= 0) ? historyIds.ensureForEntity(base.aEid) : -1L;
            snap.bHistoryId = (base.bEid >= 0) ? historyIds.ensureForEntity(base.bEid) : -1L;

            var mGear = world.getMapper(PhysicsGearJointComponent.class);
            PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
            if (gear != null) {
                snap.hasGear = true;
                snap.gearJoint1HistoryId = gear.joint1Eid >= 0
                        ? historyIds.ensureForEntity(gear.joint1Eid)
                        : -1L;
                snap.gearJoint2HistoryId = gear.joint2Eid >= 0
                        ? historyIds.ensureForEntity(gear.joint2Eid)
                        : -1L;
            }

            return snap;
        }

        void restore(World world, HistoryIdRegistry historyIds, int jointEid) {
            var mJoint = world.getMapper(PhysicsJointComponent.class);
            PhysicsJointComponent base = mJoint.get(jointEid);
            base.aEid = historyIds.entityOfHistoryId(aHistoryId);
            base.bEid = historyIds.entityOfHistoryId(bHistoryId);

            if (hasGear) {
                var mGear = world.getMapper(PhysicsGearJointComponent.class);
                PhysicsGearJointComponent gear = mGear.get(jointEid);
                gear.joint1Eid = historyIds.entityOfHistoryId(gearJoint1HistoryId);
                gear.joint2Eid = historyIds.entityOfHistoryId(gearJoint2HistoryId);
            }
        }
    }

}
