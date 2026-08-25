package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditJointBaseCommand;
import games.pixscape.studio.ops.EditorOps;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsJointUiNames;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

public final class JointProperties extends VisTable {

    private final World world;
    private final HistoryManager history;
    private final EditorOps ops;
    private final SelectionService selectionService;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;

    private final VisLabel typeValue;
    private final VisLabel bodyAValue;
    private final VisLabel bodyBValue;

    private final FloatField anchorAxField;
    private final FloatField anchorAyField;
    private final FloatField anchorBxField;
    private final FloatField anchorByField;

    private final VisCheckBox collideConnectedBox;
    private final VisTextButton deleteButton;
    private final UiBinders.CheckBoxBinder collideConnectedBinder;

    private final CollapsibleVisTable anchorsBlock = new CollapsibleVisTable(true);

    private final DistanceJointPropertiesPanel distancePanel;
    private final RevoluteJointPropertiesPanel revolutePanel;
    private final PrismaticJointPropertiesPanel prismaticPanel;
    private final WheelJointProperties wheelPanel;
    private final FrictionJointPropertiesPanel frictionPanel;
    private final MotorJointPropertiesPanel motorPanel;
    private final WeldJointPropertiesPanel weldPanel;
    private final PulleyJointPropertiesPanel pulleyPanel;
    private final GearJointPropertiesPanel gearPanel;

    private final VisTable specificPanel;

    private int jointEid = -1;
    private boolean dirty = true;
    private JointSpecificPanel activePanel;

    public JointProperties(World world, HistoryManager history, EditorOps ops, SelectionService selectionService) {
        super(true);
        this.world = world;
        this.history = history;
        this.ops = ops;
        this.selectionService = selectionService;
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);

        UiRefreshDispatchSystem postRefresh = world.getSystem(UiRefreshDispatchSystem.class);
        postRefresh.add(this::updateIfDirty);

        typeValue = new VisLabel();
        bodyAValue = new VisLabel();
        bodyBValue = new VisLabel();

        anchorAxField = new FloatField(world, this::readJointAnchorAx, this::isJointValid).setDisplayDecimals(3);
        anchorAxField.setApplier((jid, v) -> setBase(jid, j -> j.anchorAx = v));

        anchorAyField = new FloatField(world, this::readJointAnchorAy, this::isJointValid).setDisplayDecimals(3);
        anchorAyField.setApplier((jid, v) -> setBase(jid, j -> j.anchorAy = v));

        anchorBxField = new FloatField(world, this::readJointAnchorBx, this::isJointValid).setDisplayDecimals(3);
        anchorBxField.setApplier((jid, v) -> setBase(jid, j -> j.anchorBx = v));

        anchorByField = new FloatField(world, this::readJointAnchorBy, this::isJointValid).setDisplayDecimals(3);
        anchorByField.setApplier((jid, v) -> setBase(jid, j -> j.anchorBy = v));

        collideConnectedBox = new VisCheckBox("Collide connected");
        collideConnectedBinder = new UiBinders.CheckBoxBinder(
                world,
                collideConnectedBox,
                this::isJointValid,
                eid -> {
                    PhysicsJointComponent j = mJointBase.getSafe(eid, null);
                    return j != null && j.collideConnected;
                },
                (eid, v) -> setBase(eid, j -> j.collideConnected = v)
        );

        distancePanel = new DistanceJointPropertiesPanel(world, history);
        revolutePanel = new RevoluteJointPropertiesPanel(world, history);
        prismaticPanel = new PrismaticJointPropertiesPanel(world, history);
        wheelPanel = new WheelJointProperties(world, history);
        frictionPanel = new FrictionJointPropertiesPanel(world, history);
        motorPanel = new MotorJointPropertiesPanel(world, history);
        weldPanel = new WeldJointPropertiesPanel(world, history);
        pulleyPanel = new PulleyJointPropertiesPanel(world, history);
        gearPanel = new GearJointPropertiesPanel(world, history);

