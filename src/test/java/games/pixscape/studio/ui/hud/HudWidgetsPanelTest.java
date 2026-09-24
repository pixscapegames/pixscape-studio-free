package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HudWidgetsPanelTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void selectedParentCreatesAndSelectsChildWithoutChangingPanel() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        hud.editSession().edit("Create root", ignored ->
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertTrue(session.createSelectedTable(1, 1, hud, "root"));
        deferred.runAll();
        assertFalse(panel.tableButton().isChecked());
        assertEquals("table-1", session.selectedNodeId());
        assertEquals(HudPlacementKind.DIRECT,
                rootRelation(hud).placementKind);
        assertTrue(rootRelation(hud).node.fillParent);
        assertEquals(0f,
                rootRelation(hud).node.actor.width, 0f);
        assertEquals(0f,
                rootRelation(hud).node.actor.height, 0f);
        panel.containerButton().setChecked(true);
        deferred.runAll();
        assertEquals("container-1", session.selectedNodeId());
        panel.stackButton().setChecked(true);
        deferred.runAll();
        assertEquals("stack-1", session.selectedNodeId());

        session.selectNode("container-1");
        deferred.runAll();
        assertEquals("layout widgets remain draggable even though this selected Container is full",
                4, actionableWidgetCount(panel));
        int history = hud.editSession().historySize();
        panel.groupButton().setChecked(true);
        deferred.runAll();
        assertEquals(history, hud.editSession().historySize());
    }

    @Test
    public void redundantRefreshesCoalesceAtOneUiBoundary() {
        EditorDocumentManager manager = new EditorDocumentManager();
        manager.openHudScreen("hud/main", "HUD");
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(
                new HudEditorSession(), manager, deferred::post);
        int before = panel.projectionCount();

        panel.requestRefresh();
        panel.requestRefresh();
        panel.requestRefresh();

        assertTrue(panel.refreshPending());
        assertEquals(1, deferred.size());
        deferred.runAll();
        assertFalse(panel.refreshPending());
        assertEquals(before + 1, panel.projectionCount());
    }

    @Test
    public void selectingEmptyCellInItemsLetsToolboxFillIt() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        String cellId = cell(root).id;
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/cell", "HUD", asset(), new HudDocumentV1(root)));
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudHierarchyPanel hierarchy = new HudHierarchyPanel(session, manager, deferred::post);
        HudWidgetsPanel widgets = new HudWidgetsPanel(session, manager, deferred::post);

        hierarchy.tree().getSelection().set(hierarchy.tree().findCell(cellId));
        deferred.runAll();
        assertEquals(cellId, session.selectedCellId());
        assertFalse(widgets.labelButton().isDisabled());
        widgets.labelButton().setChecked(true);
        deferred.runAll();
        assertEquals(HudNodeKind.LABEL, cell(hud.document().root).content.kind);
        assertEquals(1, hud.editSession().historySize());
        assertSame(hierarchy.tree().findNode(cell(hud.document().root).content.id),
                hierarchy.tree().getSelectedNode());
    }

    @Test
    public void confirmedTableFillsCapturedEmptyCellAndSurvivesHistoryAndReload() throws Exception {
        HudNode root = table("root");
        root.table = HudLayoutAuthoring.newTableLayout(root, 1, 2, false);
        HudScreenEditorDocument hud = new HudScreenEditorDocument(
                "hud/grid", "Grid", asset(), new HudDocumentV1(root));
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        String destinationId = cell(root).id;
        HudPanelTestSupport.selectCell(session, hud, destinationId);
        HudEditorSession.WidgetDropTarget captured = session.selectedTableTarget();
        assertEquals("root", captured.parentId());
        assertEquals(destinationId, captured.cellId());

        assertTrue(session.createTableAt(2, 3, captured, hud));
        HudNode created = HudLayoutAuthoring.cell(hud.document(), destinationId).content;
        assertEquals(HudNodeKind.TABLE, created.kind);
        assertEquals(3, created.table.columns);
        assertEquals(2, created.table.rows.size());
        for (var row : created.table.rows) for (var createdCell : row.cells) {
            assertEquals(Float.valueOf(64f), createdCell.constraints.minWidth);
            assertEquals(Float.valueOf(32f), createdCell.constraints.minHeight);
            assertNull(createdCell.content);
        }
        assertFalse(session.createTableAt(1, 1, captured, hud));
        assertEquals(1, hud.editSession().historySize());
        assertTrue(hud.editSession().undo());
        assertNull(HudLayoutAuthoring.cell(hud.document(), destinationId).content);
        assertTrue(hud.editSession().redo());
        HudDocumentV1 reopened = new HudDocumentCodec().read(
                new HudDocumentCodec().write(hud.document()));
        assertEquals(3, HudLayoutAuthoring.cell(reopened, destinationId).content.table.columns);

        String secondId = hud.document().root.table.rows.get(0).cells.get(1).id;
        HudEditorSession.WidgetDropTarget second = new HudEditorSession.WidgetDropTarget(
                "root", null, false, false, "root", secondId);
        assertTrue(session.createWidgetAt(HudNodeKind.GROUP, second, hud));
        assertFalse(session.canCreateWidgetAt(HudNodeKind.GROUP, "root"));
        assertTrue(session.deleteNode(created.id));
        assertNull(HudLayoutAuthoring.cell(hud.document(), destinationId).content);
        assertTrue(session.createTableAt(1, 1, captured, hud));
    }

    @Test
    public void windowButtonCreatesOneUndoableSelectedWindow() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/window", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.windowButton().isDisabled());
        panel.windowButton().setChecked(true);
        deferred.runAll();
        assertEquals("window-1", session.selectedNodeId());
        assertEquals(1, hud.editSession().historySize());
        assertEquals(HudNodeKind.WINDOW, rootRelation(hud).node.kind);
        assertEquals(HudPlacementKind.FREE, rootRelation(hud).placementKind);
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("window-1", rootRelation(hud).node.id);
    }

    @Test
    public void dialogButtonCreatesOneUndoableSelectedDialog() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/dialog", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.dialogButton().isDisabled());
        panel.dialogButton().setChecked(true);
        deferred.runAll();
        assertEquals("dialog-1", session.selectedNodeId());
        assertEquals(1, hud.editSession().historySize());
        assertEquals(HudNodeKind.DIALOG, rootRelation(hud).node.kind);
        assertTrue(rootRelation(hud).node.dialog.modal);
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("dialog-1", rootRelation(hud).node.id);
    }

    @Test
    public void labelButtonCreatesFreeAndManagedLabelsWithNativeSizing() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode table = table("table-1");
        root.children.add(games.pixscape.runtime.hud.document.HudChild.free(
                table, new games.pixscape.runtime.hud.document.HudFreePlacement()));
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(),
                new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.labelButton().isDisabled());
        panel.labelButton().setChecked(true);
        deferred.runAll();
        assertEquals("label-1", session.selectedNodeId());
        var free = hud.document().root.children.get(1);
        assertEquals(HudPlacementKind.FREE, free.placementKind);
        assertEquals("Label", free.node.label.text);
        assertNull(free.node.label.styleName);
        assertEquals(1, hud.editSession().historySize());
        assertTrue(hud.editSession().undo());
        assertEquals(1, hud.document().root.children.size());
        assertEquals("root", session.selectedNodeId());
        assertTrue(hud.editSession().redo());
        assertEquals("label-1", hud.document().root.children.get(1).node.id);
        assertEquals("label-1", session.selectedNodeId());

        session.selectNode("table-1");
        deferred.runAll();
        panel.labelButton().setChecked(true);
        deferred.runAll();
        var managed = relation(rootRelation(hud).node);
        assertEquals(HudPlacementKind.CELL, managed.placementKind);
        assertNull(managed.cell.prefWidth);
        assertNull(managed.cell.prefHeight);
    }

    @Test
    public void textraLabelButtonCreatesOneUndoableTypingLabelWithDocumentedDefaults()
            throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.textraLabelButton().isDisabled());
        panel.textraLabelButton().setChecked(true);
        deferred.runAll();

        var relation = rootRelation(hud);
        assertEquals(HudNodeKind.TEXTRA_LABEL, relation.node.kind);
        assertEquals("textra-label-1", relation.node.id);
        assertEquals("Text", relation.node.textraLabel.text);
        assertNull(relation.node.textraLabel.styleName);
        assertNull(relation.node.textraLabel.fontAssetId);
        assertTrue(relation.node.textraLabel.typingEnabled);
        assertEquals("textra-label-1", session.selectedNodeId());
        assertEquals(1, hud.editSession().historySize());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("textra-label-1", rootRelation(hud).node.id);
    }

    @Test
    public void textButtonCreatesOneUndoableSelectedChildWithNativeManagedSize() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode table = table("table-1");
        root.children.add(games.pixscape.runtime.hud.document.HudChild.free(
                table, new games.pixscape.runtime.hud.document.HudFreePlacement()));
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(),
                new HudDocumentV1(root)));
        hud.setSelectedNodeId("table-1");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.textButtonButton().isDisabled());
        panel.textButtonButton().setChecked(true);
        deferred.runAll();

        var relation = relation(rootRelation(hud).node);
        assertEquals(HudPlacementKind.CELL, relation.placementKind);
        assertEquals("text-button-1", relation.node.id);
        assertEquals("Button", relation.node.textButton.text);
        assertNull(relation.node.textButton.styleName);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals("text-button-1", session.selectedNodeId());
        assertEquals(1, hud.editSession().historySize());
        assertTrue(hud.editSession().undo());
        assertTrue(cell(rootRelation(hud).node).content == null);
        assertEquals("table-1", session.selectedNodeId());
        assertTrue(hud.editSession().redo());
        assertEquals("text-button-1", cell(rootRelation(hud).node).content.id);
    }

    @Test
    public void checkBoxCreatesOneUndoableSelectedChildWithDefaultState() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.checkBoxButton().isDisabled());
        panel.checkBoxButton().setChecked(true);
        deferred.runAll();
        var relation = rootRelation(hud);
        assertEquals("check-box-1", relation.node.id);
        assertEquals("CheckBox", relation.node.checkBox.text);
        assertNull(relation.node.checkBox.styleName);
        assertFalse(relation.node.checkBox.checked);
        assertFalse(relation.node.checkBox.disabled);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals("check-box-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("check-box-1", rootRelation(hud).node.id);
    }

    @Test
    public void sliderCreatesOneUndoableSelectedChildWithDocumentedDefaults() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.sliderButton().isDisabled());
        panel.sliderButton().setChecked(true);
        deferred.runAll();

        var relation = rootRelation(hud);
        assertEquals(HudPlacementKind.CELL, relation.placementKind);
        assertEquals("slider-1", relation.node.id);
        assertEquals(games.pixscape.runtime.hud.document.HudSliderOrientation.HORIZONTAL,
                relation.node.slider.orientation);
        assertEquals(0f, relation.node.slider.min, 0f);
        assertEquals(100f, relation.node.slider.max, 0f);
        assertEquals(1f, relation.node.slider.stepSize, 0f);
        assertEquals(50f, relation.node.slider.value, 0f);
        assertNull(relation.node.slider.styleName);
        assertFalse(relation.node.slider.disabled);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals(1, hud.editSession().historySize());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("slider-1", rootRelation(hud).node.id);
    }

    @Test
    public void imageButtonCreatesWithoutAnImageSelectionAndKeepsNativeManagedSize() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.imageButtonButton().isDisabled());
        panel.imageButtonButton().setChecked(true);
        deferred.runAll();

        var relation = rootRelation(hud);
        assertEquals(HudNodeKind.IMAGE_BUTTON, relation.node.kind);
        assertNull(relation.node.imageButton.styleName);
        assertNull(relation.node.imageButton.imageUp);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals("image-button-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
    }

    @Test
    public void imageTextButtonCreatesWithDefaultTextAndSupportsUndoRedo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.imageTextButtonButton().isDisabled());
        panel.imageTextButtonButton().setChecked(true);
        deferred.runAll();

        var relation = rootRelation(hud);
        assertEquals(HudNodeKind.IMAGE_TEXT_BUTTON, relation.node.kind);
        assertEquals("Button", relation.node.imageTextButton.text);
        assertNull(relation.node.imageTextButton.styleName);
        assertNull(relation.node.imageTextButton.imageUp);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals("image-text-button-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("image-text-button-1", rootRelation(hud).node.id);
    }

    @Test
    public void textFieldCreatesWithBuiltInDefaultAndSupportsUndoRedo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.textFieldButton().isDisabled());
        panel.textFieldButton().setChecked(true);
        deferred.runAll();

        var relation = rootRelation(hud);
        assertEquals(HudPlacementKind.CELL, relation.placementKind);
        assertEquals(HudNodeKind.TEXT_FIELD, relation.node.kind);
        assertEquals("text-field-1", relation.node.id);
        assertEquals("", relation.node.textField.text);
        assertEquals("", relation.node.textField.messageText);
        assertNull(relation.node.textField.styleName);
        assertEquals(0, relation.node.textField.maxLength);
        assertFalse(relation.node.textField.passwordMode);
        assertNull(relation.cell.prefWidth);
        assertNull(relation.cell.prefHeight);
        assertEquals("text-field-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertEquals("root", session.selectedNodeId());
        assertTrue(hud.editSession().redo());
        assertEquals("text-field-1", rootRelation(hud).node.id);
    }

    @Test
    public void selectBoxCreatesWithNativeDefaultsAndSupportsUndoRedo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.selectBoxButton().isDisabled());
        panel.selectBoxButton().setChecked(true);
        deferred.runAll();

        var created = rootRelation(hud).node;
        assertEquals(HudNodeKind.SELECT_BOX, created.kind);
        assertEquals(java.util.List.of("Option 1", "Option 2", "Option 3"), created.selectBox.items);
        assertEquals(0, created.selectBox.selectedIndex);
        assertNull(created.selectBox.styleName);
        assertEquals(0, created.selectBox.maxListCount);
        assertFalse(created.selectBox.disabled);
        assertEquals("select-box-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("select-box-1", rootRelation(hud).node.id);
    }

    @Test
    public void listCreatesWithNativeDefaultsAndSupportsUndoRedo() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudNode root = table("root");
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(), new HudDocumentV1(root)));
        hud.setSelectedNodeId("root");
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.projectLabelResources(session, null);
        HudPanelTestSupport.projectHistoryNavigation(session, hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);

        assertFalse(panel.listButton().isDisabled());
        panel.listButton().setChecked(true);
        deferred.runAll();

        HudNode created = rootRelation(hud).node;
        assertEquals(HudNodeKind.LIST, created.kind);
        assertEquals(java.util.List.of("Item 1", "Item 2", "Item 3"), created.list.items);
        assertTrue(created.list.required);
        assertEquals(0, created.list.selectedIndex);
        assertNull(created.list.styleName);
        assertEquals("list-1", session.selectedNodeId());
        assertTrue(hud.editSession().undo());
        assertTrue(rootEmpty(hud));
        assertTrue(hud.editSession().redo());
        assertEquals("list-1", rootRelation(hud).node.id);
    }

    @Test
    public void contentScrollsVerticallyWhenDockedHeightIsSmallAndFitsWhenExpanded() {
        EditorDocumentManager manager = new EditorDocumentManager();
        manager.openHudScreen("hud/main", "HUD");
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(
                new HudEditorSession(), manager, deferred::post);

        panel.setSize(160f, 72f);
        panel.validate();
        panel.scrollPane().layout();
        assertTrue(panel.scrollPane().getMaxY() > 0f);
        assertFalse(panel.scrollPane().isScrollingDisabledY());
        assertTrue(panel.scrollPane().isScrollingDisabledX());

        panel.scrollPane().setScrollY(panel.scrollPane().getMaxY());
        panel.scrollPane().updateVisualScroll();
        assertEquals(panel.scrollPane().getMaxY(), panel.scrollPane().getVisualScrollY(), 0f);

        panel.setSize(160f, 720f);
        panel.validate();
        panel.scrollPane().layout();
        assertEquals(0f, panel.scrollPane().getMaxY(), 0f);
    }

    @Test
    public void authoringButtonsKeepTheirInstancesInOrderedWrappingCategories() {
        HudWidgetsPanel panel = new HudWidgetsPanel(
                new HudEditorSession(), new EditorDocumentManager(), Runnable::run);

        assertTrue(hasCategoryLabel(panel, "Layout"));
        assertTrue(hasCategoryLabel(panel, "Text"));
        assertTrue(hasCategoryLabel(panel, "Buttons"));
        assertTrue(hasCategoryLabel(panel, "Selection & values"));
        assertSame(panel.layoutButtons(), panel.groupButton().getParent());
        assertSame(panel.textButtons(), panel.textFieldButton().getParent());
        assertSame(panel.buttonButtons(), panel.imageTextButtonButton().getParent());
        assertSame(panel.selectionButtons(), panel.sliderButton().getParent());
        panel.setSize(1000f, 720f);
        panel.validate();
        panel.scrollPane().layout();
        assertEquals(HudWidgetsPanel.maxCategoryWidth(), panel.buttonButtons().getWidth(), 0.01f);
        panel.setSize(100f, 720f);
        panel.validate();
        panel.scrollPane().layout();
        panel.layoutButtons().invalidate();
        panel.layoutButtons().validate();
        assertTrue("a narrow panel wraps layout buttons", panel.tableButton().getY()
                < panel.groupButton().getY());
        layoutAtCategoryWidth(panel.layoutButtons());
        layoutAtCategoryWidth(panel.textButtons());
        layoutAtCategoryWidth(panel.buttonButtons());
        layoutAtCategoryWidth(panel.selectionButtons());

        ImageButton[] buttons = authoringButtons(panel);
        String[] iconKeys = {
                "widget_group", "widget_table", "widget_stack", "widget_container", "widget_scrollpane",
                "widget_window", "widget_window",
                "widget_label", "widget_textra", "widget_textbutton", "widget_imagebutton",
                "widget_textimagebutton",
                "widget_textfield", "widget_selectbox", "widget_list", "widget_checkbox", "widget_slider",
                "widget_progressbar"
        };
        Set<ImageButton.ImageButtonStyle> styles =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < buttons.length; index++) {
            ImageButton button = buttons[index];
            assertTrue(styles.add(button.getStyle()));
            assertSame(VisUI.getSkin().getDrawable(iconKeys[index]),
                    button.getStyle().imageUp);
            assertEquals(CommonLayout.HUD_WIDGET_ICON_SIZE,
                    button.getImageCell().getPrefWidth(), 0f);
            assertEquals(CommonLayout.HUD_WIDGET_ICON_SIZE,
                    button.getImageCell().getPrefHeight(), 0f);
            assertEquals(CommonLayout.HUD_WIDGET_BUTTON_SIZE, button.getWidth(), 0f);
            assertEquals(CommonLayout.HUD_WIDGET_BUTTON_SIZE, button.getHeight(), 0f);
            assertSame(Touchable.enabled, button.getTouchable());
            assertTrue(button.getListeners().size >= 3);
            assertNotSame(button.getStyle().imageUp, button.getStyle().imageOver);
            assertNotSame(button.getStyle().imageUp, button.getStyle().imageDown);
            assertNotSame(button.getStyle().imageUp, button.getStyle().imageDisabled);
            assertSame(button.getStyle().imageUp, button.getStyle().imageChecked);
            assertSame(button.getStyle().imageOver, button.getStyle().imageCheckedOver);
            assertSame(button.getStyle().imageDown, button.getStyle().imageCheckedDown);
        }
    }

    private static boolean hasCategoryLabel(HudWidgetsPanel panel, String expected) {
        for (com.badlogic.gdx.scenes.scene2d.Actor child : panel.content().getChildren()) {
            if (child instanceof com.kotcrab.vis.ui.widget.VisLabel label
                    && expected.equals(label.getText().toString())) return true;
        }
        return false;
    }

    private static void layoutAtCategoryWidth(HorizontalGroup group) {
        group.setSize(HudWidgetsPanel.maxCategoryWidth(), CommonLayout.HUD_WIDGET_BUTTON_SIZE);
        group.validate();
    }

    @Test
    public void wrappingGroupsUseAvailableWidthAndNeverExceedSevenButtonsPerLine() {
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.imageUp = VisUI.getSkin().getDrawable("widget_group");
        ImageButton[] fixture = new ImageButton[8];
        for (int index = 0; index < fixture.length; index++) {
            fixture[index] = new ImageButton(style);
            fixture[index].getImageCell().size(CommonLayout.HUD_WIDGET_BUTTON_SIZE);
        }
        HorizontalGroup group = HudWidgetsPanel.wrappingButtonGroup(fixture);
        group.setSize(HudWidgetsPanel.maxCategoryWidth(), 200f);
        group.validate();

        assertEquals(fixture[0].getY(), fixture[6].getY(), 0.01f);
        assertTrue("the eighth fixture button wraps", fixture[7].getY() < fixture[0].getY());

        group.setSize(CommonLayout.HUD_WIDGET_BUTTON_SIZE, 400f);
        group.invalidate();
        group.validate();
        assertTrue("a narrow category wraps its second button", fixture[1].getY() < fixture[0].getY());
    }

    @Test
    public void widgetIconPackingRetainsThePreexistingJointDrawable() {
        var joint = VisUI.getSkin().getDrawable("joint");

        assertEquals(23f, joint.getMinWidth(), 0f);
        assertEquals(15f, joint.getMinHeight(), 0f);
    }

    private static int actionableWidgetCount(HudWidgetsPanel panel) {
        int buttons = 0;
        for (ImageButton button : authoringButtons(panel)) {
            if (!button.isDisabled()) buttons++;
        }
        return buttons;
    }

    private static ImageButton[] authoringButtons(HudWidgetsPanel panel) {
        return new ImageButton[]{
                panel.groupButton(), panel.tableButton(), panel.stackButton(),
                panel.containerButton(), panel.scrollPaneButton(), panel.windowButton(),
                panel.dialogButton(),
                panel.labelButton(), panel.textraLabelButton(),
                panel.textButtonButton(), panel.imageButtonButton(), panel.imageTextButtonButton(),
                panel.textFieldButton(),
                panel.selectBoxButton(), panel.listButton(), panel.checkBoxButton(), panel.sliderButton(), panel.progressBarButton()
        };
    }

    @Test public void toolboxContainsConstructsButNoImageResourceAction() {
        assertEquals(java.util.List.of(HudNodeKind.GROUP, HudNodeKind.TABLE,
                HudNodeKind.STACK, HudNodeKind.CONTAINER, HudNodeKind.SCROLL_PANE,
                HudNodeKind.WINDOW, HudNodeKind.DIALOG),
                HudWidgetsPanel.layoutKinds());
        assertEquals(java.util.List.of(HudNodeKind.LABEL, HudNodeKind.TEXTRA_LABEL,
                        HudNodeKind.TEXT_FIELD, HudNodeKind.TEXT_BUTTON, HudNodeKind.IMAGE_BUTTON,
                        HudNodeKind.IMAGE_TEXT_BUTTON, HudNodeKind.CHECK_BOX,
                        HudNodeKind.SELECT_BOX, HudNodeKind.LIST, HudNodeKind.SLIDER, HudNodeKind.PROGRESS_BAR),
                HudWidgetsPanel.contentKinds());
    }

    @Test public void layoutDebugRemainsAnEditModeInvariantWithoutAToolboxToggle() throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset(),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        HudWidgetsPanel panel = new HudWidgetsPanel(session, manager, deferred::post);
        assertTrue(session.isShowingLayoutBounds());
        session.selectNode("root");
        deferred.runAll();
        assertTrue(session.isShowingLayoutBounds());
        assertFalse(panel.content().getChildren().toString().contains("Show layout"));
    }

    private static HudNode table(String id) {
        HudNode node = new HudNode(id, HudNodeKind.TABLE);
        node.table = HudLayoutAuthoring.newTableLayout(node, 1, 1, false);
        return node;
    }

    private static HudTableCell cell(HudNode table) {
        return table.table.rows.get(0).cells.get(0);
    }

    private static HudChild relation(HudNode table) {
        HudTableCell cell = cell(table);
        return HudChild.cell(cell.content, cell.constraints);
    }

    private static HudChild rootRelation(HudScreenEditorDocument hud) {
        HudNode root = hud.document().root;
        return root.kind == HudNodeKind.TABLE ? relation(root) : root.children.get(0);
    }

    private static boolean rootEmpty(HudScreenEditorDocument hud) {
        HudNode root = hud.document().root;
        return root.kind == HudNodeKind.TABLE ? cell(root).content == null : root.children.isEmpty();
    }

    private static games.pixscape.runtime.hud.HudScreenAsset asset() {
        games.pixscape.runtime.hud.HudScreenAsset asset =
                new games.pixscape.runtime.hud.HudScreenAsset();
        asset.documentId = "hud/main.json";
        return asset;
    }
}
