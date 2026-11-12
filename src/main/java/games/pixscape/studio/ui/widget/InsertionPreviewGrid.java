package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.Array;

/**
 * Responsive insertion slots, independent of payloads and authored data. Relayout never detaches cells.
 */
public final class InsertionPreviewGrid extends WidgetGroup {
    private final Array<Actor> items = new Array<>();
    private final Array<Actor> ghosts = new Array<>();
    private final float cellWidth, cellHeight;
    private Actor emptyActor;
    private int columns = 1;
    private int insertion = -1;
    private boolean[] moving;
    private int movingCount;

    public InsertionPreviewGrid(float cellWidth, float cellHeight) {
        if (cellWidth <= 0 || cellHeight <= 0) throw new IllegalArgumentException("Invalid cell size.");
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        setTouchable(Touchable.enabled);
    }

    public void setItems(Array<Actor> actors, Actor emptyActor) {
        endMove();
        clearChildren();
        items.clear();
        items.addAll(actors);
        ghosts.clear();
        insertion = -1;
        this.emptyActor = emptyActor;
        for (Actor actor : items) addActor(actor);
        if (emptyActor != null) addActor(emptyActor);
        invalidateHierarchy();
    }

    public int columns() {
        return columns;
    }

    public int insertionIndex() {
        return insertion;
    }

    @Override
    public float getPrefWidth() {
        return cellWidth;
    }

    @Override
    public float getPrefHeight() {
        int count = items.size - movingCount + ghosts.size;
        return count == 0 ? (emptyActor == null ? 0 : 40) : ((count + columns - 1) / columns) * cellHeight;
    }

    @Override
    protected void sizeChanged() {
        super.sizeChanged();
        int next = Math.max(1, (int) (getWidth() / cellWidth));
        if (next != columns) {
            columns = next;
            invalidateHierarchy();
        }
    }

    @Override
    public void layout() {
        for (int i = 0, visible = 0; i < items.size; i++) {
            if (moving != null && moving[i]) continue;
            place(items.get(i), visible + (insertion >= 0 && visible >= insertion ? ghosts.size : 0));
            visible++;
        }
        for (int i = 0; i < ghosts.size; i++) place(ghosts.get(i), insertion + i);
        if (emptyActor != null) {
            emptyActor.setVisible(items.isEmpty() && ghosts.isEmpty());
            emptyActor.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    private void place(Actor actor, int slot) {
        float width = Math.min(cellWidth, getWidth());
        actor.setBounds((slot % columns) * width + 2, getHeight() - (slot / columns + 1) * cellHeight + 2,
                Math.max(0, width - 4), cellHeight - 4);
    }

    /**
     * Map visual slots back to source indices. The entire ghost span maps to its one insertion point.
     */
    public int insertionAt(float x, float y) {
        validate();
        if (items.size == movingCount) return 0;
        float width = Math.min(cellWidth, getWidth());
        if (width <= 0) return 0;
        int row = Math.max(0, (int) ((getHeight() - y) / cellHeight));
        int column = Math.max(0, Math.min(columns - 1, (int) (x / width)));
        int slot = row * columns + column;
        if (insertion >= 0 && slot >= insertion && slot < insertion + ghosts.size) return insertion;
        int index = slot - (insertion >= 0 && slot >= insertion + ghosts.size ? ghosts.size : 0);
        if (x - column * width >= width / 2) index++;
        return Math.max(0, Math.min(items.size - movingCount, index));
    }

    public void showInsertion(int index, Array<Actor> actors) {
        if (insertion == index && ghosts.size > 0) return;
        if (ghosts.isEmpty()) {
            ghosts.addAll(actors);
            for (Actor actor : ghosts) {
                actor.getColor().a = .4f;
                actor.setTouchable(Touchable.disabled);
                addActor(actor);
            }
        }
        insertion = Math.max(0, Math.min(index, items.size - movingCount));
        invalidateHierarchy();
    }

    /**
     * Internal moves use gaps in the remaining sequence; hidden actors stay attached and retain focus bindings.
     */
    public void beginMove(int[] indices) {
        endMove();
        moving = new boolean[items.size];
        for (int index : indices) {
            if (index < 0 || index >= items.size || moving[index])
                throw new IllegalArgumentException("Invalid moving index.");
            moving[index] = true;
            movingCount++;
            items.get(index).setVisible(false);
        }
        invalidateHierarchy();
    }

    public void endMove() {
        if (moving == null) return;
        for (int i = 0; i < items.size; i++) if (moving[i]) items.get(i).setVisible(true);
        moving = null;
        movingCount = 0;
        invalidateHierarchy();
    }

    public void clearInsertion() {
        if (insertion < 0) return;
        for (Actor actor : ghosts) actor.remove();
        ghosts.clear();
        insertion = -1;
        invalidateHierarchy();
    }
}
