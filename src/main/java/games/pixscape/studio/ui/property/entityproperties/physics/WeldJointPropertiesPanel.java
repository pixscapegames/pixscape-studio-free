package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.physics.PhysicsWeldJointComponent;
import games.pixscape.studio.helper.GeometryHelper;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditWeldJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class WeldJointPropertiesPanel extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsWeldJointComponent> mWeld;
    private final HistoryManager history;

    private final FloatField referenceAngleField;
    private final FloatField frequencyField;
    private final FloatField dampingField;

    public WeldJointPropertiesPanel(World world, HistoryManager history) {
        this.world = world;
        this.mWeld = world.getMapper(PhysicsWeldJointComponent.class);
        this.history = history;

        referenceAngleField = new FloatField(world, this::readReferenceAngleDeg, this::isWeldJoint).setDisplayDecimals(3);
        referenceAngleField.setApplier((jid, v) -> setWeld(jid, w -> w.referenceAngleRad = GeometryHelper.editorDegToRotationRad(v)));

        frequencyField = new FloatField(world, this::readFrequency, this::isWeldJoint).setDisplayDecimals(3);
        frequencyField.setApplier((jid, v) -> setWeld(jid, w -> w.frequencyHz = v));

        dampingField = new FloatField(world, this::readDampingRatio, this::isWeldJoint).setDisplayDecimals(3);
        dampingField.setApplier((jid, v) -> setWeld(jid, w -> w.dampingRatio = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);

        content.add(new VisLabel("Reference angle (deg):")).left();
        content.add(referenceAngleField).width(120).left().row();

        content.add(new VisLabel("Frequency (Hz):")).left();
        content.add(frequencyField).width(120).left().row();

        content.add(new VisLabel("Damping ratio:")).left();
        content.add(dampingField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isWeldJoint(jointEid);
        int bindId = valid ? jointEid : -1;

        referenceAngleField.setEntityId(bindId);
        frequencyField.setEntityId(bindId);
        dampingField.setEntityId(bindId);
    }

    private boolean isWeldJoint(int jointEid) {
        return jointEid >= 0 && mWeld.has(jointEid);
    }

    private interface WeldMutator {
        void apply(PhysicsWeldJointComponent weld);
    }

    private void setWeld(int jointEid, WeldMutator mutator) {
        if (!isWeldJoint(jointEid) || mutator == null || history == null) return;

        PhysicsWeldJointComponent weld = mWeld.getSafe(jointEid, null);
        if (weld == null) return;

        EditWeldJointCommand.Snapshot before = EditWeldJointCommand.Snapshot.capture(weld);
        EditWeldJointCommand.Snapshot after = EditWeldJointCommand.Snapshot.capture(weld);
        if (before == null || after == null) return;

        PhysicsWeldJointComponent temp = new PhysicsWeldJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditWeldJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditWeldJointCommand(
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

    private float readReferenceAngleDeg(int jointEid) {
        PhysicsWeldJointComponent weld = mWeld.getSafe(jointEid, null);
        return weld != null ? GeometryHelper.rotationRadToEditorDeg(weld.referenceAngleRad) : 0f;
    }

    private float readFrequency(int jointEid) {
        PhysicsWeldJointComponent weld = mWeld.getSafe(jointEid, null);
        return weld != null ? weld.frequencyHz : 0f;
    }

    private float readDampingRatio(int jointEid) {
        PhysicsWeldJointComponent weld = mWeld.getSafe(jointEid, null);
        return weld != null ? weld.dampingRatio : 0f;
    }
}