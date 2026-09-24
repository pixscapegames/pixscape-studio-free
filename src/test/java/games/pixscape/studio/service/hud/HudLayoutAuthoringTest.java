package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.runtime.hud.document.HudWindowAction;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
import games.pixscape.studio.ui.config.CommonLayout;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HudLayoutAuthoringTest {
    @Test public void movingAndSwappingCellContentsKeepsCellIdentityConstraintsAndSubtrees() {
        HudDocumentV1 document = document();
        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(document.root, 1, 4, false);
        table.table.rows.get(0).cells.remove(3);
        document.root.children.add(HudChild.free(table, new games.pixscape.runtime.hud.document.HudFreePlacement()));
        HudTableCell first = table.table.rows.get(0).cells.get(0);
        HudTableCell second = table.table.rows.get(0).cells.get(1);
        HudTableCell third = table.table.rows.get(0).cells.get(2);
        first.constraints.minWidth = 41f;
        first.colspan = 2;
        second.constraints.minWidth = 47f;
        third.constraints.minWidth = 53f;
        HudNode layout = new HudNode("layout", HudNodeKind.STACK);
        HudNode leaf = new HudNode("leaf", HudNodeKind.IMAGE);
        leaf.image = new games.pixscape.runtime.hud.document.HudImageData();
        leaf.image.source = games.pixscape.runtime.hud.document.HudImageSource.REGION;
        leaf.image.resourceName = "icons/leaf";
        layout.children.add(HudChild.direct(leaf));
        first.content = layout;
        second.content = new HudNode("peer", HudNodeKind.GROUP);

        assertFalse(HudLayoutAuthoring.moveCellContent(document, table.id, first.id, "layout", first.id));
        assertFalse(HudLayoutAuthoring.moveCellContent(document, table.id, first.id, "wrong", third.id));
        assertTrue(HudLayoutAuthoring.moveCellContent(document, table.id, first.id, "layout", second.id));
        assertEquals("peer", first.content.id);
        assertEquals("layout", second.content.id);
        assertEquals("leaf", second.content.children.get(0).node.id);
        assertEquals("icons/leaf", second.content.children.get(0).node.image.resourceName);
        assertTrue(HudLayoutAuthoring.moveCellContent(document, table.id, second.id, "layout", third.id));
        assertNull(second.content);
        assertEquals("layout", third.content.id);
        assertEquals(41f, first.constraints.minWidth, 0f);
        assertEquals(2, first.colspan);
        assertEquals(47f, second.constraints.minWidth, 0f);
        assertEquals(53f, third.constraints.minWidth, 0f);
    }
    @Test
    public void windowCreationUsesExistingFreeCellDirectPlacementsAndInitialSize() {
        HudDocumentV1 document = document();
        String freeId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.WINDOW);
        HudChild free = findChild(document.root, freeId);
        assertEquals(HudPlacementKind.FREE, free.placementKind);
        assertEquals("Window", free.node.window.title);
        assertEquals(240f, free.node.actor.width, 0f);
        assertEquals(160f, free.node.actor.height, 0f);

        String tableId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.TABLE);
        String cellId = HudLayoutAuthoring.addChild(document, tableId, HudNodeKind.WINDOW);
        HudChild cell = findChild(document.root, cellId);
        assertEquals(HudPlacementKind.CELL, cell.placementKind);
        assertEquals(Float.valueOf(240f), cell.cell.prefWidth);
        assertEquals(Float.valueOf(160f), cell.cell.prefHeight);
        String nestedId = HudLayoutAuthoring.addLabel(document, cellId, "Content", null);
        assertEquals(HudPlacementKind.CELL, findChild(document.root, nestedId).placementKind);
        HudLayoutAuthoring.node(document, cellId).table =
                HudLayoutAuthoring.newTableLayout(document.root, 1, 2, false);
        HudLayoutAuthoring.node(document, cellId).table.rows.get(0).cells.get(0).content =
                HudLayoutAuthoring.node(document, nestedId);
        assertEquals(HudPlacementKind.CELL, findChild(document.root,
                HudLayoutAuthoring.addChild(document, cellId, HudNodeKind.TABLE)).placementKind);

        String stackId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.STACK);
        String directId = HudLayoutAuthoring.addChild(document, stackId, HudNodeKind.WINDOW);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, directId).placementKind);
    }

    @Test
    public void dialogCreationUsesFreeCellDirectAndNestedNonmodalDefaults() {
        HudDocumentV1 document = document();
        String rootDialogId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.DIALOG);
        HudChild rootDialog = findChild(document.root, rootDialogId);
        assertEquals(HudPlacementKind.FREE, rootDialog.placementKind);
        assertEquals("Dialog", rootDialog.node.dialog.title);
        assertTrue(rootDialog.node.dialog.modal);
        assertEquals(240f, rootDialog.node.actor.width, 0f);
        assertEquals(160f, rootDialog.node.actor.height, 0f);
        String nestedId = HudLayoutAuthoring.addChild(document, rootDialogId, HudNodeKind.DIALOG);
        HudChild nested = findChild(document.root, nestedId);
        assertEquals(HudPlacementKind.CELL, nested.placementKind);
        assertFalse(nested.node.dialog.modal);
        assertEquals(Float.valueOf(240f), nested.cell.prefWidth);
        String paneId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.SCROLL_PANE);
        String scrolledId = HudLayoutAuthoring.addChild(document, paneId, HudNodeKind.DIALOG);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, scrolledId).placementKind);
        assertFalse(findChild(document.root, scrolledId).node.dialog.modal);
        HudNode scrollRoot = new HudNode("scroll-root", HudNodeKind.SCROLL_PANE);
        scrollRoot.scrollPane = new games.pixscape.runtime.hud.document.HudScrollPaneData();
        HudDocumentV1 directScroll = new HudDocumentV1(scrollRoot);
        String directId = HudLayoutAuthoring.addChild(directScroll, "scroll-root", HudNodeKind.DIALOG);
        assertFalse(HudLayoutAuthoring.node(directScroll, directId).dialog.modal);
    }

    @Test
    public void deletingDialogPrunesButtonAssociationAndUndoRestoresIt() {
        HudDocumentV1 document = document();
        String dialogId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.DIALOG);
        String buttonId = HudLayoutAuthoring.addTextButton(document, "root", "Open", null);
        HudLayoutAuthoring.node(document, buttonId).windowActions.add(
                new HudWindowAction(dialogId, HudWindowActionKind.SHOW));
        HudDocumentEditSession history = new HudDocumentEditSession(asset(), document);
        history.edit("Remove Dialog", candidate -> {
            assertEquals("root", HudLayoutAuthoring.removeChild(candidate, dialogId));
            return candidate;
        });
        assertNull(HudLayoutAuthoring.node(history.document(), dialogId));
        assertTrue(HudLayoutAuthoring.node(history.document(), buttonId).windowActions.isEmpty());
        assertTrue(history.undo());
        assertNotNull(HudLayoutAuthoring.node(history.document(), dialogId));
        assertEquals(dialogId, HudLayoutAuthoring.node(history.document(), buttonId)
                .windowActions.get(0).targetId);
        assertTrue(history.redo());
        assertTrue(HudLayoutAuthoring.node(history.document(), buttonId).windowActions.isEmpty());
    }
    @Test
    public void labelCreationUsesParentPlacementAndNativeTablePreference() {
        HudDocumentV1 freeDocument = document();
        assertEquals("label-1", HudLayoutAuthoring.addLabel(
                freeDocument, "root", "Label", "hud-body"));
        HudChild free = freeDocument.root.children.get(0);
        assertEquals(HudPlacementKind.FREE, free.placementKind);
        assertEquals("Label", free.node.label.text);
        assertEquals("hud-body", free.node.label.styleName);
        assertEquals(0f, free.node.actor.width, 0f);
        assertEquals(0f, free.node.actor.height, 0f);

        HudNode table = new HudNode("root", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 1, false);
        HudDocumentEditSession history = new HudDocumentEditSession(
                asset(), new HudDocumentV1(table));
        history.edit("Add Label child", candidate -> {
            assertEquals("label-1", HudLayoutAuthoring.addLabel(
                    candidate, "root", "Étiquette\nHUD", null));
            return candidate;
        });

        HudTableCell cell = history.document().root.table.rows.get(0).cells.get(0);
        assertNull(cell.constraints.prefWidth);
        assertNull(cell.constraints.prefHeight);
        assertEquals("Étiquette\nHUD", cell.content.label.text);
        assertTrue(history.undo());
        assertNull(history.document().root.table.rows.get(0).cells.get(0).content);
        assertTrue(history.redo());
        HudTableCell restored = history.document().root.table.rows.get(0).cells.get(0);
        assertEquals("label-1", restored.content.id);
        assertNull(restored.content.label.styleName);
        assertNull(restored.constraints.prefWidth);

        HudDocumentV1 reloaded = new HudDocumentCodec().read(
                new HudDocumentCodec().write(history.document()));
        assertEquals("Étiquette\nHUD", reloaded.root.table.rows.get(0).cells.get(0).content.label.text);
        assertNull(reloaded.root.table.rows.get(0).cells.get(0).content.label.styleName);
    }

    @Test
    public void sliderCreationUsesFreeCellAndDirectParentPlacement() {
        HudDocumentV1 document = document();
        String freeId = HudLayoutAuthoring.addSlider(document, "root", null);
        assertEquals("slider-1", freeId);
        assertEquals(HudPlacementKind.FREE, findChild(document.root, freeId).placementKind);

        String tableId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.TABLE);
        String cellId = HudLayoutAuthoring.addSlider(document, tableId, null);
        assertEquals(HudPlacementKind.CELL, findChild(document.root, cellId).placementKind);

        String stackId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.STACK);
        String directId = HudLayoutAuthoring.addSlider(document, stackId, null);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, directId).placementKind);
        assertEquals(0f, findChild(document.root, directId).node.actor.width, 0f);
        assertEquals(0f, findChild(document.root, directId).node.actor.height, 0f);
    }

    @Test
    public void progressBarCreationUsesFreeCellAndDirectParentPlacement() {
        HudDocumentV1 document = document();
        String freeId = HudLayoutAuthoring.addProgressBar(document, "root", null);
        assertEquals("progress-bar-1", freeId);
        assertEquals(HudPlacementKind.FREE, findChild(document.root, freeId).placementKind);
        assertEquals(1f, findChild(document.root, freeId).node.progressBar.stepSize, 0f);

        String tableId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.TABLE);
        String cellId = HudLayoutAuthoring.addProgressBar(document, tableId, null);
        assertEquals(HudPlacementKind.CELL, findChild(document.root, cellId).placementKind);

        String stackId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.STACK);
        String directId = HudLayoutAuthoring.addProgressBar(document, stackId, null);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, directId).placementKind);
    }

    @Test
    public void cellAxesRetainIndependentAutomaticAndExplicitConstraintsThroughHistoryAndReload() {
        HudNode table = new HudNode("root", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 1, false);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "Button";
        table.table.rows.get(0).cells.get(0).content = button;
        HudDocumentEditSession history = new HudDocumentEditSession(asset(), new HudDocumentV1(table));

        history.edit("Set preferred width", candidate -> {
            candidate.root.table.rows.get(0).cells.get(0).constraints.prefWidth = 64f;
            return candidate;
        });
        HudTableCell edited = history.document().root.table.rows.get(0).cells.get(0);
        assertEquals(Float.valueOf(64f), edited.constraints.prefWidth);
        assertNull(edited.constraints.prefHeight);
        assertTrue(history.undo());
        assertNull(history.document().root.table.rows.get(0).cells.get(0).constraints.prefWidth);
        assertTrue(history.redo());

        HudDocumentV1 reloaded = new HudDocumentCodec().read(
                new HudDocumentCodec().write(history.document()));
        HudTableCell restored = reloaded.root.table.rows.get(0).cells.get(0);
        assertEquals(Float.valueOf(64f), restored.constraints.prefWidth);
        assertNull(restored.constraints.prefHeight);
    }

    @Test
    public void zeroSizeRemainsValidAndRoundTripsWithoutCreationNormalization() {
        HudDocumentV1 document = document();
        document.root.actor.width = 0f;
        document.root.actor.height = 0f;

        HudDocumentV1 restored = new HudDocumentCodec().read(
                new HudDocumentCodec().write(document));

        assertEquals(0f, restored.root.actor.width, 0f);
        assertEquals(0f, restored.root.actor.height, 0f);
        HudDocumentV1 missingFields = new HudDocumentCodec().read("""
                {"schemaVersion":2,"root":{"id":"legacy","kind":"GROUP",
                 "actor":{},"children":[]}}
                """);
        assertEquals(0f, missingFields.root.actor.width, 0f);
        assertEquals(0f, missingFields.root.actor.height, 0f);
        HudDocumentEditSession session = new HudDocumentEditSession(
                asset(), restored);
        assertEquals(0f, session.document().root.actor.width, 0f);
        assertEquals(0f, session.document().root.actor.height, 0f);
        assertFalse(session.isDirty());
        assertEquals(0, session.historySize());
    }

    @Test
    public void freeStructuralChildrenReceiveTheCentralCreationSize() {
        assertEquals(200f, CommonLayout.DEFAULT_FREE_WIDTH, 0f);
        assertEquals(120f, CommonLayout.DEFAULT_FREE_HEIGHT, 0f);
        for (HudNodeKind kind : List.of(HudNodeKind.GROUP,
                HudNodeKind.STACK, HudNodeKind.CONTAINER, HudNodeKind.SCROLL_PANE)) {
            HudDocumentV1 document = document();
            String id = HudLayoutAuthoring.addChild(document, "root", kind);
            HudChild child = findChild(document.root, id);

            assertEquals(HudPlacementKind.FREE, child.placementKind);
            assertEquals(CommonLayout.DEFAULT_FREE_WIDTH, child.node.actor.width, 0f);
            assertEquals(CommonLayout.DEFAULT_FREE_HEIGHT, child.node.actor.height, 0f);
        }
        HudDocumentV1 tableDocument = document();
        HudChild table = findChild(tableDocument.root,
                HudLayoutAuthoring.addChild(tableDocument, "root", HudNodeKind.TABLE));
        assertEquals(HudPlacementKind.DIRECT, table.placementKind);
        assertTrue(table.node.fillParent);
        assertEquals(0f, table.node.actor.width, 0f);
        assertEquals(0f, table.node.actor.height, 0f);
    }

    @Test
    public void scrollPaneCreationUsesOneDirectChildAndOneUndoableTransaction() {
        HudDocumentEditSession session = new HudDocumentEditSession(asset(), document());

        add(session, "root", HudNodeKind.SCROLL_PANE);

        HudNode pane = findChild(session.document().root, "scroll-pane-1").node;
        assertNotNull(pane.scrollPane);
        assertEquals(CommonLayout.DEFAULT_FREE_WIDTH, pane.actor.width, 0f);
        assertEquals(CommonLayout.DEFAULT_FREE_HEIGHT, pane.actor.height, 0f);
        assertTrue(HudLayoutAuthoring.canAddChild(session.document(), pane.id));

        add(session, pane.id, HudNodeKind.TABLE);
        HudChild content = findChild(session.document().root, "table-1");
        assertEquals(HudPlacementKind.DIRECT, content.placementKind);
        assertFalse(HudLayoutAuthoring.canAddChild(session.document(), pane.id));
        assertEquals(2, session.historySize());

        assertTrue(session.undo());
        assertTrue(HudLayoutAuthoring.canAddChild(session.document(), pane.id));
        assertTrue(session.redo());
        assertEquals(HudPlacementKind.DIRECT,
                findChild(session.document().root, "table-1").placementKind);
    }

    @Test
    public void cellAndDirectStructuralChildrenRemainLayoutManagedAtZero() {
        HudDocumentV1 document = document();
        String tableId = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.TABLE);
        String cellChild = HudLayoutAuthoring.addChild(document, tableId, HudNodeKind.STACK);
        String directChild = HudLayoutAuthoring.addChild(document, cellChild, HudNodeKind.CONTAINER);

        HudChild cell = findChild(document.root, cellChild);
        assertEquals(HudPlacementKind.CELL, cell.placementKind);
        assertEquals(0f, cell.node.actor.width, 0f);
        assertEquals(0f, cell.node.actor.height, 0f);
        HudChild direct = findChild(document.root, directChild);
        assertEquals(HudPlacementKind.DIRECT, direct.placementKind);
        assertEquals(0f, direct.node.actor.width, 0f);
        assertEquals(0f, direct.node.actor.height, 0f);
    }

    @Test
    public void rootAndFreeImageKeepTheirExistingSizingSemantics() {
        HudDocumentV1 document = document();
        assertEquals(0f, document.root.actor.width, 0f);
        assertEquals(0f, document.root.actor.height, 0f);

        assertEquals("image-1", HudLayoutAuthoring.addImage(
                document, "root", "image-1", "portrait__a1"));
        HudNode image = findChild(document.root, "image-1").node;
        assertEquals(0f, image.actor.width, 0f);
        assertEquals(0f, image.actor.height, 0f);
    }

    @Test
    public void oneCreationTransactionCarriesDefaultsThroughUndoAndRedo() {
        HudDocumentEditSession session = new HudDocumentEditSession(
                asset(), document());

        add(session, "root", HudNodeKind.TABLE);

        assertEquals(1, session.historySize());
        HudNode created = findChild(session.document().root, "table-1").node;
        assertTrue(created.fillParent);
        assertEquals(0f, created.actor.width, 0f);
        assertEquals(0f, created.actor.height, 0f);
        assertTrue(session.undo());
        assertNull(findChild(session.document().root, "table-1"));
        assertTrue(session.redo());
        HudNode restored = findChild(session.document().root, "table-1").node;
        assertTrue(restored.fillParent);
        assertEquals(0f, restored.actor.width, 0f);
        assertEquals(0f, restored.actor.height, 0f);
        assertEquals(1, session.historySize());
    }

    @Test
    public void creationDefaultsAreAuthoredWithoutScene2dActorSizingWorkaround() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/hud/HudLayoutAuthoring.java"));
        assertFalse(source.contains("com.badlogic.gdx.scenes.scene2d.Actor"));
        assertFalse(source.contains(".setSize("));
        assertFalse(source.contains(".setWidth("));
        assertFalse(source.contains(".setHeight("));
    }

    @Test
    public void allocatesSmallestUnusedKindIdAcrossTheWholeTree() {
        HudDocumentV1 document = document();
        HudNode groupOne = new HudNode("group-1", HudNodeKind.GROUP);
        HudNode groupThree = new HudNode("group-3", HudNodeKind.GROUP);
        document.root.children.add(free(groupOne));
        groupOne.children.add(free(groupThree));
        HudNode deepTable = new HudNode("table-1", HudNodeKind.TABLE);
        groupThree.children.add(free(deepTable));

        assertEquals("group-2", HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.GROUP));
        assertEquals("table-2", HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.TABLE));
        assertEquals("group-4", HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.GROUP));
        assertEquals("container-1", HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.CONTAINER));
    }

    @Test
    public void buildsNestedTreeWithPlacementDeterminedOnlyByParent() {
        HudDocumentV1 document = document();

        String containerId = HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.CONTAINER);
        String tableId = HudLayoutAuthoring.addChild(
                document, containerId, HudNodeKind.TABLE);
        String stackId = HudLayoutAuthoring.addChild(
                document, tableId, HudNodeKind.STACK);
        String groupId = HudLayoutAuthoring.addChild(
                document, stackId, HudNodeKind.GROUP);

        assertEquals("container-1", containerId);
        assertEquals("table-1", tableId);
        assertEquals("stack-1", stackId);
        assertEquals("group-1", groupId);
        assertChild(document.root.children.get(0), HudPlacementKind.FREE, true, false);
        HudNode container = document.root.children.get(0).node;
        assertNotNull(container.container);
        assertFalse(container.container.clip);
        assertChild(container.children.get(0), HudPlacementKind.DIRECT, false, false);
        HudNode table = container.children.get(0).node;
        assertChild(findChild(document.root, stackId), HudPlacementKind.CELL, false, true);
        HudNode stack = table.table.rows.get(0).cells.get(0).content;
        assertChild(stack.children.get(0), HudPlacementKind.DIRECT, false, false);

        List<HudHierarchyEntry> hierarchy = HudHierarchyProjection.from(document);
        assertEquals(List.of(0, 1, 2, 3, 4),
                hierarchy.stream().map(HudHierarchyEntry::depth).toList());
        assertEquals(List.of("root", containerId, tableId, stackId, groupId),
                hierarchy.stream().map(HudHierarchyEntry::nodeId).toList());
    }

    @Test
    public void rejectsMissingLeafAndFullContainerTargetsWithoutMutation() {
        HudDocumentV1 document = document();
        HudNode leaf = new HudNode("leaf", HudNodeKind.LABEL);
        leaf.label = new games.pixscape.runtime.hud.document.HudLabelData();
        leaf.label.text = "Leaf";
        leaf.label.styleName = "hud-body";
        document.root.children.add(free(leaf));
        String containerId = HudLayoutAuthoring.addChild(
                document, "root", HudNodeKind.CONTAINER);

        assertFalse(HudLayoutAuthoring.canAddChild(document, null));
        assertFalse(HudLayoutAuthoring.canAddChild(document, "missing"));
        assertFalse(HudLayoutAuthoring.canAddChild(document, "leaf"));
        assertTrue(HudLayoutAuthoring.canAddChild(document, containerId));
        assertEquals("table-1", HudLayoutAuthoring.addChild(
                document, containerId, HudNodeKind.TABLE));
        assertFalse(HudLayoutAuthoring.canAddChild(document, containerId));
        int childCount = find(document.root, containerId).children.size();

        assertNull(HudLayoutAuthoring.addChild(document, containerId, HudNodeKind.GROUP));
        assertNull(HudLayoutAuthoring.addChild(document, "leaf", HudNodeKind.GROUP));
        assertNull(HudLayoutAuthoring.addChild(document, "missing", HudNodeKind.GROUP));
        assertEquals(childCount, find(document.root, containerId).children.size());

        HudDocumentEditSession session = new HudDocumentEditSession(
                asset(), document);
        int historyBefore = session.historySize();
        add(session, "missing", HudNodeKind.GROUP);
        add(session, containerId, HudNodeKind.GROUP);
        assertEquals(historyBefore, session.historySize());
    }

    @Test
    public void transactionsUndoRedoAndBranchFromTheCurrentTree() {
        HudScreenAsset asset = asset();
        HudDocumentEditSession session = new HudDocumentEditSession(asset, document());
        add(session, "root", HudNodeKind.GROUP);
        add(session, "root", HudNodeKind.GROUP);
        assertEquals(2, session.historySize());
        assertTrue(session.undo());

        add(session, "root", HudNodeKind.TABLE);

        assertFalse(session.canRedo());
        assertEquals(2, session.historySize());
        assertEquals("group-1", session.document().root.children.get(0).node.id);
        assertEquals("table-1", session.document().root.children.get(1).node.id);
        assertTrue(session.undo());
        assertEquals(1, session.document().root.children.size());
        assertTrue(session.redo());
        assertEquals("table-1", session.document().root.children.get(1).node.id);
    }

    @Test
    public void undoingContainerChildLeavesValidEmptyContainer() {
        HudScreenAsset asset = asset();
        HudDocumentEditSession session = new HudDocumentEditSession(asset, document());
        String[] containerId = new String[1];
        session.edit("Add Container child", candidate -> {
            containerId[0] = HudLayoutAuthoring.addChild(
                    candidate, "root", HudNodeKind.CONTAINER);
            return candidate;
        });
        add(session, containerId[0], HudNodeKind.TABLE);

        assertTrue(session.undo());
        HudNode emptyContainer = find(session.document().root, containerId[0]);
        assertNotNull(emptyContainer);
        assertTrue(emptyContainer.children.isEmpty());
        assertTrue(HudLayoutAuthoring.canAddChild(session.document(), containerId[0]));
        assertTrue(session.redo());
        assertEquals(1, find(session.document().root, containerId[0]).children.size());
    }

    @Test
    public void separateHudSessionsKeepTreesHistoryAndContainerCapacityIndependent() {
        HudDocumentEditSession first = new HudDocumentEditSession(asset(), document());
        HudDocumentEditSession second = new HudDocumentEditSession(asset(), document());

        add(first, "root", HudNodeKind.CONTAINER);
        add(first, "container-1", HudNodeKind.TABLE);
        add(second, "root", HudNodeKind.CONTAINER);

        assertEquals(2, first.historySize());
        assertEquals(1, second.historySize());
        assertFalse(HudLayoutAuthoring.canAddChild(first.document(), "container-1"));
        assertTrue(HudLayoutAuthoring.canAddChild(second.document(), "container-1"));
        assertEquals(1, find(first.document().root, "container-1").children.size());
        assertTrue(find(second.document().root, "container-1").children.isEmpty());
    }

    @Test
    public void imageIdsAndPlacementRemainParentDriven() {
        HudDocumentV1 document = document();
        HudNode occupied = new HudNode("image-1", HudNodeKind.IMAGE);
        occupied.image = new games.pixscape.runtime.hud.document.HudImageData();
        occupied.image.resourceName = "old__a1";
        document.root.children.add(free(occupied));
        assertEquals("image-2", HudLayoutAuthoring.nextImageId(document));

        String table = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.TABLE);
        String stack = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.STACK);
        String container = HudLayoutAuthoring.addChild(document, "root", HudNodeKind.CONTAINER);
        assertEquals("image-2", HudLayoutAuthoring.addImage(
                document, "root", "image-2", "group__a2"));
        assertEquals("image-3", HudLayoutAuthoring.addImage(
                document, table, "image-3", "table__a3"));
        assertEquals("image-4", HudLayoutAuthoring.addImage(
                document, stack, "image-4", "stack__a4"));
        assertEquals("image-5", HudLayoutAuthoring.addImage(
                document, container, "image-5", "container__a5"));

        assertEquals(HudPlacementKind.FREE, findChild(document.root, "image-2").placementKind);
        assertEquals(HudPlacementKind.CELL, findChild(document.root, "image-3").placementKind);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, "image-4").placementKind);
        assertEquals(HudPlacementKind.DIRECT, findChild(document.root, "image-5").placementKind);
        assertFalse(HudLayoutAuthoring.canAddChild(document, container));
    }

    private static void add(HudDocumentEditSession session, String parentId, HudNodeKind kind) {
        session.edit("Add " + kind, candidate -> {
            HudLayoutAuthoring.addChild(candidate, parentId, kind);
            return candidate;
        });
    }

    private static HudDocumentV1 document() {
        return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
    }

    private static HudScreenAsset asset() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/test.json";
        return asset;
    }

    private static HudChild free(HudNode node) {
        return HudChild.free(node, new games.pixscape.runtime.hud.document.HudFreePlacement());
    }

    private static HudNode find(HudNode node, String id) {
        if (id.equals(node.id)) return node;
        for (HudChild child : node.children) {
            HudNode found = find(child.node, id);
            if (found != null) return found;
        }
        if (node.table != null) for (HudTableRow row : node.table.rows)
            for (HudTableCell cell : row.cells) if (cell.content != null) {
                HudNode found = find(cell.content, id);
                if (found != null) return found;
            }
        return null;
    }

    private static HudChild findChild(HudNode node, String id) {
        for (HudChild child : node.children) {
            if (id.equals(child.node.id)) return child;
            HudChild nested = findChild(child.node, id);
            if (nested != null) return nested;
        }
        if (node.table != null) for (HudTableRow row : node.table.rows)
            for (HudTableCell cell : row.cells) if (cell.content != null) {
                if (id.equals(cell.content.id)) return HudChild.cell(cell.content, cell.constraints);
                HudChild nested = findChild(cell.content, id);
                if (nested != null) return nested;
            }
        return null;
    }

    private static void assertChild(HudChild child, HudPlacementKind placement,
                                    boolean hasFree, boolean hasCell) {
        assertEquals(placement, child.placementKind);
        assertEquals(hasFree, child.free != null);
        assertEquals(hasCell, child.cell != null);
    }
}
