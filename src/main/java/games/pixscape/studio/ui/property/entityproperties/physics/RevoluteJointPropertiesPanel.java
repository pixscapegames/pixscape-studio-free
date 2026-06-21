package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsRevoluteJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditRevoluteJointCommand;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

public final class RevoluteJointPropertiesPanel extends VisTable implements JointSpecificPanel {

    private final World world;
    private final ComponentMapper<PhysicsRevoluteJointComponent> mRev;
    private final HistoryManager history;

    private final CollapsibleVisTable root = new CollapsibleVisTable(true);
    private final VisCheckBox limitEnabledBox;
    private final UiBinders.CheckBoxBinder limitEnabledBinder;
    private final FloatField lowerAngleField;
    private final FloatField upperAngleField;
    private final VisCheckBox motorEnabledBox;
    private final UiBinders.CheckBoxBinder motorEnabledBinder;
    private final FloatField motorSpeedField;
    private final FloatField maxTorqueField;

    private int jointEid = -1;

    public RevoluteJointPropertiesPanel(World world, HistoryManager history) {
        super(true);
        top().left();
        add(root).growX().row();
        this.world = world;
        this.mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
        this.history = history;

        limitEnabledBox = new VisCheckBox("Enable limit");
        limitEnabledBinder = new UiBinders.CheckBoxBinder(
                world,
                limitEnabledBox,
                this::isRevoluteJoint,
                eid -> {
                    PhysicsRevoluteJointComponent r = mRev.getSafe(eid, null);
                    return r != null && r.enableLimit;
                },
                (eid, v) -> setRev(eid, r -> r.enableLimit = v)
        );

        lowerAngleField = new FloatField(world, this::readLowerAngle, this::isRevoluteJoint).setDisplayDecimals(3);
        lowerAngleField.setApplier((jid, v) -> setRev(jid, r -> r.lowerAngleRad = v));

        upperAngleField = new FloatField(world, this::readUpperAngle, this::isRevoluteJoint).setDisplayDecimals(3);
        upperAngleField.setApplier((jid, v) -> setRev(jid, r -> r.upperAngleRad = v));

        motorEnabledBox = new VisCheckBox("Enable motor");
        motorEnabledBinder = new UiBinders.CheckBoxBinder(
                world,
                motorEnabledBox,
                this::isRevoluteJoint,
                eid -> {
                    PhysicsRevoluteJointComponent r = mRev.getSafe(eid, null);
                    return r != null && r.enableMotor;
                },
                (eid, v) -> setRev(eid, r -> r.enableMotor = v)
        );

        motorSpeedField = new FloatField(world, this::readMotorSpeed, this::isRevoluteJoint).setDisplayDecimals(3);
        motorSpeedField.setApplier((jid, v) -> setRev(jid, r -> r.motorSpeedRad = v));

        maxTorqueField = new FloatField(world, this::readMaxTorque, this::isRevoluteJoint).setDisplayDecimals(3);
        maxTorqueField.setApplier((jid, v) -> setRev(jid, r -> r.maxMotorTorque = Math.max(0f, v)));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);
        content.add(limitEnabledBox).left().colspan(2).row();
        content.add(new VisLabel("Lower angle (rad):")).left();
        content.add(lowerAngleField).width(120).left().row();
        content.add(new VisLabel("Upper angle (rad):")).left();
        content.add(upperAngleField).width(120).left().row();
        content.add(motorEnabledBox).left().colspan(2).row();
        content.add(new VisLabel("Motor speed (rad/s):")).left();
        content.add(motorSpeedField).width(120).left().row();
        content.add(new VisLabel("Max torque:")).left();
        content.add(maxTorqueField).width(120).left().row();
    }

    @Override
    public void setJointEid(int jointEid) {
        this.jointEid = jointEid;
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isRevoluteJoint(jointEid);
        int bindId = valid ? jointEid : -1;
        limitEnabledBinder.setEntityId(bindId);
        lowerAngleField.setEntityId(bindId);
        upperAngleField.setEntityId(bindId);
        motorEnabledBinder.setEntityId(bindId);
        motorSpeedField.setEntityId(bindId);
        maxTorqueField.setEntityId(bindId);
    }

    private boolean isRevoluteJoint(int jointEid) {
        return jointEid >= 0 && mRev.has(jointEid);
    }

    private interface RevMutator {
        void apply(PhysicsRevoluteJointComponent rev);
    }

    private void setRev(int jointEid, RevMutator mutator) {
        if (!isRevoluteJoint(jointEid) || mutator == null || history == null) return;
        PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEid, null);
        if (rev == null) return;

        EditRevoluteJointCommand.Snapshot before = EditRevoluteJointCommand.Snapshot.capture(rev);
        EditRevoluteJointCommand.Snapshot after = EditRevoluteJointCommand.Snapshot.capture(rev);
        if (before == null || after == null) return;

        PhysicsRevoluteJointComponent temp = new PhysicsRevoluteJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditRevoluteJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditRevoluteJointCommand(
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

    private float readLowerAngle(int jointEid) {
        PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEid, null);
        return rev != null ? rev.lowerAngleRad : 0f;
    }

    private float readUpperAngle(int jointEid) {
        PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEid, null);
        return rev != null ? rev.upperAngleRad : 0f;
    }

    private float readMotorSpeed(int jointEid) {
        PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEid, null);
        return rev != null ? rev.motorSpeedRad : 0f;
    }

    private float readMaxTorque(int jointEid) {
        PhysicsRevoluteJointComponent rev = mRev.getSafe(jointEid, null);
        return rev != null ? rev.maxMotorTorque : 0f;
    }
}
