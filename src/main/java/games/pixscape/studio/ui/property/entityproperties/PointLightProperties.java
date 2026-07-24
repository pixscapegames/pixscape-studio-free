package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.MetaTagsHelper;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.SimpleTextField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.List;

public final class PointLightProperties extends VisTable {

    // === Mappers ===
    private final ComponentMapper<VisibilityComponent> mVisibility;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<PointLightComponent> mLight;
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

    private final SimpleTextField entityName = new SimpleTextField();
    private final VisTextButton editTagsBtn;

    private final VisCheckBox visibleCheckBox;
    private final UiBinders.CheckBoxBinder visibleBinder;

    // === Point Light ===
    private final CollapsibleVisTable pointLightBlock = new CollapsibleVisTable(true);
    private final VisCheckBox enabledCheckBox;
    private final UiBinders.CheckBoxBinder enabledBinder;

    private final FloatField intensityField;
    private final FloatField radiusField;
    private final FloatField falloffField;
    private final FloatField xField;
    private final FloatField yField;

    private final ColorPicker picker;
    private final VisImage colorImage = new VisImage(VisUI.getSkin().getDrawable("white"));
    private final VisTextButton buttonPicker = new VisTextButton("");
    private final UiBinders.ColorPickerBinder pickerBinder;

    private final Color tmpColor = new Color();

    // === Context ===
    private final EntityPropertiesContext ctx;

    // === State ===
    private int currentEntityId = -1;

