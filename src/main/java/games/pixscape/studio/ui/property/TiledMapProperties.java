package games.pixscape.studio.ui.property;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.property.entityproperties.CustomPropertiesEditorRow;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.SimpleTextField;

/**
 * Specialized property panel for a tile layer. Position is local to its owning map.
 */
public final class TiledMapProperties extends VisTable {

    private final CustomPropertiesEditorRow customPropertiesRow;
    private final World world;
    private final HistoryManager history;
    private final PhysicsService physicsService;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private final ComponentMapper<PhysicsBodyComponent> mPhysicsBody;
    private final Runnable markCurrentSceneSaveRequired;

    private final SimpleTextField mapName = new SimpleTextField();
    private final VisLabel stableIdValue = new VisLabel();

    private final VisLabel tiledChunkSizeValue = new VisLabel();

    private final FloatField positionXField;
    private final FloatField positionYField;

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
        customPropertiesRow = new CustomPropertiesEditorRow(world, history, 0, markCurrentSceneSaveRequired);
        this.history = history;
        this.physicsService = physicsService;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.mPhysicsBody = world.getMapper(PhysicsBodyComponent.class);

        top().left();
        defaults().left().top().pad(1);

        mapName.setName("tiledMapName");
        mapName.bind(this::readMapName, this::applyMapName);
        stableIdValue.setName("tiledMapStableId");
        EventFlow.i().subscribe(EventFlow.EntityNameChanged.class, event -> {
            if (event.entityId() == mapEntityId) mapName.refresh();
        });

        positionXField = positionField(true);
        positionYField = positionField(false);
        EventFlow.i().subscribe(EventFlow.EntityChanged.class, event -> {
            if (event.entityId() == mapEntityId) {
                positionXField.refreshFromModel();
                positionYField.refreshFromModel();
            }
        });

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
                    execute(AddPhysicsBodyCommand.forTiledCollisions(
                            world, history.historyIds(), physicsService, mapEntityId));
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
                String rejection = requested ? games.pixscape.runtime.service.SpatialScenePolicy
                        .tiledActivationRejection(world, mapEntityId) : null;
                if (rejection != null) {
                    com.kotcrab.vis.ui.util.dialog.Dialogs.showOKDialog(getStage(), "Spatial Depth", rejection);
                    refreshFromModel();
                    return;
                }
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

        add(new VisLabel("TILE LAYER"))
                .colspan(2)
                .center()
                .padBottom(CommonLayout.PROPERTY_SECTION_TITLE_BOTTOM_PAD)
                .row();

        addProperty("Name:", mapName);
        addProperty("Id:", stableIdValue);
        add(customPropertiesRow).colspan(2).growX().row();
        addProperty("Chunk Size:", tiledChunkSizeValue);
        addProperty("Local X:", positionXField);
        addProperty("Local Y:", positionYField);

