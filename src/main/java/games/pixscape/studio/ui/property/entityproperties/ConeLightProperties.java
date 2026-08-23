package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.MetaTagsHelper;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.ui.StudioColorPickerFactory;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.SimpleTextField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.List;

public final class ConeLightProperties extends VisTable {

    private static final float MIN_ANGLE_DEG = 1f;
    private static final float MAX_ANGLE_DEG = 179f;

    // === Mappers ===
    private final ComponentMapper<VisibilityComponent> mVisibility;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<ConeLightComponent> mLight;
    private final ComponentMapper<TransformComponent> mTransform;
    private final ComponentMapper<ShaderParamsComponent> mShader;
    private final ComponentMapper<DimensionsComponent> mDim;


    // === CommonLayout ===
    private final VisLabel entityIdValueLabel = new VisLabel();
    private final VisLabel typeLabelValue = new VisLabel();
    private final VisImage icon = new VisImage();

    private final VisLabel zIndexValueLabel = new VisLabel();
    private final VisLabel layerValueLabel = new VisLabel();
    private final VisLabel tagsLabel = new VisLabel("");
    private final CustomPropertiesEditorRow customPropertiesRow;

    private final SimpleTextField entityName = new SimpleTextField();
    private final VisTextButton editTagsBtn;

    private final VisCheckBox visibleCheckBox;
    private final UiBinders.CheckBoxBinder visibleBinder;

    // === Light blocks ===
    private final CollapsibleVisTable coneBlock = new CollapsibleVisTable(true);

    private final VisCheckBox enabledCheckBox;
    private final UiBinders.CheckBoxBinder enabledBinder;

    private final FloatField intensityField;
    private final FloatField xField;
    private final FloatField yField;
    private final FloatField radiusField;
    private final FloatField coneAngleField;
    private final FloatField rotationField;
    private final FloatField softnessField;
    private final FloatField falloffField;

    private final ColorPicker picker;
    private final VisImage colorImage = new VisImage(VisUI.getSkin().getDrawable("white"));
    private final VisTextButton buttonPicker = new VisTextButton("");
    private final UiBinders.ColorPickerBinder pickerBinder;

    private final Color tmpColor = new Color();

    // === Context ===
    private final EntityPropertiesContext ctx;

    // === State ===
    private int currentEntityId = -1;

