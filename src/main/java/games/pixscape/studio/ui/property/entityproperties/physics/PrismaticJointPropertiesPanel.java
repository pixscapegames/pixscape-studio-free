package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsPrismaticJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditPrismaticJointCommand;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

public final class PrismaticJointPropertiesPanel extends VisTable implements JointSpecificPanel {

    private final World world;
    private final ComponentMapper<PhysicsPrismaticJointComponent> mPrism;
    private final HistoryManager history;

    private final CollapsibleVisTable root = new CollapsibleVisTable(true);

    private final VisCheckBox limitEnabledBox;
    private final UiBinders.CheckBoxBinder limitEnabledBinder;
    private final FloatField lowerTranslationField;
    private final FloatField upperTranslationField;

    private final VisCheckBox motorEnabledBox;
    private final UiBinders.CheckBoxBinder motorEnabledBinder;
    private final FloatField motorSpeedField;
    private final FloatField maxMotorForceField;

    private final FloatField axisXField;
    private final FloatField axisYField;

    private int jointEid = -1;

    public PrismaticJointPropertiesPanel(World world, HistoryManager history) {
        super(true);
        top().left();
        add(root).growX().row();
        this.world = world;
        this.mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        this.history = history;

        limitEnabledBox = new VisCheckBox("Enable limit");
        limitEnabledBinder = new UiBinders.CheckBoxBinder(
                world,
                limitEnabledBox,
                this::isPrismaticJoint,
                eid -> {
                    PhysicsPrismaticJointComponent p = mPrism.getSafe(eid, null);
                    return p != null && p.enableLimit;
                },
                (eid, v) -> setPrism(eid, p -> p.enableLimit = v)
        );

        lowerTranslationField = new FloatField(world, this::readLowerTranslation, this::isPrismaticJoint).setDisplayDecimals(3);
        lowerTranslationField.setApplier((jid, v) -> setPrism(jid, p -> p.lowerTranslationM = v));

        upperTranslationField = new FloatField(world, this::readUpperTranslation, this::isPrismaticJoint).setDisplayDecimals(3);
        upperTranslationField.setApplier((jid, v) -> setPrism(jid, p -> p.upperTranslationM = v));

        motorEnabledBox = new VisCheckBox("Enable motor");
        motorEnabledBinder = new UiBinders.CheckBoxBinder(
                world,
                motorEnabledBox,
                this::isPrismaticJoint,
                eid -> {
                    PhysicsPrismaticJointComponent p = mPrism.getSafe(eid, null);
                    return p != null && p.enableMotor;
                },
                (eid, v) -> setPrism(eid, p -> p.enableMotor = v)
        );

        motorSpeedField = new FloatField(world, this::readMotorSpeed, this::isPrismaticJoint).setDisplayDecimals(3);
        motorSpeedField.setApplier((jid, v) -> setPrism(jid, p -> p.motorSpeedMps = v));

        maxMotorForceField = new FloatField(world, this::readMaxMotorForce, this::isPrismaticJoint).setDisplayDecimals(3);
        maxMotorForceField.setApplier((jid, v) -> setPrism(jid, p -> p.maxMotorForce = Math.max(0f, v)));

        axisXField = new FloatField(world, this::readAxisX, this::isPrismaticJoint).setDisplayDecimals(3);
        axisXField.setApplier((jid, v) -> setAxisNormalized(jid, v, readAxisY(jid)));

        axisYField = new FloatField(world, this::readAxisY, this::isPrismaticJoint).setDisplayDecimals(3);
        axisYField.setApplier((jid, v) -> setAxisNormalized(jid, readAxisX(jid), v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);
        content.add(limitEnabledBox).left().colspan(2).row();
        content.add(new VisLabel("Lower translation (m):")).left();
        content.add(lowerTranslationField).width(120).left().row();
        content.add(new VisLabel("Upper translation (m):")).left();
        content.add(upperTranslationField).width(120).left().row();
        content.add(motorEnabledBox).left().colspan(2).row();
        content.add(new VisLabel("Motor speed (m/s):")).left();
        content.add(motorSpeedField).width(120).left().row();
        content.add(new VisLabel("Max motor force:")).left();
        content.add(maxMotorForceField).width(120).left().row();
        content.add(new VisLabel("Axis X:")).left();
        content.add(axisXField).width(120).left().row();
        content.add(new VisLabel("Axis Y:")).left();
        content.add(axisYField).width(120).left().row();
    }

    @Override
    public void setJointEid(int jointEid) {
        this.jointEid = jointEid;
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isPrismaticJoint(jointEid);
        int bindId = valid ? jointEid : -1;
        limitEnabledBinder.setEntityId(bindId);
        lowerTranslationField.setEntityId(bindId);
        upperTranslationField.setEntityId(bindId);
        motorEnabledBinder.setEntityId(bindId);
        motorSpeedField.setEntityId(bindId);
        maxMotorForceField.setEntityId(bindId);
        axisXField.setEntityId(bindId);
        axisYField.setEntityId(bindId);
    }

    private boolean isPrismaticJoint(int jointEid) {
        return jointEid >= 0 && mPrism.has(jointEid);
    }

    private interface PrismMutator {
        void apply(PhysicsPrismaticJointComponent prism);
    }

    private void setPrism(int jointEid, PrismMutator mutator) {
        if (!isPrismaticJoint(jointEid) || mutator == null || history == null) return;
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        if (prism == null) return;

        EditPrismaticJointCommand.Snapshot before = EditPrismaticJointCommand.Snapshot.capture(prism);
        EditPrismaticJointCommand.Snapshot after = EditPrismaticJointCommand.Snapshot.capture(prism);
        if (before == null || after == null) return;

        PhysicsPrismaticJointComponent temp = new PhysicsPrismaticJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditPrismaticJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditPrismaticJointCommand(
                world,
                history.historyIds(),
                jointEid,
                before,
                after
        ));
    }

    private void setAxisNormalized(int jointEid, float axisX, float axisY) {
        setPrism(jointEid, p -> {
            p.axisX = axisX;
            p.axisY = axisY;
        });
    }

    private void executeIfMeaningful(Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) return;
        history.execute(command);
        refreshFromModel();
    }

    private float readLowerTranslation(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.lowerTranslationM : 0f;
    }

    private float readUpperTranslation(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.upperTranslationM : 0f;
    }

    private float readMotorSpeed(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.motorSpeedMps : 0f;
    }

    private float readMaxMotorForce(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.maxMotorForce : 0f;
    }

    private float readAxisX(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.axisX : 0f;
    }

    private float readAxisY(int jointEid) {
        PhysicsPrismaticJointComponent prism = mPrism.getSafe(jointEid, null);
        return prism != null ? prism.axisY : 0f;
    }
}
