package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
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
import games.pixscape.studio.history.HistoryManager.SupportsNoop;
import games.pixscape.studio.history.commands.ChangeLayerOrderCommand;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.history.commands.DeleteLayerCommand;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.LayerService.LayerUI;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.scene.SceneEditorContext;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Scene HUD association followed by World rows from {@link LayerService#getLayerUIs()}. */
public class LayersPanel extends DockablePanel {

    enum EntryKind { HUD, WORLD }

    record PanelEntry(EntryKind kind, String hudScreenId, LayerUI worldLayer) {}

    private final StudioApplicationAdapter app;
    private final Consumer<Runnable> deferUi;
    private SceneEditorContext sceneContext;
    private LayerService layerService;
    private SelectionService selectionService;
    private PhysicsSelectionService physicsSelectionService;
    private HistoryManager historyManager;
    private final Runnable markCurrentSceneSaveRequired;
    private final StudioEditingModeService editingModeService;

    private final VisTable listTable;
    private final VisScrollPane scroller;

    private final Button btnAdd;
    private final Button btnDelete;
    private final Button btnUp;
    private final Button btnDown;

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

        listTable = new VisTable(false);
        listTable.top().pad(5).left();

        scroller = new VisScrollPane(listTable);
        scroller.setFadeScrollBars(false);
        scroller.addListener(new GetScrollListener(scroller));
        scroller.addListener(new LoseScroolListener());

        btnAdd = new Button(VisUI.getSkin(), "add");
        btnDelete = new Button(VisUI.getSkin(), "delete");
        btnUp = new Button(VisUI.getSkin(), "up");
        btnDown = new Button(VisUI.getSkin(), "down");

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

        VisTable center = new VisTable();
        center.add(btnUp).padLeft(50);
        center.add(btnDown);

        VisTable right = new VisTable();
        right.add(btnAdd);
        right.add(btnDelete).padLeft(4);

        buttons.add(center).expandX().center();
        buttons.add(right).right();

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
                historyManager.execute(new DeleteLayerCommand(
                        layerService,
                        activeLayerId,
                        newActiveId -> {
                            if (selectionService != null) {
                                selectionService.setActivelayerId(newActiveId);
                            }
                        }
                ));
                markDirty();
            }
        });

        btnUp.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                if (selectedEntryKind != EntryKind.WORLD) return;
                if (selectionService == null) return;
                int activeLayerId = selectionService.getActivelayerId();
                int idx = layerService.indexOfLayerEntity(activeLayerId);
                if (idx < 0) return;

                executeIfMeaningful(new ChangeLayerOrderCommand(layerService, activeLayerId, idx + 1));
                markDirty();
            }
        });

        btnDown.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                if (selectedEntryKind != EntryKind.WORLD) return;
                if (selectionService == null) return;
                int activeLayerId = selectionService.getActivelayerId();
                int idx = layerService.indexOfLayerEntity(activeLayerId);
                if (idx < 0) return;

                executeIfMeaningful(new ChangeLayerOrderCommand(layerService, activeLayerId, idx - 1));
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
        boolean hudSelected = selectedEntryKind == EntryKind.HUD;
        btnAdd.setDisabled(!allowed);
        btnDelete.setDisabled(!allowed);
        btnUp.setDisabled(!allowed || hudSelected);
        btnDown.setDisabled(!allowed || hudSelected);
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

    private void executeIfMeaningful(games.pixscape.studio.history.commands.Command command) {
        if (command instanceof SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        historyManager.execute(command);
    }

    private void markDirty() {
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
        listTable.clearChildren();

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
                listTable.add(row).growX().padBottom(6).row();
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

            listTable.add(row).growX().padBottom(2).row();

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
            @Override public void onVisibleChanged(LayerRow row, boolean visible) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                app.setSceneHudCompositionVisible(sceneContext.sceneIdentity(), visible);
            }
            @Override public void onLockedChanged(LayerRow row, boolean locked) {}

            @Override public void onRowClicked(LayerRow row) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                selectedEntryKind = EntryKind.HUD;
                selectedHudScreenId = hudScreenId;
                focusSelectedRowOnReload = false;
                for (Actor child : listTable.getChildren()) {
                    if (child instanceof LayerRow layerRow) {
                        layerRow.setSelected(layerRow == row);
                    }
                }
                refreshActionAvailability();
            }

            @Override public void onRowDoubleClicked(LayerRow row) {
                if (!editingModeService.allowsWorldEditingActions()) return;
                openSelectedHudIfCurrent(selectedEntryKind, selectedHudScreenId,
                        currentHudScreenId(), app::openHudScreen);
            }
        });
        return row;
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
