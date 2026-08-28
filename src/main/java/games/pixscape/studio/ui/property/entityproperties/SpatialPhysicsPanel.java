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
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditSpatialHeightCommand;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;

public final class SpatialPhysicsPanel extends CollapsibleWidget {
    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);
    private final VisCheckBox enabledBox = new VisCheckBox("Spatial Actor");
    private final VisLabel validationLabel = new VisLabel("");
    private final CollapsibleVisTable validationBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable detailsBlock = new CollapsibleVisTable(true);
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

                boolean enable = enabledBox.isChecked();
                ToggleSpatialActorCommand command = new ToggleSpatialActorCommand(
                        ctx.world,
                        ctx.history.historyIds(),
                        ctx.physicsService,
                        entityId,
                        enable,
                        isEligibleForActivation(entityId),
                        enable ? createDefaultFootprint(entityId) : null
                );
                if (command.isNoop()) {
                    validationLabel.setText(enable
                            ? "Spatial Actor requires valid visual bounds and one valid footprint."
                            : "Spatial Actor state contains conflicting footprints.");
                    validationBlock.show(true);
                } else {
                    validationLabel.setText("");
                    validationBlock.show(false);
                }
                executeCommand(command);
                refreshFromModel(entityId);
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

        details.add(new VisLabel("Height:")).width(CommonLayout.LABEL_WIDTH).left();
        details.add(heightField).width(CommonLayout.FIELD_WIDTH).left().row();

        validationBlock.content().add(validationLabel).left().row();
        root.add(enabledBox).left().row();
        root.add(validationBlock).growX().left().row();
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
            boolean actor = isSpatialActor(eid);
            enabledBox.setChecked(actor);
            enabledBox.setDisabled(!isEligibleForActivation(eid) && !actor);
            detailsBlock.show(actor);
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

    private boolean isSpatialActor(int eid) {
        return hasSpatialHeight(eid) || hasMarkedFootprint(eid);
    }

    private boolean hasMarkedFootprint(int eid) {
        if (eid < 0 || !ctx.mPhysFixtures.has(eid)) return false;
        PhysicsShapesComponent shapes = ctx.mPhysFixtures.get(eid);
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.spatialFootprint) return true;
        }
        return false;
    }

    private boolean isEligibleForActivation(int eid) {
        if (eid < 0 || ctx.layerService == null) return false;
        EntityMetaComponent meta = ctx.mMeta.getSafe(eid, null);
        EntityKind kind = meta != null ? meta.kind : EntityKind.UNKNOWN;
        if (kind != EntityKind.SPRITE && kind != EntityKind.ANIMATION) return false;
        EntityIndexComponent index = ctx.world.getMapper(EntityIndexComponent.class).getSafe(eid, null);
        if (index == null) return false;
        int layerIndex = index.getLayerIndex();
        int layerEntityId = ctx.layerService.getLayerEntity(layerIndex);
        LayerComponent layer = layerEntityId >= 0
                ? ctx.world.getMapper(LayerComponent.class).getSafe(layerEntityId, null)
                : null;
        return LayerService.isSpatialActorLayer(layer);
    }

    private PhysicsShapeData createDefaultFootprint(int eid) {
        DimensionsComponent dimensions = ctx.mDimensions.getSafe(eid, null);
        TransformComponent transform = ctx.mTransform.getSafe(eid, null);
        SceneMeta scene = ProjectConfig.getInstance() != null
                ? ProjectConfig.getInstance().getCurrentSceneMeta() : null;
        return dimensions != null && transform != null && scene != null
                ? createDefaultFootprint(dimensions, transform, scene.pixelsPerMeter)
                : null;
    }

    static PhysicsShapeData createDefaultFootprint(
            DimensionsComponent dimensions,
            TransformComponent transform,
            float pixelsPerMeter) {
        if (dimensions == null || transform == null || !finitePositive(pixelsPerMeter)
                || !finite(dimensions.width) || !finite(dimensions.height)
                || !finite(transform.originX) || !finite(transform.originY)
                || !finite(transform.scaleX) || !finite(transform.scaleY)) {
            return null;
        }

        float x0 = -transform.originX * transform.scaleX;
        float x1 = (dimensions.width - transform.originX) * transform.scaleX;
        float y0 = -transform.originY * transform.scaleY;
        float y1 = (dimensions.height - transform.originY) * transform.scaleY;
        if (!finite(x0) || !finite(x1) || !finite(y0) || !finite(y1)) return null;

        float minX = Math.min(x0, x1);
        float maxX = Math.max(x0, x1);
        float minY = Math.min(y0, y1);
        float visualWidthPx = maxX - minX;
        float diameterPx = visualWidthPx * 0.5f;
        float radiusPx = diameterPx * 0.5f;
        if (!finitePositive(visualWidthPx) || !finitePositive(radiusPx)) return null;
        float centerXPx = (minX + maxX) * 0.5f;
        float centerYPx = minY + radiusPx;
        if (!finite(centerXPx) || !finite(centerYPx)) return null;

        float radiusM = radiusPx / pixelsPerMeter;
        float centerXM = centerXPx / pixelsPerMeter;
        float centerYM = centerYPx / pixelsPerMeter;
        if (!finite(radiusM) || !finite(centerXM) || !finite(centerYM)) return null;

        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = radiusM;
        shape.geometry.offsetX = centerXM;
        shape.geometry.offsetY = centerYM;
        shape.enabled = true;
        shape.sensor = false;
        shape.spatialFootprint = true;
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
        if (ctx.markCurrentSceneSaveRequired != null) {
            ctx.markCurrentSceneSaveRequired.run();
        }
    }
}
