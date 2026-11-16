package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SpatialActorShapeSupport;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;

public final class SpatialActorPanel extends CollapsibleWidget {
    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    private final VisCheckBox enabledBox = new VisCheckBox("Spatial Actor");
    private final VisLabel validationLabel = new VisLabel("");
    private final CollapsibleVisTable validationBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true);
    private final FloatField altitudeField;
    private final FloatField radiusField, offsetXField, offsetYField;
    private final ActorSpatialRectanglePanel rectanglePanel;

    private int entityId = -1;
    private boolean internalRefresh = false;

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
        FloatField[] footprintFields = {altitudeField, radiusField, offsetXField, offsetYField};
        for (int i = 0; i < footprintFields.length; i++) {
            final FloatField field = footprintFields[i];
            field.setName("spatial-footprint-" + i);
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
        enabledBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || entityId < 0) {
                    return;
                }

                boolean enable = enabledBox.isChecked();
                PhysicsShapesComponent collision = ctx.mPhysFixtures.getSafe(entityId, null);
                if (!enable && collision != null && games.pixscape.runtime.service.PhysicsService.containsFootprintShape(collision.shapes)) {
                    refreshFromModel(entityId);
                    showValidation("Remove or unlink the footprint collision before disabling Spatial Actor.");
                    event.handle();
                    return;
                }
                boolean eligibleForActivation = isEligibleForActivation(entityId);
                ToggleSpatialActorCommand command = new ToggleSpatialActorCommand(
                        ctx.world,
                        ctx.history.historyIds(),
                        entityId,
                        enable,
                        eligibleForActivation,
                        enable && eligibleForActivation ? createDefaultFootprint(entityId) : null
                );
                if (enable && eligibleForActivation) {
                    games.pixscape.runtime.spatial.ActorSpatialRectangles rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
                    var animation = ctx.mAnim.getSafe(entityId, null);
                    if (animation == null) {
                        rectangles.staticRectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
                    } else {
                        for (int i = 0; i < animation.animationAssetIds.size; i++) {
                            int asset = animation.animationAssetIds.get(i);
                            if (!(ctx.assetMetaLookup.apply(asset) instanceof games.pixscape.studio.asset.AnimationAssetMeta meta)) {
                                throw new IllegalStateException("Missing animation asset " + asset);
                            }
                            for (String clip : meta.clips.keys()) {
                                rectangles.put(asset, clip, new games.pixscape.runtime.spatial.ActorSpatialRectangleData());
                            }
                        }
                    }
                    command.withRectangles(rectangles);
                }
                executeCommand(command);
                refreshFromModel(entityId);
                if (command.isNoop()) {
                    showValidation(enable
                            ? "Spatial Actor requires valid visual bounds and one valid footprint."
                            : "Spatial Actor state contains conflicting footprints.");
                }
                event.handle();
            }
        });

        setTable(root);
        root.left().top();
        root.defaults().left().top().pad(1);

        VisTable details = detailsBlock.content();
        details.left().top();
        details.defaults().left().top().pad(1);

        details.add(new VisLabel("Altitude:")).width(CommonLayout.LABEL_WIDTH).left();
        details.add(altitudeField).width(CommonLayout.FIELD_WIDTH).left().row();

        details.add(new VisLabel("Footprint radius (px):")).width(CommonLayout.LABEL_WIDTH);
        details.add(radiusField).width(CommonLayout.FIELD_WIDTH).row();
        details.add(new VisLabel("Footprint X (px):")).width(CommonLayout.LABEL_WIDTH);
        details.add(offsetXField).width(CommonLayout.FIELD_WIDTH).row();
        details.add(new VisLabel("Footprint Y (px):")).width(CommonLayout.LABEL_WIDTH);
        details.add(offsetYField).width(CommonLayout.FIELD_WIDTH).row();
        rectanglePanel = new ActorSpatialRectanglePanel(ctx, details);

        validationLabel.setWrap(true);
        validationBlock.content().add(validationLabel).growX().left().row();
        root.add(enabledBox).left().row();
        root.add(validationBlock).width(CommonLayout.LABEL_WIDTH + CommonLayout.FIELD_WIDTH).left().row();
        root.add(detailsBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).growX().left().row();
        detailsBlock.show(false);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        rectanglePanel.refreshCurrentRepresentation();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        altitudeField.setEntityId(entityId);
        refreshFromModel(entityId);
    }

    public void refreshFromModel(int eid) {
        internalRefresh = true;
        try {
            boolean height = hasSpatialRepresentation(eid);
            boolean footprint = hasMarkedFootprint(eid);
            boolean actor = SpatialActorShapeSupport.hasActorState(ctx.world, eid);
            if (height && !footprint) {
                String missing = "Spatial footprint missing.";
                showValidation(missing + " Undo the removal, or turn Spatial Actor off and on to create a footprint.");
            } else {
                showValidation("");
            }
            enabledBox.setChecked(actor);
            enabledBox.setDisabled(!isEligibleForActivation(eid) && !actor);
            detailsBlock.show(actor);
            rectanglePanel.refresh(eid);
            altitudeField.setEntityId(eid);
            altitudeField.refreshFromModel();
            for (FloatField field : new FloatField[]{radiusField, offsetXField, offsetYField}) {
                field.setEntityId(eid);
                field.refreshFromModel();
            }
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private void showValidation(String message) {
        validationLabel.setText(message);
        validationBlock.show(!message.isEmpty());
    }

    private boolean hasSpatialRepresentation(int eid) {
        return eid >= 0 && ctx.mSpatialRepresentation.has(eid);
    }

    private boolean hasMarkedFootprint(int eid) {
        return eid >= 0 && ctx.world.getMapper(SpatialFootprintComponent.class).has(eid);
    }

    public void refreshLayerEligibility() {
        if (entityId < 0) {
            return;
        }
        boolean actor = SpatialActorShapeSupport.hasActorState(ctx.world, entityId);
        enabledBox.setDisabled(!isEligibleForActivation(entityId) && !actor);
    }

    private boolean isEligibleForActivation(int eid) {
        if (eid < 0 || ctx.layerService == null) {
            return false;
        }
        EntityMetaComponent meta = ctx.mMeta.getSafe(eid, null);
        EntityKind kind = meta != null ? meta.kind : EntityKind.UNKNOWN;
        int layerEntityId = ctx.layerService.getEffectiveLayerEntity(eid);
        EntityIndexComponent index = ctx.mEntityIndex.getSafe(eid, null);
        LayerComponent layer = layerEntityId >= 0
                ? ctx.world.getMapper(LayerComponent.class).getSafe(layerEntityId, null)
                : null;
        SceneMeta scene = ctx.sceneMeta;
        return canActivateSpatialActor(scene, kind, index, layer);
    }

    static boolean canActivateSpatialActor(
            SceneMeta scene,
            EntityKind kind,
            EntityIndexComponent index,
            LayerComponent layer) {
        return scene != null
                && (kind == EntityKind.SPRITE || kind == EntityKind.ANIMATION)
                && index != null
                && LayerService.isSpatialActorLayer(layer);
    }

    private SpatialFootprintComponent createDefaultFootprint(int eid) {
        DimensionsComponent dimensions = ctx.mDimensions.getSafe(eid, null);
        TransformComponent transform = ctx.mTransform.getSafe(eid, null);
        SceneMeta scene = ctx.sceneMeta;
        return dimensions != null && transform != null && scene != null
                ? createDefaultFootprint(dimensions, transform)
                : null;
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
        if (eid < 0 || !hasSpatialRepresentation(eid) || !hasMarkedFootprint(eid) || edit == null) {
            return;
        }

        SpatialFootprintComponent component = ctx.world.getMapper(SpatialFootprintComponent.class).get(eid);
        EditSpatialFootprintCommand.Snapshot before = EditSpatialFootprintCommand.Snapshot.capture(component);
        EditSpatialFootprintCommand.Snapshot after = edit.apply(before);
        if (!finite(after.altitude()) || !finite(after.offsetX()) || !finite(after.offsetY()) || !finitePositive(after.radius())) {
            refreshFromModel(eid);
            return;
        }
        executeCommand(new EditSpatialFootprintCommand(
                ctx.world,
                ctx.history.historyIds(),
                eid,
                before,
                after,
                ctx.sceneMeta == null ? 0f : ctx.sceneMeta.pixelsPerMeter
        ));
        refreshFromModel(eid);
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
