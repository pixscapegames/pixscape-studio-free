package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsMotorJointComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.helper.GeometryHelper;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditMotorJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class MotorJointPropertiesPanel extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsMotorJointComponent> mMotor;
    private final HistoryManager history;

    private final FloatField linearOffsetXField;
    private final FloatField linearOffsetYField;
    private final FloatField angularOffsetField;
    private final FloatField maxForceField;
    private final FloatField maxTorqueField;
    private final FloatField correctionFactorField;

    public MotorJointPropertiesPanel(World world, HistoryManager history) {
        this.world = world;
        this.mMotor = world.getMapper(PhysicsMotorJointComponent.class);
        this.history = history;

        linearOffsetXField = new FloatField(world, this::readLinearOffsetXPx, this::isMotorJoint).setDisplayDecimals(3);
        linearOffsetXField.setApplier((jid, v) -> setMotor(jid, m -> m.linearOffsetX = pxToM(v)));

        linearOffsetYField = new FloatField(world, this::readLinearOffsetYPx, this::isMotorJoint).setDisplayDecimals(3);
        linearOffsetYField.setApplier((jid, v) -> setMotor(jid, m -> m.linearOffsetY = pxToM(v)));

        angularOffsetField = new FloatField(world, this::readAngularOffsetDeg, this::isMotorJoint).setDisplayDecimals(3);
        angularOffsetField.setApplier((jid, v) -> setMotor(jid, m -> m.angularOffsetRad = GeometryHelper.editorDegToRotationRad(v)));

        maxForceField = new FloatField(world, this::readMaxForce, this::isMotorJoint).setDisplayDecimals(3);
        maxForceField.setApplier((jid, v) -> setMotor(jid, m -> m.maxForce = v));

        maxTorqueField = new FloatField(world, this::readMaxTorque, this::isMotorJoint).setDisplayDecimals(3);
        maxTorqueField.setApplier((jid, v) -> setMotor(jid, m -> m.maxTorque = v));

        correctionFactorField = new FloatField(world, this::readCorrectionFactor, this::isMotorJoint).setDisplayDecimals(3);
        correctionFactorField.setApplier((jid, v) -> setMotor(jid, m -> m.correctionFactor = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);

        content.add(new VisLabel("Linear offset X (px):")).left();
        content.add(linearOffsetXField).width(120).left().row();

        content.add(new VisLabel("Linear offset Y (px):")).left();
        content.add(linearOffsetYField).width(120).left().row();

        content.add(new VisLabel("Angular offset (deg):")).left();
        content.add(angularOffsetField).width(120).left().row();

        content.add(new VisLabel("Max force:")).left();
        content.add(maxForceField).width(120).left().row();

        content.add(new VisLabel("Max torque:")).left();
        content.add(maxTorqueField).width(120).left().row();

        content.add(new VisLabel("Correction factor:")).left();
        content.add(correctionFactorField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isMotorJoint(jointEid);
        int bindId = valid ? jointEid : -1;

        linearOffsetXField.setEntityId(bindId);
        linearOffsetYField.setEntityId(bindId);
        angularOffsetField.setEntityId(bindId);
        maxForceField.setEntityId(bindId);
        maxTorqueField.setEntityId(bindId);
        correctionFactorField.setEntityId(bindId);
    }

    private boolean isMotorJoint(int jointEid) {
        return jointEid >= 0 && mMotor.has(jointEid);
    }

    private interface MotorMutator {
        void apply(PhysicsMotorJointComponent motor);
    }

    private void setMotor(int jointEid, MotorMutator mutator) {
        if (!isMotorJoint(jointEid) || mutator == null || history == null) return;

        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        if (motor == null) return;

        EditMotorJointCommand.Snapshot before = EditMotorJointCommand.Snapshot.capture(motor);
        EditMotorJointCommand.Snapshot after = EditMotorJointCommand.Snapshot.capture(motor);
        if (before == null || after == null) return;

        PhysicsMotorJointComponent temp = new PhysicsMotorJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditMotorJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditMotorJointCommand(
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

    private float readLinearOffsetXPx(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? mToPx(motor.linearOffsetX) : 0f;
    }

    private float readLinearOffsetYPx(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? mToPx(motor.linearOffsetY) : 0f;
    }

    private float readAngularOffsetDeg(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? GeometryHelper.rotationRadToEditorDeg(motor.angularOffsetRad) : 0f;
    }

    private float readMaxForce(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? motor.maxForce : 0f;
    }

    private float readMaxTorque(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? motor.maxTorque : 0f;
    }

    private float readCorrectionFactor(int jointEid) {
        PhysicsMotorJointComponent motor = mMotor.getSafe(jointEid, null);
        return motor != null ? motor.correctionFactor : 0.3f;
    }

    private float resolvePixelsPerMeter() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg.getCurrentSceneMeta();
        return meta.pixelsPerMeter;
    }

    private float mToPx(float meters) {
        return meters * resolvePixelsPerMeter();
    }

    private float pxToM(float pixels) {
        return pixels / resolvePixelsPerMeter();
    }
}