    public ConeLightProperties(EntityPropertiesContext ctx) {
        super(true);
        buttonPicker.setColor(CommonLayout.BUTTON_COLOR);
        this.ctx = ctx;

        mVisibility = ctx.world.getMapper(VisibilityComponent.class);
        mEntityIndex = ctx.world.getMapper(EntityIndexComponent.class);
        mLight = ctx.world.getMapper(ConeLightComponent.class);
        mTransform = ctx.world.getMapper(TransformComponent.class);
        mShader = ctx.world.getMapper(ShaderParamsComponent.class);
        mDim = ctx.world.getMapper(DimensionsComponent.class);
        editTagsBtn = new VisTextButton("Edit tags…");
        editTagsBtn.setColor(CommonLayout.BUTTON_COLOR);
        editTagsBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openEditTagsDialog();
            }
        });
        customPropertiesRow = new CustomPropertiesEditorRow(ctx);

        entityName.bind(
                () -> {
                    if (currentEntityId < 0) return "";
                    PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(currentEntityId, null);
                    return (identity != null && identity.name != null) ? identity.name : "";
                },
                this::applyEntityName
        );

        visibleCheckBox = new VisCheckBox("Visible");
        visibleBinder = new UiBinders.CheckBoxBinder(
                ctx.world, visibleCheckBox,
                mVisibility::has,
                (int e) -> mVisibility.get(e).isVisible(),
                (Integer e, Boolean v) -> {
                    if (e == null) return;
                    var c = mVisibility.getSafe(e, null);
                    if (c != null && c.visible != v) {
                        c.visible = v;
                    }
                }
        );

        enabledCheckBox = new VisCheckBox("Enabled");
        enabledBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                enabledCheckBox,
                mLight::has,
                (int e) -> {
                    ConeLightComponent light = mLight.getSafe(e, null);
                    return light != null && light.enabled;
                },
                (Integer e, Boolean v) -> {
                    if (e == null) return;
                    ConeLightComponent light = mLight.getSafe(e, null);
                    if (light != null && light.enabled != v) {
                        light.enabled = v;
                        markLightDirty(e);
                    }
                }
        );

        intensityField = new FloatField(ctx.world, this::readIntensity, this::hasConeLight).setDisplayDecimals(3);
        intensityField.setApplier((eid, v) -> {
            setLight(eid, l -> l.intensity = Math.max(0f, v));
        });

        xField = new FloatField(ctx.world, this::readX, this::hasConeLight).setDisplayDecimals(3);
        xField.setApplier((eid, v) -> setPositionX(eid, v));

        yField = new FloatField(ctx.world, this::readY, this::hasConeLight).setDisplayDecimals(3);
        yField.setApplier((eid, v) -> setPositionY(eid, v));

        radiusField = new FloatField(ctx.world, this::readRadius, this::hasConeLight).setDisplayDecimals(3);
        radiusField.setApplier((eid, v) -> {
            setLight(eid, l -> l.radius = Math.max(0f, v));
            setRadius(eid, Math.max(0f, v));
        });

        coneAngleField = new FloatField(ctx.world, this::readConeAngle, this::hasConeLight).setDisplayDecimals(2);
        coneAngleField.setApplier((eid, v) -> {
            setLight(eid, l -> l.coneAngleDeg = MathUtils.clamp(v, MIN_ANGLE_DEG, MAX_ANGLE_DEG));
            setConeAngle(eid, v);
        });

        rotationField = new FloatField(ctx.world, this::readRotationDeg, this::hasConeLight).setDisplayDecimals(2);
        rotationField.setApplier(this::setRotationDeg);

        softnessField = new FloatField(ctx.world, this::readSoftness, this::hasConeLight).setDisplayDecimals(3);
        softnessField.setApplier((eid, v) -> {
            float softness = MathUtils.clamp(v, 0f, 1f);
            setLight(eid, l -> l.softness = softness);
            setSoftness(eid, softness);
        });

        falloffField = new FloatField(ctx.world, this::readFalloff, this::hasConeLight).setDisplayDecimals(3);
        falloffField.setApplier((eid, v) -> {
            float falloff = Math.max(0f, v);
            setLight(eid, l -> l.falloff = falloff);
            setFalloff(eid, falloff);
        });

        buttonPicker.setColor(Color.WHITE);
        buttonPicker.add(colorImage).width(50).height(25);

        picker = StudioColorPickerFactory.create("Cone light color");
        pickerBinder = new UiBinders.ColorPickerBinder(
                ctx.world,
                picker,
                mLight::has,
                this::readColorPacked,
                (Integer e, Integer before, Integer after) -> setLightColor(e, after),
                colorImage::setColor
        );

        buttonPicker.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                if (getStage() != null) getStage().addActor(picker.fadeIn());
            }
        });

        // === Layout ===
        defaults().left().top().pad(5);

        add(buildCommonHeader()).growX().left().row();

        VisTable coneContent = coneBlock.content();
        coneContent.left().top();
        coneContent.defaults().left().pad(1);
        coneContent.add(enabledCheckBox).left().colspan(2).row();
        coneContent.add(new VisLabel("X:"));
        coneContent.add(xField).width(120).left().row();
        coneContent.add(new VisLabel("Y:"));
        coneContent.add(yField).width(120).left().row();
        coneContent.add(new VisLabel("Color:"));
        coneContent.add(buttonPicker).left().colspan(2).row();
        coneContent.add(new VisLabel("Intensity:")).left();
        coneContent.add(intensityField).width(120).left().row();
        coneContent.add(new VisLabel("Radius:"));
        coneContent.add(radiusField).width(120).left().row();
        coneContent.add(new VisLabel("Angle (deg):"));
        coneContent.add(coneAngleField).width(120).left().row();
        coneContent.add(new VisLabel("Rotation (deg):"));
        coneContent.add(rotationField).width(120).left().row();
        coneContent.add(new VisLabel("Softness:"));
        coneContent.add(softnessField).width(120).left().row();
        coneContent.add(new VisLabel("Falloff:"));
        coneContent.add(falloffField).width(120).left().row();

        add(coneBlock).growX().left().row();

        EventFlow.i().subscribe(EventFlow.EntityChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            onEntityChanged(evt.op());
        });
        EventFlow.i().subscribe(EventFlow.CustomPropertiesChanged.class, evt -> {
            if (evt.entityId() == currentEntityId) customPropertiesRow.refresh();
        });
    }

    private void onEntityChanged(TransformOp op) {
        if (op == TransformOp.MOVE) {
            xField.refreshFromModel();
            yField.refreshFromModel();
            return;
        }
        if (op == TransformOp.ROTATE) {
            rotationField.refreshFromModel();
            return;
        }
        if (op == TransformOp.SCALE) {
            radiusField.refreshFromModel();
        }
    }

    private VisTable buildCommonHeader() {
        VisTable header = new VisTable(true);
        header.left().top();
        header.defaults().left().pad(1);

        VisTable type = new VisTable(true);
        type.add(typeLabelValue).padRight(10);
        type.add(icon);
        header.add(type)
                .colspan(3)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .growX()
                .row();

        header.add(new VisLabel("Internal ID:")).left();
        header.add(entityIdValueLabel).colspan(2).left().row();

        header.add(new VisLabel("Name:")).left();
        header.add(entityName).colspan(2).width(200).left().row();

        header.add(new VisLabel("Tags:")).left();
        header.add(tagsLabel).left().width(100).growX();
        header.add(editTagsBtn).right().row();

        header.add(new VisLabel("Properties:")).left();
        header.add(customPropertiesRow).colspan(2).growX().left().row();

        header.add(new VisLabel("Layer:")).left();
        header.add(layerValueLabel).colspan(2).left().row();

        header.add(new VisLabel("Zindex:")).left();
        header.add(zIndexValueLabel).colspan(2).left().row();

        header.add(visibleCheckBox).colspan(3).left().padTop(6).row();

        header.pack();
        return header;
    }

    public void setEntityId(int entityId) {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId)) return;

        currentEntityId = entityId;

        PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(entityId, null);
        int stableId = (identity != null && identity.stableId > 0) ? identity.stableId : 0;
        entityIdValueLabel.setText(String.valueOf(stableId));
        icon.setDrawable(ctx.iconResolver.iconForEntity(entityId));

        EntityMetaComponent meta = ctx.mMeta.getSafe(entityId, null);
        EntityKind kind = (meta != null && meta.kind != null) ? meta.kind : EntityKind.UNKNOWN;
        typeLabelValue.setText(kind.name());
        entityName.refresh();
        EntityIndexComponent entityIndex = mEntityIndex.getSafe(entityId, null);

        int zIndex = (entityIndex != null) ? entityIndex.getZIndex() : 0;
        zIndexValueLabel.setText(String.valueOf(zIndex));

        int layerIndex = (entityIndex != null) ? entityIndex.getLayerIndex() : 0;
        String layerName = (ctx.layerService != null) ? ctx.layerService.getNameByIndex(layerIndex) : "";
        layerValueLabel.setText(layerName != null ? layerName : "");

        visibleBinder.setEntityId(entityId);

        enabledBinder.setEntityId(entityId);
        xField.setEntityId(entityId);
        yField.setEntityId(entityId);
        intensityField.setEntityId(entityId);
        radiusField.setEntityId(entityId);
        coneAngleField.setEntityId(entityId);
        rotationField.setEntityId(entityId);
        softnessField.setEntityId(entityId);
        falloffField.setEntityId(entityId);
        pickerBinder.setEntityId(entityId);

        refreshTagsLabel();
        customPropertiesRow.setEntityId(entityId);
    }

    private void refreshTagsLabel() {
        if (currentEntityId < 0) {
            tagsLabel.setText("");
            return;
        }
        PixscapeTagComponent tags = ctx.mTags.getSafe(currentEntityId, null);
        tagsLabel.setText(tags != null ? MetaTagsHelper.toCsv(tags.tags) : "");
    }

    private void openEditTagsDialog() {
        if (currentEntityId < 0) return;

        PixscapeTagComponent tags = ctx.mTags.has(currentEntityId) ? ctx.mTags.get(currentEntityId) : null;
        List<String> beforeTags =
                (tags != null && tags.tags != null)
                        ? MetaTagsHelper.toList(tags.tags)
                        : java.util.Collections.emptyList();

        EditTagsDialog dlg = new EditTagsDialog("Edit Tags", beforeTags, this::applyTags);
        dlg.show(getStage());
    }

    private void applyTags(Array<String> newTags) {
        if (currentEntityId < 0) return;

        ctx.tagRegistry.setTags(currentEntityId, newTags);
        refreshTagsLabel();
    }

    private boolean hasConeLight(int entityId) {
        return entityId >= 0
                && ctx.world.getEntityManager().isActive(entityId)
                && mLight.has(entityId);
    }

    private interface LightMutator {
        void apply(ConeLightComponent light);
    }

    private void setLight(int entityId, LightMutator mutator) {
        if (!hasConeLight(entityId)) return;
        ConeLightComponent light = mLight.get(entityId);
        if (light == null) return;
        mutator.apply(light);
        markLightDirty(entityId);
    }

    private void setConeAngle(int entityId, float value) {
        float angleDegrees = MathUtils.clamp(value, 1f, 179f) * 0.5f;
        setShaderFloat(entityId, "u_coneCos", MathUtils.cosDeg(angleDegrees));
    }

    private void setFalloff(int entityId, float value) {
        setShaderFloat(entityId, "u_falloff", value);
    }

    private void setSoftness(int entityId, float value) {
        setShaderFloat(entityId, "u_softness", value);
    }

    private void setShaderFloat(int entityId, String name, float value) {
        ShaderParamsComponent shader = mShader.get(entityId);
        if (shader == null || name == null || name.isEmpty()) return;
        if (shader.floats == null) {
            shader.floats = new Array<>();
        }
        for (ShaderFloatParam param : shader.floats) {
            if (param != null && name.equals(param.name)) {
                param.value = value;
                return;
            }
        }
        shader.floats.add(new ShaderFloatParam(name, value));
    }

    private void setRadius(int entityId, float value) {
        float d = value * 2f;
        DimensionsComponent dim = mDim.getSafe(entityId, null);
        dim.width = d;
        dim.height = d;
        TransformComponent t = mTransform.getSafe(entityId, null);
        t.originX = d * 0.5f;
        t.originY = d * 0.5f;
    }

    private void setRotationDeg(int entityId, float value) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        transform.rotationRad = MathUtils.degreesToRadians * value;
        markLightDirty(entityId);
    }

    private void setPositionX(int entityId, float value) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        if (transform == null || transform.x == value) return;
        transform.x = value;
        markPositionDirty(entityId);
    }

    private void setPositionY(int entityId, float value) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        if (transform == null || transform.y == value) return;
        transform.y = value;
        markPositionDirty(entityId);
    }

    private float readIntensity(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.intensity : 0f;
    }

    private float readRadius(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.radius : 0f;
    }

    private float readConeAngle(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.coneAngleDeg : 0f;
    }

    private float readX(int entityId) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        return transform != null ? transform.x : 0f;
    }

    private float readY(int entityId) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        return transform != null ? transform.y : 0f;
    }

    private float readRotationDeg(int entityId) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        if (transform != null) {
            return transform.rotationRad * MathUtils.radiansToDegrees;
        }
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.rotationDeg : 0f;
    }

    private float readSoftness(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.softness : 0f;
    }

    private float readFalloff(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.falloff : 0f;
    }

    private int readColorPacked(int entityId) {
        ConeLightComponent light = mLight.getSafe(entityId, null);
        if (light == null) return Color.rgba8888(Color.WHITE);
        tmpColor.set(light.r, light.g, light.b, 1f);
        return Color.rgba8888(tmpColor);
    }

    private void setLightColor(int entityId, int packed) {
        if (!hasConeLight(entityId)) return;
        ConeLightComponent light = mLight.get(entityId);
        if (light == null) return;
        Color.rgba8888ToColor(tmpColor, packed);
        if (light.r == tmpColor.r && light.g == tmpColor.g && light.b == tmpColor.b) return;
        light.r = tmpColor.r;
        light.g = tmpColor.g;
        light.b = tmpColor.b;
        markLightDirty(entityId);
    }

    private void markLightDirty(int entityId) {
        if (ctx.dirtyTracker != null) {
            ctx.dirtyTracker.geometry(entityId, GeometryDirty.SIZE | GeometryDirty.ROTATION);
            ctx.dirtyTracker.color(entityId);
            ctx.dirtyTracker.material(entityId);
        }
    }

    private void markPositionDirty(int entityId) {
        if (ctx.dirtyTracker != null) {
            ctx.dirtyTracker.geometry(entityId, GeometryDirty.POSITION);
        }
    }

    private void applyEntityName(String newName) {
        if (currentEntityId < 0 || !ctx.world.getEntityManager().isActive(currentEntityId)) return;

        PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(currentEntityId, null);
        String before = (identity != null && identity.name != null) ? identity.name : "";
        String after = newName != null ? newName : "";
        if (before.equals(after)) return;

        long historyId = ctx.history.historyIds().ensureForEntity(currentEntityId);
        ChangeEntityNameCommand command = new ChangeEntityNameCommand(
                ctx.world,
                ctx.history.historyIds(),
                historyId,
                before,
                after,
                ctx.sourceTag
        );

        if (!command.isNoop()) {
            ctx.history.execute(command);
        }
    }
}
