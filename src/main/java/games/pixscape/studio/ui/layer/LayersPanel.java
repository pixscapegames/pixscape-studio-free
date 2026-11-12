package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ChangeLayerOrderCommand;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.history.commands.DeleteLayerCommand;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.LayerService.LayerUI;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Scene HUD association followed by World rows from {@link LayerService#getLayerUIs()}.
 */
public class LayersPanel extends DockablePanel {

    enum EntryKind {HUD, WORLD}

    record PanelEntry(EntryKind kind, String hudScreenId, LayerUI worldLayer) {
    }

    private final StudioApplicationAdapter app;
    private final Consumer<Runnable> deferUi;
    private SceneEditorContext sceneContext;
    private LayerService layerService;
    private SelectionService selectionService;
    private PhysicsSelectionService physicsSelectionService;
    private HistoryManager historyManager;
    private final Runnable markCurrentSceneSaveRequired;
    private final StudioEditingModeService editingModeService;

    private final LayerInsertionPreview listTable;
    private final VisScrollPane scroller;

    private LayerRow movingRow, movingGhost;
    private SceneEditorContext movingContext;
    private int[] movingOrder;
    private long movingRevision;
    private int destination = -1;
    private final Vector2 dragPointer = new Vector2(), localPointer = new Vector2();
    private Stage dragStage;
    private final InputListener cancelDragKey = new InputListener() {
        @Override
        public boolean keyDown(InputEvent event, int keycode) {
            if (movingRow == null || keycode != Input.Keys.ESCAPE) return false;
            cancelMove();
            event.stop();
            return true;
        }
    };

    private final Button btnAdd;
    private final Button btnDelete;

    private final CheckBox cbAllVisible;
    private final CheckBox cbAllLocked;
    private final VisTable sceneContent = new VisTable();
    private boolean syncingBulk = false;
    private EntryKind selectedEntryKind = EntryKind.WORLD;
    private String selectedHudScreenId;

    private final int MY_TAG = EventFlow.tag(this);
    private boolean dirty = true;
    private boolean refreshPending;
    private boolean focusSelectedRowOnReload = true;

    public LayersPanel(StudioApplicationAdapter app) {
        this(app, runnable -> {
            if (Gdx.app != null) Gdx.app.postRunnable(runnable);
            else runnable.run();
        });
    }

    LayersPanel(StudioApplicationAdapter app, Consumer<Runnable> deferUi) {
        super("Layers");

        this.app = app;
        this.deferUi = Objects.requireNonNull(deferUi, "deferUi");
        var canvas = app.getCanvas();
        var sceneContext = app.getSceneEditorContext();
        bindServices(sceneContext);
        this.editingModeService = canvas.getStudioEditingModeService();
        this.markCurrentSceneSaveRequired = app.getSceneService()::markCurrentSceneSaveRequired;
        bindRefresh(sceneContext);

        listTable = new LayerInsertionPreview();

        scroller = new VisScrollPane(listTable);
        scroller.setFadeScrollBars(false);
        scroller.setFlickScroll(false);
        scroller.setScrollingDisabled(true, false);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());

        btnAdd = new Button(VisUI.getSkin(), "add");
        btnDelete = new Button(VisUI.getSkin(), "delete");

        cbAllVisible = new CheckBox("", VisUI.getSkin(), "eye");
        cbAllLocked = new CheckBox("", VisUI.getSkin(), "padlock");


        buildUI();
        hookButtons();

        markDirty();

        EventFlow.i().subscribe(EventFlow.LayerNameChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });
        EventFlow.i().subscribe(EventFlow.CurrentLayerChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });
        EventFlow.i().subscribe(EventFlow.LayerOrderChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });
        EventFlow.i().subscribe(EventFlow.LayerSpatialDepthChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });
        EventFlow.i().subscribe(EventFlow.LayerLockChanged.class, evt -> {
            if (evt.sourceTag() == MY_TAG) return;
            markDirty();
        });
        EventFlow.i().subscribe(EventFlow.SceneHudAssociationChanged.class,
                evt -> requestHudAssociationRefresh(evt.sceneTag()));
        EventFlow.i().subscribe(EventFlow.StudioEditingModeChanged.class, evt -> {
            refreshActionAvailability();
            if (editingModeService.allowsWorldEditingActions()) markDirty();
        });
        refreshActionAvailability();
    }

    private void buildUI() {
        VisTable bulkControls = new VisTable();
        bulkControls.left();
        bulkControls.add(cbAllVisible).padLeft(10).padRight(6);
        bulkControls.add(cbAllLocked).padBottom(2);

        VisTable buttons = new VisTable();

        VisTable right = new VisTable();
        right.add(btnAdd);
        right.add(btnDelete).padLeft(4);

        buttons.add(right).expandX().right();

        sceneContent.add(bulkControls).growX().padBottom(4).row();
        sceneContent.add(scroller).grow().row();
        sceneContent.add(buttons).growX().fillX().padTop(4f);

        add(sceneContent).grow();
    }

    private void hookButtons() {
        btnAdd.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                createLayerImmediately();
            }
        });

        btnDelete.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                if (selectedEntryKind == EntryKind.HUD) {
                    boolean removed = removeSelectedHudIfCurrent(
                            selectedEntryKind,
                            selectedHudScreenId,
                            currentHudScreenId(),
                            () -> app.removeHudScreenAssociation(sceneContext));
                    selectedEntryKind = EntryKind.WORLD;
                    selectedHudScreenId = null;
                    if (removed) focusSelectedRowOnReload = true;
                    markDirty();
                    return;
                }
                int activeLayerId = selectionService != null
                        ? selectionService.getActivelayerId()
                        : -1;

                if (activeLayerId == -1) return;
                var owner = sceneContext;
                var canvas = app.getCanvas();
                var resolver = canvas.getAssetVisualResolver();
                historyManager.execute(new DeleteLayerCommand(
                        layerService,
                        activeLayerId,
                        newActiveId -> {
                            owner.selectionService().setActivelayerId(newActiveId);
                        },
                        entity -> games.pixscape.studio.helper.RenderRebindHelper.rebindHistoryEntityRenderAssets(
                                canvas, owner.sceneIdentity(), resolver, entity, owner.world())
                ));
                markDirty();
            }
        });

        cbAllVisible.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                if (syncingBulk) return;

                boolean visible = cbAllVisible.isChecked();
                Array<LayerUI> layers = layerService.getLayerUIs();
                for (LayerUI ui : layers) {
                    layerService.setLayerVisible(ui.layerEntityId(), visible);
                }
                flagPreviewSaveRequired();
                markDirty();
                event.stop();
            }
        });

        cbAllLocked.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                if (syncingBulk) return;

                boolean locked = cbAllLocked.isChecked();
                Array<LayerUI> layers = layerService.getLayerUIs();
                for (LayerUI ui : layers) {
                    layerService.setLayerLocked(ui.layerEntityId(), locked);
                }
                flagPreviewSaveRequired();
                markDirty();
                event.stop();
            }
        });
    }

    private void refreshActionAvailability() {
        boolean allowed = editingModeService.allowsWorldEditingActions();
        btnAdd.setDisabled(!allowed);
        btnDelete.setDisabled(!allowed);
        cbAllVisible.setDisabled(!allowed);
        cbAllLocked.setDisabled(!allowed);
        listTable.setTouchable(allowed ? Touchable.childrenOnly : Touchable.disabled);
    }

    private void createLayerImmediately() {
        int previousLayerId = selectionService != null
                ? selectionService.getActivelayerId()
                : -1;
        int insertionIndex = insertionIndexForNewLayer(
                layerService, previousLayerId, selectedEntryKind);
        CreateLayerCommand command = new CreateLayerCommand(
                layerService,
                insertionIndex,
                "New Layer",
                previousLayerId,
                layerId -> {
                    selectedEntryKind = EntryKind.WORLD;
                    selectedHudScreenId = null;
                    if (selectionService != null) {
                        selectionService.setActivelayerId(layerId);
                    }
                }
        );
        historyManager.execute(command);
        markDirty();
    }

    static int insertionIndexForNewLayer(LayerService layerService, int activeLayerId) {
        return insertionIndexForNewLayer(layerService, activeLayerId, EntryKind.WORLD);
    }

    static int insertionIndexForNewLayer(LayerService layerService, int activeLayerId,
                                         EntryKind selectedEntryKind) {
        if (selectedEntryKind == EntryKind.HUD) return layerService.count();
        int activeIndex = layerService.indexOfLayerEntity(activeLayerId);
        return activeIndex >= 0 ? activeIndex + 1 : layerService.count();
    }

    static boolean removeSelectedHudIfCurrent(EntryKind selectedEntryKind,
                                              String selectedHudScreenId,
                                              String currentHudScreenId,
                                              BooleanSupplier removeAssociation) {
        return selectedEntryKind == EntryKind.HUD
                && Objects.equals(selectedHudScreenId, currentHudScreenId)
                && currentHudScreenId != null
                && removeAssociation != null
                && removeAssociation.getAsBoolean();
    }

    static boolean openSelectedHudIfCurrent(EntryKind selectedEntryKind,
                                            String selectedHudScreenId,
                                            String currentHudScreenId,
                                            Consumer<String> openHudScreen) {
        if (selectedEntryKind != EntryKind.HUD
                || currentHudScreenId == null
                || !Objects.equals(selectedHudScreenId, currentHudScreenId)
                || openHudScreen == null) {
            return false;
        }
        openHudScreen.accept(currentHudScreenId);
        return true;
    }

    private void focusRow(LayerRow row) {
        if (row == null) return;

        listTable.validate();
        scroller.layout();
        scroller.scrollTo(0f, row.getY(), row.getWidth(), row.getHeight(), false, true);
        scroller.updateVisualScroll();
    }

    private void markDirty() {
        cancelMove();
        dirty = true;
    }

    public void requestHudAssociationRefresh(String sceneTag) {
        if (sceneContext != null && Objects.equals(sceneContext.sceneIdentity(), sceneTag)) {
            requestRefresh();
        }
    }

    private void requestRefresh() {
        markDirty();
        if (refreshPending) return;
        refreshPending = true;
        deferUi.accept(() -> {
            if (!refreshPending) return;
            refreshPending = false;
            updateIfDirty();
        });
    }

    private void flagPreviewSaveRequired() {
        if (app.getEditorDocumentManager().isActive(
                games.pixscape.studio.document.EditorDocumentType.GAME_OBJECT)) {
            // A Game Object tab owns its isolated World. Layer visibility is authored asset
            // state here, so it must not mark the project Scene selected before the tab opened.
            sceneContext.markExplicitSaveRequired();
            return;
        }
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private void updateIfDirty() {
        if (!dirty) return;
        dirty = false;
        reloadFromService();
    }

    public void bindSceneContext(SceneEditorContext context) {
        cancelMove();
        if (context == null || context.isDisposed()) return;
        bindServices(context);
        bindRefresh(context);
        selectedEntryKind = EntryKind.WORLD;
        selectedHudScreenId = null;
        focusSelectedRowOnReload = true;
        markDirty();
        updateIfDirty();
    }

    private void bindServices(SceneEditorContext context) {
        sceneContext = context;
        layerService = context.layerService();
        selectionService = context.selectionService();
        physicsSelectionService = context.physicsSelectionService();
        historyManager = context.historyManager();
    }

    private void bindRefresh(SceneEditorContext context) {
        UiRefreshDispatchSystem postProcess =
                context.world().getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);
    }

    /**
     * Rebuilds the full list from LayerService.getLayerUIs().
     */
    private void reloadFromService() {
        cancelMove();
        listTable.resetRows();

        Array<LayerUI> layers = layerService.getLayerUIs();
        String hudScreenId = currentHudScreenId();
        if (hudScreenId == null && selectedEntryKind == EntryKind.HUD) {
            selectedEntryKind = EntryKind.WORLD;
            selectedHudScreenId = null;
        } else if (hudScreenId != null && selectedEntryKind == EntryKind.HUD) {
            selectedHudScreenId = hudScreenId;
        }

        boolean allVisible = true;
        boolean allLocked = true;

        for (LayerUI ui : layers) {
            allVisible &= ui.visible();
            allLocked &= ui.locked();
        }

        syncingBulk = true;
        cbAllVisible.setProgrammaticChangeEvents(false);
        cbAllLocked.setProgrammaticChangeEvents(false);
        cbAllVisible.setChecked(allVisible);
        cbAllLocked.setChecked(allLocked);
        cbAllVisible.setProgrammaticChangeEvents(true);
        cbAllLocked.setProgrammaticChangeEvents(true);
        syncingBulk = false;

        int activeLayerId = selectionService != null
                ? selectionService.getActivelayerId()
                : -1;

        LayerRow selectedRow = null;

        for (PanelEntry entry : entriesFor(hudScreenId, layers)) {
            if (entry.kind() == EntryKind.HUD) {
                LayerRow row = createHudRow(entry.hudScreenId());
                boolean isSelected = selectedEntryKind == EntryKind.HUD;
                row.setSelected(isSelected);
                if (isSelected) selectedRow = row;
                listTable.addRow(row, true);
                continue;
            }

            final LayerUI ui = entry.worldLayer();

            LayerRow row = new LayerRow();
            row.setData(
                    ui.layerEntityId(),
                    ui.index(),
                    ui.name(),
                    ui.spatialEnabled(),
                    ui.visible(),
                    ui.locked()
            );

            boolean isSelected = selectedEntryKind == EntryKind.WORLD
                    && ui.layerEntityId() == activeLayerId;
            row.setSelected(isSelected);

            if (isSelected) {
                selectedRow = row;
            }

            row.setListener(new LayerRow.Listener() {
                @Override
                public void onVisibleChanged(LayerRow row, boolean visible) {
                    if (!editingModeService.allowsWorldEditingActions()) return;
                    layerService.setLayerVisible(ui.layerEntityId(), visible);
                    flagPreviewSaveRequired();
                    markDirty();
                }

                @Override
                public void onLockedChanged(LayerRow row, boolean locked) {
                    if (!editingModeService.allowsWorldEditingActions()) return;
                    layerService.setLayerLocked(ui.layerEntityId(), locked);
                    flagPreviewSaveRequired();
                    markDirty();
                }

                @Override
                public void onRowClicked(LayerRow row) {
                    if (!editingModeService.allowsWorldEditingActions()) return;
                    selectedEntryKind = EntryKind.WORLD;
                    selectedHudScreenId = null;
                    if (selectionService != null) {
                        int newLayer = ui.layerEntityId();

                        focusSelectedRowOnReload = false;
                        selectionService.clearSelection();
                        physicsSelectionService.clear();
                        selectionService.setActivelayerId(newLayer);

                    }
                    refreshActionAvailability();
                }
            });

            installReorder(row, ui);
            listTable.addRow(row, false);

        }

        listTable.invalidateHierarchy();

        boolean shouldFocus = focusSelectedRowOnReload;
        focusSelectedRowOnReload = true;

        if (shouldFocus && selectedRow != null) {
            focusRow(selectedRow);
        }
        refreshActionAvailability();
    }

    private LayerRow createHudRow(String hudScreenId) {
        LayerRow row = new LayerRow();
        row.setData(-1, -1, "HUD — " + hudDisplayName(hudScreenId),
                false, app.isSceneHudCompositionVisible(sceneContext.sceneIdentity()), false);
        row.setLayerControlsVisible(true, false);
        row.setListener(new LayerRow.Listener() {
            @Override
            public void onVisibleChanged(LayerRow row, boolean visible) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                app.setSceneHudCompositionVisible(sceneContext.sceneIdentity(), visible);
            }

            @Override
            public void onLockedChanged(LayerRow row, boolean locked) {
            }

            @Override
            public void onRowClicked(LayerRow row) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                selectedEntryKind = EntryKind.HUD;
                selectedHudScreenId = hudScreenId;
                EventFlow.i().publish(new EventFlow.SceneHudLayerSelected(sceneContext.sceneIdentity(), MY_TAG));
                focusSelectedRowOnReload = false;
                for (Actor child : listTable.getChildren()) {
                    if (child instanceof LayerRow layerRow) {
                        layerRow.setSelected(layerRow == row);
                    }
                }
                refreshActionAvailability();
            }

            @Override
            public void onRowDoubleClicked(LayerRow row) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                openSelectedHudIfCurrent(selectedEntryKind, selectedHudScreenId,
                        currentHudScreenId(), app::openHudScreen);
            }
        });
        return row;
    }

    private void installReorder(LayerRow row, LayerUI ui) {
        row.addCaptureListener(new DragListener() {
            {
                setTapSquareSize(4);
            } // Same threshold as the tile animation sequence.

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return button == Input.Buttons.LEFT && pointer == 0 && !dirty
                        && editingModeService.allowsWorldEditingActions()
                        && row.isDragHandle(event.getTarget())
                        && super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void dragStart(InputEvent event, float x, float y, int pointer) {
                row.cancelActivation(); // Including cancelled drags: release must never become a click.
                if (dirty || row.getParent() != listTable || !editingModeService.allowsWorldEditingActions()) return;
                cancelMove();
                movingRow = row;
                movingContext = sceneContext;
                movingRevision = historyManager.getMutationRevision();
                movingOrder = new int[layerService.count()];
                for (int i = 0; i < movingOrder.length; i++) movingOrder[i] = layerService.getLayerEntity(i);
                movingGhost = new LayerRow();
                movingGhost.setData(ui.layerEntityId(), ui.index(), ui.name(), ui.spatialEnabled(), ui.visible(), ui.locked());
                movingGhost.setSelected(true);
                dragStage = getStage();
                dragStage.addCaptureListener(cancelDragKey);
                dragPointer.set(event.getStageX(), event.getStageY());
                updateMove(0);
            }

            @Override
            public void drag(InputEvent event, float x, float y, int pointer) {
                if (movingRow != row) return;
                dragPointer.set(event.getStageX(), event.getStageY());
                updateMove(0);
            }

            @Override
            public void dragStop(InputEvent event, float x, float y, int pointer) {
                if (movingRow != row) return;
                dragPointer.set(event.getStageX(), event.getStageY());
                updateMove(0);
                int target = destination;
                boolean valid = !event.isTouchFocusCancel() && target >= 0 && moveContextValid() && orderUnchanged();
                int entity = row.getEntityId();
                cancelMove();
                if (!valid) return;
                // Display order is reversed; destination is a gap AFTER removing the source.
                var command = new ChangeLayerOrderCommand(layerService, entity, layerService.count() - 1 - target);
                if (command.isNoop()) return;
                focusSelectedRowOnReload = false;
                historyManager.execute(command);
                markDirty();
            }
        });
    }

    private boolean moveContextValid() {
        if (movingRow == null || movingContext != sceneContext || sceneContext.isDisposed()
                || getStage() != dragStage || dirty || historyManager.getMutationRevision() != movingRevision
                || !editingModeService.allowsWorldEditingActions()) return false;
        for (Actor actor = this; actor != null; actor = actor.getParent()) if (!actor.isVisible()) return false;
        return true;
    }

    /**
     * Cold release validation also covers mutations whose queued notifications have not flushed yet.
     */
    private boolean orderUnchanged() {
        if (movingOrder.length != layerService.count()) return false;
        for (int i = 0; i < movingOrder.length; i++) if (movingOrder[i] != layerService.getLayerEntity(i)) return false;
        return true;
    }

    private void updateMove(float delta) {
        if (!moveContextValid()) {
            cancelMove();
            return;
        }
        scroller.validate();
        Actor hit = dragStage.hit(dragPointer.x, dragPointer.y, true);
        scroller.stageToLocalCoordinates(localPointer.set(dragPointer));
        float bottom = scroller.getStyle().background == null ? 0 : scroller.getStyle().background.getBottomHeight();
        float height = scroller.getScrollHeight();
        boolean inside = height > 0 && hit != null && (hit == scroller || hit.isDescendantOf(scroller))
                && localPointer.x >= 0 && localPointer.x < scroller.getScrollWidth()
                && localPointer.y >= bottom && localPointer.y <= bottom + height;
        if (!inside) {
            destination = -1;
            listTable.clearPreview();
            return;
        }
        // Bound elapsed time after stalls. Constant scroll extent prevents ghost-induced jumps.
        float edge = Math.min(28, height / 3);
        float fromBottom = localPointer.y - bottom;
        float scroll = fromBottom < edge ? 1 - fromBottom / edge
                : fromBottom > height - edge ? -(fromBottom - height + edge) / edge : 0;
        if (scroll != 0 && delta > 0) {
            scroller.setScrollY(Math.max(0, Math.min(scroller.getMaxY(),
                    scroller.getScrollY() + scroll * 240 * Math.min(delta, .05f))));
            scroller.updateVisualScroll();
            scroller.layout();
        }
        listTable.stageToLocalCoordinates(localPointer.set(dragPointer));
        destination = listTable.insertionAt(localPointer.y, movingRow);
        if (destination < 0) listTable.clearPreview();
        else listTable.showInsertion(movingRow, movingGhost, destination);
    }

    private void cancelMove() {
        if (dragStage != null) dragStage.removeCaptureListener(cancelDragKey);
        if (listTable != null) listTable.clearPreview();
        movingRow = movingGhost = null;
        movingContext = null;
        movingOrder = null;
        dragStage = null;
        destination = -1;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (movingRow != null) updateMove(delta);
    }

    @Override
    protected void setStage(Stage stage) {
        if (stage != getStage()) cancelMove();
        super.setStage(stage);
    }

    static Array<PanelEntry> entriesFor(String hudScreenId, Array<LayerUI> layers) {
        Array<PanelEntry> entries = new Array<>();
        if (hudScreenId != null && !hudScreenId.isBlank()) {
            entries.add(new PanelEntry(EntryKind.HUD, hudScreenId, null));
        }
        if (layers != null) {
            for (int i = layers.size - 1; i >= 0; i--) {
                entries.add(new PanelEntry(EntryKind.WORLD, null, layers.get(i)));
            }
        }
        return entries;
    }

    private String currentHudScreenId() {
        ProjectConfig configuration = ProjectConfig.getInstance();
        if (configuration == null || sceneContext == null) return null;
        for (String sceneName : configuration.getSceneNames()) {
            SceneMeta meta = configuration.getSceneMeta(sceneName);
            if (!Objects.equals(sceneContext.sceneIdentity(),
                    configuration.canonicalSceneTagFor(meta))) continue;
            String screenId = meta.defaultHudScreenId;
            if (screenId == null || screenId.isBlank()) return null;
            try {
                return HudScreenAssetId.normalize(screenId);
            } catch (RuntimeException ignored) {
                return screenId.trim();
            }
        }
        return null;
    }

    private static String hudDisplayName(String hudScreenId) {
        try {
            return HudScreenAssetId.assetName(hudScreenId);
        } catch (RuntimeException ignored) {
            return hudScreenId;
        }
    }

}
