package games.pixscape.studio.ui.property;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.SimpleFloatSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.*;

public class LayerProperties extends VisTable {

    private final World world;
    private final HistoryManager history;
    private final PhysicsService physicsService;
    private final LayerService layerService;

    private final ComponentMapper<LayerComponent> mIndex;
    private final ComponentMapper<LayerParallaxComponent> mParallax;
    private final ComponentMapper<PhysicsBodyComponent> mPhysBody;
    private final ComponentMapper<TiledLayerComponent> mTiled;

    public final VisLabel indexValueLabel;
    public final TextField nameField;
    public final TextField descriptionField;
    public final VisLabel typeValueLabel;

    private final TiledMapProperties tiledMapProperties;

    private final UiBinders.FloatSpinnerBinder parallaxXBinder;
    private final UiBinders.FloatSpinnerBinder parallaxYBinder;

    private final VisCheckBox parallaxCheckBox;
    private final VisCheckBox collisionsCheckBox;
    private final VisCheckBox spatialCheckBox;
    private final FloatField defaultAltitudeField;
    private final FloatField defaultHeightField;

    private final CollapsibleVisTable parallaxSection = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable parallaxBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable collisionsSection = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable spatialSection = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable spatialBlock = new CollapsibleVisTable(true, true);
    private final CollapsibleVisTable tiledSection = new CollapsibleVisTable(true, true);

    private final int MY_TAG = EventFlow.tag(this);
    private boolean internalParallaxRefresh = false;
    private boolean internalCollisionsRefresh = false;
    private boolean internalSpatialRefresh = false;
    private final Runnable markCurrentSceneSaveRequired;

    public LayerProperties(
            World world, HistoryManager history, PhysicsService physicsService,
            LayerService layerService,
            Runnable markCurrentSceneSaveRequired) {
        super(true);
        this.world = world;
        this.history = history;
        this.physicsService = physicsService;
        this.layerService = layerService;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;

        this.mIndex = world.getMapper(LayerComponent.class);
        this.mParallax = world.getMapper(LayerParallaxComponent.class);
        this.mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.tiledMapProperties = new TiledMapProperties(world, markCurrentSceneSaveRequired);

        UiFieldFactory factory = new UiFieldFactory(world);

        VisLabel nameLabel = new VisLabel("Name:");
        VisLabel descriptionLabel = new VisLabel("Description:");
        VisLabel indexLabel = new VisLabel("Index:");
        VisLabel typeLabel = new VisLabel("Type:");

        indexValueLabel = new VisLabel();
        typeValueLabel = new VisLabel();
        nameField = factory.layerName();
        nameField.onEnter(() -> {
            EventFlow.i().publish(
                    new EventFlow.LayerNameChanged(nameField.getEntityId(),
                            nameField.getText(),
                            MY_TAG));
        });
        descriptionField = factory.layerDescription();

        parallaxCheckBox = new VisCheckBox("Parallax");
        parallaxCheckBox.left();

        collisionsCheckBox = new VisCheckBox("Collisions");
        collisionsCheckBox.left();

        spatialCheckBox = new VisCheckBox("Spatial Depth");
        spatialCheckBox.left();

        defaultAltitudeField = new FloatField(
                world,
                eid -> mTiled.get(tiledMapEntityId(eid)).defaultTileAltitude,
                this::hasTiledSpatialDefaults
        ).setDisplayDecimals(2);

        defaultHeightField = new FloatField(
                world,
                eid -> mTiled.get(tiledMapEntityId(eid)).defaultTileHeight,
                this::hasTiledSpatialDefaults
        ).setDisplayDecimals(2);

        defaultAltitudeField.setApplier((eid, value) ->
                submitTiledSpatialEdit(eid, snapshot -> snapshot.withDefaultAltitude(value)));
        defaultHeightField.setApplier((eid, value) ->
                submitTiledSpatialEdit(eid, snapshot -> snapshot.withDefaultHeight(Math.max(0f, value))));

        SimpleFloatSpinnerModel modelX = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);
        SimpleFloatSpinnerModel modelY = new SimpleFloatSpinnerModel(1f, 0f, 10f, 0.01f);

