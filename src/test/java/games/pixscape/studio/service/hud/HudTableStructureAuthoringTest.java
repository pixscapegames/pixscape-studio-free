package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableLayout;
import org.junit.Test;

import static games.pixscape.studio.service.hud.HudLayoutAuthoring.StructureAction.*;
import static org.junit.Assert.*;

public class HudTableStructureAuthoringTest {
    @Test public void windowAndDialogContentTablesUseTheSameStructuralCommands() {
        for (HudNodeKind kind : new HudNodeKind[]{HudNodeKind.WINDOW, HudNodeKind.DIALOG}) {
            HudNode root = new HudNode("root", HudNodeKind.GROUP);
            HudNode owner = new HudNode("owner", kind);
            owner.actor.width = 240f;
            owner.actor.height = 160f;
            if (kind == HudNodeKind.WINDOW) owner.window = new HudWindowData();
            else owner.dialog = new HudDialogData();
            owner.table = HudLayoutAuthoring.newTableLayout(root, 1, 2, false);
            root.children.add(HudChild.free(owner, new HudFreePlacement()));
            HudDocumentV1 document = new HudDocumentV1(root);
            String anchor = owner.table.rows.get(0).cells.get(0).id;
            HudLayoutAuthoring.editStructure(document, INSERT_ROW_AFTER, anchor, anchor);
            assertEquals(2, owner.table.rows.size());
            HudLayoutAuthoring.editStructure(document, INSERT_COLUMN_AFTER, anchor, anchor);
            assertEquals(3, owner.table.columns);
            valid(document);
        }
    }
    @Test public void minimumAndMaximumGridDimensionsDisableStructuralCommands() {
        HudDocumentV1 single = grid(1, 1);
        String cell = single.root.table.rows.get(0).cells.get(0).id;
        assertNotNull(HudLayoutAuthoring.structureRejection(single, DELETE_ROW, cell, cell));
        assertNotNull(HudLayoutAuthoring.structureRejection(single, DELETE_COLUMN, cell, cell));
        HudDocumentV1 maxRows = grid(64, 1);
        String rowCell = maxRows.root.table.rows.get(0).cells.get(0).id;
        assertNotNull(HudLayoutAuthoring.structureRejection(maxRows, INSERT_ROW_BEFORE,
                rowCell, rowCell));
        HudDocumentV1 maxColumns = grid(1, 64);
        String columnCell = maxColumns.root.table.rows.get(0).cells.get(0).id;
        assertNotNull(HudLayoutAuthoring.structureRejection(maxColumns, INSERT_COLUMN_AFTER,
                columnCell, columnCell));
    }
    @Test public void rowInsertionUsesNewMinimaAndDeletionNeverLosesContent() {
        HudDocumentV1 document = grid(2, 2);
        HudTableLayout table = document.root.table;
        HudTableCell original = table.rows.get(0).cells.get(0);
        original.constraints.minWidth = 13f;
        String inserted = HudLayoutAuthoring.editStructure(document, INSERT_ROW_BEFORE,
                original.id, original.id);
        assertEquals(3, table.rows.size());
        assertEquals(inserted, table.rows.get(0).cells.get(0).id);
        assertEquals(Float.valueOf(64f), table.rows.get(0).cells.get(0).constraints.minWidth);
        assertEquals(Float.valueOf(32f), table.rows.get(0).cells.get(0).constraints.minHeight);
        assertEquals(Float.valueOf(13f), original.constraints.minWidth);
        String after = HudLayoutAuthoring.editStructure(document, INSERT_ROW_AFTER,
                original.id, original.id);
        assertEquals(after, table.rows.get(2).cells.get(0).id);
        assertNotEquals(inserted, after);
        valid(document);

        original.content = new HudNode("widget", HudNodeKind.GROUP);
        assertNotNull(HudLayoutAuthoring.structureRejection(document, DELETE_ROW,
                original.id, original.id));
        assertEquals(4, table.rows.size());
        original.content = null;
        HudLayoutAuthoring.editStructure(document, DELETE_ROW, original.id, original.id);
        assertEquals(3, table.rows.size());
        valid(document);
    }

    @Test public void columnInsertionAndDeletionRespectDifferentSpansAtomically() {
        HudDocumentV1 document = grid(2, 3);
        HudTableLayout table = document.root.table;
        HudTableCell left = table.rows.get(0).cells.get(0);
        HudTableCell right = table.rows.get(0).cells.get(2);
        HudTableCell secondRowSpan = table.rows.get(1).cells.get(1);
        left.colspan = 2;
        table.rows.get(0).cells.remove(1);
        secondRowSpan.colspan = 2;
        table.rows.get(1).cells.remove(2);
        secondRowSpan.content = new HudNode("kept", HudNodeKind.GROUP);
        secondRowSpan.constraints.minWidth = 19f;
        valid(document);

        String inserted = HudLayoutAuthoring.editStructure(document, INSERT_COLUMN_BEFORE,
                right.id, right.id);
        assertEquals(4, table.columns);
        assertEquals(inserted, table.rows.get(0).cells.get(1).id);
        assertEquals(3, secondRowSpan.colspan);
        assertEquals("kept", secondRowSpan.content.id);
        assertEquals(Float.valueOf(19f), secondRowSpan.constraints.minWidth);
        valid(document);

        HudTableCell occupiedInOtherRow = table.rows.get(1).cells.get(0);
        occupiedInOtherRow.content = new HudNode("blocking", HudNodeKind.GROUP);
        assertNotNull(HudLayoutAuthoring.structureRejection(document, DELETE_COLUMN,
                left.id, left.id, 0));
        assertEquals(4, table.columns);
        occupiedInOtherRow.content = null;
        left.content = new HudNode("wide", HudNodeKind.GROUP);
        String leftId = left.id;
        HudLayoutAuthoring.editStructure(document, DELETE_COLUMN, left.id, left.id, 0);
        assertEquals(3, table.columns);
        assertEquals(leftId, left.id);
        assertEquals(1, left.colspan);
        assertEquals("wide", left.content.id);
        valid(document);
    }

