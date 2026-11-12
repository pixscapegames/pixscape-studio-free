package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisTable;

/**
 * Local vertical insertion presentation. No layer indices, services or history mutations.
 * Like the animation grid, sources stay attached to preserve Scene2D touch focus.
 * The original table cells keep the scroll extent constant throughout the gesture.
 */
final class LayerInsertionPreview extends VisTable {
    private final Array<LayerRow> rows = new Array<>();
    private LayerRow source, ghost, hud;
    private int insertion = -1;

    LayerInsertionPreview() {
        super(false);
        top().pad(5).left();
    }

    void addRow(LayerRow row, boolean fixed) {
        add(row).growX().padBottom(fixed ? 6 : 2).row();
        if (fixed) hud = row;
        else rows.add(row);
    }

    void resetRows() {
        clearPreview();
        clearChildren();
        rows.clear();
        hud = null;
    }

    void showInsertion(LayerRow source, LayerRow ghost, int index) {
        if (this.source == source && insertion == index) return;
        this.source = source;
        this.ghost = ghost;
        source.setVisible(false);
        ghost.setTouchable(Touchable.disabled);
        ghost.getColor().a = .4f;
        if (ghost.getParent() != this) addActor(ghost);
        insertion = index;
        invalidate();
        validate();
    }

    void clearPreview() {
        if (source == null) return;
        source.setVisible(true);
        if (ghost != null) ghost.remove();
        source = ghost = null;
        insertion = -1;
        invalidate();
    }

    /**
     * Inverse of the displayed layout: the entire ghost maps to the same reduced gap.
     */
    int insertionAt(float y, LayerRow moving) {
        validate();
        if (hud != null && y >= hud.getY()) return -1;
        if (ghost != null && y >= ghost.getY() - 2 && y <= ghost.getTop()) return insertion;
        int gap = 0;
        for (LayerRow row : rows) {
            if (row == moving) continue;
            if (y >= row.getY() + row.getHeight() / 2) return gap;
            gap++;
        }
        return gap;
    }

    @Override
    public void layout() {
        super.layout();
        if (source == null || rows.isEmpty()) return;
        float top = rows.first().getTop();
        float height = rows.first().getHeight();
        float x = rows.first().getX(), width = rows.first().getWidth();
        int slot = 0;
        for (LayerRow row : rows) {
            if (row == source) continue;
            if (slot == insertion) slot++;
            row.setBounds(x, top - height - slot++ * (height + 2), width, height);
        }
        ghost.setBounds(x, top - height - insertion * (height + 2), width, height);
    }
}
