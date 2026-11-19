package games.pixscape.studio.ui.property.entityproperties;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.*;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.helper.MetaTagsHelper;
import games.pixscape.studio.history.commands.ChangeEntityNameCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.history.commands.AddPhysicsBodyCommand;
import games.pixscape.studio.history.commands.EditRenderRepeatCommand;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.physics.BodyPanel;
import games.pixscape.studio.ui.widget.SimpleTextField;
import games.pixscape.studio.ui.widget.UiBinders;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

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
    private final CustomPropertiesEditorRow customPropertiesRow;

    private final SimpleTextField entityName = new SimpleTextField();
    private final VisTextButton editTagsBtn;

    private final VisCheckBox visibleCheckBox;
    private final UiBinders.CheckBoxBinder visibleBinder;

    private final EntityPropertiesContext ctx;

    private final TransformPanel transformPanel;
    private final TexturePanel texturePanel;
    private final ToggleSection textureSection;
    private final MaterialPanel materialPanel;
    private final LightingPanel lightingPanel;
    private final AnimationPanel animationPanel;
    private final ParticleFxPanel particleFxPanel;
    private final RepeatablePanel repeatablePanel;
    private final SpatialActorPanel spatialPanel;
    private final BodyPanel bodyPanel;

    private final ToggleSection transformSection;
    private final ToggleSection materialSection;
    private final ToggleSection lightingSection;
    private final ToggleSection animationSection;
    private final ToggleSection particleSection;
    private final ToggleSection repeatableSection;
    private final ToggleSection spatialSection;
    private final ToggleSection physicsSection;
    private final ToggleSection gameObjectSection;
    private final VisLabel sourceAssetValue = new VisLabel("Scene-only");

    private int currentEntityId = -1;
    private boolean scenePhysicsEnabled = false;
    private final VisTextButton addComponent = new VisTextButton("Add component…");
    private final VisTextButton addChild = new VisTextButton("Add child…");
    private BiConsumer<Integer, Actor> addChildAction;
    private PopupMenu componentMenu;
    private boolean sectionsRefreshRequired;

    public EntityProperties(EntityPropertiesContext ctx,
                            StudioAssetVisualResolver visualResolver,
                            Supplier<Array<AssetMeta>> images,
                            IntConsumer onTextureBound) {
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
        customPropertiesRow = new CustomPropertiesEditorRow(ctx.world, ctx.history, ctx.sourceTag, ctx.markCurrentSceneSaveRequired);

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
        texturePanel = new TexturePanel(ctx, visualResolver, images, onTextureBound);
        materialPanel = new MaterialPanel(ctx);
        lightingPanel = new LightingPanel(ctx, false);
        animationPanel = new AnimationPanel(ctx);
        particleFxPanel = new ParticleFxPanel(ctx);
        repeatablePanel = new RepeatablePanel(ctx);
        spatialPanel = new SpatialActorPanel(ctx);
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
        textureSection = new ToggleSection("Texture", texturePanel.content());
        materialSection = new ToggleSection("Material", materialPanel);
        CollapsibleWidget lightingContent = new CollapsibleWidget();
        lightingContent.setTable(lightingPanel);
        lightingSection = new ToggleSection("Lighting", lightingContent);
        animationSection = new ToggleSection("Animation", animationPanel);
        particleSection = new ToggleSection("Particle FX", particleFxPanel);
        repeatableSection = new ToggleSection("Repeat", repeatablePanel);
        spatialSection = new ToggleSection("Spatial", spatialPanel);
        physicsSection = new ToggleSection("Physics", bodyPanel);
        spatialSection.setRemovalAction("remove-spatial", "Remove Spatial from this object and its descendants", spatialPanel::removeSpatial);
        physicsSection.setRemovalAction("remove-physics",
                "Remove Body, shapes and dependent joints.\nUse Undo to restore them and their previous physics parameters.",
                bodyPanel::removeBody);
        repeatableSection.setRemovalAction("remove-repeat", "Remove Repeat", repeatablePanel::removeRepeat);
        VisTable gameObjectPanel = new VisTable();
        gameObjectPanel.left();
        gameObjectPanel.add(sourceAssetValue).left();
        CollapsibleWidget gameObjectContent = new CollapsibleWidget();
        gameObjectContent.setTable(gameObjectPanel);
        gameObjectSection = new ToggleSection("Source asset", gameObjectContent);

        defaults().left().top().pad(5);

        add(buildCommonHeader()).growX().left().row();
        add(gameObjectSection).growX().left().pad(0).row();
        add(transformSection).growX().left().pad(0).row();
        add(textureSection).growX().left().pad(0).row();
        add(materialSection).growX().left().pad(0).row();
        add(animationSection).growX().left().pad(0).row();
        add(particleSection).growX().left().pad(0).row();
        add(repeatableSection).growX().left().pad(0).row();
        add(spatialSection).growX().left().pad(0).row();
        add(lightingSection).growX().left().pad(0).row();
        add(physicsSection).growX().left().pad(0).row();

        EventFlow.i().subscribe(EventFlow.EntityChanged.class, evt -> {
            closeComponentMenu();
            if (evt.entityId() != currentEntityId) return;
            onFieldsChanged(evt.op());
        });
        EventFlow.i().subscribe(EventFlow.EntityNameChanged.class, evt -> {
            if (evt.entityId() != currentEntityId) return;
            entityName.refresh();
        });
        EventFlow.i().subscribe(EventFlow.ShaderListChanged.class, evt -> materialPanel.refreshShaderList());
        EventFlow.i().subscribe(EventFlow.ScenePhysicsEnabledChanged.class, evt -> {
            syncScenePhysicsEnabled();
            updateSectionsVisibility();
        });
        EventFlow.i().subscribe(EventFlow.SceneLightingChanged.class, evt -> {
            if (evt.scene() != ctx.sceneMeta) {
                return;
            }
            closeComponentMenu();
            sectionsRefreshRequired = true;
        });
        EventFlow.i().subscribe(EventFlow.PhysicsBodyStructureChanged.class, evt -> {
            closeComponentMenu();
            if (evt.entityId() != currentEntityId) return;
            bodyPanel.setEntityId(currentEntityId);
            spatialPanel.refreshFromModel(currentEntityId);
            updateSectionsVisibility();
        });
        EventFlow.i().subscribe(EventFlow.LayerSpatialDepthChanged.class, evt -> {
            closeComponentMenu();
            sectionsRefreshRequired = true;
        });
        EventFlow.i().subscribe(EventFlow.SpatialActorChanged.class, evt -> {
            closeComponentMenu();
            // An ancestor may be the selected child's effective owner. Read after publication.
            sectionsRefreshRequired = true;
        });
        EventFlow.i().subscribe(EventFlow.RenderRepeatChanged.class, evt -> {
            closeComponentMenu();
            if (evt.entityId() != currentEntityId) return;
            repeatablePanel.refresh();
            sectionsRefreshRequired = true;
        });
        EventFlow.i().subscribe(EventFlow.ParticleFlagsChanged.class, evt -> {
            if (evt.entityId() == currentEntityId) particleFxPanel.refresh();
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
        type.add(icon).padRight(10).center();
        type.add(typeLabelValue).center();
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

        header.add(new VisLabel("Properties:")).width(CommonLayout.LABEL_WIDTH).left();
        header.add(customPropertiesRow).row();

        header.add(new VisLabel("Layer:")).width(CommonLayout.LABEL_WIDTH).left();
        new Tooltip.Builder("Members inherit the layer of their top-level Game Object.")
                .target(layerValueLabel).build();
        header.add(layerValueLabel).colspan(2).left().row();

        header.add(new VisLabel("Zindex:")).width(CommonLayout.LABEL_WIDTH).left();
        header.add(zIndexValueLabel).colspan(2).left().row();

        header.add(visibleCheckBox).colspan(3).left().padTop(6).row();
        addComponent.setName("entity-add-component");
        addChild.setName("entity-add-child");
        addComponent.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showComponentMenu();
            }
        });
        addChild.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (addChildAction != null) {
                    addChildAction.accept(currentEntityId, addChild);
                }
            }
        });
        VisTable actions = new VisTable(true);
        actions.add(addComponent);
        actions.add(addChild);
        header.add(actions).colspan(3).left().padTop(6).row();

        header.pack();
        return header;
    }

    public void setEntityId(int entityId) {
        if (entityId < 0 || !ctx.world.getEntityManager().isActive(entityId)) return;

        if (componentMenu != null) {
            componentMenu.remove();
        }
        currentEntityId = entityId;
        syncScenePhysicsEnabled();

        PixscapeIdentityComponent identity = ctx.mIdentity.getSafe(entityId, null);
        int stableId = displayedPersistentId(identity);
        entityIdValueLabel.setText(String.valueOf(stableId));
        icon.setDrawable(ctx.iconResolver.iconForEntity(entityId));

        typeLabelValue.setText(ctx.iconResolver.displayNameForEntity(entityId));

        entityName.refresh();

        EntityIndexComponent entityIndex = mEntityIndex.getSafe(entityId, null);
        boolean member = ctx.world.getMapper(GameObjectMemberComponent.class).has(entityId);
        GameObjectComponent gameObject = ctx.world.getMapper(GameObjectComponent.class)
                .getSafe(entityId, null);

        refreshZIndex();

        refreshLayerInfo();
        if (member) {
            zIndexValueLabel.setText("Local: " + (entityIndex != null ? entityIndex.zIndex : 0));
        }
        visibleCheckBox.setVisible(mV.has(entityId));
        String sourceAssetId = gameObject != null ? gameObject.sourceAssetId : null;
        sourceAssetValue.setText(sourceAssetId == null || sourceAssetId.isBlank()
                ? "Scene-only" : sourceAssetId);

        visibleBinder.setEntityId(entityId);

        transformPanel.setEntityId(entityId);
        texturePanel.setEntityId(entityId);
        refreshTransformTitle();
        materialPanel.setEntityId(entityId);
        lightingPanel.setEntityId(entityId);
        animationPanel.setEntityId(entityId);
        particleFxPanel.setEntityId(entityId);
        repeatablePanel.setEntityId(entityId);
        spatialPanel.setEntityId(entityId);
        bodyPanel.setEntityId(entityId);

        updateSectionsVisibility();
        refreshTagsLabel();
        customPropertiesRow.setEntityId(entityId);
    }

    public void refreshLayerInfo() {
        if (currentEntityId < 0 || !ctx.world.getEntityManager().isActive(currentEntityId)) return;
        layerValueLabel.setText(ctx.layerService.describeEffectiveLayer(currentEntityId));
        spatialPanel.refreshLayerEligibility();
        updateSectionsVisibility();
    }

    public void refreshTransformTitle() {
        if (currentEntityId < 0 || !ctx.world.getEntityManager().isActive(currentEntityId)) return;
        boolean member = ctx.world.getMapper(GameObjectMemberComponent.class).has(currentEntityId);
        transformSection.setTitle(member ? "Local Transform" : "Transform", member
                ? "Position, rotation and scale are relative to the parent Game Object." : null);
    }

    public void refreshZIndex() {
        refreshZIndexLabel(
                ctx.world, mEntityIndex, currentEntityId, zIndexValueLabel);
    }

    static void refreshZIndexLabel(
            com.artemis.World world,
            ComponentMapper<EntityIndexComponent> indexes,
            int entityId,
            VisLabel label) {
        if (world == null || indexes == null || label == null
                || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return;
        }
        EntityIndexComponent index = indexes.getSafe(entityId, null);
        label.setText(String.valueOf(index != null ? index.zIndex : 0));
    }

    static int displayedPersistentId(PixscapeIdentityComponent identity) {
        return identity != null && identity.stableId > 0 ? identity.stableId : 0;
    }

    private void updateSectionsVisibility() {
        textureSection.setApplicable(texturePanel.isApplicable(currentEntityId));
        lightingSection.setApplicable(ctx.sceneMeta != null && ctx.sceneMeta.lightingEnabled
                && currentEntityId >= 0 && ctx.mMat.has(currentEntityId)
                && !ctx.world.getMapper(ParticleEmitterComponent.class).has(currentEntityId));
        if (currentEntityId < 0) return;

        EntityMetaComponent meta = ctx.mMeta.getSafe(currentEntityId, null);
        EntityKind kind = (meta != null && meta.kind != null) ? meta.kind : EntityKind.UNKNOWN;

        boolean isParticle = kind == EntityKind.PARTICLE;
        boolean isAnim = ctx.world.getMapper(AnimationComponent.class).has(currentEntityId);
        GameObjectComponent composition = ctx.world.getMapper(GameObjectComponent.class).getSafe(currentEntityId, null);
        boolean tiledStructure = TiledEntityRole.of(ctx.world, currentEntityId)
                != TiledEntityRole.NONE;

        transformSection.setApplicable(true);
        gameObjectSection.setApplicable(composition != null && composition.sourceAssetId != null
                && !composition.sourceAssetId.isBlank() && !tiledStructure);
        addComponent.setVisible(!tiledStructure);
        addChild.setVisible(!tiledStructure);
        materialSection.setApplicable(isMaterialApplicable(kind, ctx.mMat.has(currentEntityId)));
        animationSection.setApplicable(isAnim);
        particleSection.setApplicable(isParticle);
        repeatableSection.setApplicable(ctx.mRepeat.has(currentEntityId) && !tiledStructure);

        boolean physicsApplicable = isPhysicsApplicable();
        spatialSection.setApplicable(isSpatialApplicable());
        physicsSection.setApplicable(physicsApplicable);
        spatialSection.setRemovalAvailable(ctx.world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).has(currentEntityId)
                && games.pixscape.studio.service.SpatialActorShapeSupport.ancestorOwner(ctx.world, currentEntityId) < 0, true);
        physicsSection.setRemovalAvailable(true, bodyPanel.canRemoveBody());
        repeatableSection.setRemovalAvailable(true, true);

        invalidateHierarchy();
    }

    static boolean isMaterialApplicable(EntityKind kind, boolean hasMaterial) {
        return kind != EntityKind.PARTICLE && hasMaterial;
    }

    private boolean isSpatialApplicable() {
        return currentEntityId >= 0 && ctx.mTransform.has(currentEntityId)
                && TiledEntityRole.of(ctx.world, currentEntityId)
                == TiledEntityRole.NONE
                && games.pixscape.studio.service.SpatialActorShapeSupport.hasActorState(ctx.world, currentEntityId);
    }

    private boolean isPhysicsApplicable() {
        return scenePhysicsEnabled && ctx.mPhysBody.has(currentEntityId)
                && TiledEntityRole.of(ctx.world, currentEntityId) == TiledEntityRole.NONE;
    }

    private void syncScenePhysicsEnabled() {
        SceneMeta meta = ctx.sceneMeta;
        scenePhysicsEnabled = meta != null && meta.physicsEnabled;
    }

    public void setAddChildAction(BiConsumer<Integer, Actor> action) {
        addChildAction = action;
    }

    private void closeComponentMenu() {
        if (componentMenu != null) {
            componentMenu.remove();
            componentMenu = null;
        }
    }

    @Override
    public void act(float delta) {
        if (sectionsRefreshRequired) {
            sectionsRefreshRequired = false;
            if (currentEntityId >= 0 && ctx.world.getEntityManager().isActive(currentEntityId)) {
                spatialPanel.setEntityId(currentEntityId);
                updateSectionsVisibility();
            }
        }
        super.act(delta);
    }

    private String physicsRejection() {
        if (ctx.sceneMeta == null || !ctx.sceneMeta.physicsEnabled) {
            return "Enable Physics in the scene first.";
        }
        return AddPhysicsBodyCommand.actorRejection(ctx.world, currentEntityId);
    }

    private String repeatRejection() {
        if (currentEntityId >= 0 && ctx.mRepeat.has(currentEntityId)) {
            return "Repeat is already present.";
        }
        return EditRenderRepeatCommand.activationRejection(ctx.world, currentEntityId);
    }

    private boolean isInspectedEntity(int entity) {
        return ctx.selectionService.isOnlySelected(entity)
                || ctx.selectionService.getInspectedFootprintOwner() == entity;
    }

    public void revealSpatial() {
        spatialSection.expand();
        validate();
        for (Actor ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll) {
                scroll.validate();
                var position = spatialSection.localToAscendantCoordinates(scroll.getActor(), new com.badlogic.gdx.math.Vector2());
                scroll.scrollTo(position.x, position.y, spatialSection.getWidth(), spatialSection.getHeight());
                scroll.updateVisualScroll();
                break;
            }
        }
    }

    private void showComponentMenu() {
        if (getStage() == null || currentEntityId < 0) {
            return;
        }
        if (componentMenu != null) {
            componentMenu.remove();
        }
        int target = currentEntityId;
        var identity = ctx.mIdentity.getSafe(target, null);
        int stableId = displayedPersistentId(identity);
        boolean physicsEnabled = ctx.sceneMeta != null && ctx.sceneMeta.physicsEnabled;
        boolean spatialLayer = ctx.layerService.admitsSpatialActor(target);
        componentMenu = new PopupMenu() {
            @Override
            public void act(float delta) {
                if (currentEntityId != target || EntityProperties.this.getStage() != getStage()
                        || !isInspectedEntity(target)
                        || displayedPersistentId(ctx.mIdentity.getSafe(target, null)) != stableId
                        || physicsEnabled != (ctx.sceneMeta != null && ctx.sceneMeta.physicsEnabled)
                        || spatialLayer != ctx.layerService.admitsSpatialActor(target)) {
                    remove();
                    return;
                }
                super.act(delta);
            }
        };
        if (spatialLayer && !games.pixscape.studio.service.SpatialActorShapeSupport.hasActorState(ctx.world, target)) {
            addCapabilityItem(componentMenu, "Spatial", spatialPanel.activationRejection(),
                    spatialPanel::activationRejection, spatialPanel::addFootprint);
        }
        if (!ctx.mPhysBody.has(target)) {
            addCapabilityItem(componentMenu, "Physics", physicsRejection(), this::physicsRejection, bodyPanel::addBody);
        }
        if (!ctx.mRepeat.has(target)) {
            String repeatReason = repeatRejection();
            MenuItem repeat = addCapabilityItem(componentMenu, "Repeat", repeatReason, this::repeatRejection, () -> { });
            if (repeatReason == null) {
                PopupMenu axes = new PopupMenu();
                addCapabilityItem(axes, "Repeat X", null, this::repeatRejection, () -> repeatablePanel.addRepeat(true, false));
                addCapabilityItem(axes, "Repeat Y", null, this::repeatRejection, () -> repeatablePanel.addRepeat(false, true));
                addCapabilityItem(axes, "Repeat X and Y", null, this::repeatRejection, () -> repeatablePanel.addRepeat(true, true));
                repeat.setSubMenu(axes);
                new Tooltip.Builder("Activation sets local rotation to 0°. Inherited rotation is unchanged.")
                        .target(repeat).build();
            }
        }
        if (componentMenu.getChildren().notEmpty()) {
            var position = addComponent.localToStageCoordinates(new com.badlogic.gdx.math.Vector2());
            componentMenu.showMenu(getStage(), position.x, position.y);
        }
    }

    private MenuItem addCapabilityItem(PopupMenu menu, String label, String reason,
                                       Supplier<String> validate, Runnable action) {
        if (reason != null) {
            return null;
        }
        MenuItem item = new MenuItem(label);
        int target = currentEntityId;
        item.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (item.isDisabled() || target != currentEntityId || !isInspectedEntity(target)) {
                    return;
                }
                String refusal = validate.get();
                if (refusal == null) {
                    action.run();
                    sectionsRefreshRequired = true;
                } else {
                    com.kotcrab.vis.ui.util.dialog.Dialogs.showOKDialog(getStage(), "Cannot add component", refusal);
                }
            }
        });
        menu.addItem(item);
        return item;
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
        texturePanel.requestRefresh();
        lightingPanel.setEntityId(currentEntityId);
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

    public void dispose() {
        materialPanel.dispose();
    }

}