    @Test public void mergeAndSplitPreserveAnchorConstraintsAndSingleWidget() {
        HudDocumentV1 document = grid(1, 4);
        HudTableLayout table = document.root.table;
        HudTableCell first = table.rows.get(0).cells.get(0);
        HudTableCell second = table.rows.get(0).cells.get(1);
        HudTableCell third = table.rows.get(0).cells.get(2);
        first.constraints.minWidth = 17f;
        second.constraints.minWidth = 29f;
        third.content = new HudNode("right-widget", HudNodeKind.GROUP);
        String selected = HudLayoutAuthoring.editStructure(document, MERGE, first.id, third.id);
        assertEquals(first.id, selected);
        assertEquals(3, first.colspan);
        assertEquals("right-widget", first.content.id);
        assertEquals(Float.valueOf(17f), first.constraints.minWidth);
        assertEquals(2, table.rows.get(0).cells.size());
        valid(document);

        first.content = null;
        String split = HudLayoutAuthoring.editStructure(document, SPLIT, first.id, first.id);
        assertEquals(first.id, split);
        assertEquals(1, first.colspan);
        assertEquals(Float.valueOf(17f), first.constraints.minWidth);
        assertEquals(Float.valueOf(64f), table.rows.get(0).cells.get(1).constraints.minWidth);
        assertNotEquals(second.id, table.rows.get(0).cells.get(1).id);
        valid(document);

        table.rows.get(0).cells.get(0).content = new HudNode("one", HudNodeKind.GROUP);
        table.rows.get(0).cells.get(1).content = new HudNode("two", HudNodeKind.GROUP);
        assertNotNull(HudLayoutAuthoring.structureRejection(document, MERGE,
                table.rows.get(0).cells.get(0).id, table.rows.get(0).cells.get(1).id));
    }

    @Test public void mergeOfAlreadyMergedCellsAddsTheirSpansWithoutChangingColumns() {
        HudDocumentV1 document = grid(1, 4);
        HudTableLayout table = document.root.table;
        HudTableCell left = table.rows.get(0).cells.get(0);
        HudTableCell right = table.rows.get(0).cells.get(2);
        left.colspan = 2;
        table.rows.get(0).cells.remove(1);
        right.colspan = 2;
        table.rows.get(0).cells.remove(2);
        right.content = new HudNode("kept", HudNodeKind.GROUP);
        assertEquals(left.id, HudLayoutAuthoring.editStructure(document, MERGE,
                right.id, left.id));
        assertEquals(4, left.colspan);
        assertEquals(4, table.columns);
        assertEquals("kept", left.content.id);
        valid(document);
    }

    @Test public void editSessionUndoRedoAndCodecRestoreExactStructure() {
        HudDocumentV1 document = grid(1, 2);
        String firstId = document.root.table.rows.get(0).cells.get(0).id;
        String secondId = document.root.table.rows.get(0).cells.get(1).id;
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/structure.json";
        try (HudDocumentEditSession edits = new HudDocumentEditSession(asset, document)) {
            edits.edit("Merge cells", candidate -> {
                HudLayoutAuthoring.editStructure(candidate, MERGE, firstId, secondId);
                return candidate;
            });
            assertEquals(1, edits.historySize());
            assertEquals(2, HudLayoutAuthoring.cell(edits.document(), firstId).colspan);
            assertTrue(edits.undo());
            assertNotNull(HudLayoutAuthoring.cell(edits.document(), secondId));
            assertTrue(edits.redo());
            assertNull(HudLayoutAuthoring.cell(edits.document(), secondId));
            HudDocumentV1 reopened = new HudDocumentCodec().read(new HudDocumentCodec().write(edits.document()));
            assertEquals(2, HudLayoutAuthoring.cell(reopened, firstId).colspan);
            valid(reopened);
        }
    }

    private static HudDocumentV1 grid(int rows, int columns) {
        HudNode root = new HudNode("root", HudNodeKind.TABLE);
        root.table = HudLayoutAuthoring.newTableLayout(root, rows, columns, false);
        return new HudDocumentV1(root);
    }

    private static void valid(HudDocumentV1 document) {
        assertTrue(new HudDocumentValidator().validate(document).isValid());
    }
}
