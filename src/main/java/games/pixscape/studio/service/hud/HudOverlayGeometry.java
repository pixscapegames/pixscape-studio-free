package games.pixscape.studio.service.hud;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.utils.Align;

/** Scene2D-only geometry used by editor overlays. */
public final class HudOverlayGeometry {
    private HudOverlayGeometry() {}

    /** Returns the transformed Actor AABB in the supplied overlay's local coordinates. */
    public static Rectangle actorBoundsInOverlay(Actor actor, Actor overlay, Rectangle out) {
        if (actor == null || overlay == null || out == null) {
            throw new IllegalArgumentException("Actor, overlay, and output rectangle are required.");
        }
        return transformedAabb(actor, 0f, 0f, actor.getWidth(), actor.getHeight(), overlay, out);
    }

    /** Returns the native ScrollPane widget viewport, excluding background padding and scroll bars. */
    public static Rectangle scrollPaneContentBoundsInOverlay(
            ScrollPane pane, Actor overlay, Rectangle out) {
        if (pane == null || overlay == null || out == null) {
            throw new IllegalArgumentException("ScrollPane, overlay, and output rectangle are required.");
        }
        Rectangle local = scrollPaneContentBoundsLocal(pane, new Rectangle());
        return transformedAabb(pane, local.x, local.y, local.width, local.height, overlay, out);
    }

    /** Returns the native widget viewport in ScrollPane-local coordinates. */
    public static Rectangle scrollPaneContentBoundsLocal(ScrollPane pane, Rectangle out) {
        if (pane == null || out == null) {
            throw new IllegalArgumentException("ScrollPane and output rectangle are required.");
        }
        pane.validate();
        pane.layout(); // Applies the native visual scroll amount to the widget before reading its viewport.
        Actor widget = pane.getActor();
        float x;
        float y;
        if (widget != null) {
            x = widget.getX() + (int) pane.getVisualScrollX();
            y = widget.getY() + (int) (pane.getMaxY() - pane.getVisualScrollY());
        } else {
            ScrollPane.ScrollPaneStyle style = pane.getStyle();
            x = style.background != null ? style.background.getLeftWidth() : 0f;
            y = style.background != null ? style.background.getBottomHeight() : 0f;
        }
        return out.set(x, y, pane.getScrollWidth(), pane.getScrollHeight());
    }

    /** Window clips its authored Table children to the padded content, excluding the title. */
    public static Rectangle windowContentBoundsLocal(Window window, Rectangle out) {
        if (window == null || out == null) {
            throw new IllegalArgumentException("Window and output rectangle are required.");
        }
        return out.set(window.getPadLeft(), window.getPadBottom(),
                Math.max(0f, window.getWidth() - window.getPadLeft() - window.getPadRight()),
                Math.max(0f, window.getHeight() - window.getPadTop() - window.getPadBottom()));
    }

    public static Rectangle windowContentBoundsInOverlay(Window window, Actor overlay, Rectangle out) {
        Rectangle local = windowContentBoundsLocal(window, new Rectangle());
        return transformedAabb(window, local.x, local.y, local.width, local.height, overlay, out);
    }

    /** Intersects actor bounds with every native clipping ancestor. */
    public static Rectangle visibleActorBoundsInOverlay(Actor actor, Actor overlay, Rectangle out) {
        if (actor == null || overlay == null || out == null) {
            throw new IllegalArgumentException("Actor, overlay, and output rectangle are required.");
        }
        if (!isEffectivelyVisible(actor)) return out.set(0f, 0f, 0f, 0f);
        actorBoundsInOverlay(actor, overlay, out);
        return intersectClippingAncestors(actor, overlay, out);
    }

    /** Intersects a Table cell with every native clipping ancestor of its actor. */
    public static Rectangle visibleCellBoundsInOverlay(
            Cell<?> cell, Actor cellActor, Actor overlay, Rectangle out) {
        if (cell == null || cellActor == null || overlay == null || out == null) {
            throw new IllegalArgumentException("Cell, actor, overlay, and output rectangle are required.");
        }
        if (!isEffectivelyVisible(cellActor)) return out.set(0f, 0f, 0f, 0f);
        cellBoundsInOverlay(cell, overlay, out);
        return intersectClippingAncestors(cellActor, overlay, out);
    }

    /** Returns native allocated cell bounds even when the cell deliberately has no Actor. */
    public static Rectangle visibleCellBoundsInOverlay(Cell<?> cell, Actor overlay, Rectangle out) {
        if (cell == null || overlay == null || out == null) {
            throw new IllegalArgumentException("Cell, overlay, and output rectangle are required.");
        }
        Table table = cell.getTable();
        if (table == null || !isEffectivelyVisible(table)) return out.set(0f, 0f, 0f, 0f);
        cellBoundsInOverlay(cell, overlay, out);
        if (table instanceof Window window) {
            intersect(out, windowContentBoundsInOverlay(window, overlay, new Rectangle()));
        }
        return intersectClippingAncestors(table, overlay, out);
    }

