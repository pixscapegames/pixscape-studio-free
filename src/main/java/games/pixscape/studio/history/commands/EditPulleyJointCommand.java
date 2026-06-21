package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsPulleyJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditPulleyJointCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final float groundAx;
        private final float groundAy;
        private final float groundBx;
        private final float groundBy;
        private final float lengthAM;
        private final float lengthBM;
        private final float ratio;

        public Snapshot(float groundAx,
                        float groundAy,
                        float groundBx,
                        float groundBy,
                        float lengthAM,
                        float lengthBM,
                        float ratio) {
            this.groundAx = groundAx;
            this.groundAy = groundAy;
            this.groundBx = groundBx;
            this.groundBy = groundBy;
            this.lengthAM = Math.max(0f, lengthAM);
            this.lengthBM = Math.max(0f, lengthBM);
            this.ratio = Math.max(1e-6f, ratio);
        }

        public static Snapshot capture(PhysicsPulleyJointComponent pulley) {
            if (pulley == null) return null;
            return new Snapshot(
                    pulley.groundAx,
                    pulley.groundAy,
                    pulley.groundBx,
                    pulley.groundBy,
                    pulley.lengthAM,
                    pulley.lengthBM,
                    pulley.ratio
            );
        }

        public void apply(PhysicsPulleyJointComponent pulley) {
            pulley.groundAx = groundAx;
            pulley.groundAy = groundAy;
            pulley.groundBx = groundBx;
            pulley.groundBy = groundBy;
            pulley.lengthAM = lengthAM;
            pulley.lengthBM = lengthBM;
            pulley.ratio = ratio;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(groundAx, other.groundAx) == 0
                    && Float.compare(groundAy, other.groundAy) == 0
                    && Float.compare(groundBx, other.groundBx) == 0
                    && Float.compare(groundBy, other.groundBy) == 0
                    && Float.compare(lengthAM, other.lengthAM) == 0
                    && Float.compare(lengthBM, other.lengthBM) == 0
                    && Float.compare(ratio, other.ratio) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditPulleyJointCommand(World world,
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
        return "Edit Pulley Joint";
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

        ComponentMapper<PhysicsPulleyJointComponent> mPulley =
                world.getMapper(PhysicsPulleyJointComponent.class);
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(entityId, null);
        if (pulley == null) return;

        snapshot.apply(pulley);

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