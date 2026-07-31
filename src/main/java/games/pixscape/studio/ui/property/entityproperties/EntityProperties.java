package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.*;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.MetaTagsHelper;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.physics.BodyPanel;
import games.pixscape.studio.ui.widget.SimpleTextField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.List;

public class EntityProperties extends VisTable {

    static final String ENTITY_ID_LABEL = "ID:";
    static final String ENTITY_ID_TOOLTIP = "Persistent ID of this entity.\n"
            + "This is not an Asset ID or an ECS entity ID.";

    private final ComponentMapper<VisibilityComponent> mV;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;

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

    private final EntityPropertiesContext ctx;

    private final TransformPanel transformPanel;
    private final MaterialPanel materialPanel;
    private final AnimationPanel animationPanel;
    private final ParticleFxPanel particleFxPanel;
    private final RepeatablePanel repeatablePanel;
    private final SpatialPhysicsPanel spatialPanel;
    private final BodyPanel bodyPanel;

    private final ToggleSection transformSection;
    private final ToggleSection materialSection;
    private final ToggleSection animationSection;
    private final ToggleSection particleSection;
    private final ToggleSection repeatableSection;
    private final ToggleSection spatialSection;
    private final ToggleSection physicsSection;

    private int currentEntityId = -1;
    private boolean scenePhysicsEnabled = false;

