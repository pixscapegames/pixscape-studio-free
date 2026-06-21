package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditGearJointCommand;
import games.pixscape.studio.ui.widget.FloatField;

public final class GearJointPropertiesPanel extends JointTypeProperties {

    private final World world;
    private final ComponentMapper<PhysicsGearJointComponent> mGear;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private final HistoryManager history;

    private final VisLabel joint1Value;
    private final VisLabel joint2Value;
    private final FloatField ratioField;

    public GearJointPropertiesPanel(World world, HistoryManager history) {
        this.world = world;
        this.mGear = world.getMapper(PhysicsGearJointComponent.class);
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.history = history;

        joint1Value = new VisLabel();
        joint2Value = new VisLabel();

        ratioField = new FloatField(world, this::readRatio, this::isGearJoint).setDisplayDecimals(3);
        ratioField.setApplier((jid, v) -> setGear(jid, g -> g.ratio = v));

        VisTable content = root.content();
        content.left().top();
        content.defaults().left().top().pad(1);

        content.add(new VisLabel("Source joint 1:")).left();
        content.add(joint1Value).left().row();

        content.add(new VisLabel("Source joint 2:")).left();
        content.add(joint2Value).left().row();

        content.add(new VisLabel("Ratio:")).left();
        content.add(ratioField).width(120).left().row();
    }

    @Override
    public void refreshFromModel() {
        boolean valid = isGearJoint(jointEid);
        int bindId = valid ? jointEid : -1;

        ratioField.setEntityId(bindId);

        if (!valid) {
            joint1Value.setText("-");
            joint2Value.setText("-");
            return;
        }

        PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
        if (gear == null) {
            joint1Value.setText("-");
            joint2Value.setText("-");
            return;
        }

        joint1Value.setText(formatJointRef(gear.joint1Eid));
        joint2Value.setText(formatJointRef(gear.joint2Eid));
    }

    private boolean isGearJoint(int jointEid) {
        return jointEid >= 0 && mGear.has(jointEid);
    }

    private interface GearMutator {
        void apply(PhysicsGearJointComponent gear);
    }

    private void setGear(int jointEid, GearMutator mutator) {
        if (!isGearJoint(jointEid) || mutator == null || history == null) return;

        PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
        if (gear == null) return;

        EditGearJointCommand.Snapshot before = EditGearJointCommand.Snapshot.capture(gear);
        EditGearJointCommand.Snapshot after = EditGearJointCommand.Snapshot.capture(gear);
        if (before == null || after == null) return;

        PhysicsGearJointComponent temp = new PhysicsGearJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditGearJointCommand.Snapshot.capture(temp);

        executeIfMeaningful(new EditGearJointCommand(
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

    private float readRatio(int jointEid) {
        PhysicsGearJointComponent gear = mGear.getSafe(jointEid, null);
        return gear != null ? gear.ratio : 1f;
    }

    private String formatJointRef(int sourceJointEid) {
        if (sourceJointEid < 0 || !world.getEntityManager().isActive(sourceJointEid)) {
            return "-";
        }

        PhysicsJointComponent base = mJointBase.getSafe(sourceJointEid, null);
        if (base == null) {
            return "-";
        }

        PixscapeIdentityComponent identity = mIdentity.getSafe(sourceJointEid, null);
        if (identity == null || identity.stableId <= 0L) {
            return "-";
        }

        return jointTypeName(base.type) + " #" + identity.stableId;
    }

    private static String jointTypeName(int type) {
        return switch (type) {
            case PhysicsJointComponent.TYPE_REVOLUTE -> "REVOLUTE";
            case PhysicsJointComponent.TYPE_PRISMATIC -> "PRISMATIC";
            case PhysicsJointComponent.TYPE_DISTANCE -> "DISTANCE";
            case PhysicsJointComponent.TYPE_WHEEL -> "WHEEL";
            case PhysicsJointComponent.TYPE_FRICTION -> "FRICTION";
            case PhysicsJointComponent.TYPE_WELD -> "WELD";
            case PhysicsJointComponent.TYPE_MOTOR -> "MOTOR";
            case PhysicsJointComponent.TYPE_PULLEY -> "PULLEY";
            case PhysicsJointComponent.TYPE_GEAR -> "GEAR";
            default -> "UNKNOWN";
        };
    }
}