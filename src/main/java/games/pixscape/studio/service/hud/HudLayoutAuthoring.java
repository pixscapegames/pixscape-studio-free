package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudContainerData;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudImageTextButtonData;
import games.pixscape.runtime.hud.document.HudTextFieldData;
import games.pixscape.runtime.hud.document.HudTextraLabelData;
import games.pixscape.runtime.hud.document.HudSelectBoxData;
import games.pixscape.runtime.hud.document.HudListData;
import games.pixscape.runtime.hud.document.HudCheckBoxData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.runtime.hud.document.HudProgressBarData;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudDialogResultButton;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableLayout;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.studio.ui.config.CommonLayout;

import java.util.HashSet;
import java.util.Set;

/** Pure mutations for the deliberately narrow STEP-6B.2 layout-child authoring slice. */
public final class HudLayoutAuthoring {
    public enum StructureAction {
        INSERT_ROW_BEFORE, INSERT_ROW_AFTER, INSERT_COLUMN_BEFORE, INSERT_COLUMN_AFTER,
        DELETE_ROW, DELETE_COLUMN, MERGE, SPLIT
    }

    public record CellPosition(String tableId, int row, int cellIndex, int column, int colspan) {
        public int endColumn() { return column + colspan; }
    }

    public static CellPosition cellPosition(HudDocumentV1 document, String cellId) {
        HudNode owner = tableOwner(document, cellId);
        if (owner == null || owner.table == null) return null;
        for (int rowIndex = 0; rowIndex < owner.table.rows.size(); rowIndex++) {
            int column = 0;
            HudTableRow row = owner.table.rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < row.cells.size(); cellIndex++) {
                HudTableCell cell = row.cells.get(cellIndex);
                if (cell.id.equals(cellId)) return new CellPosition(owner.id, rowIndex,
                        cellIndex, column, cell.colspan);
                column += cell.colspan;
            }
        }
        return null;
    }

    /** Null means the requested structural edit is currently available. */
    public static String structureRejection(HudDocumentV1 document, StructureAction action,
                                            String anchorId, String otherId) {
        CellPosition anchor = cellPosition(document, anchorId);
        return structureRejection(document, action, anchorId, otherId,
                anchor != null ? anchor.column() : -1);
    }

    public static String structureRejection(HudDocumentV1 document, StructureAction action,
                                            String anchorId, String otherId, int logicalColumn) {
        CellPosition anchor = cellPosition(document, anchorId);
        if (anchor == null) return "Select a cell in a table.";
        HudNode table = node(document, anchor.tableId());
        HudTableLayout layout = table.table;
        HudTableRow row = layout.rows.get(anchor.row());
        switch (action) {
            case INSERT_ROW_BEFORE, INSERT_ROW_AFTER -> {
                if (layout.rows.size() >= 64) return "The table already has 64 rows.";
            }
            case INSERT_COLUMN_BEFORE, INSERT_COLUMN_AFTER -> {
                if (layout.columns >= 64) return "The table already has 64 columns.";
            }
            case DELETE_ROW -> {
                if (layout.rows.size() == 1) return "The last row cannot be removed.";
                for (HudTableCell cell : row.cells)
                    if (cell.content != null) return "Empty the row before deleting it.";
            }
            case DELETE_COLUMN -> {
                if (layout.columns == 1) return "The last column cannot be removed.";
                if (logicalColumn < anchor.column() || logicalColumn >= anchor.endColumn())
                    return "Choose a logical column within the selected cell.";
                int column = logicalColumn;
                for (HudTableRow candidateRow : layout.rows) {
                    int start = 0;
                    for (HudTableCell cell : candidateRow.cells) {
                        if (column >= start && column < start + cell.colspan) {
                            if (cell.colspan == 1 && cell.content != null)
                                return "A widget occupies this column in another row.";
                            break;
                        }
                        start += cell.colspan;
                    }
                }
            }
            case MERGE -> {
                CellPosition other = cellPosition(document, otherId);
                if (other == null || !anchor.tableId().equals(other.tableId())
                        || anchor.row() != other.row()) return "Select cells in the same row.";
                int left = Math.min(anchor.cellIndex(), other.cellIndex());
                int right = Math.max(anchor.cellIndex(), other.cellIndex());
                if (left == right) return "Select at least two cells.";
                int occupied = 0;
                for (int index = left; index <= right; index++)
                    if (row.cells.get(index).content != null) occupied++;
                if (occupied > 1) return "The range contains more than one widget.";
            }
            case SPLIT -> {
                if (anchor.colspan() == 1) return "This cell is not merged.";
            }
        }
        return null;
    }

    /** Mutates an isolated candidate only and returns the cell to select after publication. */
    public static String editStructure(HudDocumentV1 candidate, StructureAction action,
                                       String anchorId, String otherId) {
        CellPosition anchor = cellPosition(candidate, anchorId);
        return editStructure(candidate, action, anchorId, otherId,
                anchor != null ? anchor.column() : -1);
    }

    public static String editStructure(HudDocumentV1 candidate, StructureAction action,
                                       String anchorId, String otherId, int logicalColumn) {
        String rejection = structureRejection(candidate, action, anchorId, otherId, logicalColumn);
        if (rejection != null) throw new HudEditRejectedException(rejection);
        CellPosition anchor = cellPosition(candidate, anchorId);
        HudTableLayout layout = node(candidate, anchor.tableId()).table;
        HudTableRow row = layout.rows.get(anchor.row());
        switch (action) {
            case INSERT_ROW_BEFORE, INSERT_ROW_AFTER -> {
                int at = anchor.row() + (action == StructureAction.INSERT_ROW_AFTER ? 1 : 0);
                HudTableRow added = new HudTableRow();
                layout.rows.add(at, added);
                for (int column = 0; column < layout.columns; column++)
                    added.cells.add(newStructureCell(candidate.root));
                return added.cells.get(Math.min(anchor.column(), layout.columns - 1)).id;
            }
            case INSERT_COLUMN_BEFORE, INSERT_COLUMN_AFTER -> {
                int boundary = action == StructureAction.INSERT_COLUMN_BEFORE
                        ? anchor.column() : anchor.endColumn();
                String selected = null;
                for (int rowIndex = 0; rowIndex < layout.rows.size(); rowIndex++) {
                    HudTableRow targetRow = layout.rows.get(rowIndex);
                    int start = 0;
                    for (int index = 0; index < targetRow.cells.size(); index++) {
                        HudTableCell cell = targetRow.cells.get(index);
                        int end = start + cell.colspan;
                        if (boundary > start && boundary < end) {
                            cell.colspan++;
                            if (rowIndex == anchor.row()) selected = cell.id;
                            break;
                        }
                        if (boundary <= start || boundary == end) {
                            int at = boundary == end ? index + 1 : index;
                            HudTableCell added = newStructureCell(candidate.root);
                            targetRow.cells.add(at, added);
                            if (rowIndex == anchor.row()) selected = added.id;
                            break;
                        }
                        start = end;
                    }
                }
                layout.columns++;
                return selected;
            }
            case DELETE_ROW -> {
                layout.rows.remove(anchor.row());
                int nextRow = Math.min(anchor.row(), layout.rows.size() - 1);
                return cellCovering(layout.rows.get(nextRow), anchor.column()).id;
            }
            case DELETE_COLUMN -> {
                int column = logicalColumn;
                for (HudTableRow targetRow : layout.rows) {
                    int start = 0;
                    for (int index = 0; index < targetRow.cells.size(); index++) {
                        HudTableCell cell = targetRow.cells.get(index);
                        if (column >= start && column < start + cell.colspan) {
                            if (cell.colspan > 1) cell.colspan--;
                            else targetRow.cells.remove(index);
                            break;
                        }
                        start += cell.colspan;
                    }
                }
                layout.columns--;
                HudTableRow surviving = layout.rows.get(anchor.row());
                return cellCovering(surviving, Math.min(column, layout.columns - 1)).id;
            }
            case MERGE -> {
                CellPosition other = cellPosition(candidate, otherId);
                int left = Math.min(anchor.cellIndex(), other.cellIndex());
                int right = Math.max(anchor.cellIndex(), other.cellIndex());
                HudTableCell merged = row.cells.get(left);
                for (int index = right; index > left; index--) {
                    HudTableCell removed = row.cells.remove(index);
                    merged.colspan += removed.colspan;
                    if (removed.content != null) merged.content = removed.content;
                }
                return merged.id;
            }
            case SPLIT -> {
                HudTableCell split = row.cells.get(anchor.cellIndex());
                int span = split.colspan;
                split.colspan = 1;
                for (int offset = 1; offset < span; offset++)
                    row.cells.add(anchor.cellIndex() + offset, newStructureCell(candidate.root));
                return split.id;
            }
        }
        throw new IllegalStateException("Unsupported HUD table structure action.");
    }

    private static HudTableCell cellCovering(HudTableRow row, int column) {
        int start = 0;
        for (HudTableCell cell : row.cells) {
            if (column < start + cell.colspan) return cell;
            start += cell.colspan;
        }
        throw new IllegalStateException("HUD row has no cell at logical column " + column + ".");
    }

    private static HudTableCell newStructureCell(HudNode root) {
        Set<String> ids = new HashSet<>();
        collectCellIds(root, ids);
        HudTableCell cell = new HudTableCell();
        do { cell.id = "cell-" + java.util.UUID.randomUUID(); }
        while (ids.contains(cell.id));
        cell.constraints.minWidth = 64f;
        cell.constraints.minHeight = 32f;
        return cell;
    }
    private HudLayoutAuthoring() {
    }

    public static boolean canAddChild(HudDocumentV1 document, String parentId) {
        return document != null && canAccept(find(document.root, parentId));
    }

    /** Removes one non-root child relation and returns its surviving parent ID, or {@code null}. */
    public static String removeChild(HudDocumentV1 candidate, String nodeId) {
        if (candidate == null || candidate.root == null || nodeId == null
                || nodeId.equals(candidate.root.id)) return null;
        Set<String> removed = new HashSet<>();
        String parentId = removeChild(candidate.root, nodeId, removed);
        if (parentId != null) removeWindowActionsTo(candidate.root, removed);
        return parentId;
    }

    public static String addDialogResultButton(HudDocumentV1 candidate, String dialogId,
                                               HudNodeKind kind) {
        HudNode dialog = node(candidate, dialogId);
        if (dialog == null || dialog.kind != HudNodeKind.DIALOG || dialog.dialog == null
                || kind != HudNodeKind.TEXT_BUTTON && kind != HudNodeKind.IMAGE_BUTTON
                && kind != HudNodeKind.IMAGE_TEXT_BUTTON) return null;
        String id = nextId(candidate.root, kind == HudNodeKind.TEXT_BUTTON ? "text-button"
                : kind == HudNodeKind.IMAGE_BUTTON ? "image-button" : "image-text-button");
        HudNode button = new HudNode(id, kind);
        if (kind == HudNodeKind.TEXT_BUTTON) {
            button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
            button.textButton.text = "Button";
        } else if (kind == HudNodeKind.IMAGE_BUTTON) {
            button.imageButton = new games.pixscape.runtime.hud.document.HudImageButtonData();
        } else {
            button.imageTextButton = new games.pixscape.runtime.hud.document.HudImageTextButtonData();
            button.imageTextButton.text = "Button";
        }
        int suffix = 1;
        String resultId = "result-1";
        boolean taken;
        do {
            taken = false;
            for (HudDialogResultButton entry : dialog.dialog.resultButtons)
                if (entry != null && resultId.equals(entry.resultId)) { taken = true; break; }
            if (taken) resultId = "result-" + ++suffix;
        } while (taken);
        dialog.dialog.resultButtons.add(new HudDialogResultButton(button, resultId, true));
        return id;
    }

    public static boolean moveDialogResultButton(HudDocumentV1 candidate, String buttonId, int offset) {
        HudNode dialog = resultButtonOwner(candidate, buttonId);
        if (dialog == null || (offset != -1 && offset != 1)) return false;
        var entries = dialog.dialog.resultButtons;
        for (int i = 0; i < entries.size(); i++) if (buttonId.equals(entries.get(i).button.id)) {
            int target = i + offset;
            if (target < 0 || target >= entries.size()) return false;
            java.util.Collections.swap(entries, i, target);
            return true;
        }
        return false;
    }

    public static HudNode resultButtonOwner(HudDocumentV1 document, String buttonId) {
        return document == null ? null : resultButtonOwner(document.root, buttonId);
    }

    public static HudDialogResultButton resultButton(HudDocumentV1 document, String buttonId) {
        HudNode owner = resultButtonOwner(document, buttonId);
        if (owner == null) return null;
        for (HudDialogResultButton entry : owner.dialog.resultButtons)
            if (buttonId.equals(entry.button.id)) return entry;
        return null;
    }

    private static HudNode resultButtonOwner(HudNode node, String buttonId) {
        if (node == null || buttonId == null) return null;
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (HudDialogResultButton entry : node.dialog.resultButtons)
                if (entry != null && entry.button != null && buttonId.equals(entry.button.id)) return node;
        if (node.children != null) for (HudChild child : node.children) if (child != null) {
            HudNode owner = resultButtonOwner(child.node, buttonId);
            if (owner != null) return owner;
        }
        if (node.table != null && node.table.rows != null) for (HudTableRow row : node.table.rows)
            if (row != null && row.cells != null) for (HudTableCell cell : row.cells) if (cell != null) {
                HudNode owner = resultButtonOwner(cell.content, buttonId);
                if (owner != null) return owner;
            }
        return null;
    }

    /** Returns the immediate parent of one non-root node, without mutating the document. */
    public static String parentId(HudDocumentV1 document, String nodeId) {
        if (document == null || document.root == null || nodeId == null
                || nodeId.equals(document.root.id)) return null;
        return parentId(document.root, nodeId);
    }

    /** Returns a node's immediate authored child relation, or {@code null} for the root/missing node. */
    public static HudChild childRelation(HudDocumentV1 document, String nodeId) {
        if (document == null || document.root == null || nodeId == null
                || nodeId.equals(document.root.id)) return null;
        return childRelation(document.root, nodeId);
    }

    /** Returns the explicit cell that owns a widget, or null for non-tabular placement. */
    public static HudTableCell containingCell(HudDocumentV1 document, String nodeId) {
        return document != null ? containingCell(document.root, nodeId) : null;
    }

    /** Returns an explicit cell by its persistent ID. */
    public static HudTableCell cell(HudDocumentV1 document, String cellId) {
        return document != null ? cell(document.root, cellId) : null;
    }

    /** Returns the TABLE, WINDOW, or DIALOG that owns an explicit cell. */
    public static HudNode tableOwner(HudDocumentV1 document, String cellId) {
        return document != null ? tableOwner(document.root, cellId) : null;
    }

    /** Exchanges only the direct contents of two cells in the same authored table. */
    public static boolean moveCellContent(HudDocumentV1 candidate, String tableId,
                                          String sourceCellId, String widgetId, String destinationCellId) {
        if (candidate == null || tableId == null || sourceCellId == null || widgetId == null
                || destinationCellId == null || sourceCellId.equals(destinationCellId)) return false;
        HudNode table = node(candidate, tableId);
        if (table == null || !isTabular(table.kind) || table.table == null) return false;
        HudTableCell source = directCell(table, sourceCellId);
        HudTableCell destination = directCell(table, destinationCellId);
        if (source == null || destination == null || source.content == null
                || !widgetId.equals(source.content.id)) return false;
        HudNode displaced = destination.content;
        destination.content = source.content;
        source.content = displaced;
        return true;
    }

    private static HudTableCell directCell(HudNode table, String cellId) {
        if (table.table.rows == null) return null;
        for (HudTableRow row : table.table.rows) if (row != null && row.cells != null)
            for (HudTableCell cell : row.cells)
                if (cell != null && cellId.equals(cell.id)) return cell;
        return null;
    }

    /** Returns the first unoccupied explicit cell in native document order. */
    public static HudTableCell firstEmptyCell(HudNode tableParent) {
        if (tableParent == null || !isTabular(tableParent.kind) || tableParent.table == null
                || tableParent.table.rows == null) return null;
        for (HudTableRow row : tableParent.table.rows) {
            if (row == null || row.cells == null) continue;
            for (HudTableCell cell : row.cells) if (cell != null && cell.content == null) return cell;
        }
        return null;
    }

    /** Returns the live authored node by ID, or {@code null}. */
    public static HudNode node(HudDocumentV1 document, String nodeId) {
        return document != null ? find(document.root, nodeId) : null;
    }

    /** Mutates only the supplied isolated candidate and returns the new persisted ID, or null. */
    public static String addChild(
            HudDocumentV1 candidate, String parentId, HudNodeKind childKind) {
        if (!isAuthorableKind(childKind) || candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String childId = nextId(candidate.root, prefix(childKind));
        HudNode child = new HudNode(childId, childKind);
        if (childKind == HudNodeKind.TABLE) child.table = newTableLayout(candidate.root, 1, 1, true);
        if (childKind == HudNodeKind.CONTAINER) child.container = new HudContainerData();
        if (childKind == HudNodeKind.SCROLL_PANE) {
            child.scrollPane = new HudScrollPaneData();
            applyFreeCreationDefaults(child);
        }
        if (childKind == HudNodeKind.WINDOW) {
            child.window = new HudWindowData();
            child.table = newTableLayout(candidate.root, 1, 1, false);
            child.actor.width = 240f;
            child.actor.height = 160f;
        }
        if (childKind == HudNodeKind.DIALOG) {
            child.dialog = new HudDialogData();
            child.table = newTableLayout(candidate.root, 1, 1, false);
            child.dialog.modal = parent == candidate.root && parent.kind == HudNodeKind.GROUP;
            child.actor.width = 240f;
            child.actor.height = 160f;
        }
        if (parent.kind == HudNodeKind.GROUP && childKind != HudNodeKind.WINDOW
                && childKind != HudNodeKind.DIALOG
                && !(parent == candidate.root && childKind == HudNodeKind.TABLE))
            applyFreeCreationDefaults(child);
        if (parent == candidate.root && parent.kind == HudNodeKind.GROUP
                && childKind == HudNodeKind.TABLE) {
            child.fillParent = true;
            parent.children.add(HudChild.direct(child));
        } else {
            addByParent(parent, child);
        }
        if ((childKind == HudNodeKind.WINDOW || childKind == HudNodeKind.DIALOG)
                && (parent.kind == HudNodeKind.TABLE || parent.kind == HudNodeKind.WINDOW
                || parent.kind == HudNodeKind.DIALOG)) {
            HudTableCell ownerCell = containingCell(candidate, childId);
            ownerCell.constraints.prefWidth = child.actor.width;
            ownerCell.constraints.prefHeight = child.actor.height;
        }
        return childId;
    }

    /** Adds a TABLE with an explicit, initially empty grid. */
    public static String addTable(HudDocumentV1 candidate, String parentId, int rows, int columns) {
        if (rows < 1 || columns < 1 || candidate == null) return null;
        String id = addChild(candidate, parentId, HudNodeKind.TABLE);
        HudNode table = id != null ? find(candidate.root, id) : null;
        if (table != null) {
            // addChild creates a normal 1x1 TABLE for the generic layout factory. Remove that
            // temporary authored grid before allocating the confirmed grid so IDs begin at the
            // first available cell and no detached cell identity influences allocation.
            table.table = null;
            table.table = newTableLayout(candidate.root, rows, columns, true);
        }
        return id;
    }

    public static String nextImageId(HudDocumentV1 document) {
        return document != null ? nextId(document.root, "image") : null;
    }

    /** Adds a Label using native Scene2D natural sizing unless the parent constrains it. */
    public static String addLabel(HudDocumentV1 candidate, String parentId,
                                  String text, String styleName) {
        if (candidate == null || text == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String labelId = nextId(candidate.root, "label");
        HudNode label = new HudNode(labelId, HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = text;
        label.label.styleName = styleName;
        addByParent(parent, label);
        return labelId;
    }

    /** Adds a Textra TypingLabel with the first-version authored defaults. */
    public static String addTextraLabel(HudDocumentV1 candidate, String parentId,
                                        String text, String styleName) {
        if (candidate == null || text == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String labelId = nextId(candidate.root, "textra-label");
        HudNode label = new HudNode(labelId, HudNodeKind.TEXTRA_LABEL);
        label.textraLabel = new HudTextraLabelData();
        label.textraLabel.text = text;
        label.textraLabel.styleName = styleName;
        label.textraLabel.typingEnabled = true;
        addByParent(parent, label);
        return labelId;
    }

    /** Adds a TextButton using native Scene2D natural sizing unless the parent constrains it. */
    public static String addTextButton(HudDocumentV1 candidate, String parentId,
                                       String text, String styleName) {
        if (candidate == null || text == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String buttonId = nextId(candidate.root, "text-button");
        HudNode button = new HudNode(buttonId, HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = text;
        button.textButton.styleName = styleName;
        addByParent(parent, button);
        return buttonId;
    }

    /** Adds an ImageButton using native Scene2D natural sizing unless the parent constrains it. */
    public static String addImageButton(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String buttonId = nextId(candidate.root, "image-button");
        HudNode button = new HudNode(buttonId, HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.styleName = styleName;
        addByParent(parent, button);
        return buttonId;
    }

    /** Adds an ImageTextButton using native Scene2D natural sizing unless the parent constrains it. */
    public static String addImageTextButton(HudDocumentV1 candidate, String parentId,
                                            String text, String styleName) {
        if (candidate == null || text == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String buttonId = nextId(candidate.root, "image-text-button");
        HudNode button = new HudNode(buttonId, HudNodeKind.IMAGE_TEXT_BUTTON);
        button.imageTextButton = new HudImageTextButtonData();
        button.imageTextButton.text = text;
        button.imageTextButton.styleName = styleName;
        addByParent(parent, button);
        return buttonId;
    }

    /** Adds a TextField using native Scene2D natural sizing unless the parent constrains it. */
    public static String addTextField(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;

        String fieldId = nextId(candidate.root, "text-field");
        HudNode field = new HudNode(fieldId, HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        field.textField.styleName = styleName;
        addByParent(parent, field);
        return fieldId;
    }

    /** Adds a SelectBox with the bounded native defaults and automatic dimensions. */
    public static String addSelectBox(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String boxId = nextId(candidate.root, "select-box");
        HudNode box = new HudNode(boxId, HudNodeKind.SELECT_BOX);
        box.selectBox = new HudSelectBoxData();
        box.selectBox.items.add("Option 1");
        box.selectBox.items.add("Option 2");
        box.selectBox.items.add("Option 3");
        box.selectBox.selectedIndex = 0;
        box.selectBox.styleName = styleName;
        addByParent(parent, box);
        return boxId;
    }

    /** Adds a native List with useful sample rows and automatic dimensions. */
    public static String addList(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String id = nextId(candidate.root, "list");
        HudNode list = new HudNode(id, HudNodeKind.LIST);
        list.list = new HudListData();
        list.list.items.add("Item 1");
        list.list.items.add("Item 2");
        list.list.items.add("Item 3");
        list.list.selectedIndex = 0;
        list.list.styleName = styleName;
        addByParent(parent, list);
        return id;
    }

    /** Adds a CheckBox using native Scene2D natural sizing unless the parent constrains it. */
    public static String addCheckBox(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String boxId = nextId(candidate.root, "check-box");
        HudNode box = new HudNode(boxId, HudNodeKind.CHECK_BOX);
        box.checkBox = new HudCheckBoxData();
        box.checkBox.text = "CheckBox";
        box.checkBox.styleName = styleName;
        addByParent(parent, box);
        return boxId;
    }

    /** Adds a Slider using native Scene2D defaults and natural sizing. */
    public static String addSlider(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String sliderId = nextId(candidate.root, "slider");
        HudNode slider = new HudNode(sliderId, HudNodeKind.SLIDER);
        slider.slider = new HudSliderData();
        slider.slider.styleName = styleName;
        addByParent(parent, slider);
        return sliderId;
    }

    /** Adds a non-interactive native ProgressBar using the Slider numeric defaults. */
    public static String addProgressBar(HudDocumentV1 candidate, String parentId, String styleName) {
        if (candidate == null) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String progressBarId = nextId(candidate.root, "progress-bar");
        HudNode progressBar = new HudNode(progressBarId, HudNodeKind.PROGRESS_BAR);
        progressBar.progressBar = new HudProgressBarData();
        progressBar.progressBar.styleName = styleName;
        addByParent(parent, progressBar);
        return progressBarId;
    }

    /** Adds a REGION Image while preserving the exact parent-driven placement policy. */
    public static String addImage(HudDocumentV1 candidate, String parentId,
                                  String expectedId, String resourceName) {
        return addImage(candidate, parentId, expectedId, resourceName, null);
    }

    /** Adds an Image and applies pointer-local coordinates only for a free-position Group parent. */
    public static String addImage(HudDocumentV1 candidate, String parentId,
                                  String expectedId, String resourceName,
                                  HudFreePlacement freePlacement) {
        return addImage(candidate, parentId, expectedId, resourceName, freePlacement, 0f, 0f);
    }

    /** Adds an Image with parent-appropriate natural sizing defaults. */
    public static String addImage(HudDocumentV1 candidate, String parentId,
                                  String expectedId, String resourceName,
                                  HudFreePlacement freePlacement,
                                  float naturalWidth, float naturalHeight) {
        if (candidate == null || resourceName == null || resourceName.isBlank()) return null;
        HudNode parent = find(candidate.root, parentId);
        if (!canAccept(parent)) return null;
        String imageId = nextId(candidate.root, "image");
        if (expectedId != null && !expectedId.equals(imageId)) {
            throw new HudEditRejectedException("HUD Image ID allocation changed before publication.");
        }
        HudNode image = new HudNode(imageId, HudNodeKind.IMAGE);
        image.image = new HudImageData();
        image.image.source = HudImageSource.REGION;
        image.image.resourceName = resourceName;
        if (parent.kind == HudNodeKind.GROUP && naturalWidth > 0f && naturalHeight > 0f) {
            image.actor.width = naturalWidth;
            image.actor.height = naturalHeight;
        }
        addByParent(parent, image, freePlacement);
        return imageId;
    }

    private static void addByParent(HudNode parent, HudNode child) {
        addByParent(parent, child, null);
    }

    private static void addByParent(HudNode parent, HudNode child, HudFreePlacement freePlacement) {
        switch (parent.kind) {
            case GROUP -> parent.children.add(HudChild.free(child,
                    freePlacement != null ? freePlacement : new HudFreePlacement()));
            case TABLE, WINDOW, DIALOG -> {
                HudTableCell cell = firstEmptyCell(parent);
                if (cell == null) throw new IllegalStateException("The selected HUD table has no empty cell.");
                cell.content = child;
            }
            case STACK, CONTAINER, SCROLL_PANE -> parent.children.add(HudChild.direct(child));
            default -> throw new IllegalStateException("Unsupported HUD authoring parent: "
                    + parent.kind + ".");
        }
    }

    private static boolean isAuthorableKind(HudNodeKind kind) {
        return kind == HudNodeKind.GROUP || kind == HudNodeKind.TABLE
                || kind == HudNodeKind.STACK || kind == HudNodeKind.CONTAINER
                || kind == HudNodeKind.SCROLL_PANE || kind == HudNodeKind.WINDOW
                || kind == HudNodeKind.DIALOG;
    }

    private static void applyFreeCreationDefaults(HudNode node) {
        node.actor.width = CommonLayout.DEFAULT_FREE_WIDTH;
        node.actor.height = CommonLayout.DEFAULT_FREE_HEIGHT;
    }

    private static String prefix(HudNodeKind kind) {
        return switch (kind) {
            case GROUP -> "group";
            case TABLE -> "table";
            case STACK -> "stack";
            case CONTAINER -> "container";
            case SCROLL_PANE -> "scroll-pane";
            case WINDOW -> "window";
            case DIALOG -> "dialog";
            default -> throw new IllegalArgumentException("HUD layout kind is not authorable: "
                    + kind + ".");
        };
    }

    private static boolean canAccept(HudNode node) {
        return node != null && (node.kind == HudNodeKind.GROUP
                || isTabular(node.kind) && firstEmptyCell(node) != null
                || node.kind == HudNodeKind.STACK
                || (node.kind == HudNodeKind.CONTAINER || node.kind == HudNodeKind.SCROLL_PANE) && node.children != null
                && node.children.isEmpty());
    }

    private static HudNode find(HudNode node, String nodeId) {
        if (node == null || nodeId == null) return null;
        if (nodeId.equals(node.id)) return node;
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (HudDialogResultButton entry : node.dialog.resultButtons) if (entry != null) {
                HudNode found = find(entry.button, nodeId);
                if (found != null) return found;
            }
        if (node.children != null) for (HudChild child : node.children) {
            if (child == null) continue;
            HudNode found = find(child.node, nodeId);
            if (found != null) return found;
        }
        if (isTabular(node.kind) && node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) {
                    HudNode found = cell != null ? find(cell.content, nodeId) : null;
                    if (found != null) return found;
                }
        }
        return null;
    }

    private static String removeChild(HudNode parent, String nodeId, Set<String> removed) {
        if (parent == null || parent.children == null) return null;
        if (parent.dialog != null && parent.dialog.resultButtons != null)
            for (int i = 0; i < parent.dialog.resultButtons.size(); i++) {
                HudDialogResultButton entry = parent.dialog.resultButtons.get(i);
                if (entry != null && entry.button != null && nodeId.equals(entry.button.id)) {
                    removed.add(nodeId);
                    parent.dialog.resultButtons.remove(i);
                    return parent.id;
                }
            }
        for (int index = 0; index < parent.children.size(); index++) {
            HudChild child = parent.children.get(index);
            if (child == null || child.node == null) continue;
            if (nodeId.equals(child.node.id)) {
                collectIds(child.node, removed);
                parent.children.remove(index);
                return parent.id;
            }
            String nestedParentId = removeChild(child.node, nodeId, removed);
            if (nestedParentId != null) return nestedParentId;
        }
        if (isTabular(parent.kind) && parent.table != null && parent.table.rows != null) {
            for (HudTableRow row : parent.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) {
                    if (cell == null || cell.content == null) continue;
                    if (nodeId.equals(cell.content.id)) {
                        collectIds(cell.content, removed);
                        cell.content = null;
                        return parent.id;
                    }
                    String nestedParentId = removeChild(cell.content, nodeId, removed);
                    if (nestedParentId != null) return nestedParentId;
                }
        }
        return null;
    }

    private static void removeWindowActionsTo(HudNode node, Set<String> removed) {
        if (node == null) return;
        if (node.windowActions != null) {
            node.windowActions.removeIf(action -> action != null && removed.contains(action.targetId));
        }
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (HudDialogResultButton entry : node.dialog.resultButtons)
                if (entry != null) removeWindowActionsTo(entry.button, removed);
        if (node.children != null) {
            for (HudChild child : node.children) {
                if (child != null) removeWindowActionsTo(child.node, removed);
            }
        }
        if (isTabular(node.kind) && node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) if (cell != null) removeWindowActionsTo(cell.content, removed);
        }
    }

    private static String parentId(HudNode parent, String nodeId) {
        if (parent == null || parent.children == null) return null;
        if (parent.dialog != null && parent.dialog.resultButtons != null)
            for (HudDialogResultButton entry : parent.dialog.resultButtons)
                if (entry != null && entry.button != null && nodeId.equals(entry.button.id))
                    return parent.id;
        for (HudChild child : parent.children) {
            if (child == null || child.node == null) continue;
            if (nodeId.equals(child.node.id)) return parent.id;
            String nestedParentId = parentId(child.node, nodeId);
            if (nestedParentId != null) return nestedParentId;
        }
        if (isTabular(parent.kind) && parent.table != null && parent.table.rows != null) {
            for (HudTableRow row : parent.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) {
                    if (cell == null || cell.content == null) continue;
                    if (nodeId.equals(cell.content.id)) return parent.id;
                    String nestedParentId = parentId(cell.content, nodeId);
                    if (nestedParentId != null) return nestedParentId;
                }
        }
        return null;
    }

    private static HudChild childRelation(HudNode parent, String nodeId) {
        if (parent == null || parent.children == null) return null;
        for (HudChild child : parent.children) {
            if (child == null || child.node == null) continue;
            if (nodeId.equals(child.node.id)) return child;
            HudChild nested = childRelation(child.node, nodeId);
            if (nested != null) return nested;
        }
        return null;
    }

    private static String nextId(HudNode root, String prefix) {
        Set<String> ids = new HashSet<>();
        collectIds(root, ids);
        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            String candidate = prefix + "-" + suffix;
            if (!ids.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("No available HUD node ID for " + prefix + ".");
    }

    /** Creates the complete first-version explicit grid without publishing any document mutation. */
    public static HudTableLayout newTableLayout(HudNode root, int rows, int columns,
                                                 boolean applyCreationMinimums) {
        if (rows < 1 || columns < 1) throw new IllegalArgumentException("Table dimensions must be positive.");
        HudTableLayout layout = new HudTableLayout();
        layout.columns = columns;
        Set<String> usedIds = new HashSet<String>();
        collectCellIds(root, usedIds);
        int nextSuffix = 1;
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            HudTableRow row = new HudTableRow();
            layout.rows.add(row);
            for (int columnIndex = 0; columnIndex < columns; columnIndex++) {
                HudTableCell cell = new HudTableCell();
                while (usedIds.contains("cell-" + nextSuffix)) nextSuffix++;
                cell.id = "cell-" + nextSuffix++;
                usedIds.add(cell.id);
                if (applyCreationMinimums) {
                    cell.constraints.minWidth = 64f;
                    cell.constraints.minHeight = 32f;
                }
                row.cells.add(cell);
            }
        }
        return layout;
    }

    private static void collectCellIds(HudNode node, Set<String> ids) {
        if (node == null) return;
        if (node.table != null && node.table.rows != null) for (HudTableRow row : node.table.rows)
            if (row != null && row.cells != null) for (HudTableCell cell : row.cells) {
                if (cell == null) continue;
                if (cell.id != null) ids.add(cell.id);
                collectCellIds(cell.content, ids);
            }
        if (node.children != null) for (HudChild child : node.children)
            if (child != null) collectCellIds(child.node, ids);
    }

    private static void collectIds(HudNode node, Set<String> ids) {
        if (node == null) return;
        if (node.id != null) ids.add(node.id);
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (HudDialogResultButton entry : node.dialog.resultButtons)
                if (entry != null) collectIds(entry.button, ids);
        if (node.children != null) for (HudChild child : node.children) {
            if (child != null) collectIds(child.node, ids);
        }
        if (isTabular(node.kind) && node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) if (cell != null) collectIds(cell.content, ids);
        }
    }

    private static HudTableCell containingCell(HudNode node, String nodeId) {
        if (node == null || nodeId == null) return null;
        if (isTabular(node.kind) && node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells) {
                    if (cell == null) continue;
                    if (cell.content != null && nodeId.equals(cell.content.id)) return cell;
                    HudTableCell nested = containingCell(cell.content, nodeId);
                    if (nested != null) return nested;
                }
        }
        if (node.children != null) for (HudChild child : node.children) if (child != null) {
            HudTableCell nested = containingCell(child.node, nodeId);
            if (nested != null) return nested;
        }
        return null;
    }

    private static HudTableCell cell(HudNode node, String cellId) {
        if (node == null || cellId == null) return null;
        if (node.table != null && node.table.rows != null) for (HudTableRow row : node.table.rows)
            if (row != null && row.cells != null) for (HudTableCell candidate : row.cells) {
                if (candidate == null) continue;
                if (cellId.equals(candidate.id)) return candidate;
                HudTableCell nested = cell(candidate.content, cellId);
                if (nested != null) return nested;
            }
        if (node.children != null) for (HudChild child : node.children) if (child != null) {
            HudTableCell nested = cell(child.node, cellId);
            if (nested != null) return nested;
        }
        return null;
    }

    private static HudNode tableOwner(HudNode node, String cellId) {
        if (node == null || cellId == null) return null;
        if (isTabular(node.kind) && node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell candidate : row.cells) {
                    if (candidate == null) continue;
                    if (cellId.equals(candidate.id)) return node;
                    HudNode nested = tableOwner(candidate.content, cellId);
                    if (nested != null) return nested;
                }
        }
        if (node.children != null) for (HudChild child : node.children) if (child != null) {
            HudNode nested = tableOwner(child.node, cellId);
            if (nested != null) return nested;
        }
        return null;
    }

    private static boolean isTabular(HudNodeKind kind) {
        return kind == HudNodeKind.TABLE || kind == HudNodeKind.WINDOW || kind == HudNodeKind.DIALOG;
    }
}
