package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.util.InputValidator;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditWheelJointCommand;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

public final class WheelJointProperties extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsWheelJointComponent> mWheel;
    private final HistoryManager history;
    private final FloatField axisXField;
    private final FloatField axisYField;
    private final FloatField frequencyField;
    private final FloatField dampingField;
    private final VisCheckBox motorEnabledBox;
    private final UiBinders.CheckBoxBinder motorEnabledBinder;
    private final FloatField motorSpeedField;
    private final FloatField maxTorqueField;


    public WheelJointProperties(World world, HistoryManager history) {
        this.world = world;
        this.mWheel = world.getMapper(PhysicsWheelJointComponent.class);
        this.history = history;

        axisXField = new FloatField(world, this::readAxisX, this::isWheelJoint).setDisplayDecimals(3);
        axisXField.setApplier((jid, v) -> setWheel(jid, w -> w.axisX = v));

        axisYField = new FloatField(world, this::readAxisY, this::isWheelJoint).setDisplayDecimals(3);
        axisYField.setApplier((jid, v) -> setWheel(jid, w -> w.axisY = v));

        axisXField.setValidator(axisNonZeroValidator(axisXField::getText, axisYField::getText));
        axisYField.setValidator(axisNonZeroValidator(axisYField::getText, axisXField::getText));

        frequencyField = new FloatField(world, this::readFrequency, this::isWheelJoint).setDisplayDecimals(3);
        frequencyField.setApplier((jid, v) -> setWheel(jid, w -> w.frequencyHz = v));

        dampingField = new FloatField(world, this::readDampingRatio, this::isWheelJoint).setDisplayDecimals(3);
        dampingField.setApplier((jid, v) -> setWheel(jid, w -> w.dampingRatio = v));

        motorEnabledBox = new VisCheckBox("Enable motor");
        motorEnabledBinder = new UiBinders.CheckBoxBinder(
                world,
                motorEnabledBox,
                this::isWheelJoint,
                eid -> {
                    PhysicsWheelJointComponent w = mWheel.getSafe(eid, null);
                    return w != null && w.enableMotor;
                },
                (eid, v) -> setWheel(eid, w -> w.enableMotor = v)
        );

        motorSpeedField = new FloatField(world, this::readMotorSpeed, this::isWheelJoint).setDisplayDecimals(3);
        motorSpeedField.setApplier((jid, v) -> setWheel(jid, w -> w.motorSpeedRad = v));

        maxTorqueField = new FloatField(world, this::readMaxTorque, this::isWheelJoint).setDisplayDecimals(3);
        maxTorqueField.setApplier((jid, v) -> setWheel(jid, w -> w.maxMotorTorque = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);
        content.add(new VisLabel("Axis X:")).left();
        content.add(axisXField).width(120).left().row();
        content.add(new VisLabel("Axis Y:")).left();
        content.add(axisYField).width(120).left().row();
        content.add(new VisLabel("Frequency (Hz):")).left();
        content.add(frequencyField).width(120).left().row();
        content.add(new VisLabel("Damping ratio:")).left();
        content.add(dampingField).width(120).left().row();
        content.add(motorEnabledBox).left().colspan(2).row();
        content.add(new VisLabel("Motor speed (rad/s):")).left();
        content.add(motorSpeedField).width(120).left().row();
        content.add(new VisLabel("Max motor torque:")).left();
        content.add(maxTorqueField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isWheelJoint(jointEid);
        int bindId = valid ? jointEid : -1;
        axisXField.setEntityId(bindId);
        axisYField.setEntityId(bindId);
        frequencyField.setEntityId(bindId);
        dampingField.setEntityId(bindId);
        motorEnabledBinder.setEntityId(bindId);
        motorSpeedField.setEntityId(bindId);
        maxTorqueField.setEntityId(bindId);
    }

    private boolean isWheelJoint(int jointEid) {
        return jointEid >= 0 && mWheel.has(jointEid);
    }

    private interface WheelMutator {
        void apply(PhysicsWheelJointComponent wheel);
    }

    private void setWheel(int jointEid, WheelMutator mutator) {
        if (!isWheelJoint(jointEid) || mutator == null || history == null) return;
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        if (wheel == null) return;

        EditWheelJointCommand.Snapshot before = EditWheelJointCommand.Snapshot.capture(wheel);
        EditWheelJointCommand.Snapshot after = EditWheelJointCommand.Snapshot.capture(wheel);
        if (before == null || after == null) return;

        PhysicsWheelJointComponent temp = new PhysicsWheelJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditWheelJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditWheelJointCommand(
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

    private float readAxisX(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.axisX : 0f;
    }

    private float readAxisY(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.axisY : 0f;
    }

    private float readFrequency(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.frequencyHz : 0f;
    }

    private float readDampingRatio(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.dampingRatio : 0f;
    }

    private float readMotorSpeed(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.motorSpeedRad : 0f;
    }

    private float readMaxTorque(int jointEid) {
        PhysicsWheelJointComponent wheel = mWheel.getSafe(jointEid, null);
        return wheel != null ? wheel.maxMotorTorque : 0f;
    }


    private static final float AXIS_EPS2 = 1e-6f; // eps^2

    private static Float tryParseFloatLoose(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || "-".equals(t) || ".".equals(t)) return null;
        try {
            return Float.parseFloat(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static InputValidator axisNonZeroValidator(
            java.util.function.Supplier<String> thisText,
            java.util.function.Supplier<String> otherText
    ) {
        return input -> {
            // "input" = current field text (the one being edited)
            Float x = tryParseFloatLoose(input);
            Float y = tryParseFloatLoose(otherText.get());

            // tant que l’un des deux est incomplet, on laisse passer
            // (this avoids blocking typing)
            if (x == null || y == null) return true;

            float x2 = x * x;
            float y2 = y * y;
            return (x2 + y2) > AXIS_EPS2;
        };
    }
}
