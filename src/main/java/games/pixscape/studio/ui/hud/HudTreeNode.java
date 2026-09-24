package games.pixscape.studio.ui.hud;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTree;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.ui.tree.StudioTreeRow;

/** One native tree entry; rows and result headings are derived presentation only. */
public final class HudTreeNode extends VisTree.Node {
    public enum Type { WIDGET, CELL, ROW, RESULTS }

    private final Type type;
    private final String nodeId;
    private final String cellId;
    private final String rowAnchorCellId;
    private final HudNodeKind kind;
    private final StudioTreeRow row;

    public HudTreeNode(String nodeId, HudNodeKind kind) {
        this(Type.WIDGET, nodeId, null, null, kind, nodeId + "  ·  " + kind, "node:" + nodeId);
    }

    static HudTreeNode cell(String cellId, String label) {
        return new HudTreeNode(Type.CELL, null, cellId, null, null, label, "cell:" + cellId);
    }

    static HudTreeNode row(int rowIndex, String firstCellId) {
        return new HudTreeNode(Type.ROW, null, null, firstCellId, null,
                "Row " + (rowIndex + 1), "row:" + firstCellId);
    }

    static HudTreeNode results(String dialogId) {
        return new HudTreeNode(Type.RESULTS, null, null, null, null,
                "Result buttons", "results:" + dialogId);
    }

    private HudTreeNode(Type type, String nodeId, String cellId, String rowAnchorCellId,
                        HudNodeKind kind, String label, String key) {
        this.type = type;
        this.nodeId = nodeId;
        this.cellId = cellId;
        this.rowAnchorCellId = rowAnchorCellId;
        this.kind = kind;
        row = new StudioTreeRow(label);
        setActor(row.actor());
        setValue(key);
        setSelectable(type == Type.WIDGET || type == Type.CELL);
    }

    public Type type() { return type; }
    public String nodeId() { return nodeId; }
    public String cellId() { return cellId; }
    public String rowAnchorCellId() { return rowAnchorCellId; }
    public HudNodeKind kind() { return kind; }
    public VisLabel label() { return row.label(); }
}
