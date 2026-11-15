package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;

import java.util.List;

/**
 * Drawing-only insertion projection. Node actors and their touch focus never move.
 */
final class ItemMoveTree extends IdVisTree {
    record Row(EntityNode node, int depth, boolean ghost, float top, float height) {
    }

    private List<Row> preview;
    private Rectangle culling;
    private final java.util.ArrayList<EntityNode> visibleNodes = new java.util.ArrayList<>();

    @Override
    public void layout() {
        super.layout();
        visibleNodes.clear();
        collectVisible(getRootNodes());
    }

    private void collectVisible(com.badlogic.gdx.utils.Array<EntityNode> nodes) {
        for (EntityNode node : nodes) {
            visibleNodes.add(node);
            if (node.isExpanded()) {
                collectVisible(node.getChildren());
            }
        }
    }

    List<EntityNode> visibleNodes() {
        validate();
        return visibleNodes;
    }

    void preview(List<Row> rows) {
        preview = rows;
    }

    List<Row> preview() {
        return preview;
    }

    @Override
    public void setCullingArea(Rectangle area) {
        super.setCullingArea(area);
        culling = area;
    }

    private int firstAt(float y) {
        int low = 0, high = preview.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            Row row = preview.get(mid);
            if (row.top() - row.height() > y) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    Row previewRowAt(float y) {
        int index = firstAt(y);
        if (index == preview.size()) return null;
        Row row = preview.get(index);
        return y <= row.top() ? row : null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (culling == null) {
            drawRows(batch, parentAlpha);
            return;
        }
        // Reserve the fixed eye column inside the viewport, including during horizontal scrolling and DnD.
        batch.flush();
        if (clipBegin(getX() + culling.x, getY() + culling.y,
                Math.max(0, culling.width - ItemVisibilityColumn.WIDTH), culling.height)) {
            drawRows(batch, parentAlpha);
            batch.flush();
            clipEnd();
        }
    }

    private void drawRows(Batch batch, float parentAlpha) {
        if (preview == null) {
            super.draw(batch, parentAlpha);
            return;
        }
        validate();
        float packed = batch.getPackedColor();
        // Like the normal tree, clip to the ScrollPane; visit only visible rows after a binary lookup.
        for (int i = culling == null ? 0 : firstAt(culling.y + culling.height); i < preview.size(); i++) {
            Row row = preview.get(i);
            if (culling != null && row.top() < culling.y) break;
            var node = row.node();
            float x = getX() + 18 + row.depth() * getIndentSpacing();
            float y = getY() + row.top() - row.height();
            float alpha = parentAlpha * getColor().a * (row.ghost() ? .45f : 1f);
            if (row.ghost() || getSelection().contains(node)) {
                batch.setColor(1, 1, 1, alpha);
                if (getStyle().selection != null) getStyle().selection.draw(batch, getX(), y, getWidth(), row.height());
            }
            if (!row.ghost() && node.getChildren().size > 0) {
                var toggle = node.isExpanded() ? getStyle().minus : getStyle().plus;
                batch.setColor(1, 1, 1, alpha);
                toggle.draw(batch, x - 16, y + (row.height() - toggle.getMinHeight()) / 2,
                        toggle.getMinWidth(), toggle.getMinHeight());
            }
            if (node.getIcon() != null) {
                batch.setColor(1, 1, 1, alpha);
                node.getIcon().draw(batch, x, y + (row.height() - 16) / 2, 16, 16);
                x += 20;
            }
            var font = node.getLabel().getStyle().font;
            float fontColor = font.getColor().toFloatBits();
            var color = node.getLabel().getColor();
            font.setColor(color.r, color.g, color.b, alpha);
            font.draw(batch, node.getLabel().getText(), x, y + (row.height() + font.getCapHeight()) / 2);
            com.badlogic.gdx.graphics.Color.abgr8888ToColor(font.getColor(), fontColor);
        }
        batch.setPackedColor(packed);
    }
}