        VisTable spatialDefaults = spatialDefaultsBlock.content();
        spatialDefaults.left().top().padTop(5);
        spatialDefaults.defaults().left().top().pad(1);
        spatialDefaults.add(new VisLabel("Altitude:"))
                .width(CommonLayout.LABEL_WIDTH).left();
        spatialDefaults.add(defaultAltitudeField)
                .width(CommonLayout.FIELD_WIDTH).left().row();
        spatialDefaults.add(new VisLabel("Default height:"))
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
        // Native text button; no bound ECS button wrapper is needed for opening a modal.
        com.kotcrab.vis.ui.widget.VisTextButton lighting = new com.kotcrab.vis.ui.widget.VisTextButton("Lighting descriptions...");
        lighting.setName("tiledMapLightingDescriptions");
        lighting.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showLightingDescriptions();
            }
        });
        add(lighting).colspan(2).left().row();
    }

    private void showLightingDescriptions() {
        if (!isValidMap(mapEntityId) || history == null) return;
        final int entity = mapEntityId;
        com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
        games.pixscape.studio.ui.widget.SimpleTextArea text = new games.pixscape.studio.ui.widget.SimpleTextArea().withMultilineEditing();
        text.setText(json.prettyPrint(json.toJson(mTiled.get(entity).lightingDescriptions, com.badlogic.gdx.utils.Array.class,
                games.pixscape.runtime.render.lighting.TileSurfaceDescription.class)));
        VisLabel error = new VisLabel("");
        error.setWrap(true);
        StudioDialog dialog = new StudioDialog("Tile lighting descriptions (explicit profiles)") {
            @Override
            protected void result(Object result) {
                if (!Boolean.TRUE.equals(result)) return;
                try {
                    var values = json.fromJson(com.badlogic.gdx.utils.Array.class, games.pixscape.runtime.render.lighting.TileSurfaceDescription.class, text.getText());
                    var command = new EditTileLightingDescriptionsCommand(world, history.historyIds(), entity, values);
                    history.execute(command);
                    if (command.rejection() != null) {
                        error.setText(command.rejection());
                        cancel();
                        return;
                    }
                    flagPreviewSaveRequired();
                } catch (RuntimeException invalid) {
                    error.setText(invalid.getMessage());
                    cancel();
                }
            }
        };
        dialog.getContentTable().add(new VisLabel("One description per asset ID. rounded: {centerGX: 1, centerGY: 1, radiusCells: 1}.\nSix facets; requires two compatible tile-linked walls. No automatic image detection.")).width(730).left().row();
        dialog.getContentTable().add(text).width(730).height(310).grow().row();
        dialog.getContentTable().add(error).width(730).left().row();
        dialog.button("Apply", true);
        dialog.button("Cancel", false);
        dialog.show(getStage());
    }

    public void setMapEntityId(int mapEntityId) {
        this.mapEntityId = isValidMap(mapEntityId) ? mapEntityId : -1;
        customPropertiesRow.setEntityId(this.mapEntityId);
        refreshFromModel();
    }

    int mapEntityId() {
        return mapEntityId;
    }

    private void refreshFromModel() {
        TiledLayerComponent tiled = isValidMap(mapEntityId) ? mTiled.get(mapEntityId) : null;
        internalRefresh = true;
        try {
            if (tiled == null) {
                showUnknownValues();
                return;
            }

            mapName.setDisabled(false);
            mapName.refresh();
            PixscapeIdentityComponent identity = mIdentity.getSafe(mapEntityId, null);
            boolean hasStableId = identity != null && identity.stableId > 0;
            stableIdValue.setText(hasStableId ? Integer.toString(identity.stableId) : "Not assigned");
            tiledChunkSizeValue.setText(Integer.toString(tiled.chunkSize));

            positionXField.setEntityId(mapEntityId);
            positionYField.setEntityId(mapEntityId);

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
        mapName.refresh();
        mapName.setDisabled(true);
        stableIdValue.setText("Not assigned");
        tiledChunkSizeValue.setText("?");
        positionXField.setEntityId(-1);
        positionYField.setEntityId(-1);
        collisionsCheckBox.setChecked(false);
        collisionsCheckBox.setDisabled(true);
        spatialDepthCheckBox.setChecked(false);
        spatialDefaultsBlock.show(false);
        defaultAltitudeField.setEntityId(-1);
        defaultHeightField.setEntityId(-1);
    }

    private FloatField positionField(boolean xAxis) {
        var transforms = world.getMapper(games.pixscape.runtime.component.TransformComponent.class);
        FloatField field = new FloatField(world, e -> xAxis ? transforms.get(e).x : transforms.get(e).y,
                e -> mTiled.has(e) && transforms.has(e)) {
            @Override
            protected String formatValue(Float value) {
                return value == null ? "" : Float.toString(value);
            }
        };
        field.setName(xAxis ? "tiledLayerPositionX" : "tiledLayerPositionY");
        field.setValidator(FloatField.finiteValidator(false));
        field.setApplier((entity, value) -> {
            var before = EditTransformCommand.Snapshot.capture(transforms.get(entity));
            execute(new EditTransformCommand(world, history.historyIds(), entity, TransformOp.MOVE, before,
                    xAxis ? before.withX(value) : before.withY(value)));
        });
        return field;
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

    private String readMapName() {
        if (!isValidMap(mapEntityId)) return "";
        PixscapeIdentityComponent identity = mIdentity.getSafe(mapEntityId, null);
        return identity != null && identity.name != null ? identity.name : "";
    }

    private void applyMapName(String name) {
        if (!isValidMap(mapEntityId) || history == null || readMapName().equals(name)) return;
        history.execute(new ChangeEntityNameCommand(
                world, history.historyIds(), history.historyIds().ensureForEntity(mapEntityId),
                readMapName(), name, EventFlow.tag(this)));
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

}