    public EntityProperties(EntityPropertiesContext ctx) {
        super(true);
        this.ctx = ctx;

        mV = ctx.world.getMapper(VisibilityComponent.class);
        mEntityIndex = ctx.world.getMapper(EntityIndexComponent.class);

        editTagsBtn = new VisTextButton("Edit tags…");
        editTagsBtn.setColor(CommonLayout.BUTTON_COLOR);
        editTagsBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openEditTagsDialog();
            }
        });

        visibleCheckBox = new VisCheckBox("Visible");
        visibleBinder = new UiBinders.CheckBoxBinder(
                ctx.world, visibleCheckBox,
                mV::has,
                (int e) -> mV.get(e).isVisible(),
                (Integer e, Boolean v) -> {
                    if (e == null) return;
                    var c = mV.getSafe(e, null);
                    if (c != null && c.visible != v) {
                        c.visible = v;
                    }
                }
        );

        transformPanel = new TransformPanel(ctx);
        materialPanel = new MaterialPanel(ctx);
        animationPanel = new AnimationPanel(ctx);
        particleFxPanel = new ParticleFxPanel(ctx);
        repeatablePanel = new RepeatablePanel(ctx);
        spatialPanel = new SpatialPhysicsPanel(ctx);
        bodyPanel = new BodyPanel(ctx, true);

        entityName.bind(
                () -> {
                    if (currentEntityId < 0) return "";
                    PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(currentEntityId, null);
                    return (identity != null && identity.name != null) ? identity.name : "";
                },
                this::applyEntityName
        );

        transformSection = new ToggleSection("Transform", transformPanel);
        materialSection = new ToggleSection("Material", materialPanel);
        animationSection = new ToggleSection("Animation", animationPanel);
        particleSection = new ToggleSection("Particle FX", particleFxPanel);
        repeatableSection = new ToggleSection("Repeatable", repeatablePanel);
        spatialSection = new ToggleSection("Spatial", spatialPanel);
        physicsSection = new ToggleSection("Physics", bodyPanel);

        defaults().left().top().pad(5);

        add(buildCommonHeader()).growX().left().row();
        add(transformSection).growX().left().pad(0).row();
        add(materialSection).growX().left().pad(0).row();
        add(animationSection).growX().left().pad(0).row();
        add(particleSection).growX().left().pad(0).row();
        add(repeatableSection).growX().left().pad(0).row();
        add(spatialSection).growX().left().pad(0).row();
        add(physicsSection).growX().left().pad(0).row();

        EventFlow.i().subscribe(EventFlow.EntityChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            onFieldsChanged(evt.op());
        });
        EventFlow.i().subscribe(EventFlow.EntityNameChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            entityName.refresh();
        });
        EventFlow.i().subscribe(EventFlow.ShaderListChanged.class, evt -> materialPanel.refreshShaderList());
        EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class, evt -> {
            scenePhysicsEnabled = evt.enabled();
            updateSectionsVisibility();
        });
        EventFlow.i().subscribe(EventFlow.PhysicsBodyStructureChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            bodyPanel.setEntityId(currentEntityId);
            updateSectionsVisibility();
        });
        EventFlow.i().subscribe(EventFlow.SpatialHeightChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            spatialPanel.setEntityId(currentEntityId);
            updateSectionsVisibility();
        });
        EventFlow.i().subscribe(EventFlow.RenderRepeatChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            repeatablePanel.refresh();
        });

        materialPanel.refreshShaderList();
        syncScenePhysicsEnabled();
        updateSectionsVisibility();
    }

    private VisTable buildCommonHeader() {
        VisTable header = new VisTable(true);
        header.left().top();
        header.defaults().left().pad(1);

        VisTable type = new VisTable(true);
        type.add(typeLabelValue).padRight(10).center();
        type.add(icon).center();
        header.add(type)
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .row();

        VisLabel entityIdLabel = new VisLabel(ENTITY_ID_LABEL);
        Tooltip entityIdTooltip = new Tooltip.Builder(ENTITY_ID_TOOLTIP)
                .target(entityIdLabel)
                .build();
        entityIdTooltip.setAppearDelayTime(0f);
        header.add(entityIdLabel).width(CommonLayout.LABEL_WIDTH).left();
        header.add(entityIdValueLabel).colspan(2).left().row();

        header.add(new VisLabel("Name:")).width(CommonLayout.LABEL_WIDTH).left();
        header.add(entityName).colspan(2).width(200).left().row();

        header.add(new VisLabel("Tags:")).width(CommonLayout.LABEL_WIDTH).left();
        VisTable tagsRight = new VisTable(true);
        tagsRight.add(tagsLabel).left().width(100).growX();
        editTagsBtn.setColor(CommonLayout.BUTTON_COLOR);
        tagsRight.add(editTagsBtn).right();
        header.add(tagsRight).row();

        header.add(new VisLabel("Layer:")).width(CommonLayout.LABEL_WIDTH).left();
        header.add(layerValueLabel).colspan(2).left().row();

        header.add(new VisLabel("Zindex:")).width(CommonLayout.LABEL_WIDTH).left();
        header.add(zIndexValueLabel).colspan(2).left().row();

        header.add(visibleCheckBox).colspan(3).left().padTop(6).row();

        header.pack();
        return header;
    }

    public void setEntityId(int entityId) {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId)) return;

        currentEntityId = entityId;
        syncScenePhysicsEnabled();

        PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(entityId, null);
        int stableId = displayedPersistentId(identity);
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

        transformPanel.setEntityId(entityId);
        materialPanel.setEntityId(entityId);
        animationPanel.setEntityId(entityId);
        particleFxPanel.setEntityId(entityId);
        repeatablePanel.setEntityId(entityId);
        spatialPanel.setEntityId(entityId);
        bodyPanel.setEntityId(entityId);

        updateSectionsVisibility();
        refreshTagsLabel();
    }

    static int displayedPersistentId(PixscapeIdentityComponent identity) {
        return identity != null && identity.stableId > 0 ? identity.stableId : 0;
    }

    private void updateSectionsVisibility() {
        if (currentEntityId < 0) return;

        EntityMetaComponent meta = ctx.mMeta.getSafe(currentEntityId, null);
        EntityKind kind = (meta != null && meta.kind != null) ? meta.kind : EntityKind.UNKNOWN;

        boolean isParticle = kind == EntityKind.PARTICLE;
        boolean isAnim = kind == EntityKind.ANIMATION;
        boolean isSprite = kind == EntityKind.SPRITE;

        transformSection.setApplicable(true);
        materialSection.setApplicable(!isParticle);
        animationSection.setApplicable(isAnim);
        particleSection.setApplicable(isParticle);
        repeatableSection.setApplicable(repeatablePanel.isApplicable() && (isSprite || isAnim));

        boolean physicsApplicable = isPhysicsApplicable();
        spatialSection.setApplicable(isSpatialApplicable(isSprite, isAnim));
        physicsSection.setApplicable(physicsApplicable);

        invalidateHierarchy();
    }

    private boolean isSpatialApplicable(boolean isSprite, boolean isAnim) {
        return (isSprite || isAnim) && isEntityInSpatialLayer();
    }

    private boolean isPhysicsApplicable() {
        return scenePhysicsEnabled && isEntityInPhysicsLayer();
    }

    private void syncScenePhysicsEnabled() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        SceneMeta meta = cfg != null ? cfg.getCurrentSceneMeta() : null;
        scenePhysicsEnabled = meta != null && meta.physicsEnabled;
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

    private void onFieldsChanged(TransformOp op) {
        transformPanel.onFieldsChanged(op);
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

    private boolean isEntityInPhysicsLayer() {
        if (currentEntityId < 0 || ctx.layerService == null || mEntityIndex == null) {
            return false;
        }
        EntityIndexComponent entityIndex = mEntityIndex.getSafe(currentEntityId, null);
        if (entityIndex == null) {
            return false;
        }
        int layerIndex = entityIndex.getLayerIndex();
        return ctx.layerService.getLayerTypeByIndex(layerIndex) == LayerComponent.TYPE_PHYSICS;
    }

    private boolean isEntityInSpatialLayer() {
        if (currentEntityId < 0 || ctx.layerService == null || mEntityIndex == null) {
            return false;
        }
        EntityIndexComponent entityIndex = mEntityIndex.getSafe(currentEntityId, null);
        if (entityIndex == null) {
            return false;
        }
        int layerIndex = entityIndex.getLayerIndex();
        int layerType = ctx.layerService.getLayerTypeByIndex(layerIndex);
        if (layerType != LayerComponent.TYPE_PHYSICS && layerType != LayerComponent.TYPE_TILED) {
            return false;
        }

        int layerEntityId = ctx.layerService.getLayerEntity(layerIndex);
        if (layerEntityId < 0) {
            return false;
        }

        LayerComponent layer = ctx.world.getMapper(LayerComponent.class).getSafe(layerEntityId, null);
        if (layer != null && layer.spatialEnabled) {
            return true;
        }

        TiledLayerComponent tiled = ctx.world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        return tiled != null && (tiled.spatialEnabled || (tiled.data != null && tiled.data.spatialEnabled));
    }

}