        deleteButton = new VisTextButton("Delete");
        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!isJointValid(jointEid)) return;
                if (ops != null) {
                    ops.deleteJoint(jointEid);
                }
                if (selectionService != null) {
                    selectionService.clearSelection();
                }
            }
        });

        top().left();
        defaults().left().pad(1);

        add(new VisLabel("JOINT"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();

        VisTable common = new VisTable(true);
        common.left().top();

        common.add(new VisLabel("Type:")).left();
        common.add(typeValue).left().growX().row();
        common.add(new VisLabel("Body A:")).left();
        common.add(bodyAValue).left().row();
        common.add(new VisLabel("Body B:")).left().padRight(15);
        common.add(bodyBValue).left().growX().row();
        add(common).growX().colspan(2).left().growX().row();

        add(collideConnectedBox).left().colspan(2).row();

        VisTable anchorsContent = anchorsBlock.content();
        anchorsContent.left().top();
        anchorsContent.defaults().left().pad(1);
        anchorsContent.add(new VisLabel("Anchor A")).left();
        anchorsContent.add(makeXYStack(anchorAxField, anchorAyField)).left().row();
        anchorsContent.add(new VisLabel("Anchor B")).left();
        anchorsContent.add(makeXYStack(anchorBxField, anchorByField)).left().row();
        add(anchorsBlock).growX().left().colspan(2).row();
        specificPanel = new VisTable(true);
        specificPanel.left().top();
        specificPanel.defaults().left().top();
        add(specificPanel).growX().left().top().colspan(2).row();
        add(deleteButton).center().colspan(2).padTop(30).row();
    }

    public void setJointEntityId(int jointEid) {
        this.jointEid = jointEid;
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public void updateIfDirty() {
        if (!dirty) return;
        dirty = false;
        refreshFromModel();
    }

    private void refreshFromModel() {
        PhysicsJointComponent base = getBase();
        if (base == null) {
            setInvalidUi();
            return;
        }

        typeValue.setText(PhysicsJointUiNames.typeName(base.type));
        bodyAValue.setText(formatInternalId(base.aEid));
        bodyBValue.setText(formatInternalId(base.bEid));

        anchorAxField.setEntityId(jointEid);
        anchorAyField.setEntityId(jointEid);
        anchorBxField.setEntityId(jointEid);
        anchorByField.setEntityId(jointEid);
        anchorsBlock.show(shouldShowAnchors(base.type));
        collideConnectedBinder.setEntityId(jointEid);

        JointSpecificPanel panel = resolveSpecificPanel(base.type);
        if (panel != activePanel) {
            activePanel = panel;
            specificPanel.clearChildren();
            if (panel != null) {
                specificPanel.add((Actor) panel).left().top().growX().row();
            }
        }

        if (panel != null) {
            panel.setJointEid(jointEid);
            panel.refreshFromModel();
        }
        deleteButton.setDisabled(!isJointValid(jointEid));

        invalidateHierarchy();
    }

    private String formatInternalId(int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return "-";
        }

        PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
        if (identity == null || identity.stableId <= 0L) {
            return "-";
        }

        return String.valueOf(identity.stableId);
    }

    private boolean shouldShowAnchors(int type) {
        return switch (type) {
            case PhysicsJointComponent.TYPE_MOTOR,
                 PhysicsJointComponent.TYPE_GEAR -> false;
            default -> true;
        };
    }

    private JointSpecificPanel resolveSpecificPanel(int type) {
        return switch (type) {
            case PhysicsJointComponent.TYPE_DISTANCE -> distancePanel;
            case PhysicsJointComponent.TYPE_REVOLUTE -> revolutePanel;
            case PhysicsJointComponent.TYPE_PRISMATIC -> prismaticPanel;
            case PhysicsJointComponent.TYPE_WHEEL -> wheelPanel;
            case PhysicsJointComponent.TYPE_FRICTION -> frictionPanel;
            case PhysicsJointComponent.TYPE_MOTOR -> motorPanel;
            case PhysicsJointComponent.TYPE_WELD -> weldPanel;
            case PhysicsJointComponent.TYPE_PULLEY -> pulleyPanel;
            case PhysicsJointComponent.TYPE_GEAR -> gearPanel;
            default -> null;
        };
    }

    private void setInvalidUi() {
        typeValue.setText("Invalid joint");
        bodyAValue.setText("-");
        bodyBValue.setText("-");
        anchorAxField.setEntityId(-1);
        anchorAyField.setEntityId(-1);
        anchorBxField.setEntityId(-1);
        anchorByField.setEntityId(-1);
        anchorsBlock.show(true);
        collideConnectedBinder.setEntityId(-1);
        if (activePanel != null) {
            activePanel.setJointEid(-1);
            activePanel.refreshFromModel();
        }
        specificPanel.clearChildren();
        activePanel = null;
        deleteButton.setDisabled(!isJointValid(jointEid));
        invalidateHierarchy();
    }

    private boolean isJointValid(int jointEid) {
        return jointEid >= 0
                && world.getEntityManager().isActive(jointEid)
                && mJointBase.has(jointEid);
    }

    private PhysicsJointComponent getBase() {
        if (!isJointValid(jointEid)) return null;
        return mJointBase.getSafe(jointEid, null);
    }

    private interface BaseMutator {
        void apply(PhysicsJointComponent joint);
    }

    private void setBase(int jointEid, BaseMutator mutator) {
        if (!isJointValid(jointEid) || mutator == null || history == null) return;
        PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
        if (base == null) return;

        EditJointBaseCommand.Snapshot before = EditJointBaseCommand.Snapshot.capture(base);
        EditJointBaseCommand.Snapshot after = EditJointBaseCommand.Snapshot.capture(base);
        if (before == null || after == null) return;

        PhysicsJointComponent temp = new PhysicsJointComponent();
        after.apply(temp);
        mutator.apply(temp);
        after = EditJointBaseCommand.Snapshot.capture(temp);

        EditJointBaseCommand command = new EditJointBaseCommand(
                world,
                history.historyIds(),
                jointEid,
                before,
                after
        );
        executeIfMeaningful(command);
    }

    private void executeIfMeaningful(Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) return;
        history.execute(command);
        markDirty();
    }

    private float readJointAnchorAx(int jointEid) {
        PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
        return base != null ? base.anchorAx : 0f;
    }

    private float readJointAnchorAy(int jointEid) {
        PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
        return base != null ? base.anchorAy : 0f;
    }

    private float readJointAnchorBx(int jointEid) {
        PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
        return base != null ? base.anchorBx : 0f;
    }

    private float readJointAnchorBy(int jointEid) {
        PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
        return base != null ? base.anchorBy : 0f;
    }

    private static VisTable makeXYStack(Actor xField, Actor yField) {
        VisTable t = new VisTable(true);
        t.defaults().left().pad(1);

        VisTable r1 = new VisTable(true);
        r1.add(new VisLabel("x:")).width(10).padRight(4);
        r1.add(xField).width(120);

        VisTable r2 = new VisTable(true);
        r2.add(new VisLabel("y:")).width(10).padRight(4);
        r2.add(yField).width(120);

        t.add(r1).left().row();
        t.add(r2).left().row();
        return t;
    }
}
