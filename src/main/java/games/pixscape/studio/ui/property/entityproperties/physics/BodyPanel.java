package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.EntityPropertiesContext;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.ArrayList;
import java.util.List;

public final class BodyPanel extends CollapsibleWidget {
    private static final Array<String> BODY_TYPES = Array.with("Static", "Kinematic", "Dynamic");
    private static final String EMPTY_SHAPE_SUMMARY = "0 circles • 0 quads • 0 polygons";
    private static final String EMPTY_JOINT_SUMMARY = "0 joints";

    private final EntityPropertiesContext ctx;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<TiledLayerComponent> mTiled;

    private final boolean showBodyToggle;

    private final VisTable root = new VisTable(true);
    private final VisCheckBox addPhysicsBox = new VisCheckBox("Body");
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true);

    private final VisSelectBox<String> bodyTypeBox = new VisSelectBox<>();
    private final FloatField gravityScale;
    private final FloatField linearDamping;
    private final FloatField angularDamping;
    private final VisCheckBox fixedRotationBox = new VisCheckBox("Fixed rotation");
    private final VisCheckBox bulletBox = new VisCheckBox("Bullet");
    private final VisCheckBox allowSleepBox = new VisCheckBox("Allow sleep");
    private final VisLabel shapeSummaryLabel = new VisLabel(EMPTY_SHAPE_SUMMARY);
    private final VisLabel jointSummaryLabel = new VisLabel(EMPTY_JOINT_SUMMARY);
    private final VisTextButton editShapesBtn = new VisTextButton("Edit shapes");

    private final UiBinders.SelectBoxBinder<String> bodyTypeBinder;
    private final UiBinders.CheckBoxBinder fixedRotationBinder;
    private final UiBinders.CheckBoxBinder bulletBinder;
    private final UiBinders.CheckBoxBinder allowSleepBinder;

    private int entityId = -1;
    private boolean internalRefresh = false;

    private final int MY_TAG = EventFlow.tag(this);

    private boolean lastHasPhysics = false;
    private String lastShapeSummary = EMPTY_SHAPE_SUMMARY;
    private String lastJointSummary = EMPTY_JOINT_SUMMARY;

    public BodyPanel(EntityPropertiesContext ctx) {
        this(ctx, true);
    }

    public BodyPanel(EntityPropertiesContext ctx, boolean showBodyToggle) {
        super();
        this.ctx = ctx;
        this.showBodyToggle = showBodyToggle;
        this.mJointBase = ctx.world.getMapper(PhysicsJointComponent.class);
        this.mTiled = ctx.world.getMapper(TiledLayerComponent.class);

        editShapesBtn.setColor(CommonLayout.BUTTON_COLOR);

        gravityScale = new FloatField(
                ctx.world,
                eid -> ctx.mPhysBody.get(eid).gravityScale,
                this::hasPhysics
        ).setDisplayDecimals(2);

        linearDamping = new FloatField(
                ctx.world,
                eid -> ctx.mPhysBody.get(eid).linearDamping,
                this::hasPhysics
        ).setDisplayDecimals(2);

        angularDamping = new FloatField(
                ctx.world,
                eid -> ctx.mPhysBody.get(eid).angularDamping,
                this::hasPhysics
        ).setDisplayDecimals(2);

        gravityScale.setApplier((eid, v) ->
                submitBodyEdit(eid, s -> s.withGravityScale(v)));

        linearDamping.setApplier((eid, v) ->
                submitBodyEdit(eid, s -> s.withLinearDamping(Math.max(0f, v))));

        angularDamping.setApplier((eid, v) ->
                submitBodyEdit(eid, s -> s.withAngularDamping(Math.max(0f, v))));

        addPhysicsBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!showBodyToggle || internalRefresh || entityId < 0) return;

                boolean enableBody = addPhysicsBox.isChecked();
                boolean hadPhysicsBeforeToggle = hasPhysics(entityId);

                executeBodyToggle(enableBody, hadPhysicsBeforeToggle);
                refreshFromModel(entityId);

                EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(entityId, MY_TAG));
                event.handle();
            }
        });

        editShapesBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || entityId < 0 || !hasPhysics(entityId)) return;

                ctx.physicsSelectionService.focusBody(entityId);
                ctx.physicsSelectionService.clearSelectionOnly();

                EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(MY_TAG));
                refreshFromModel(entityId);
                event.handle();
            }
        });

        setTable(root);
        root.left().top();
        root.defaults().left().top().pad(1);

        VisTable d = detailsBlock.content();
        d.left().top().padTop(5);
        d.defaults().left().top().pad(1);

        d.add(new VisLabel("Type")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(bodyTypeBox).width(CommonLayout.FIELD_WIDTH).left().row();

        d.add(new VisLabel("Gravity scale")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(gravityScale).left().row();

        d.add(new VisLabel("Lin damping")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(linearDamping).left().row();

        d.add(new VisLabel("Ang damping")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(angularDamping).left().row();

        d.add(fixedRotationBox).left().colspan(2).row();
        d.add(bulletBox).left().colspan(2).row();
        d.add(allowSleepBox).left().colspan(2).row();

        d.addSeparator().colspan(2).growX().padTop(2).padBottom(2).row();
        d.add(new VisLabel("Summary")).center().colspan(2).row();

        d.add(new VisLabel("Shapes")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(shapeSummaryLabel).left().growX().row();

        d.add(new VisLabel("Joints")).width(CommonLayout.LABEL_WIDTH).left();
        d.add(jointSummaryLabel).left().growX().row();

        d.addSeparator().colspan(2).growX().padTop(2).padBottom(2).row();

        VisTable actions = new VisTable(true);
        actions.left().top();
        actions.defaults().left().top().padRight(4f);
        actions.add(editShapesBtn).left();

        d.add(actions).center().colspan(2).padTop(4).row();

        if (showBodyToggle) {
            root.add(addPhysicsBox).left().row();
            root.add(detailsBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().left().row();
        } else {
            root.add(detailsBlock).growX().left().row();
        }

        detailsBlock.show(false);

        bodyTypeBinder = new UiBinders.SelectBoxBinder<>(
                ctx.world,
                bodyTypeBox,
                this::hasPhysics,
                eid -> BODY_TYPES.get(clamp(ctx.mPhysBody.get(eid).type, BODY_TYPES.size - 1)),
                (eid, before, after) -> {
                    int idx = BODY_TYPES.indexOf(after, false);
                    int typeIdx = (idx < 0) ? PhysicsBodyComponent.DYNAMIC : idx;
                    submitBodyEdit(eid, s -> s.withType(typeIdx));
                }
        );
        bodyTypeBinder.setItems(BODY_TYPES);

        fixedRotationBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                fixedRotationBox,
                this::hasPhysics,
                eid -> ctx.mPhysBody.get(eid).fixedRotation,
                (eid, v) -> submitBodyEdit(eid, s -> s.withFixedRotation(v))
        );

        bulletBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                bulletBox,
                this::hasPhysics,
                eid -> ctx.mPhysBody.get(eid).bullet,
                (eid, v) -> submitBodyEdit(eid, s -> s.withBullet(v))
        );

        allowSleepBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                allowSleepBox,
                this::hasPhysics,
                eid -> ctx.mPhysBody.get(eid).allowSleep,
                (eid, v) -> submitBodyEdit(eid, s -> s.withAllowSleep(v))
        );
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        gravityScale.setEntityId(entityId);
        linearDamping.setEntityId(entityId);
        angularDamping.setEntityId(entityId);
        refreshFromModel(entityId);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (entityId < 0 || internalRefresh) return;

        boolean has = hasPhysics(entityId);
        String shapeSummary = buildShapeSummary(entityId);
        String jointSummary = buildJointSummary(entityId);

        if (has != lastHasPhysics
                || !shapeSummary.equals(lastShapeSummary)
                || !jointSummary.equals(lastJointSummary)) {
            refreshFromModel(entityId);
        }
    }

    private void executeBodyToggle(boolean enableBody, boolean hadPhysicsBeforeToggle) {
        boolean needsDefaultFixture = enableBody
                && !hadPhysicsBeforeToggle
                && shouldCreateDefaultFixture(entityId)
                && countFixtures(entityId) == 0;

        TogglePhysicsBodyCommand toggleCommand = new TogglePhysicsBodyCommand(
                ctx.world,
                ctx.history.historyIds(),
                ctx.physicsService,
                entityId,
                enableBody,
                PhysicsBodyComponent.DYNAMIC,
                !needsDefaultFixture
        );

        if (!needsDefaultFixture) {
            ctx.history.execute(toggleCommand);
            return;
        }

        List<Command> commands = new ArrayList<>(2);
        commands.add(toggleCommand);
        commands.add(new AddFixtureCommand(
                ctx.world,
                ctx.history.historyIds(),
                ctx.physicsSelectionService,
                ctx.physicsService,
                entityId
        ));

        ctx.history.execute(new CompositeCommand("Enable body with default shape", commands));
    }

    private void refreshFromModel(int eid) {
        internalRefresh = true;
        try {
            boolean has = hasPhysics(eid);

            if (showBodyToggle) {
                addPhysicsBox.setChecked(has);
            }

            detailsBlock.show(has);

            bodyTypeBinder.setEntityId(eid);
            fixedRotationBinder.setEntityId(eid);
            bulletBinder.setEntityId(eid);
            allowSleepBinder.setEntityId(eid);

            gravityScale.setEntityId(eid);
            linearDamping.setEntityId(eid);
            angularDamping.setEntityId(eid);

            String shapeSummary = buildShapeSummary(eid);
            String jointSummary = buildJointSummary(eid);

            shapeSummaryLabel.setText(shapeSummary);
            jointSummaryLabel.setText(jointSummary);

            editShapesBtn.setDisabled(!canAddShape(eid));

            lastHasPhysics = has;
            lastShapeSummary = shapeSummary;
            lastJointSummary = jointSummary;
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private boolean hasPhysics(int eid) {
        return eid >= 0 && ctx.mPhysBody.has(eid);
    }

    private boolean canAddShape(int eid) {
        return hasPhysics(eid);
    }

    private boolean shouldCreateDefaultFixture(int eid) {
        return eid >= 0 && !mTiled.has(eid);
    }

    private int countFixtures(int eid) {
        PhysicsShapesComponent fixtures = ctx.mPhysFixtures.getSafe(eid, null);
        if (fixtures == null || fixtures.shapes == null) return 0;
        return fixtures.shapes.size;
    }

    private String buildShapeSummary(int eid) {
        PhysicsShapesComponent fixtures = ctx.mPhysFixtures.getSafe(eid, null);
        if (fixtures == null || fixtures.shapes == null || fixtures.shapes.size == 0) {
            return EMPTY_SHAPE_SUMMARY;
        }

        int circles = 0;
        int quads = 0;
        int polygons = 0;

        for (int i = 0, n = fixtures.shapes.size; i < n; i++) {
            PhysicsShapeData f = fixtures.shapes.get(i);
            if (f == null) continue;
            if (f.shapeType == PhysicsShapeData.SHAPE_CIRCLE) circles++;
            else if (f.shapeType == PhysicsShapeData.SHAPE_BOX) quads++;
            else if (f.shapeType == PhysicsShapeData.SHAPE_POLYGON) polygons++;
        }

        return circles + " circles • " + quads + " quads • " + polygons + " polygons";
    }

    private String buildJointSummary(int eid) {
        return countJoints(eid) + " joints";
    }

    private int countJoints(int bodyEid) {
        if (bodyEid < 0) return 0;

        IntBag bag = ctx.world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int[] data = bag.getData();
        int count = 0;

        for (int i = 0, n = bag.size(); i < n; i++) {
            int jointEid = data[i];
            PhysicsJointComponent base = mJointBase.getSafe(jointEid, null);
            if (base == null) continue;
            if (base.aEid == bodyEid || base.bEid == bodyEid) count++;
        }
        return count;
    }

    private void submitBodyEdit(
            int eid,
            java.util.function.UnaryOperator<EditPhysicsBodyCommand.Snapshot> edit
    ) {
        if (eid < 0 || !hasPhysics(eid) || edit == null) return;

        EditPhysicsBodyCommand.Snapshot before =
                EditPhysicsBodyCommand.Snapshot.capture(ctx.mPhysBody.get(eid));
        EditPhysicsBodyCommand.Snapshot after = edit.apply(before);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(
                ctx.world,
                ctx.history.historyIds(),
                eid,
                before,
                after
        );
        executeCommand(command);
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        ctx.history.execute(command);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