    /** Scene2D hides an entire branch when any of its actors is invisible. */
    public static boolean isEffectivelyVisible(Actor actor) {
        if (actor == null) return false;
        for (Actor current = actor; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    private static Rectangle intersectClippingAncestors(Actor actor, Actor overlay, Rectangle visible) {
        Rectangle clip = new Rectangle();
        for (Actor ancestor = actor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof ScrollPane pane) {
                scrollPaneContentBoundsInOverlay(pane, overlay, clip);
            } else if (ancestor instanceof Window window) {
                windowContentBoundsInOverlay(window, overlay, clip);
            } else if ((ancestor instanceof Container<?> container && container.getClip())
                    || (ancestor instanceof Table table && table.getClip())) {
                actorBoundsInOverlay(ancestor, overlay, clip);
            } else {
                continue;
            }
            intersect(visible, clip);
            if (visible.width <= 0f || visible.height <= 0f) return visible;
        }
        return visible;
    }

    private static void intersect(Rectangle visible, Rectangle clip) {
        float minX = Math.max(visible.x, clip.x);
        float minY = Math.max(visible.y, clip.y);
        float maxX = Math.min(visible.x + visible.width, clip.x + clip.width);
        float maxY = Math.min(visible.y + visible.height, clip.y + clip.height);
        visible.set(minX, minY, Math.max(0f, maxX - minX), Math.max(0f, maxY - minY));
    }

    /**
     * Returns the full allocated Table Cell rectangle, including its padding area, in overlay-local
     * coordinates. This intentionally differs from the child Actor's inner bounds.
     *
     * <p>Allocation math is adapted and modified from Skin Composer's Scene Composer
     * {@code EditWidget}, commit 131acac21a4511fd0b577505f31dd6064e9e0418.</p>
     */
    public static Rectangle cellBoundsInOverlay(Cell<?> cell, Actor overlay, Rectangle out) {
        if (cell == null || overlay == null || out == null) {
            throw new IllegalArgumentException("Cell, overlay, and output rectangle are required.");
        }
        Table table = cell.getTable();
        if (table == null) throw new IllegalArgumentException("Cell must belong to a Table.");
        table.validate();

        float contentWidth = 0f;
        float contentHeight = 0f;
        for (int column = 0; column < table.getColumns(); column++) {
            contentWidth += table.getColumnWidth(column);
        }
        for (int row = 0; row < table.getRows(); row++) {
            contentHeight += table.getRowHeight(row);
        }

        float x = table.getPadLeft();
        float y = table.getPadBottom();
        float availableWidth = table.getWidth() - table.getPadLeft() - table.getPadRight();
        float availableHeight = table.getHeight() - table.getPadTop() - table.getPadBottom();
        if ((table.getAlign() & Align.right) != 0) x += availableWidth - contentWidth;
        else if ((table.getAlign() & Align.left) == 0) x += (availableWidth - contentWidth) * 0.5f;
        if ((table.getAlign() & Align.top) != 0) y += availableHeight - contentHeight;
        else if ((table.getAlign() & Align.bottom) == 0) y += (availableHeight - contentHeight) * 0.5f;

        for (int column = 0; column < cell.getColumn(); column++) {
            x += table.getColumnWidth(column);
        }
        // Scene2D rows are indexed from the top, while local Y grows from the bottom.
        for (int row = table.getRows() - 1; row > cell.getRow(); row--) {
            y += table.getRowHeight(row);
        }

        float cellWidth = 0f;
        for (int column = cell.getColumn();
             column < cell.getColumn() + cell.getColspan(); column++) {
            cellWidth += table.getColumnWidth(column);
        }
        return transformedAabb(table, x, y, cellWidth,
                table.getRowHeight(cell.getRow()), overlay, out);
    }

    private static Rectangle transformedAabb(Actor coordinateSpace, float x, float y,
                                             float width, float height,
                                             Actor overlay, Rectangle out) {
        Vector2 corner = transform(coordinateSpace, overlay, x, y);
        float minX = corner.x;
        float maxX = corner.x;
        float minY = corner.y;
        float maxY = corner.y;

        corner = transform(coordinateSpace, overlay, x + width, y);
        minX = Math.min(minX, corner.x);
        maxX = Math.max(maxX, corner.x);
        minY = Math.min(minY, corner.y);
        maxY = Math.max(maxY, corner.y);
        corner = transform(coordinateSpace, overlay, x + width, y + height);
        minX = Math.min(minX, corner.x);
        maxX = Math.max(maxX, corner.x);
        minY = Math.min(minY, corner.y);
        maxY = Math.max(maxY, corner.y);
        corner = transform(coordinateSpace, overlay, x, y + height);
        minX = Math.min(minX, corner.x);
        maxX = Math.max(maxX, corner.x);
        minY = Math.min(minY, corner.y);
        maxY = Math.max(maxY, corner.y);
        return out.set(minX, minY, maxX - minX, maxY - minY);
    }

    private static Vector2 transform(Actor coordinateSpace, Actor overlay, float x, float y) {
        Vector2 point = new Vector2(x, y);
        coordinateSpace.localToStageCoordinates(point);
        return overlay.stageToLocalCoordinates(point);
    }
}