        Spinner parallaxXSpinner = new Spinner("Parallax x", modelX);
        Spinner parallaxYSpinner = new Spinner("Parallax y", modelY);
        modelX.setPrecision(2);
        modelY.setPrecision(2);

        parallaxXBinder = new UiBinders.FloatSpinnerBinder(
                world,
                parallaxXSpinner,
                modelX,
                mParallax::has,
                eid -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    return (lp != null) ? lp.factorX : 1f;
                },
                (eid, value) -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    if (lp == null) return;
                    lp.factorX = value;
                    flagPreviewSaveRequired();
                }
        );

        parallaxYBinder = new UiBinders.FloatSpinnerBinder(
                world,
                parallaxYSpinner,
                modelY,
                mParallax::has,
                eid -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    return (lp != null) ? lp.factorY : 1f;
                },
                (eid, value) -> {
                    LayerParallaxComponent lp = mParallax.getSafe(eid, null);
                    if (lp == null) return;
                    lp.factorY = value;
                    flagPreviewSaveRequired();
                }
        );

        parallaxCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalParallaxRefresh) return;

                int layerEntityId = nameField.getEntityId();
                if (layerEntityId == -1) return;
                if (!supportsEditableParallax(layerEntityId)) return;

                if (parallaxCheckBox.isChecked()) {
                    parallaxBlock.show(true);
                    LayerParallaxComponent lp = mParallax.getSafe(layerEntityId, null);
                    if (lp == null) {
                        lp = mParallax.create(layerEntityId);
                        lp.factorX = 1f;
                        lp.factorY = 1f;
                        flagPreviewSaveRequired();
                    }
                    parallaxXBinder.setEntityId(layerEntityId);
                    parallaxYBinder.setEntityId(layerEntityId);
                } else {
                    parallaxBlock.show(false);
                    if (mParallax.has(layerEntityId)) {
                        mParallax.remove(layerEntityId);
                        flagPreviewSaveRequired();
                    }
                    parallaxXBinder.setEntityId(-1);
                    parallaxYBinder.setEntityId(-1);
                }
            }
        });

        collisionsCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalCollisionsRefresh) return;

                int layerEntityId = nameField.getEntityId();
                if (layerEntityId < 0 || !isTiledLayer(layerEntityId)) return;
                if (!isScenePhysicsEnabled()) {
                    refreshFromModel(layerEntityId);
                    return;
                }

                boolean currentlyActive = mPhysBody.has(tiledMapEntityId(layerEntityId));
                boolean requestedActive = collisionsCheckBox.isChecked();

                if (requestedActive == currentlyActive) {
                    refreshFromModel(layerEntityId);
                    return;
                }

                if (requestedActive) {
                    addPhysicsToTiledLayer(layerEntityId);
                    refreshFromModel(layerEntityId);
                    return;
                }

                showRemoveCollisionsDialog(layerEntityId);
            }
        });

        spatialCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalSpatialRefresh) return;

                int layerEntityId = nameField.getEntityId();
                if (layerEntityId < 0) return;

                boolean currentlyActive = isLayerSpatialEnabled(layerEntityId);
                boolean requestedActive = spatialCheckBox.isChecked();
                if (requestedActive == currentlyActive) {
                    refreshFromModel(layerEntityId);
                    return;
                }

                if (!isTiledLayer(layerEntityId)) {
                    if (requestedActive && !isScenePhysicsEnabled()) {
                        refreshFromModel(layerEntityId);
                        return;
                    }
                    executeOrdinarySpatialToggle(layerEntityId, requestedActive);
                    refreshFromModel(layerEntityId);
                    return;
                }

                if (requestedActive) {
                    executeTiledSpatialToggle(layerEntityId, true);
                    refreshFromModel(layerEntityId);
                    return;
                }

                showDisableSpatialDialog(layerEntityId);
                event.handle();
            }
        });

        parallaxBlock.content().add(parallaxXSpinner).width(80).left().growX().row();
        parallaxBlock.content().add(parallaxYSpinner).width(80).left().growX().row();
        parallaxBlock.show(false);

        VisTable spatialDetails = spatialBlock.content();
        spatialDetails.left().top().padTop(5);
        spatialDetails.defaults().left().top().pad(1);

        spatialDetails.add(new VisLabel("Default Altitude:")).width(CommonLayout.LABEL_WIDTH).left();
        spatialDetails.add(defaultAltitudeField).width(CommonLayout.FIELD_WIDTH).left().row();
        spatialDetails.add(new VisLabel("Default Height:")).width(CommonLayout.LABEL_WIDTH).left();
        spatialDetails.add(defaultHeightField).width(CommonLayout.FIELD_WIDTH).left().row();
        spatialBlock.show(false);

        add(new VisLabel("LAYER"))
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .colspan(2)
                .row();

        add(nameLabel).left();
        add(nameField).growX().row();

        add(descriptionLabel).top().left();
        add(descriptionField).growX().row();

        add(typeLabel).left();
        add(typeValueLabel).left().row();

        add(indexLabel).left();
        add(indexValueLabel).left().row();

        parallaxSection.content().addSeparator().growX().row();
        parallaxSection.content().add(parallaxCheckBox).left().growX().row();
        parallaxSection.content().add(parallaxBlock).padLeft(55).left().growX().row();
        parallaxSection.show(false);

        collisionsSection.content().addSeparator().growX().row();
        collisionsSection.content().add(collisionsCheckBox).left().growX().row();
        collisionsSection.show(false);

        VisLabel spatialTitle = new VisLabel("SPATIAL");
        spatialTitle.setAlignment(Align.center);

        spatialSection.content().addSeparator().growX().row();
        spatialSection.content().add(spatialTitle)
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .expandX()
                .row();
        spatialSection.content().add(spatialCheckBox).left().growX().row();
        spatialSection.content().add(spatialBlock).padLeft(CommonLayout.PAD_LEFT_SUBMENU).left().growX().row();
        spatialSection.show(false);

        tiledSection.content().addSeparator().growX().row();
        tiledSection.content().add(tiledMapProperties).growX().row();
        tiledSection.show(false);

        add(parallaxSection).colspan(2).left().growX().row();
        add(collisionsSection).colspan(2).left().growX().row();
        add(spatialSection).colspan(2).left().growX().row();
        add(tiledSection).colspan(2).left().growX().row();
    }

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    public void setLayerEntityId(int layerEntityId) {
        nameField.setEntityId(layerEntityId);
        descriptionField.setEntityId(layerEntityId);
        refreshFromModel(layerEntityId);
    }

    private void refreshFromModel(int layerEntityId) {
        LayerComponent lic = mIndex.getSafe(layerEntityId, null);
        if (lic == null) {
            Gdx.app.error(
                    "LayerProperties",
                    "Layer entity has no LayerComponent. layerEntityId=" +
                            layerEntityId +
                            " (likely stale id after world rebuild)"
            );
            return;
        }

        indexValueLabel.setText(lic.layerIndex);
        typeValueLabel.setText(buildLayerTypeLabel(lic.type));

        boolean isTiled = lic.type == LayerComponent.TYPE_TILED;
        boolean scenePhysicsEnabled = isScenePhysicsEnabled();
        boolean collisionsSupported = isTiled && scenePhysicsEnabled;
        int mapEntityId = isTiled ? tiledMapEntityId(layerEntityId) : -1;
        boolean collisionsActive = collisionsSupported && mPhysBody.has(mapEntityId);
        boolean isOrdinary = lic.type == LayerComponent.TYPE_CLASSIC;
        boolean ordinarySpatialVisible = isOrdinary && shouldShowOrdinarySpatialProperty(
                lic.spatialEnabled,
                scenePhysicsEnabled,
                layerService.hasOtherSpatialActorLayer(layerEntityId));
        boolean spatialSupported = isTiled || ordinarySpatialVisible;
        boolean spatialActive = isLayerSpatialEnabled(layerEntityId);
        boolean supportsParallax = supportsEditableParallax(layerEntityId, lic);
        boolean hasParallax = mParallax.has(layerEntityId);

        internalParallaxRefresh = true;
        try {
            parallaxCheckBox.setChecked(hasParallax);
            parallaxSection.show(supportsParallax);
            parallaxBlock.show(supportsParallax && hasParallax);
        } finally {
            internalParallaxRefresh = false;
        }

        if (supportsParallax) {
            parallaxXBinder.setEntityId(layerEntityId);
            parallaxYBinder.setEntityId(layerEntityId);
        } else {
            parallaxXBinder.setEntityId(-1);
            parallaxYBinder.setEntityId(-1);
        }

        internalCollisionsRefresh = true;
        try {
            collisionsCheckBox.setChecked(collisionsActive);
            collisionsSection.show(collisionsSupported);
        } finally {
            internalCollisionsRefresh = false;
        }

        internalSpatialRefresh = true;
        try {
            spatialCheckBox.setText(isTiled ? "Spatial Depth" : "Spatial");
            spatialCheckBox.setChecked(spatialActive);
            spatialSection.show(spatialSupported);
            spatialBlock.show(isTiled && spatialActive);
            defaultAltitudeField.setEntityId(isTiled && spatialActive ? layerEntityId : -1);
            defaultHeightField.setEntityId(isTiled && spatialActive ? layerEntityId : -1);
            if (isTiled && spatialActive) {
                defaultAltitudeField.refreshFromModel();
                defaultHeightField.refreshFromModel();
            }
        } finally {
            internalSpatialRefresh = false;
        }

        tiledSection.show(isTiled);
        if (isTiled) {
            tiledMapProperties.setMapEntityId(mapEntityId);
        }

        invalidateHierarchy();
    }

    private String buildLayerTypeLabel(int type) {
        if (type != LayerComponent.TYPE_TILED) {
            return LayerService.typeDisplayName(type);
        }
        return buildTiledTypeLabel(currentSceneMeta());
    }

    private String buildTiledTypeLabel(SceneMeta sceneMeta) {
        if (sceneMeta == null || !sceneMeta.tiledEnabled || sceneMeta.tiledProjection == null) {
            return "Tiled";
        }

        return switch (sceneMeta.tiledProjection) {
            case ISO -> "Tiled isometric";
            case ORTHO -> "Tiled orthogonal";
        };
    }

    private SceneMeta currentSceneMeta() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null ? cfg.getCurrentSceneMeta() : null;
    }

    private boolean isScenePhysicsEnabled() {
        SceneMeta meta = currentSceneMeta();
        return meta != null && meta.physicsEnabled;
    }

    private boolean isTiledLayer(int layerEntityId) {
        LayerComponent lic = mIndex.getSafe(layerEntityId, null);
        return lic != null && lic.type == LayerComponent.TYPE_TILED;
    }

    private int tiledMapEntityId(int layerEntityId) {
        return layerService.findTiledMapForHost(layerEntityId);
    }

    private boolean supportsEditableParallax(int layerEntityId) {
        LayerComponent lic = mIndex.getSafe(layerEntityId, null);
        return supportsEditableParallax(layerEntityId, lic);
    }

    private boolean supportsEditableParallax(int layerEntityId, LayerComponent lic) {
        if (lic == null) return false;

        if (lic.type == LayerComponent.TYPE_CLASSIC) {
            return true;
        }

        if (lic.type == LayerComponent.TYPE_TILED) {
            return !mPhysBody.has(tiledMapEntityId(layerEntityId));
        }

        return false;
    }

    private void addPhysicsToTiledLayer(int layerEntityId) {
        if (!isScenePhysicsEnabled()) {
            refreshFromModel(layerEntityId);
            return;
        }

        int mapEntityId = tiledMapEntityId(layerEntityId);
        if (mapEntityId < 0) return;
        Command command = new AddPhysicsBodyCommand(
                world,
                history.historyIds(),
                physicsService,
                mapEntityId,
                PhysicsBodyComponent.STATIC,
                false
        );
        history.execute(command);
    }

    private void submitTiledSpatialEdit(
            int layerEntityId,
            java.util.function.UnaryOperator<EditTiledLayerSpatialDefaultsCommand.Snapshot> edit
    ) {
        if (layerEntityId < 0 || !hasTiledSpatialDefaults(layerEntityId) || edit == null) return;

        int mapEntityId = tiledMapEntityId(layerEntityId);
        if (mapEntityId < 0) return;
        TiledLayerComponent component = mTiled.get(mapEntityId);
        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(component);
        EditTiledLayerSpatialDefaultsCommand.Snapshot after = edit.apply(before);
        executeCommand(new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                mapEntityId,
                before,
                after
        ));
        refreshFromModel(layerEntityId);
    }

    private boolean hasTiledSpatialDefaults(int layerEntityId) {
        return isTiledLayer(layerEntityId) && isLayerSpatialEnabled(layerEntityId);
    }

    private float defaultTiledSpatialHeight(int layerEntityId) {
        TiledLayerComponent tiled = mTiled.getSafe(tiledMapEntityId(layerEntityId), null);
        if (tiled != null && tiled.data != null && tiled.data.tileHeight > 0) {
            return tiled.data.tileHeight;
        }

        SceneMeta meta = currentSceneMeta();
        return meta != null && meta.tileHeight > 0f ? meta.tileHeight : 0f;
    }

    private void executeCommand(Command command) {
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        history.execute(command);
        flagPreviewSaveRequired();
    }

    private void executeTiledSpatialToggle(int layerEntityId, boolean enabled) {
        int mapEntityId = tiledMapEntityId(layerEntityId);
        if (mapEntityId < 0) return;
        Command command = new ToggleLayerSpatialDepthCommand(
                world,
                history.historyIds(),
                layerEntityId,
                mapEntityId,
                enabled,
                0f,
                defaultTiledSpatialHeight(layerEntityId)
        );
        executeCommand(command);
    }

    static boolean shouldShowOrdinarySpatialProperty(
            boolean currentlyEnabled,
            boolean scenePhysicsEnabled,
            boolean anotherOrdinaryLayerEnabled) {
        return currentlyEnabled || (scenePhysicsEnabled && !anotherOrdinaryLayerEnabled);
    }

    private void executeOrdinarySpatialToggle(int layerEntityId, boolean enabled) {
        executeCommand(new ToggleSpatialActorLayerCommand(
                world,
                history.historyIds(),
                layerService,
                layerEntityId,
                enabled
        ));
    }

    private boolean isLayerSpatialEnabled(int layerEntityId) {
        LayerComponent layer = mIndex.getSafe(layerEntityId, null);
        if (layer == null) return false;
        if (layer.spatialEnabled) return true;

        TiledLayerComponent tiled = mTiled.getSafe(tiledMapEntityId(layerEntityId), null);
        return tiled != null && (tiled.spatialEnabled || (tiled.data != null && tiled.data.spatialEnabled));
    }

    private void removePhysicsFromTiledLayer(int layerEntityId) {
        int mapEntityId = tiledMapEntityId(layerEntityId);
        if (mapEntityId < 0) return;
        history.execute(new RemovePhysicsBodyCommand(
                world,
                history.historyIds(),
                physicsService,
                mapEntityId
        ));
        flagPreviewSaveRequired();
    }

    private void showRemoveCollisionsDialog(int layerEntityId) {
        if (!mPhysBody.has(tiledMapEntityId(layerEntityId))) {
            refreshFromModel(layerEntityId);
            return;
        }

        VisDialog dialog = new StudioDialog("Warning") {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    removePhysicsFromTiledLayer(layerEntityId);
                }
                refreshFromModel(layerEntityId);
            }
        };

        dialog.text(
                """
                        Removing collisions will delete the physics on this layer.
                        This action can be undone."""
        );
        dialog.button("Remove", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        } else {
            refreshFromModel(layerEntityId);
        }
    }

    private void showDisableSpatialDialog(int layerEntityId) {
        VisDialog dialog = new StudioDialog("Warning") {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    executeTiledSpatialToggle(layerEntityId, false);
                }
                refreshFromModel(layerEntityId);
            }
        };

        dialog.text(
                """
                        Disable Spatial Depth on this layer?

                        This will remove spatial data from entities in this layer and disable tiled spatial defaults."""
        );
        dialog.button("Disable", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        } else {
            refreshFromModel(layerEntityId);
        }
    }
}
