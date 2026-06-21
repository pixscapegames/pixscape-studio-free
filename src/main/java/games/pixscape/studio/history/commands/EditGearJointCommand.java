package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditGearJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final int joint1Eid;
        private final int joint2Eid;
        private final float ratio;

        public Snapshot(int joint1Eid, int joint2Eid, float ratio) {
            this.joint1Eid = joint1Eid;
            this.joint2Eid = joint2Eid;
            this.ratio = ratio;
        }

        public static Snapshot capture(PhysicsGearJointComponent gear) {
            if (gear == null) return null;
            return new Snapshot(
                    gear.joint1Eid,
                    gear.joint2Eid,
                    gear.ratio
            );
        }

        public Snapshot withRatio(float value) {
            return new Snapshot(joint1Eid, joint2Eid, value);
        }

        public void apply(PhysicsGearJointComponent gear) {
            gear.joint1Eid = joint1Eid;
            gear.joint2Eid = joint2Eid;
            gear.ratio = ratio;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return joint1Eid == other.joint1Eid
                    && joint2Eid == other.joint2Eid
                    && Float.compare(ratio, other.ratio) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditGearJointCommand(World world,
                                HistoryIdRegistry historyIds,
                                int jointEntityId,
                                Snapshot before,
                                Snapshot after) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.jointHistoryId = historyIds != null ? historyIds.ensureForEntity(jointEntityId) : -1L;

        this.noop = world == null
                || historyIds == null
                || jointHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Gear Joint";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after);
    }

    @Override
    public void undo() {
        apply(before);
    }

    private void apply(Snapshot snapshot) {
        if (noop || snapshot == null) return;

        int entityId = resolveJointEntityId();
        if (entityId < 0) return;

        ComponentMapper<PhysicsGearJointComponent> mGear =
                world.getMapper(PhysicsGearJointComponent.class);
        PhysicsGearJointComponent gear = mGear.getSafe(entityId, null);
        if (gear == null) return;

        snapshot.apply(gear);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.joint(entityId, JointDirtyBits.ALL);
        }
        EventFlow.i().publish(new EventFlow.JointParametersChanged(entityId, EventFlow.tag(this)));
    }

    private int resolveJointEntityId() {
        int entityId = historyIds.entityOfHistoryId(jointHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }
}