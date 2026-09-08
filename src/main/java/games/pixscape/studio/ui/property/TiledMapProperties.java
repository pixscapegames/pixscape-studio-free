package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddPhysicsBodyCommand;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.EditTiledLayerSpatialDefaultsCommand;
import games.pixscape.studio.history.commands.RemovePhysicsBodyCommand;
import games.pixscape.studio.history.commands.ToggleTiledMapSpatialDepthCommand;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.UiBinders;

/** Authoritative property panel for one actual Tiled Map entity. */
public final class TiledMapProperties extends VisTable {

    private final World world;
    private final HistoryManager history;
    private final PhysicsService physicsService;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<PhysicsBodyComponent> mPhysicsBody;
    private final Runnable markCurrentSceneSaveRequired;

    private final VisLabel tiledWidthValue = new VisLabel();
    private final VisLabel tiledHeightValue = new VisLabel();
    private final VisLabel tiledProjectionValue = new VisLabel();
    private final VisLabel tiledTileWidthValue = new VisLabel();
    private final VisLabel tiledTileHeightValue = new VisLabel();
    private final VisLabel tiledChunkSizeValue = new VisLabel();

    private final IntSpinnerModel originXModel = new IntSpinnerModel(0, -100000, 100000, 1);
    private final IntSpinnerModel originYModel = new IntSpinnerModel(0, -100000, 100000, 1);
    private final Spinner tiledOriginXSpinner;
    private final Spinner tiledOriginYSpinner;
    private final UiBinders.IntSpinnerBinder tiledOriginXBinder;
    private final UiBinders.IntSpinnerBinder tiledOriginYBinder;

    private final VisCheckBox collisionsCheckBox = new VisCheckBox("Collisions");
    private final VisCheckBox spatialDepthCheckBox = new VisCheckBox("Spatial Depth");
    private final CollapsibleVisTable spatialDefaultsBlock =
            new CollapsibleVisTable(true, true);
    private final FloatField defaultAltitudeField;
    private final FloatField defaultHeightField;

    private int mapEntityId = -1;
    private boolean internalRefresh;