    public PointLightProperties(EntityPropertiesContext ctx) {
        super(true);
        buttonPicker.setColor(CommonLayout.BUTTON_COLOR);
        this.ctx = ctx;

        mVisibility = ctx.world.getMapper(VisibilityComponent.class);
        mEntityIndex = ctx.world.getMapper(EntityIndexComponent.class);
        mLight = ctx.world.getMapper(PointLightComponent.class);
        mTransform = ctx.world.getMapper(TransformComponent.class);
        mDim = ctx.world.getMapper(DimensionsComponent.class);
        mShader = ctx.world.getMapper(ShaderParamsComponent.class);

        // === CommonLayout UI creation ===
        editTagsBtn = new VisTextButton("Edit tags…");
        editTagsBtn.setColor(CommonLayout.BUTTON_COLOR);
        editTagsBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openEditTagsDialog();
            }
        });


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

        // === Point Light UI ===
        enabledCheckBox = new VisCheckBox("Enabled");
        enabledBinder = new UiBinders.CheckBoxBinder(
                ctx.world,
                enabledCheckBox,
                mLight::has,
                (int e) -> {
                    PointLightComponent light = mLight.getSafe(e, null);
                    return light != null && light.enabled;
                },
                (Integer e, Boolean v) -> {
                    if (e == null) return;
                    PointLightComponent light = mLight.getSafe(e, null);
                    if (light != null && light.enabled != v) {
                        light.enabled = v;
                        markLightDirty(e);
                    }
                }
        );

        intensityField = new FloatField(ctx.world, this::readIntensity, this::hasPointLight).setDisplayDecimals(3);
        intensityField.setApplier((eid, v) -> setLight(eid, l -> l.intensity = Math.max(0f, v)));

        xField = new FloatField(ctx.world, this::readX, this::hasPointLight).setDisplayDecimals(3);
        xField.setApplier((eid, v) -> setPositionX(eid, v));

        yField = new FloatField(ctx.world, this::readY, this::hasPointLight).setDisplayDecimals(3);
        yField.setApplier((eid, v) -> setPositionY(eid, v));

        radiusField = new FloatField(ctx.world, this::readRadius, this::hasPointLight).setDisplayDecimals(3);
        radiusField.setApplier((eid, v) -> {
            float radius = Math.max(0f, v);
            setLight(eid, l -> l.radius = radius);
            setRadius(eid, radius);
        });

        falloffField = new FloatField(ctx.world, this::readFalloff, this::hasPointLight).setDisplayDecimals(3);
        falloffField.setApplier((eid, v) -> {
            float falloff = Math.max(0f, v);
            setLight(eid, l -> l.falloff = falloff);
            setFalloff(eid, falloff);
        });

        buttonPicker.setColor(Color.WHITE);
        buttonPicker.add(colorImage).width(50).height(25);

        picker = new ColorPicker("Point light color");
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

        VisTable lightContent = pointLightBlock.content();
        lightContent.left().top();
        lightContent.defaults().left().pad(1);
        lightContent.add(enabledCheckBox).left().colspan(2).row();
        lightContent.add(new VisLabel("X:"));
        lightContent.add(xField).width(120).left().row();
        lightContent.add(new VisLabel("Y:"));
        lightContent.add(yField).width(120).left().row();
        lightContent.add(new VisLabel("Color:"));
        lightContent.add(buttonPicker).left().colspan(2).row();
        lightContent.add(new VisLabel("Intensity:")).left();
        lightContent.add(intensityField).width(120).left().row();
        lightContent.add(new VisLabel("Radius:"));
        lightContent.add(radiusField).width(120).left().row();
        lightContent.add(new VisLabel("Falloff:"));
        lightContent.add(falloffField).width(120).left().row();

        add(pointLightBlock).growX().left().row();

        EventFlow.i().subscribe(EventFlow.EntityChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            onEntityChanged(evt.op());
        });
    }

    private void onEntityChanged(TransformOp op) {
        if (op == TransformOp.MOVE) {
            xField.refreshFromModel();
            yField.refreshFromModel();
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
        // EntityIndexComponent can be absent for some entities.
        EntityIndexComponent entityIndex = mEntityIndex.getSafe(entityId, null);

        int zIndex = (entityIndex != null) ? entityIndex.getZIndex() : 0;
        zIndexValueLabel.setText(String.valueOf(zIndex));

        int layerIndex = (entityIndex != null) ? entityIndex.getLayerIndex() : 0;
        String layerName = (ctx.layerService != null) ? ctx.layerService.getNameByIndex(layerIndex) : "";
        layerValueLabel.setText(layerName != null ? layerName : "");

        visibleBinder.setEntityId(entityId);

        // light fields
        enabledBinder.setEntityId(entityId);
        xField.setEntityId(entityId);
        yField.setEntityId(entityId);
        intensityField.setEntityId(entityId);
        radiusField.setEntityId(entityId);
        falloffField.setEntityId(entityId);
        pickerBinder.setEntityId(entityId);

        refreshTagsLabel();
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

    private boolean hasPointLight(int entityId) {
        return entityId >= 0
                && ctx.world.getEntityManager().isActive(entityId)
                && mLight.has(entityId);
    }

    private interface LightMutator {
        void apply(PointLightComponent light);
    }

    private void setLight(int entityId, LightMutator mutator) {
        if (!hasPointLight(entityId)) return;
        PointLightComponent light = mLight.get(entityId);
        if (light == null) return;
        mutator.apply(light);
        markLightDirty(entityId);
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

    private void setFalloff(int entityId, float value) {
        setShaderFloat(entityId, value);
    }

    private void setShaderFloat(int entityId, float value) {
        ShaderParamsComponent shader = mShader.get(entityId);
        if (shader == null) return;
        if (shader.floats == null) {
            shader.floats = new Array<>();
        }
        for (ShaderFloatParam param : shader.floats) {
            if (param != null && "u_falloff".equals(param.name)) {
                param.value = value;
                return;
            }
        }
        shader.floats.add(new ShaderFloatParam("u_falloff", value));
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
        PointLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.intensity : 0f;
    }

    private float readRadius(int entityId) {
        PointLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.radius : 0f;
    }

    private float readFalloff(int entityId) {
        PointLightComponent light = mLight.getSafe(entityId, null);
        return light != null ? light.falloff : 0f;
    }

    private float readX(int entityId) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        return transform != null ? transform.x : 0f;
    }

    private float readY(int entityId) {
        TransformComponent transform = mTransform.getSafe(entityId, null);
        return transform != null ? transform.y : 0f;
    }

    private int readColorPacked(int entityId) {
        PointLightComponent light = mLight.getSafe(entityId, null);
        if (light == null) return Color.rgba8888(Color.WHITE);
        tmpColor.set(light.r, light.g, light.b, 1f);
        return Color.rgba8888(tmpColor);
    }

    private void setLightColor(int entityId, int packed) {
        if (!hasPointLight(entityId)) return;
        PointLightComponent light = mLight.get(entityId);
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
            // radius = taille, falloff = shader param (mat), intensity/couleur = color
            ctx.dirtyTracker.geometry(entityId, GeometryDirty.SIZE);
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
