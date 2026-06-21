package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditJointBaseCommand implements Command, HistoryManager.SupportsNoop {

    public static final class Snapshot {
        private final boolean collideConnected;
        private final float anchorAx;
        private final float anchorAy;
        private final float anchorBx;
        private final float anchorBy;

        public Snapshot(boolean collideConnected,
                        float anchorAx,
                        float anchorAy,
                        float anchorBx,
                        float anchorBy) {
            this.collideConnected = collideConnected;
            this.anchorAx = anchorAx;
            this.anchorAy = anchorAy;
            this.anchorBx = anchorBx;
            this.anchorBy = anchorBy;
        }

        public static Snapshot capture(PhysicsJointComponent joint) {
            if (joint == null) return null;
            return new Snapshot(
                    joint.collideConnected,
                    joint.anchorAx,
                    joint.anchorAy,
                    joint.anchorBx,
                    joint.anchorBy
            );
        }

        public Snapshot withCollideConnected(boolean value) {
            return new Snapshot(value, anchorAx, anchorAy, anchorBx, anchorBy);
        }

        public Snapshot withAnchorAx(float value) {
            return new Snapshot(collideConnected, value, anchorAy, anchorBx, anchorBy);
        }

        public Snapshot withAnchorAy(float value) {
            return new Snapshot(collideConnected, anchorAx, value, anchorBx, anchorBy);
        }

        public Snapshot withAnchorBx(float value) {
            return new Snapshot(collideConnected, anchorAx, anchorAy, value, anchorBy);
        }

        public Snapshot withAnchorBy(float value) {
            return new Snapshot(collideConnected, anchorAx, anchorAy, anchorBx, value);
        }

        public void apply(PhysicsJointComponent joint) {
            joint.collideConnected = collideConnected;
            joint.anchorAx = anchorAx;
            joint.anchorAy = anchorAy;
            joint.anchorBx = anchorBx;
            joint.anchorBy = anchorBy;
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return collideConnected == other.collideConnected
                    && Float.compare(anchorAx, other.anchorAx) == 0
                    && Float.compare(anchorAy, other.anchorAy) == 0
                    && Float.compare(anchorBx, other.anchorBx) == 0
                    && Float.compare(anchorBy, other.anchorBy) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long jointHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;

    public EditJointBaseCommand(World world,
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
        return "Edit Joint";
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

        ComponentMapper<PhysicsJointComponent> mJoint = world.getMapper(PhysicsJointComponent.class);
        PhysicsJointComponent joint = mJoint.getSafe(entityId, null);
        if (joint == null) return;

        snapshot.apply(joint);

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
