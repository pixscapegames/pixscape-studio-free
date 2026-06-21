package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditDistanceJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class DistanceJointPropertiesPanel extends VisTable implements JointSpecificPanel {

    private final World world;
    private final ComponentMapper<PhysicsDistanceJointComponent> mDist;
    private final HistoryManager history;

    private final FloatField distanceMField;
    private final FloatField freqHzField;
    private final FloatField dampingField;

    private int jointEid = -1;

    public DistanceJointPropertiesPanel(World world, HistoryManager history) {
        super(true);
        this.world = world;
        this.mDist = world.getMapper(PhysicsDistanceJointComponent.class);
        this.history = history;

        top().left();
        defaults().left().top().pad(1);

        distanceMField = new FloatField(world, this::readJointDistanceM, this::isDistanceJoint).setDisplayDecimals(4);
        distanceMField.setApplier((jid, v) -> setDist(jid, d -> d.lengthM = Math.max(0.001f, v)));

        freqHzField = new FloatField(world, this::readJointFreqHz, this::isDistanceJoint).setDisplayDecimals(3);
        freqHzField.setApplier((jid, v) -> setDist(jid, d -> d.frequencyHz = Math.max(0f, v)));

        dampingField = new FloatField(world, this::readJointDamping, this::isDistanceJoint).setDisplayDecimals(3);
        dampingField.setApplier((jid, v) -> setDist(jid, d -> d.dampingRatio = clamp01(v)));

        add(new VisLabel("Distance (m):")).left();
        add(distanceMField).width(120).left().row();

        add(new VisLabel("Freq (Hz):")).left();
        add(freqHzField).width(120).left().row();

        add(new VisLabel("Damping [0..1]:")).left();
        add(dampingField).width(120).left().row();
    }

    @Override
    public void setJointEid(int jointEid) {
        this.jointEid = jointEid;
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isDistanceJoint(jointEid);
        int bindId = valid ? jointEid : -1;
        distanceMField.setEntityId(bindId);
        freqHzField.setEntityId(bindId);
        dampingField.setEntityId(bindId);
    }

    private boolean isDistanceJoint(int jointEid) {
        return jointEid >= 0 && mDist.has(jointEid);
    }

    private interface DistMutator {
        void apply(PhysicsDistanceJointComponent dist);
    }

    private void setDist(int jointEid, DistMutator mutator) {
        if (!isDistanceJoint(jointEid) || mutator == null || history == null) return;
        PhysicsDistanceJointComponent dist = mDist.getSafe(jointEid, null);
        if (dist == null) return;

        EditDistanceJointCommand.Snapshot before = EditDistanceJointCommand.Snapshot.capture(dist);
        EditDistanceJointCommand.Snapshot after = EditDistanceJointCommand.Snapshot.capture(dist);
        if (before == null || after == null) return;

        PhysicsDistanceJointComponent temp = new PhysicsDistanceJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditDistanceJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditDistanceJointCommand(
                world,
                history.historyIds(),
                jointEid,
                before,
                after
        ));
    }

    private void executeIfMeaningful(Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) return;
        history.execute(command);
        refreshFromModel();
    }

    private float readJointDistanceM(int jointEid) {
        PhysicsDistanceJointComponent dist = mDist.getSafe(jointEid, null);
        return dist != null ? dist.lengthM : 0f;
    }

    private float readJointFreqHz(int jointEid) {
        PhysicsDistanceJointComponent dist = mDist.getSafe(jointEid, null);
        return dist != null ? dist.frequencyHz : 0f;
    }

    private float readJointDamping(int jointEid) {
        PhysicsDistanceJointComponent dist = mDist.getSafe(jointEid, null);
        return dist != null ? dist.dampingRatio : 0f;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}