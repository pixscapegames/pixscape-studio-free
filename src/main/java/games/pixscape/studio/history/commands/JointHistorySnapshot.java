package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.HistoryIdRegistry;

/** Restores joint references through history identities after ECS ids change. */
final class JointHistorySnapshot {
    private final long aHistoryId;
    private final long bHistoryId;
    private final long gearJoint1HistoryId;
    private final long gearJoint2HistoryId;
    private final boolean hasGear;

    private JointHistorySnapshot(long aHistoryId, long bHistoryId,
                                 long gearJoint1HistoryId, long gearJoint2HistoryId,
                                 boolean hasGear) {
        this.aHistoryId = aHistoryId;
        this.bHistoryId = bHistoryId;
        this.gearJoint1HistoryId = gearJoint1HistoryId;
        this.gearJoint2HistoryId = gearJoint2HistoryId;
        this.hasGear = hasGear;
    }

    static JointHistorySnapshot capture(World world, HistoryIdRegistry historyIds, int jointEid) {
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(jointEid);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class)
                .getSafe(jointEid, null);
        return new JointHistorySnapshot(
                historyId(world, historyIds, base.aEid),
                historyId(world, historyIds, base.bEid),
                gear != null ? historyId(world, historyIds, gear.joint1Eid) : -1L,
                gear != null ? historyId(world, historyIds, gear.joint2Eid) : -1L,
                gear != null);
    }

    void restore(World world, HistoryIdRegistry historyIds, int jointEid) {
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(jointEid);
        base.aEid = historyIds.entityOfHistoryId(aHistoryId);
        base.bEid = historyIds.entityOfHistoryId(bHistoryId);
        if (hasGear) {
            PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(jointEid);
            gear.joint1Eid = historyIds.entityOfHistoryId(gearJoint1HistoryId);
            gear.joint2Eid = historyIds.entityOfHistoryId(gearJoint2HistoryId);
        }
    }

    private static long historyId(World world, HistoryIdRegistry historyIds, int entityId) {
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? historyIds.ensureForEntity(entityId) : -1L;
    }
}