    public TiledMapProperties(World world,
                              HistoryManager history,
                              PhysicsService physicsService,
                              Runnable markCurrentSceneSaveRequired) {
        super(true);
        this.world = world;
        this.history = history;
        this.physicsService = physicsService;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mPhysicsBody = world.getMapper(PhysicsBodyComponent.class);

        top().left();
        defaults().left().top().pad(1);

        tiledOriginXSpinner = new Spinner("", originXModel);
        tiledOriginXSpinner.getTextField().setTouchable(Touchable.disabled);
        tiledOriginYSpinner = new Spinner("", originYModel);
        tiledOriginYSpinner.getTextField().setTouchable(Touchable.disabled);

        tiledOriginXBinder = originBinder(tiledOriginXSpinner, originXModel, true);
        tiledOriginYBinder = originBinder(tiledOriginYSpinner, originYModel, false);

        collisionsCheckBox.setName("tiledMapCollisions");
        collisionsCheckBox.left();
        spatialDepthCheckBox.setName("tiledMapSpatialDepth");
        spatialDepthCheckBox.left();
        spatialDefaultsBlock.setName("tiledMapSpatialDefaults");

        defaultAltitudeField = new FloatField(
                world,
                eid -> mTiled.get(eid).defaultTileAltitude,
                this::hasSpatialDepth).setDisplayDecimals(2);
        defaultAltitudeField.setName("tiledMapDefaultAltitude");
        defaultHeightField = new FloatField(
                world,
                eid -> mTiled.get(eid).defaultTileHeight,
                this::hasSpatialDepth).setDisplayDecimals(2);
        defaultHeightField.setName("tiledMapDefaultHeight");
        defaultAltitudeField.setApplier((eid, value) ->
                submitSpatialDefaults(eid, snapshot -> snapshot.withDefaultAltitude(value)));
        defaultHeightField.setApplier((eid, value) ->
                submitSpatialDefaults(eid,
                        snapshot -> snapshot.withDefaultHeight(Math.max(0f, value))));

        collisionsCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || !isValidMap(mapEntityId)) return;
                boolean requested = collisionsCheckBox.isChecked();
                boolean current = mPhysicsBody.has(mapEntityId);
                if (requested == current) return;
                if (requested && !isScenePhysicsEnabled()) {
                    refreshFromModel();
                    return;
                }
                if (requested) {
                    execute(new AddPhysicsBodyCommand(
                            world,
                            history.historyIds(),
                            physicsService,
                            mapEntityId,
                            PhysicsBodyComponent.STATIC,
                            false));
                    refreshFromModel();
                } else {
                    showRemoveCollisionsDialog(mapEntityId);
                    event.handle();
                }
            }
        });

        spatialDepthCheckBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalRefresh || !isValidMap(mapEntityId)) return;
                TiledLayerComponent tiled = mTiled.get(mapEntityId);
                boolean requested = spatialDepthCheckBox.isChecked();
                if (requested == tiled.spatialEnabled) return;
                execute(new ToggleTiledMapSpatialDepthCommand(
                        world,
                        history.historyIds(),
                        mapEntityId,
                        requested,
                        tiled.defaultTileAltitude,
                        tiled.defaultTileHeight > 0f
                                ? tiled.defaultTileHeight
                                : tiled.tileHeight));
                refreshFromModel();
            }
        });

        add(new VisLabel("TILED MAP"))
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .row();

        addProperty("Projection:", tiledProjectionValue);
        addProperty("Cell Width:", tiledTileWidthValue);
        addProperty("Cell Height:", tiledTileHeightValue);
        addProperty("Chunk Size:", tiledChunkSizeValue);
        addProperty("Origin X:", tiledOriginXSpinner);
        addProperty("Origin Y:", tiledOriginYSpinner);
        addProperty("Width (cells):", tiledWidthValue);
        addProperty("Height (cells):", tiledHeightValue);

        VisTable spatialDefaults = spatialDefaultsBlock.content();
        spatialDefaults.left().top().padTop(5);
        spatialDefaults.defaults().left().top().pad(1);
        spatialDefaults.add(new VisLabel("Default Altitude:"))
                .width(CommonLayout.LABEL_WIDTH).left();
        spatialDefaults.add(defaultAltitudeField)
                .width(CommonLayout.FIELD_WIDTH).left().row();
        spatialDefaults.add(new VisLabel("Default Height:"))
                .width(CommonLayout.LABEL_WIDTH).left();
        spatialDefaults.add(defaultHeightField)
                .width(CommonLayout.FIELD_WIDTH).left().row();
        spatialDefaultsBlock.show(false);

        addSeparator().colspan(2).growX().row();
        add(collisionsCheckBox).colspan(2).left().growX().row();
        add(spatialDepthCheckBox).colspan(2).left().growX().row();
        add(spatialDefaultsBlock)
                .colspan(2)
                .padLeft(CommonLayout.PAD_LEFT_SUBMENU)
                .left()
                .growX()
                .row();
    }

    public void setMapEntityId(int mapEntityId) {
        this.mapEntityId = isValidMap(mapEntityId) ? mapEntityId : -1;
        refreshFromModel();
    }

    int mapEntityId() {
        return mapEntityId;
    }

    private void refreshFromModel() {
        TiledLayerComponent tiled = mTiled.getSafe(mapEntityId, null);
        internalRefresh = true;
        try {
            if (tiled == null) {
                showUnknownValues();
                return;
            }

            tiledWidthValue.setText(String.valueOf(tiled.mapWidthCells));
            tiledHeightValue.setText(String.valueOf(tiled.mapHeightCells));
            tiledProjectionValue.setText(buildTiledProjectionLabel(tiled));
            tiledTileWidthValue.setText(Integer.toString(tiled.tileWidth));
            tiledTileHeightValue.setText(Integer.toString(tiled.tileHeight));
            tiledChunkSizeValue.setText(Integer.toString(tiled.chunkSize));

            originXModel.setStep(Math.max(1, tiled.tileWidth));
            originYModel.setStep(Math.max(1, tiled.tileHeight));
            tiledOriginXBinder.setEntityId(mapEntityId);
            tiledOriginYBinder.setEntityId(mapEntityId);

            boolean collisionsActive = mPhysicsBody.has(mapEntityId);
            collisionsCheckBox.setChecked(collisionsActive);
            collisionsCheckBox.setDisabled(!isScenePhysicsEnabled() && !collisionsActive);
            spatialDepthCheckBox.setChecked(tiled.spatialEnabled);
            spatialDefaultsBlock.show(tiled.spatialEnabled);
            defaultAltitudeField.setEntityId(tiled.spatialEnabled ? mapEntityId : -1);
            defaultHeightField.setEntityId(tiled.spatialEnabled ? mapEntityId : -1);
            if (tiled.spatialEnabled) {
                defaultAltitudeField.refreshFromModel();
                defaultHeightField.refreshFromModel();
            }
        } finally {
            internalRefresh = false;
        }
        invalidateHierarchy();
    }

    private void showUnknownValues() {
        tiledWidthValue.setText("?");
        tiledHeightValue.setText("?");
        tiledProjectionValue.setText("Unknown");
        tiledTileWidthValue.setText("?");
        tiledTileHeightValue.setText("?");
        tiledChunkSizeValue.setText("?");
        originXModel.setStep(1);
        originYModel.setStep(1);
        tiledOriginXBinder.setEntityId(-1);
        tiledOriginYBinder.setEntityId(-1);
        collisionsCheckBox.setChecked(false);
        collisionsCheckBox.setDisabled(true);
        spatialDepthCheckBox.setChecked(false);
        spatialDefaultsBlock.show(false);
        defaultAltitudeField.setEntityId(-1);
        defaultHeightField.setEntityId(-1);
    }

    private UiBinders.IntSpinnerBinder originBinder(Spinner spinner,
                                                     IntSpinnerModel model,
                                                     boolean xAxis) {
        return new UiBinders.IntSpinnerBinder(
                world,
                spinner,
                model,
                mTiled::has,
                eid -> Math.round(xAxis ? mTiled.get(eid).originX : mTiled.get(eid).originY),
                (eid, value) -> {
                    TiledLayerComponent tiled = mTiled.get(eid);
                    if (xAxis) {
                        tiled.originX = value;
                        tiled.data.originX = value;
                    } else {
                        tiled.originY = value;
                        tiled.data.originY = value;
                    }
                    tiled.data.rebuildWithNewSize(tiled.mapWidthCells, tiled.mapHeightCells);
                    DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
                    if (dirty != null) dirty.layer(eid);
                    flagPreviewSaveRequired();
                });
    }

    private void submitSpatialDefaults(
            int entityId,
            java.util.function.UnaryOperator<EditTiledLayerSpatialDefaultsCommand.Snapshot> edit) {
        if (!hasSpatialDepth(entityId) || edit == null) return;
        TiledLayerComponent tiled = mTiled.get(entityId);
        EditTiledLayerSpatialDefaultsCommand.Snapshot before =
                EditTiledLayerSpatialDefaultsCommand.Snapshot.capture(tiled);
        execute(new EditTiledLayerSpatialDefaultsCommand(
                world,
                history.historyIds(),
                entityId,
                before,
                edit.apply(before)));
        refreshFromModel();
    }

    private void execute(Command command) {
        if (history == null || command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop
                && supportsNoop.isNoop()) {
            return;
        }
        history.execute(command);
        flagPreviewSaveRequired();
    }

    private void showRemoveCollisionsDialog(int targetMapEntityId) {
        if (!isValidMap(targetMapEntityId) || !mPhysicsBody.has(targetMapEntityId)) {
            refreshFromModel();
            return;
        }

        VisDialog dialog = new StudioDialog("Warning") {
            @Override
            protected void result(Object object) {
                if (Boolean.TRUE.equals(object)) {
                    confirmRemoveCollisions(targetMapEntityId);
                } else {
                    refreshFromModel();
                }
            }
        };
        dialog.text(
                """
                        Removing collisions will delete all collision shapes from this Map.
                        This action can be undone.""");
        dialog.button("Remove", true);
        dialog.button("Cancel", false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();

        if (getStage() != null) {
            dialog.show(getStage());
        } else {
            refreshFromModel();
        }
    }

    void confirmRemoveCollisions(int targetMapEntityId) {
        if (!isValidMap(targetMapEntityId) || !mPhysicsBody.has(targetMapEntityId)) {
            refreshFromModel();
            return;
        }
        execute(new RemovePhysicsBodyCommand(
                world,
                history.historyIds(),
                physicsService,
                targetMapEntityId,
                true));
        refreshFromModel();
    }

    private boolean hasSpatialDepth(int entityId) {
        TiledLayerComponent tiled = mTiled.getSafe(entityId, null);
        return tiled != null && tiled.spatialEnabled;
    }

    private boolean isValidMap(int entityId) {
        return entityId >= 0
                && world.getEntityManager().isActive(entityId)
                && mTiled.has(entityId);
    }

    private boolean isScenePhysicsEnabled() {
        ProjectConfig config = ProjectConfig.getInstance();
        SceneMeta scene = config != null ? config.getCurrentSceneMeta() : null;
        return scene != null && scene.physicsEnabled;
    }

    private void addProperty(String label, Actor value) {
        add(new VisLabel(label)).left();
        add(value).left().growX().row();
    }

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private String buildTiledProjectionLabel(TiledLayerComponent tiled) {
        if (tiled == null || tiled.projection == null) return "Unknown";
        return switch (tiled.projection) {
            case ISO -> "Isometric";
            case ORTHO -> "Orthogonal";
        };
    }
}
