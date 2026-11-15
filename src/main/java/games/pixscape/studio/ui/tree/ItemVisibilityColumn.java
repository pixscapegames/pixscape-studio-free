package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.studio.service.LayerService;

/** Fixed viewport gutter before the scrollbar: clicks never reach tree selection or DnD. */
final class ItemVisibilityColumn extends Widget {
    static final float WIDTH = 26;
    private final ItemMoveTree tree;
    private final CheckBox.CheckBoxStyle eye = VisUI.getSkin().get("eye", CheckBox.CheckBoxStyle.class);
    private final Vector2 position = new Vector2();
    private final VisLabel explanation = new VisLabel("");
    private LayerService layers;

    ItemVisibilityColumn(ItemMoveTree tree) {
        this.tree = tree;
        setName("items-visibility");
        new Tooltip.Builder(explanation).target(this).build();
        addListener(new ClickListener(Input.Buttons.LEFT) {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                EntityNode node = nodeAt(y);
                if (eligible(node) && node == nodeAt(getTouchDownY())) {
                    int entity = node.getEntityId();
                    layers.setTiledEditorVisible(entity, !layers.isTiledEditorVisible(entity));
                    describe(node);
                }
                event.stop();
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                describe(nodeAt(y));
                return false;
            }
        });
    }

    void bind(LayerService layers) {
        this.layers = layers;
    }

    private EntityNode nodeAt(float y) {
        if (tree.preview() != null) {
            return null;
        }
        tree.stageToLocalCoordinates(localToStageCoordinates(position.set(0, y)));
        return tree.getNodeAt(position.y);
    }

    private boolean eligible(EntityNode node) {
        return layers != null && node != null && (node.isEntityNode() || node.isTiledMapNode())
                && layers.hasTiledVisibilityControl(node.getEntityId());
    }

    private void describe(EntityNode node) {
        explanation.setText(!eligible(node) ? "Editing visibility"
                : !layers.isTiledEditorVisible(node.getEntityId()) ? "Hidden in Studio. Click to show."
                : !layers.isPublishedContentVisible(node.getEntityId())
                ? "Enabled in Studio, but hidden by a parent or Runtime visibility. Click to hide."
                : "Visible in Studio. Click to hide. Previews and exports are unchanged.");
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // Layout owns the flattened row list. Scrolling only translates it; no ancestry walk per frame.
        var nodes = tree.visibleNodes();
        stageToLocalCoordinates(tree.localToStageCoordinates(position.set(0, 0)));
        float offset = position.y;
        float packed = batch.getPackedColor();
        if (tree.preview() != null) {
            for (ItemMoveTree.Row row : tree.preview()) {
                drawEye(batch, row.node(), offset + row.top() - row.height(), row.height(), parentAlpha);
            }
        } else {
            int low = 0, high = nodes.size();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (nodes.get(mid).getActor().getY() + offset > getHeight()) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            for (int i = low; i < nodes.size(); i++) {
                EntityNode node = nodes.get(i);
                float y = node.getActor().getY() + offset;
                if (y + node.getActor().getHeight() < 0) {
                    break;
                }
                drawEye(batch, node, y, node.getActor().getHeight(), parentAlpha);
            }
        }
        batch.setPackedColor(packed);
    }

    private void drawEye(Batch batch, EntityNode node, float y, float height, float alpha) {
        if (!eligible(node)) {
            return;
        }
        Drawable icon = layers.isTiledEditorVisible(node.getEntityId()) ? eye.checkboxOn : eye.checkboxOff;
        y += (height - icon.getMinHeight()) / 2;
        if (y < 0 || y + icon.getMinHeight() > getHeight()) {
            return;
        }
        boolean inherited = layers.isTiledEditorVisible(node.getEntityId())
                && !layers.isPublishedContentVisible(node.getEntityId());
        batch.setColor(1, 1, 1, alpha * (inherited ? .35f : 1));
        icon.draw(batch, getX() + (WIDTH - icon.getMinWidth()) / 2, getY() + y,
                icon.getMinWidth(), icon.getMinHeight());
    }
}
