package games.pixscape.studio.ui.layer;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
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
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

/**
 * Minimal Layers panel: LayerRow table based on LayerService.getLayerUIs().
 */
public class LayersPanel extends DockablePanel {

    private final LayerService layerService;
    private final SelectionService selectionService;
    private final PhysicsSelectionService physicsSelectionService;
    private final HistoryManager historyManager;
    private final Runnable markCurrentSceneSaveRequired;

    private final VisTable listTable;
    private final VisScrollPane scroller;

    private final Button btnAdd;
    private final Button btnDelete;
    private final Button btnUp;
    private final Button btnDown;

    private final CheckBox cbAllVisible;
    private final CheckBox cbAllLocked;
    private boolean syncingBulk = false;

    private final int MY_TAG = EventFlow.tag(this);
    private boolean dirty = true;
    private boolean focusSelectedRowOnReload = true;

    public LayersPanel(StudioApplicationAdapter app) {
        super("Layers");

        var canvas = app.getCanvas();
        this.layerService = canvas.getLayerService();
        this.selectionService = canvas.getSelectionService();
        this.physicsSelectionService = canvas.getPhysicsSelectionService();
        this.historyManager = canvas.getHistoryManager();
        this.markCurrentSceneSaveRequired = app.getSceneService()::markCurrentSceneSaveRequired;
        UiRefreshDispatchSystem postProcess = canvas.getEcsWorld().getSystem(UiRefreshDispatchSystem.class);
        postProcess.add(this::updateIfDirty);

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

        add(bulkControls).growX().padBottom(4).row();
        add(scroller).grow().row();
        add(buttons).growX().fillX().padTop(4f);
    }

    private void hookButtons() {
        btnAdd.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                NewLayerDialog dialog = new NewLayerDialog(
                        request -> {
                            CreateLayerCommand command = new CreateLayerCommand(
                                    layerService,
                                    layerService.count(),
                                    request.name(),
                                    layerId -> {
                                        if (selectionService != null) {
                                            selectionService.setActivelayerId(layerId);
                                        }
                                    }
                            );
                            historyManager.execute(command);

                            markDirty();
                        }
                );

                if (getStage() != null) {
                    dialog.show(getStage());
                }
            }
        });

        btnDelete.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
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
                if (syncingBulk) return;

                boolean locked = cbAllLocked.isChecked();
                Array<LayerUI> layers = layerService.getLayerUIs();
                for (LayerUI ui : layers) {
                    layerService.setLayerLocked(ui.layerEntityId(), locked);
                }
                markDirty();
                event.stop();
            }
        });
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

    private void flagPreviewSaveRequired() {
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
    }

    private void updateIfDirty() {
        if (!dirty) return;
        dirty = false;
        reloadFromService();
    }

    /**
     * Rebuilds the full list from LayerService.getLayerUIs().
     */
    private void reloadFromService() {
        listTable.clearChildren();

        Array<LayerUI> layers = layerService.getLayerUIs();
        int n = layers.size;
        if (n == 0) return;

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

        for (int i = n - 1; i >= 0; i--) {
            final LayerUI ui = layers.get(i);

            LayerRow row = new LayerRow();
            row.setData(
                    ui.layerEntityId(),
                    ui.index(),
                    ui.name(),
                    ui.visible(),
                    ui.locked()
            );

            boolean isSelected = (ui.layerEntityId() == activeLayerId);
            row.setSelected(isSelected);

            if (isSelected) {
                selectedRow = row;
            }

            row.setListener(new LayerRow.Listener() {
                @Override
                public void onVisibleChanged(LayerRow row, boolean visible) {
                    layerService.setLayerVisible(ui.layerEntityId(), visible);
                    flagPreviewSaveRequired();
                    markDirty();
                }

                @Override
                public void onLockedChanged(LayerRow row, boolean locked) {
                    layerService.setLayerLocked(ui.layerEntityId(), locked);
                    markDirty();
                }

                @Override
                public void onRowClicked(LayerRow row) {
                    if (selectionService != null) {
                        int newLayer = ui.layerEntityId();

                        focusSelectedRowOnReload = false;
                        selectionService.clearSelection();
                        physicsSelectionService.clear();
                        selectionService.setActivelayerId(newLayer);

                    }
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
    }

}
