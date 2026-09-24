package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisTree;

/** String-ID tree index for the active HUD projection. Mouse clicks choose one entry. */
public final class HudVisTree extends VisTree<HudTreeNode, String> {
    private final ObjectMap<String, HudTreeNode> nodesById = new ObjectMap<>();
    private final ObjectMap<String, HudTreeNode> cellsById = new ObjectMap<>();
    private final ObjectMap<String, HudTreeNode> entriesByValue = new ObjectMap<>();

    public HudVisTree() {
        getSelection().setMultiple(false);
    }

    public void clearNodes() {
        super.clearChildren();
        nodesById.clear();
        cellsById.clear();
        entriesByValue.clear();
    }

    public void registerNode(HudTreeNode node) {
        if (node == null) return;
        if (node.nodeId() != null) nodesById.put(node.nodeId(), node);
        if (node.cellId() != null) cellsById.put(node.cellId(), node);
        entriesByValue.put((String) node.getValue(), node);
    }

    @Override public HudTreeNode findNode(String nodeId) {
        return nodeId == null ? null : nodesById.get(nodeId);
    }

    @Override public void restoreExpandedValues(Array<String> values) {
        for (String value : values) {
            HudTreeNode node = entriesByValue.get(value);
            if (node != null) {
                node.setExpanded(true);
                node.expandTo();
            }
        }
    }

    public HudTreeNode findCell(String cellId) {
        return cellId == null ? null : cellsById.get(cellId);
    }
}
