package games.pixscape.studio.ui.tree;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;

/** Shared visual row used by Studio trees; domain identity remains owned by each node type. */
public final class StudioTreeRow {
    private final VisTable actor = new VisTable();
    private final VisLabel label;

    public StudioTreeRow(String text) {
        label = new VisLabel(text);
        actor.add(label).left();
    }

    public VisTable actor() { return actor; }
    public VisLabel label() { return label; }
    public void setText(String text) { label.setText(text); }
}
