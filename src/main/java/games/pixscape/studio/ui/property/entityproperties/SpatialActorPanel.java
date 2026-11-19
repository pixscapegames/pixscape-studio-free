package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.service.SpatialActorShapeSupport;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;

public final class SpatialActorPanel extends CollapsibleWidget {
    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    private final CollapsibleVisTable ownFields = new CollapsibleVisTable(true);
    private final CollapsibleVisTable representationFields = new CollapsibleVisTable(true);
    private final CollapsibleVisTable inheritedMessage = new CollapsibleVisTable(false);
    private final VisLabel validationLabel = new VisLabel("");
    private final CollapsibleVisTable validationBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true);
    private final FloatField altitudeField;
    private final FloatField radiusField, offsetXField, offsetYField;
    private final ActorSpatialRectanglePanel rectanglePanel;
    private final FloatField[] footprintFields;
    private final float[] displayedFootprint = new float[4];

    private int entityId = -1;
    private int footprintOwner = -1;
    private final VisCheckBox inheritedToggle = new VisCheckBox("Inherited from parent");
    private boolean refreshingInheritance;

    public SpatialActorPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        altitudeField = new FloatField(
                ctx.world,
                eid -> ctx.world.getMapper(SpatialFootprintComponent.class).get(eid).altitude,
                this::hasMarkedFootprint
        ).setDisplayDecimals(2);

        altitudeField.setApplier((eid, value) ->
                submitSpatialEdit(eid, snapshot -> snapshot.withAltitude(value)));
        radiusField = new FloatField(ctx.world, eid -> ctx.world.getMapper(SpatialFootprintComponent.class).get(eid).radiusPx,
                this::hasMarkedFootprint).setDisplayDecimals(2);
        offsetXField = new FloatField(ctx.world, eid -> ctx.world.getMapper(SpatialFootprintComponent.class).get(eid).localOffsetXPx,
                this::hasMarkedFootprint).setDisplayDecimals(2);
        offsetYField = new FloatField(ctx.world, eid -> ctx.world.getMapper(SpatialFootprintComponent.class).get(eid).localOffsetYPx,
                this::hasMarkedFootprint).setDisplayDecimals(2);
        radiusField.setApplier((eid, value) -> submitSpatialEdit(eid, s -> s.withRadius(value)));
        offsetXField.setApplier((eid, value) -> submitSpatialEdit(eid, s -> s.withOffsetX(value)));
        offsetYField.setApplier((eid, value) -> submitSpatialEdit(eid, s -> s.withOffsetY(value)));
        footprintFields = new FloatField[]{altitudeField, radiusField, offsetXField, offsetYField};
        for (int i = 0; i < footprintFields.length; i++) {
            final FloatField field = footprintFields[i];
            field.setName("spatial-footprint-" + i);
            field.setKeepInvalidInput(true);
            field.setValidator(FloatField.finiteValidator(i == 1));
            field.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int key) {
                    if (key != com.badlogic.gdx.Input.Keys.ESCAPE) {
                        return false;
                    }
                    field.refreshFromModel();
                    event.stop();
                    return true;
                }
            });
        }
        setTable(root);
        root.left().top();
        root.defaults().left().top().pad(1);

        VisTable details = detailsBlock.content();
        details.left().top();
        details.defaults().left().top().pad(1);

        addGroupTitle(details, "Footprint", "Owner pose / local offsets (px)");
        inheritedToggle.setName("spatial-inherited-footprint");
        inheritedToggle.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (refreshingInheritance) {
                    return;
                }
                executeCommand(new games.pixscape.studio.history.commands.SetFootprintInheritanceCommand(
                        ctx.world, ctx.history.historyIds(), entityId, inheritedToggle.isChecked()));
                refreshFromModel(entityId);
                event.stop();
            }
        });
        inheritedMessage.content().add(inheritedToggle).left();
        details.add(inheritedMessage).colspan(2).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().row();
        VisTable fields = ownFields.content();
        fields.defaults().left().pad(1);
        String[] labels = {"Altitude (px):", "Radius (px):", "X (px):", "Y (px):"};
        for (int i = 0; i < footprintFields.length; i++) {
            fields.add(new VisLabel(labels[i])).width(CommonLayout.LABEL_WIDTH)
                    .padLeft(CommonLayout.PAD_LEFT_SUBMENU).expandX().left();
            fields.add(footprintFields[i]).width(CommonLayout.FIELD_WIDTH).row();
        }
        details.add(ownFields).colspan(2).pad(0).padBottom(15).growX().row();
        representationFields.content().left().top();
        representationFields.content().defaults().left().top().pad(1);
        rectanglePanel = new ActorSpatialRectanglePanel(ctx, representationFields.content());
        details.add(representationFields).colspan(2).pad(0).growX().row();

        validationLabel.setWrap(true);
        validationBlock.content().add(validationLabel).growX().left().row();
        root.add(validationBlock).width(CommonLayout.LABEL_WIDTH + CommonLayout.FIELD_WIDTH).left().row();
        root.add(detailsBlock).growX().left().row();
        detailsBlock.show(false);
    }

    static void addGroupTitle(VisTable table, String title, String coordinates) {
        VisLabel label = new VisLabel(title);
        table.add(label).colspan(2).left().padBottom(3).row();
        new com.kotcrab.vis.ui.widget.Tooltip.Builder(coordinates).target(label).build();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        rectanglePanel.refreshCurrentRepresentation();
        int owner = SpatialActorShapeSupport.owner(ctx.world, entityId);
        if (owner != footprintOwner) {
            refreshFromModel(entityId);
        }
        if (footprintOwner == entityId && hasMarkedFootprint(entityId)) {
            var footprint = ctx.world.getMapper(SpatialFootprintComponent.class).get(footprintOwner);
            for (int i = 0; i < footprintFields.length; i++) {
                float value = footprintValue(footprint, i);
                if (!footprintFields[i].hasKeyboardFocus() && displayedFootprint[i] != value) {
                    footprintFields[i].refreshFromModel();
                    displayedFootprint[i] = value;
                }
            }
        }
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        altitudeField.setEntityId(entityId);
        refreshFromModel(entityId);
    }

    public void refreshFromModel(int eid) {
        footprintOwner = SpatialActorShapeSupport.owner(ctx.world, eid);
        boolean own = hasMarkedFootprint(eid);
        showValidation("");
        ownFields.show(own);
        inheritedMessage.show(SpatialActorShapeSupport.ancestorOwner(ctx.world, eid) >= 0);
        refreshingInheritance = true;
        inheritedToggle.setChecked(!own);
        refreshingInheritance = false;
        if (!ctx.layerService.admitsSpatialActor(eid) && footprintOwner >= 0) {
            showValidation("Retained footprint: this object is outside the Spatial actor layer.");
        }
        detailsBlock.show(true);
        representationFields.show(eid >= 0 && ctx.mTexRegion.has(eid));
        rectanglePanel.refresh(eid);
        for (FloatField field : footprintFields) {
            field.setEntityId(own ? eid : -1);
            field.refreshFromModel();
        }
        invalidateHierarchy();
    }

    public void removeSpatial() {
        if (!hasMarkedFootprint(entityId) || SpatialActorShapeSupport.ancestorOwner(ctx.world, entityId) >= 0
                || getStage() == null) {
            return;
        }
        int target = entityId;
        long revision = ctx.history.getMutationRevision();
        var identity = ctx.mIdentity.getSafe(target, null);
        var command = new games.pixscape.studio.history.commands.RemoveSpatialSubtreeCommand(
                ctx.world, ctx.history.historyIds(), target);
        var dialog = new games.pixscape.studio.ui.modal.StudioDialog("Remove Spatial") {
            @Override
            protected void result(Object result) {
                if (Boolean.TRUE.equals(result) && ctx.history.getMutationRevision() == revision
                        && ctx.world.getEntityManager().isActive(target)
                        && ctx.mIdentity.getSafe(target, null) == identity) {
                    executeCommand(command);
                }
                refreshFromModel(entityId);
            }
        };
        dialog.setName("remove-spatial-dialog");
        dialog.text("Remove Spatial from this object and all its descendants?\n"
                + "Use Undo to restore all.");
        dialog.button("Cancel", false);
        dialog.button("OK", true);
        dialog.closeOnEscape();
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.show(getStage());
    }

    private static float footprintValue(SpatialFootprintComponent footprint, int property) {
        return switch (property) {
            case 0 -> footprint.altitude;
            case 1 -> footprint.radiusPx;
            case 2 -> footprint.localOffsetXPx;
            default -> footprint.localOffsetYPx;
        };
    }

    private void showValidation(String message) {
        validationLabel.setText(message);
        validationBlock.show(!message.isEmpty());
    }

    private boolean hasMarkedFootprint(int eid) {
        return eid >= 0 && ctx.world.getMapper(SpatialFootprintComponent.class).has(eid);
    }

    public void refreshLayerEligibility() {
        if (entityId < 0) {
            return;
        }
        refreshFromModel(entityId);
    }

    public String activationRejection() {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId) || !ctx.mTransform.has(entityId)) {
            return "A footprint requires an object with a transform.";
        }
        if (hasMarkedFootprint(entityId)) {
            return "This object already owns a footprint.";
        }
        if (SpatialActorShapeSupport.ancestorOwner(ctx.world, entityId) >= 0) {
            return "This object already belongs to a spatial composition.";
        }
        if (games.pixscape.runtime.tiled.TiledEntityRole.of(ctx.world, entityId)
                != games.pixscape.runtime.tiled.TiledEntityRole.NONE) {
            return "Tiled structures do not own actor footprints.";
        }
        return ctx.layerService.admitsSpatialActor(entityId) ? null
                : "Enable Spatial on this object's scene layer first (one actor layer per scene).";
    }

    public void addFootprint() {
        String rejection = activationRejection();
        if (rejection != null) {
            showValidation(rejection);
            return;
        }
        executeCommand(new ToggleSpatialActorCommand(ctx.world, ctx.history.historyIds(),
                entityId, true, true, createDefaultFootprint(entityId)));
        refreshFromModel(entityId);
    }

    private SpatialFootprintComponent createDefaultFootprint(int eid) {
        DimensionsComponent dimensions = ctx.mDimensions.getSafe(eid, null);
        TransformComponent transform = ctx.mTransform.getSafe(eid, null);
        if (transform == null) {
            return null;
        }
        SpatialFootprintComponent suggested = createDefaultFootprint(dimensions, transform);
        return suggested != null ? suggested : new SpatialFootprintComponent();
    }

    static SpatialFootprintComponent createDefaultFootprint(
            DimensionsComponent dimensions,
            TransformComponent transform) {
        if (dimensions == null || transform == null
                || !finite(dimensions.width) || !finite(dimensions.height)
                || !finite(transform.originX) || !finite(transform.originY)
                || !finite(transform.scaleX) || !finite(transform.scaleY)) {
            return null;
        }

        float x0 = -transform.originX * transform.scaleX;
        float x1 = (dimensions.width - transform.originX) * transform.scaleX;
        float y0 = -transform.originY * transform.scaleY;
        float y1 = (dimensions.height - transform.originY) * transform.scaleY;
        if (!finite(x0) || !finite(x1) || !finite(y0) || !finite(y1)) {
            return null;
        }

        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float visualWidthPx = maxX - minX;
        float diameterPx = visualWidthPx * 0.5f;
        float radiusPx = diameterPx * 0.5f;
        if (!finitePositive(visualWidthPx) || !finitePositive(radiusPx)) {
            return null;
        }
        float centerXPx = (minX + maxX) * 0.5f;
        float centerYPx = minY + radiusPx;
        if (!finite(centerXPx) || !finite(centerYPx)) {
            return null;
        }

        SpatialFootprintComponent shape = new SpatialFootprintComponent();
        shape.radiusPx = radiusPx;
        shape.localOffsetXPx = centerXPx;
        shape.localOffsetYPx = centerYPx;
        return shape;
    }

    private static boolean finitePositive(float value) {
        return finite(value) && value > 0f;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private void submitSpatialEdit(
            int eid,
            java.util.function.UnaryOperator<EditSpatialFootprintCommand.Snapshot> edit
    ) {
        if (eid < 0 || !hasMarkedFootprint(eid) || edit == null) {
            return;
        }

        SpatialFootprintComponent component = ctx.world.getMapper(SpatialFootprintComponent.class).get(eid);
        EditSpatialFootprintCommand.Snapshot before = EditSpatialFootprintCommand.Snapshot.capture(component);
        EditSpatialFootprintCommand.Snapshot after = edit.apply(before);
        if (!finite(after.altitude()) || !finite(after.offsetX()) || !finite(after.offsetY()) || !finitePositive(after.radius())) {
            return;
        }
        executeCommand(new EditSpatialFootprintCommand(
                ctx.world,
                ctx.history.historyIds(),
                eid,
                before,
                after
        ));
        refreshFromModel(entityId);
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        ctx.history.execute(command);
        if (ctx.markCurrentSceneSaveRequired != null) {
            ctx.markCurrentSceneSaveRequired.run();
        }
    }
}
