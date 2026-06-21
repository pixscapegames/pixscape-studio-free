package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsPulleyJointComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditPulleyJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class PulleyJointPropertiesPanel extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsPulleyJointComponent> mPulley;
    private final HistoryManager history;

    private final FloatField groundAxField;
    private final FloatField groundAyField;
    private final FloatField groundBxField;
    private final FloatField groundByField;
    private final FloatField lengthAField;
    private final FloatField lengthBField;
    private final FloatField ratioField;

    public PulleyJointPropertiesPanel(World world, HistoryManager history) {
        this.world = world;
        this.mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
        this.history = history;

        groundAxField = new FloatField(world, this::readGroundAxPx, this::isPulleyJoint).setDisplayDecimals(3);
        groundAxField.setApplier((jid, v) -> setPulley(jid, p -> p.groundAx = pxToM(v)));

        groundAyField = new FloatField(world, this::readGroundAyPx, this::isPulleyJoint).setDisplayDecimals(3);
        groundAyField.setApplier((jid, v) -> setPulley(jid, p -> p.groundAy = pxToM(v)));

        groundBxField = new FloatField(world, this::readGroundBxPx, this::isPulleyJoint).setDisplayDecimals(3);
        groundBxField.setApplier((jid, v) -> setPulley(jid, p -> p.groundBx = pxToM(v)));

        groundByField = new FloatField(world, this::readGroundByPx, this::isPulleyJoint).setDisplayDecimals(3);
        groundByField.setApplier((jid, v) -> setPulley(jid, p -> p.groundBy = pxToM(v)));

        lengthAField = new FloatField(world, this::readLengthAPx, this::isPulleyJoint).setDisplayDecimals(3);
        lengthAField.setApplier((jid, v) -> setPulley(jid, p -> p.lengthAM = pxToM(v)));

        lengthBField = new FloatField(world, this::readLengthBPx, this::isPulleyJoint).setDisplayDecimals(3);
        lengthBField.setApplier((jid, v) -> setPulley(jid, p -> p.lengthBM = pxToM(v)));

        ratioField = new FloatField(world, this::readRatio, this::isPulleyJoint).setDisplayDecimals(3);
        ratioField.setApplier((jid, v) -> setPulley(jid, p -> p.ratio = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);

        content.add(new VisLabel("Ground A X (px):")).left();
        content.add(groundAxField).width(120).left().row();

        content.add(new VisLabel("Ground A Y (px):")).left();
        content.add(groundAyField).width(120).left().row();

        content.add(new VisLabel("Ground B X (px):")).left();
        content.add(groundBxField).width(120).left().row();

        content.add(new VisLabel("Ground B Y (px):")).left();
        content.add(groundByField).width(120).left().row();

        content.add(new VisLabel("Length A (px):")).left();
        content.add(lengthAField).width(120).left().row();

        content.add(new VisLabel("Length B (px):")).left();
        content.add(lengthBField).width(120).left().row();

        content.add(new VisLabel("Ratio:")).left();
        content.add(ratioField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isPulleyJoint(jointEid);
        int bindId = valid ? jointEid : -1;

        groundAxField.setEntityId(bindId);
        groundAyField.setEntityId(bindId);
        groundBxField.setEntityId(bindId);
        groundByField.setEntityId(bindId);
        lengthAField.setEntityId(bindId);
        lengthBField.setEntityId(bindId);
        ratioField.setEntityId(bindId);
    }

    private boolean isPulleyJoint(int jointEid) {
        return jointEid >= 0 && mPulley.has(jointEid);
    }

    private interface PulleyMutator {
        void apply(PhysicsPulleyJointComponent pulley);
    }

    private void setPulley(int jointEid, PulleyMutator mutator) {
        if (!isPulleyJoint(jointEid) || mutator == null || history == null) return;

        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        if (pulley == null) return;

        EditPulleyJointCommand.Snapshot before = EditPulleyJointCommand.Snapshot.capture(pulley);
        EditPulleyJointCommand.Snapshot after = EditPulleyJointCommand.Snapshot.capture(pulley);
        if (before == null || after == null) return;

        PhysicsPulleyJointComponent temp = new PhysicsPulleyJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditPulleyJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditPulleyJointCommand(
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

    private float readGroundAxPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.groundAx) : 0f;
    }

    private float readGroundAyPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.groundAy) : 0f;
    }

    private float readGroundBxPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.groundBx) : 0f;
    }

    private float readGroundByPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.groundBy) : 0f;
    }

    private float readLengthAPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.lengthAM) : 0f;
    }

    private float readLengthBPx(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? mToPx(pulley.lengthBM) : 0f;
    }

    private float readRatio(int jointEid) {
        PhysicsPulleyJointComponent pulley = mPulley.getSafe(jointEid, null);
        return pulley != null ? pulley.ratio : 1f;
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