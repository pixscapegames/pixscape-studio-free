package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditSpatialHeightCommand;
import games.pixscape.studio.history.commands.ToggleSpatialHeightCommand;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;

public final class SpatialPhysicsPanel extends CollapsibleWidget {
    private static final float DEFAULT_ACTOR_SPATIAL_HEIGHT = 1f;

    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    private final VisCheckBox enabledBox = new VisCheckBox("Enable Spatial");
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true, true);
    private final FloatField altitudeField;
    private final FloatField heightField;

    private int entityId = -1;
    private boolean internalRefresh = false;

    public SpatialPhysicsPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        altitudeField = new FloatField(
                ctx.world,
                eid -> ctx.mSpatialHeight.get(eid).altitude,
                this::hasSpatialHeight
        ).setDisplayDecimals(2);

        heightField = new FloatField(
                ctx.world,
                eid -> ctx.mSpatialHeight.get(eid).height,
                this::hasSpatialHeight
        ).setDisplayDecimals(2);

        altitudeField.setApplier((eid, value) ->
                submitSpatialEdit(eid, snapshot -> snapshot.withAltitude(value)));
        heightField.setApplier((eid, value) ->
                submitSpatialEdit(eid, snapshot -> snapshot.withHeight(Math.max(0f, value))));

        enabledBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || entityId < 0) return;

                ToggleSpatialHeightCommand command = new ToggleSpatialHeightCommand(
                        ctx.world,
                        ctx.history.historyIds(),
                        entityId,
                        enabledBox.isChecked(),
                        0f,
                        DEFAULT_ACTOR_SPATIAL_HEIGHT
                );
                executeCommand(command);
                refreshFromModel(entityId);
                event.handle();
            }
        });

        setTable(root);
        root.left().top();
        root.defaults().left().top().pad(1);

        VisTable details = detailsBlock.content();
        details.left().top().padTop(5);
        details.defaults().left().top().pad(1);

        details.add(new VisLabel("Altitude:")).width(CommonLayout.LABEL_WIDTH).left();
        details.add(altitudeField).width(CommonLayout.FIELD_WIDTH).left().row();

        details.add(new VisLabel("Height:")).width(CommonLayout.LABEL_WIDTH).left();
        details.add(heightField).width(CommonLayout.FIELD_WIDTH).left().row();

        root.add(enabledBox).left().row();
        root.add(detailsBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().left().row();
        detailsBlock.show(false);
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        altitudeField.setEntityId(entityId);
        heightField.setEntityId(entityId);
        refreshFromModel(entityId);
    }

    public void refreshFromModel(int eid) {
        internalRefresh = true;
        try {
            boolean has = hasSpatialHeight(eid);
            enabledBox.setChecked(has);
            detailsBlock.show(has);
            altitudeField.setEntityId(eid);
            heightField.setEntityId(eid);
            altitudeField.refreshFromModel();
            heightField.refreshFromModel();
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private boolean hasSpatialHeight(int eid) {
        return eid >= 0 && ctx.mSpatialHeight.has(eid);
    }

    private void submitSpatialEdit(
            int eid,
            java.util.function.UnaryOperator<EditSpatialHeightCommand.Snapshot> edit
    ) {
        if (eid < 0 || !hasSpatialHeight(eid) || edit == null) return;

        SpatialHeightComponent component = ctx.mSpatialHeight.get(eid);
        EditSpatialHeightCommand.Snapshot before = EditSpatialHeightCommand.Snapshot.capture(component);
        EditSpatialHeightCommand.Snapshot after = edit.apply(before);
        executeCommand(new EditSpatialHeightCommand(
                ctx.world,
                ctx.history.historyIds(),
                eid,
                before,
                after
        ));
        refreshFromModel(eid);
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        ctx.history.execute(command);
        if (ctx.markPreviewSaveRequired != null) {
            ctx.markPreviewSaveRequired.run();
        }
    }
}
