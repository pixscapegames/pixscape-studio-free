package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsFrictionJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditFrictionJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class FrictionJointPropertiesPanel extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsFrictionJointComponent> mFriction;
    private final HistoryManager history;

    private final FloatField maxForceField;
    private final FloatField maxTorqueField;

    public FrictionJointPropertiesPanel(World world, HistoryManager history) {
        this.world = world;
        this.mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
        this.history = history;

        maxForceField = new FloatField(world, this::readMaxForce, this::isFrictionJoint).setDisplayDecimals(3);
        maxForceField.setApplier((jid, v) -> setFriction(jid, f -> f.maxForce = v));

        maxTorqueField = new FloatField(world, this::readMaxTorque, this::isFrictionJoint).setDisplayDecimals(3);
        maxTorqueField.setApplier((jid, v) -> setFriction(jid, f -> f.maxTorque = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);

        content.add(new VisLabel("Max force:")).left();
        content.add(maxForceField).width(120).left().row();

        content.add(new VisLabel("Max torque:")).left();
        content.add(maxTorqueField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isFrictionJoint(jointEid);
        int bindId = valid ? jointEid : -1;

        maxForceField.setEntityId(bindId);
        maxTorqueField.setEntityId(bindId);
    }

    private boolean isFrictionJoint(int jointEid) {
        return jointEid >= 0 && mFriction.has(jointEid);
    }

    private interface FrictionMutator {
        void apply(PhysicsFrictionJointComponent friction);
    }

    private void setFriction(int jointEid, FrictionMutator mutator) {
        if (!isFrictionJoint(jointEid) || mutator == null || history == null) return;

        PhysicsFrictionJointComponent friction = mFriction.getSafe(jointEid, null);
        if (friction == null) return;

        EditFrictionJointCommand.Snapshot before = EditFrictionJointCommand.Snapshot.capture(friction);
        EditFrictionJointCommand.Snapshot after = EditFrictionJointCommand.Snapshot.capture(friction);
        if (before == null || after == null) return;

        PhysicsFrictionJointComponent temp = new PhysicsFrictionJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditFrictionJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditFrictionJointCommand(
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

    private float readMaxForce(int jointEid) {
        PhysicsFrictionJointComponent friction = mFriction.getSafe(jointEid, null);
        return friction != null ? friction.maxForce : 0f;
    }

    private float readMaxTorque(int jointEid) {
        PhysicsFrictionJointComponent friction = mFriction.getSafe(jointEid, null);
        return friction != null ? friction.maxTorque : 0f;
    }
}